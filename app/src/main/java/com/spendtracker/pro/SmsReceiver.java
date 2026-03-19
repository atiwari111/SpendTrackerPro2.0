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

        AppDatabase db = AppDatabase.getInstance(ctx);
        // SECURITY FIX: dedup via indexed smsHash — never query by raw SMS body
        String hash = sha1(body + ts);
        if (db.transactionDao().existsHash(hash)) return;

        // ── Save transaction ──────────────────────────────────────
        Transaction tx       = new Transaction();
        tx.merchant          = p.merchant;
        tx.amount            = p.amount;
        tx.paymentMethod     = p.paymentMethod;
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
        // Detect self-transfers (own account moves) — exclude from spending totals
        tx.isSelfTransfer    = isSelfTransfer(body);
        db.transactionDao().insert(tx);

        // ── Credit card auto-update ───────────────────────────────
        // If this is a credit card debit, find the matching card by last-4 digits
        // (extracted from the SMS) and refresh its currentSpent for the billing cycle.
        if ("CREDIT_CARD".equals(p.paymentMethod) && !tx.isSelfTransfer && !tx.isCredit) {
            updateCreditCardSpent(db, body, ts);
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
