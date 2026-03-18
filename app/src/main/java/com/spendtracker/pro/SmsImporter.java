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

                    // ── PNB bare UPI debit deduplication ─────────────────────────
                    // PNB sends: (1) advance notice with merchant name, then
                    //            (2) actual debit SMS with only UPI ref, no merchant.
                    // If we already have a transaction with same amount within 3 days
                    // from the same account, skip the bare UPI debit to avoid duplicates.
                    if (isBareUpiDebit(body) && isDuplicateDebit(db, parsed.amount, date)) {
                        continue;
                    }

                    Transaction t = new Transaction();
                    t.amount         = parsed.amount;
                    t.merchant       = parsed.merchant;
                    t.category       = parsed.category;
                    t.categoryIcon   = CategoryEngine.getInfo(parsed.category).icon;
                    t.timestamp      = date;
                    t.smsHash        = hash;
                    t.isSelfTransfer = isSelfTransfer(body);
                    t.paymentMethod  = parsed.paymentMethod;
                    t.paymentDetail  = parsed.paymentDetail;
                    t.rawSms         = body;
                    t.smsAddress     = sender;
                    t.isManual       = false;

                    db.transactionDao().insert(t);
                    inserted++;
                }

                if (cb != null) cb.onComplete(inserted);

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
     * Returns true if a transaction with the same amount already exists in DB
     * within a 3-day window around the given timestamp.
     * Used to deduplicate advance-notice + actual-debit pairs.
     */
    private static boolean isDuplicateDebit(AppDatabase db, double amount, long timestamp) {
        try {
            long window = 3L * 24 * 60 * 60 * 1000; // 3 days in ms
            long start  = timestamp - window;
            long end    = timestamp + window;
            java.util.List<Transaction> nearby =
                    db.transactionDao().getByDateRange(start, end);
            for (Transaction t : nearby) {
                if (Math.abs(t.amount - amount) < 0.01) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static String sha1(String text) {

        try {

            MessageDigest md = MessageDigest.getInstance("SHA-1");

            byte[] result = md.digest(text.getBytes());

            StringBuilder sb = new StringBuilder();

            for (byte b : result) {
                sb.append(String.format("%02x", b));
            }

            return sb.toString();

        } catch (Exception e) {

            return String.valueOf(text.hashCode());
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
}
