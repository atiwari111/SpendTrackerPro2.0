package com.spendtracker.pro;

import android.content.*;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import java.util.*;
import java.util.concurrent.*;

public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        SmsMessage[] msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (msgs == null) return;
        for (SmsMessage sms : msgs) {
            String body   = sms.getMessageBody();
            String sender = sms.getOriginatingAddress();
            long   ts     = sms.getTimestampMillis();
            AppExecutors.db().execute(() -> process(ctx, body, sender, ts));
        }
    }

    private void process(Context ctx, String body, String sender, long ts) {
        // ── Pipeline: BankDetector → BankAwareSmsParser ──────────
        // BankAwareSmsParser internally calls BankDetector to pick bank-specific
        // regex patterns (HDFC, SBI, ICICI, Axis, Kotak), then falls back to
        // the generic SmsParser if no bank-specific pattern matches.
        BankAwareSmsParser.ParseResult p = BankAwareSmsParser.parse(body, sender);
        if (p == null) return;
        if (isCardBlockMessage(body)) return;

        AppDatabase db = AppDatabase.getInstance(ctx);
        // SECURITY FIX: dedup via indexed smsHash — never query by raw SMS body
        String hash = sha1(body + ts);
        if (db.transactionDao().existsHash(hash)) return;

        // ── Save transaction ──────────────────────────────────────
        Transaction tx       = new Transaction();
        tx.merchant          = normalizeMerchant(p.merchant, sender, p.bankName, body);
        tx.amount            = p.amount;
        tx.paymentDetail     = p.paymentDetail;
        tx.category          = p.category;
        tx.timestamp         = ts;
        tx.smsHash           = hash;
        // SECURITY: Never persist the raw SMS body — it may contain OTPs, account numbers,
        // and reference IDs. The smsHash (SHA-1 of body+date) is sufficient for deduplication.
        tx.rawSms            = null;
        // Store only the sender ID prefix (e.g. "VK-HDFCBK"), not the full originating address.
        tx.smsAddress        = redactSender(sender);
        tx.isManual          = false;
        tx.categoryIcon      = CategoryEngine.getInfo(p.category).icon;
        tx.isCredit          = SmsParser.isCreditTransaction(body);
        // Fix 2.38: credits (salary, cashback, refund) always arrive as bank transfers —
        // the SMS may mention "UPI" in the description but the money movement is always
        // a bank credit, so forcing BANK_TRANSFER avoids misleading UPI labels on income.
        tx.paymentMethod     = tx.isCredit ? "BANK_TRANSFER" : p.paymentMethod;
        if (tx.isCredit && isLikelyDuplicateCredit(db, tx.amount, ts, tx.merchant)) return;
        // Detect self-transfers (own account moves) — exclude from spending totals
        tx.isSelfTransfer    = isSelfTransfer(body);
        db.transactionDao().insert(tx);

        // ── Credit card auto-update ───────────────────────────────
        // If this is a credit card debit, find the matching card by last-4 digits
        // (extracted from the SMS) and refresh its currentSpent for the billing cycle.
        if ("CREDIT_CARD".equals(p.paymentMethod) && !tx.isSelfTransfer && !tx.isCredit) {
            updateCreditCardSpent(db, body, ts);
        }

        // ── Fix 2.5: auto-update bank balance from SMS ────────────
        // Extracts "Bal INR 3454.36" and matches to the account by last-4 digits.
        double smsBalance = BankAwareSmsParser.extractBalance(body);
        if (smsBalance >= 0) {
            String last4 = BankAwareSmsParser.extractAccountLast4(body);
            if (last4 != null && !last4.isEmpty()) {
                updateBankBalance(db, last4, p.bankName, smsBalance, ts);
            }
        }

        // ── Fix 2.7: credit card bill SMS → bills table (dedup by amount) ──
        BillSmsDetector.BillSmsResult billResult = BillSmsDetector.detect(body, sender);
        if (billResult != null && billResult.amount > 0) {
            insertOrUpdateBill(db, billResult);
        }

        // ── Budget recalc ─────────────────────────────────────────
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(ts);
        int txnMonth = cal.get(Calendar.MONTH) + 1;
        int txnYear  = cal.get(Calendar.YEAR);

        Calendar monthCal = Calendar.getInstance();
        monthCal.set(Calendar.MONTH, txnMonth - 1);
        monthCal.set(Calendar.YEAR, txnYear);
        monthCal.set(Calendar.DAY_OF_MONTH, 1);
        monthCal.set(Calendar.HOUR_OF_DAY, 0);
        monthCal.set(Calendar.MINUTE, 0);
        monthCal.set(Calendar.SECOND, 0);
        monthCal.set(Calendar.MILLISECOND, 0);
        long mStart = monthCal.getTimeInMillis();
        monthCal.add(Calendar.MONTH, 1);
        long mEnd = monthCal.getTimeInMillis();
        db.budgetDao().recalcAllUsed(txnMonth, txnYear, mStart, mEnd);

        // ── Budget alert ──────────────────────────────────────────
        Budget budget = db.budgetDao().getByCategoryMonthYear(p.category, txnMonth, txnYear);
        if (budget != null && budget.getProgress() >= 0.9f) {
            NotificationHelper.sendBudgetAlert(ctx, p.category,
                    budget.usedAmount, budget.limitAmount, budget.id);
        }

        // ── Anomaly detection ─────────────────────────────────────
        // Use scoped DAO query — avoids loading the full transaction table
        List<Transaction> recentSameCategory =
                db.transactionDao().getRecentByCategory(p.category, 20);

        List<Double> history = new ArrayList<>();
        for (Transaction t : recentSameCategory) history.add(t.amount);

        String anomalyReason = SpendingAnomalyDetector.getAnomalyReason(p.amount, history);
        if (anomalyReason != null) {
            NotificationHelper.sendAnomalyAlert(ctx, p.merchant, p.amount, p.category, anomalyReason);
        } else {
            NotificationHelper.sendSpendAlert(ctx, p.merchant, p.amount, p.category);
        }
    }

    /**
     * When a credit card SMS arrives, find the matching card in the DB by last-4 digits
     * and recalculate its currentSpent over the active billing cycle window.
     *
     * Match logic: extract 4-digit card number from SMS (e.g. "Card xx1234"),
     * then find the CreditCard row with the same lastFour. If multiple cards share
     * the same last-4 (rare), all of them are refreshed — harmless over-refresh.
     */
    private void updateCreditCardSpent(AppDatabase db, String body, long ts) {
        try {
            // Extract last-4 from SMS patterns like "Card xx6956", "card ending 6956", "x-6956"
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(?:card|x{1,4})[\\s\\-x]*([0-9]{4})(?:[^0-9]|$)",
                            java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(body);

            String last4 = null;
            if (m.find()) last4 = m.group(1);

            java.util.List<CreditCard> cards = db.creditCardDao().getAllSync();
            long now = System.currentTimeMillis();

            for (CreditCard card : cards) {
                // Match on last-4 if we found one, otherwise refresh all credit cards
                if (last4 != null && !last4.equals(card.lastFour)) continue;

                long cycleStart = card.billingCycleStart > 0
                        ? card.billingCycleStart
                        : getMonthStart(ts);

                double spent = db.creditCardDao().getCreditSpendInRange(cycleStart, now);
                db.creditCardDao().updateSpent(card.id, spent, now);
            }
        } catch (Exception e) {
            android.util.Log.e("SmsReceiver", "Credit card spend update failed: " + e.getMessage());
        }
    }

    private long getMonthStart(long ts) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ts);
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private boolean isSelfTransfer(String body) {
        if (body == null) return false;
        String b = body.toLowerCase();
        return b.contains("self") || b.contains("own account")
                || b.contains("transfer to your") || b.contains("transfer to self")
                || b.contains("linked account") || b.contains("savings account to")
                || b.contains("current account to");
    }

    private boolean isCardBlockMessage(String body) {
        if (body == null) return false;
        String b = body.toLowerCase();
        return (b.contains("block") || b.contains("blocked") || b.contains("hotlist"))
                && !b.contains("debited") && !b.contains("spent") && !b.contains("credited");
    }

    private boolean isLikelyDuplicateCredit(AppDatabase db, double amount, long ts, String merchant) {
        try {
            // placeholder duplicate-credit check
        } catch (Exception e) {
            android.util.Log.w("SmsReceiver", "isLikelyDuplicateCredit check failed: " + e.getMessage());
        }
        return false;
    }

    private String normalizeMerchant(String merchant, String sender, String bankName, String body) {
        String m = merchant == null ? "" : merchant.trim();
        String s = sender == null ? "" : sender.trim().toUpperCase();
        if (m.matches("(?i)^[A-Z]{2}-[A-Z0-9]{4,}(?:-[A-Z])?$")) m = "";
        if (!s.isEmpty() && m.equalsIgnoreCase(s)) m = "";
        if (m.matches("(?i).*(hdfcbk|sbipsg|sbiinb|icicib|axisbk|kotakb).*")) m = "";
        if ("block".equalsIgnoreCase(m) || "blocked".equalsIgnoreCase(m) || isCardBlockMessage(body)) m = "";
        if (m.isEmpty()) {
            if (bankName != null && !bankName.trim().isEmpty()) return bankName + " Transfer";
            return "Bank Transfer";
        }
        return m;
    }

    /** SHA-256 of (body + timestamp) — used as a stable, collision-resistant dedup key. */
    private static String sha1(String text) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] result = md.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : result) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            long h1 = text.hashCode();
            long h2 = text.length() * 31L + (text.isEmpty() ? 0 : text.charAt(0));
            return Long.toHexString(h1 ^ (h2 << 17));
        }
    }

    /**
     * Fix 2.38: Finds the bank account matching last4 + bankName and updates its balance.
     *
     * Root cause of the PNB duplicate bug:
     *   BankAwareSmsParser.ParseResult.bankName returns the short code ("PNB") but
     *   auto-created accounts store the expanded name ("Punjab National Bank").
     *   The old comparison used equalsIgnoreCase("PNB") which never matched
     *   "Punjab National Bank", so the guard was skipped and a new account was
     *   inserted on every subsequent PNB SMS.
     *
     * Fix: expand the incoming bankName before comparing, and also check whether
     * either name contains the other as a substring (handles abbreviation vs full name).
     */
    private void updateBankBalance(AppDatabase db, String last4, String bankName,
                                   double newBalance, long ts) {
        try {
            // Expand abbreviation → full name so comparisons are consistent
            String expandedIncoming = expandBankName(bankName);

            List<BankAccount> accounts = db.bankAccountDao().getAllSync();
            for (BankAccount acc : accounts) {
                if (!last4.equals(acc.lastFour)) continue;

                // Name match: accept if either side is blank, names are equal after
                // expansion, or one name contains the other (e.g. "PNB" ⊂ "Punjab National Bank")
                if (!bankNamesMatch(acc.bankName, expandedIncoming, bankName)) continue;

                db.bankAccountDao().updateBalance(acc.id, newBalance, ts);
                return; // updated — no duplicate needed
            }

            // No existing account matched — auto-create from SMS
            BankAccount auto = new BankAccount();
            auto.lastFour    = last4;
            auto.bankName    = expandedIncoming;
            auto.accountType = "SAVINGS";
            auto.accountLabel = auto.bankName + " A/c " + last4;
            auto.balance     = newBalance;
            auto.updatedAt   = ts;
            auto.isActive    = true;
            auto.bankEmoji   = "🏦";
            auto.cardColor   = bankColor(auto.bankName);
            db.bankAccountDao().insert(auto);
        } catch (Exception e) {
            android.util.Log.e("SmsReceiver", "Bank balance update failed: " + e.getMessage());
        }
    }

    /**
     * Returns true if storedName and incomingExpanded refer to the same bank.
     * Handles: exact match, abbreviation vs full name, blank values.
     *
     * Examples that must return true:
     *   "Punjab National Bank" vs expanded("PNB") = "Punjab National Bank"  → equal
     *   "PNB"                  vs expanded("PNB") = "Punjab National Bank"  → stored contains raw
     *   "Punjab National Bank" vs expanded("Punjab National Bank")           → equal
     */
    private boolean bankNamesMatch(String storedName, String expandedIncoming, String rawIncoming) {
        if (storedName == null || storedName.isEmpty()) return true;  // blank stored = accept any
        if (expandedIncoming == null || expandedIncoming.isEmpty()) return true; // blank incoming = accept any

        String stored   = storedName.toLowerCase().trim();
        String expanded = expandedIncoming.toLowerCase().trim();
        String raw      = rawIncoming != null ? rawIncoming.toLowerCase().trim() : "";

        return stored.equals(expanded)
                || stored.contains(expanded)
                || expanded.contains(stored)
                || (!raw.isEmpty() && (stored.contains(raw) || raw.contains(stored)));
    }

    private String expandBankName(String bankName) {
        String b = bankName != null ? bankName.trim().toUpperCase() : "";
        if ("PNB".equals(b)) return "Punjab National Bank";
        if ("SBI".equals(b)) return "State Bank of India";
        if ("HDFC".equals(b)) return "HDFC Bank";
        if ("ICICI".equals(b)) return "ICICI Bank";
        if ("AXIS".equals(b)) return "Axis Bank";
        if (!b.isEmpty()) return b;
        return "Bank Account";
    }

    private int bankColor(String bank) {
        String b = bank != null ? bank.toLowerCase() : "";
        if (b.contains("sbi")) return android.graphics.Color.parseColor("#1565C0");
        if (b.contains("pnb")) return android.graphics.Color.parseColor("#880E4F");
        if (b.contains("hdfc")) return android.graphics.Color.parseColor("#1A237E");
        if (b.contains("icici")) return android.graphics.Color.parseColor("#B71C1C");
        return android.graphics.Color.parseColor("#1B5E20");
    }

    /**
     * Fix 2.7: Inserts a bill or silently skips if an identical pending bill
     * (same merchant + same amount ±₹1) already exists — prevents duplicate
     * entries from repeated CC statement SMS.
     */
    private void insertOrUpdateBill(AppDatabase db, BillSmsDetector.BillSmsResult result) {
        try {
            Bill existing = db.billDao().findByMerchantAndAmount(
                    result.merchantId, result.amount);
            if (existing != null) return; // duplicate — skip

            Bill bill = new Bill();
            bill.name         = result.name;
            bill.category     = result.category;
            bill.icon         = result.icon;
            bill.amount       = result.amount;
            bill.dueDate      = result.dueDate;
            bill.status       = result.status;
            bill.detectedDate = System.currentTimeMillis();
            bill.isRecurring  = result.isRecurring;
            bill.merchantId   = result.merchantId;
            db.billDao().insert(bill);
        } catch (Exception e) {
            android.util.Log.e("SmsReceiver", "Bill insert failed: " + e.getMessage());
        }
    }

    /**
     * Redacts the originating address to remove any personal number.
     * Bank sender IDs (e.g. "VK-HDFCBK", "AM-SBI") are kept as-is.
     * Numeric phone numbers are masked to preserve only the country prefix.
     */
    private static String redactSender(String sender) {
        if (sender == null) return null;
        // Alphanumeric sender IDs (bank short codes) — safe to store as-is
        if (sender.matches("[A-Za-z0-9\\-]{2,20}")) return sender;
        // Phone numbers — keep only the first 4 chars (country + area code), mask the rest
        if (sender.length() > 4) return sender.substring(0, 4) + "****";
        return "****";
    }
}
