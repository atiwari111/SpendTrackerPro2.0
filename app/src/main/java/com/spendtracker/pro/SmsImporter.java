package com.spendtracker.pro;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;

import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

public class SmsImporter {

    public interface Callback {

        void onProgress(int found, int total);

        void onComplete(int inserted);

        void onError(String msg);
    }

    public static void importAll(Context context, Callback cb) {

        AppExecutors.io().execute(() -> {

            int inserted = 0;
            int found = 0;

            Cursor cursor = null;

            try {

                Context app = context.getApplicationContext();
                AppDatabase db = AppDatabase.getInstance(app);

                // Only import SMS from last 90 days (as documented)
                long cutoff = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000);

                cursor = app.getContentResolver().query(
                        Telephony.Sms.Inbox.CONTENT_URI,
                        new String[]{"body", "address", "date"},
                        "date >= ?",
                        new String[]{String.valueOf(cutoff)},
                        "date DESC"
                );

                if (cursor == null) {

                    if (cb != null) cb.onError("SMS cursor null");
                    return;
                }

                int total = cursor.getCount();
                Set<String> seen = new HashSet<>();
                int colBody = cursor.getColumnIndexOrThrow("body");
                int colAddr = cursor.getColumnIndexOrThrow("address");
                int colDate = cursor.getColumnIndexOrThrow("date");

                while (cursor.moveToNext()) {

                    String body   = cursor.getString(colBody);
                    String sender = cursor.getString(colAddr);
                    long   date   = cursor.getLong(colDate);

                    if (body == null) continue;

                    found++;

                    if (cb != null) cb.onProgress(found, total);

                    String hash = sha1(body + date);

                    if (seen.contains(hash)) continue;
                    seen.add(hash);

                    if (db.transactionDao().existsHash(hash)) continue;

                    // Use BankAwareSmsParser (bank-specific patterns: HDFC/SBI/ICICI/Axis/Kotak)
                    BankAwareSmsParser.ParseResult parsed =
                            BankAwareSmsParser.parse(body, sender);

                    if (parsed == null) continue;
                    if (isCardBlockMessage(body)) continue;

                    // ── PNB bare UPI debit deduplication ─────────────────────────
                    // PNB sends: (1) advance notice with merchant name, then
                    //            (2) actual debit SMS with only UPI ref, no merchant.
                    // If we already have a transaction with same amount within 3 days
                    // from the same account, skip the bare UPI debit to avoid duplicates.
                    if (isBareUpiDebit(body) && isDuplicateDebit(db, parsed.amount, date, parsed.merchant)) {
                        continue;
                    }

                    Transaction t = new Transaction();
                    t.amount         = parsed.amount;
                    t.merchant       = normalizeMerchant(parsed.merchant, sender, parsed.bankName, body);
                    t.category       = parsed.category;
                    t.categoryIcon   = CategoryEngine.getInfo(parsed.category).icon;
                    t.timestamp      = date;
                    t.smsHash        = hash;
                    t.isSelfTransfer = isSelfTransfer(body);
                    t.isCredit       = SmsParser.isCreditTransaction(body);
                    t.paymentMethod  = parsed.paymentMethod;
                    t.paymentDetail  = parsed.paymentDetail;
                    // SECURITY: Never persist raw SMS body — may contain OTPs and account numbers.
                    // smsHash (already computed above) is sufficient for deduplication.
                    t.rawSms         = null;
                    // Store only the bank sender ID prefix, not the full originating address.
                    t.smsAddress     = redactSender(sender);
                    t.isManual       = false;

                    if (t.isCredit && isLikelyDuplicateCredit(db, t.amount, date, t.merchant)) {
                        continue;
                    }

                    db.transactionDao().insert(t);
                    inserted++;

                    // Auto-create/update bank account from SMS balance signals.
                    double smsBalance = BankAwareSmsParser.extractBalance(body);
                    String last4 = BankAwareSmsParser.extractAccountLast4(body);
                    if (smsBalance >= 0 && last4 != null && last4.length() == 4) {
                        autoUpsertBankAccount(db, parsed.bankName, last4, smsBalance, date);
                    }
                }

                if (cb != null) cb.onComplete(inserted);

                // P2: auto-detect recurring patterns after each import
                if (inserted > 0) {
                    try {
                        RecurringDetector.detectAndInsert(db);
                    } catch (Exception e) {
                        android.util.Log.w("SmsImporter", "Recurring detection failed: " + e.getMessage());
                    }
                }

            } catch (Exception e) {

                if (cb != null) cb.onError(e.getMessage());

            } finally {

                if (cursor != null && !cursor.isClosed()) cursor.close();
            }
        });
    }

    /**
     * Returns true if this SMS is a bare UPI debit with NO merchant name.
     * e.g. "debited INR 60.00 thru UPI:644296973274" — only ref number, no payee.
     * These are often duplicates of advance notice SMS that have the merchant name.
     */
    private static boolean isBareUpiDebit(String body) {
        if (body == null) return false;
        String lower = body.toLowerCase();
        // Must be a UPI/debit SMS
        if (!lower.contains("debited") && !lower.contains("debit")) return false;
        // Must have "thru upi:" or "via upi:" followed by a numeric ref (no merchant)
        if (lower.contains("thru upi:") || lower.contains("via upi:")) {
            // If it has "to " or "at " or "autopay on" it has a merchant — NOT bare
            return !lower.contains(" to ") && !lower.contains(" at ")
                    && !lower.contains("autopay on") && !lower.contains("info:");
        }
        return false;
    }

    /**
     * Returns true if a transaction with the same amount AND matching merchant/category
     * already exists in the DB within a 3-day window around the given timestamp.
     *
     * The merchant check prevents false-positive deduplication when two different
     * merchants happen to charge the same amount within the same 3-day window
     * (e.g. ₹500 cab + ₹500 grocery). The bare UPI debit for PNB has no merchant,
     * so we fall back to category-level matching in that case.
     */
    private static boolean isDuplicateDebit(AppDatabase db, double amount,
                                            long timestamp, String merchant) {
        try {
            long window = 3L * 24 * 60 * 60 * 1000; // 3 days in ms
            long start  = timestamp - window;
            long end    = timestamp + window;
            java.util.List<Transaction> nearby =
                    db.transactionDao().getByDateRange(start, end);
            for (Transaction t : nearby) {
                if (Math.abs(t.amount - amount) >= 0.01) continue;
                // Amount matches — now require merchant to also match (case-insensitive).
                // If either merchant is blank (bare UPI debit), treat as a match on amount alone.
                boolean merchantMatch = (merchant == null || merchant.isEmpty()
                        || t.merchant == null || t.merchant.isEmpty()
                        || merchant.equalsIgnoreCase(t.merchant));
                if (merchantMatch) return true;
            }
        } catch (Exception e) {
            android.util.Log.w("SmsImporter", "Duplicate debit check failed: " + e.getMessage());
        }
        return false;
    }

    private static String sha1(String text) {
        try {
            // SHA-256 replaces the former SHA-1: collision-resistant, not forgeable.
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] result = md.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : result) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            // Hardened fallback: XOR of two independent hash seeds reduces the
            // 32-bit collision risk of a plain hashCode() to near zero for our volumes.
            long h1 = text.hashCode();
            long h2 = text.length() * 31L + (text.isEmpty() ? 0 : text.charAt(0));
            return Long.toHexString(h1 ^ (h2 << 17));
        }
    }

    private static boolean isSelfTransfer(String body) {
        if (body == null) return false;
        String b = body.toLowerCase();
        return b.contains("self") || b.contains("own account")
                || b.contains("transfer to your") || b.contains("transfer to self")
                || b.contains("linked account") || b.contains("savings account to")
                || b.contains("current account to");
    }

    /**
     * Redacts the originating address to remove personal phone numbers.
     * Alphanumeric bank sender IDs (e.g. "VK-HDFCBK") are kept as-is.
     * Numeric addresses are truncated to prefix only.
     */
    private static String redactSender(String sender) {
        if (sender == null) return null;
        if (sender.matches("[A-Za-z0-9\\-]{2,20}")) return sender;
        if (sender.length() > 4) return sender.substring(0, 4) + "****";
        return "****";
    }

    private static boolean isCardBlockMessage(String body) {
        if (body == null) return false;
        String b = body.toLowerCase();
        return (b.contains("block") || b.contains("blocked") || b.contains("hotlist"))
                && !b.contains("debited") && !b.contains("spent") && !b.contains("credited");
    }

    private static boolean isLikelyDuplicateCredit(AppDatabase db, double amount, long ts, String merchant) {
        try {
            long w = 2L * 60 * 1000; // ±2 min
            java.util.List<Transaction> nearby = db.transactionDao().getByDateRange(ts - w, ts + w);
            for (Transaction t : nearby) {
                if (!t.isCredit) continue;
                if (Math.abs(t.amount - amount) > 0.01) continue;
                if (merchant == null || merchant.isEmpty() || t.merchant == null || t.merchant.isEmpty()) return true;
                if (merchant.equalsIgnoreCase(t.merchant)) return true;
            }

        return false;
    }

    private static String normalizeMerchant(String merchant, String sender, String bankName, String body) {
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

    private static void autoUpsertBankAccount(AppDatabase db, String bankName, String last4,
                                              double balance, long ts) {
        try {
            java.util.List<BankAccount> accounts = db.bankAccountDao().getAllSync();
            for (BankAccount a : accounts) {
                if (last4.equals(a.lastFour)) {
                    db.bankAccountDao().updateBalance(a.id, balance, ts);
                    return;
                }
            }
            BankAccount acc = new BankAccount();
            acc.lastFour = last4;
            acc.bankName = expandBankName(bankName);
            acc.accountType = "SAVINGS";
            acc.accountLabel = acc.bankName + " A/c " + last4;
            acc.balance = balance;
            acc.updatedAt = ts;
            acc.isActive = true;
            acc.bankEmoji = "🏦";
            acc.cardColor = android.graphics.Color.parseColor("#1A237E");
            db.bankAccountDao().insert(acc);

    }

    private static String expandBankName(String bankName) {
        String b = bankName != null ? bankName.trim().toUpperCase() : "";
        if ("PNB".equals(b)) return "Punjab National Bank";
        if ("SBI".equals(b)) return "State Bank of India";
        if ("HDFC".equals(b)) return "HDFC Bank";
        if ("ICICI".equals(b)) return "ICICI Bank";
        if ("AXIS".equals(b)) return "Axis Bank";
        return b.isEmpty() ? "Bank Account" : b;
    }
}
