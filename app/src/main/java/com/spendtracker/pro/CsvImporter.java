package com.spendtracker.pro;

import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.text.*;
import java.util.*;

/**
 * Imports transactions from a CSV file.
 * Supported column formats (auto-detected, case-insensitive):
 *   date, merchant/description, amount, category (optional), payment (optional), notes (optional)
 *
 * Example CSV rows:
 *   2024-01-15, Swiggy, 350, Food, UPI, Dinner
 *   15/01/2024, Amazon, 1299, Shopping, Credit Card,
 */
public class CsvImporter {

    public interface Callback {
        void onProgress(int done, int total);
        void onComplete(int imported, int skipped);
        void onError(String message);
    }

    private static final SimpleDateFormat[] DATE_FORMATS = {
        new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT),
        new SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
        new SimpleDateFormat("dd-MM-yyyy", Locale.ROOT),
        new SimpleDateFormat("MM/dd/yyyy", Locale.ROOT),
        new SimpleDateFormat("dd MMM yyyy", Locale.ROOT),
        new SimpleDateFormat("dd-MMM-yyyy", Locale.ROOT),
        new SimpleDateFormat("dd MMM yy", Locale.ROOT),
    };

    public static void importFromUri(Context ctx, Uri fileUri, Callback cb) {
        // Use the shared IO executor — avoids untracked thread creation and Context leaks.
        // The ApplicationContext is captured so Activity destruction doesn't leak memory.
        final Context appCtx = ctx.getApplicationContext();
        AppExecutors.io().execute(() -> {
            try {
                InputStream is = appCtx.getContentResolver().openInputStream(fileUri);
                if (is == null) { cb.onError("Cannot open file"); return; }

                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                List<String[]> rows = new ArrayList<>();
                String line;
                boolean firstLine = true;
                int[] colMap = null; // [dateIdx, merchantIdx, amountIdx, categoryIdx, paymentIdx, notesIdx]

                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    String[] cols = splitCsvLine(line);

                    if (firstLine) {
                        firstLine = false;
                        // Try to detect header row
                        colMap = detectColumns(cols);
                        if (colMap != null) continue; // skip header
                        // No header detected — use positional defaults: date,merchant,amount,[category],[payment],[notes]
                        colMap = new int[]{0, 1, 2, 3, 4, 5};
                    }
                    rows.add(cols);
                }
                reader.close();

                AppDatabase db = AppDatabase.getInstance(appCtx);
                int imported = 0, skipped = 0;
                final int total = rows.size();
                final int[] cm = colMap != null ? colMap : new int[]{0, 1, 2, 3, 4, 5};

                Calendar cal = Calendar.getInstance();
                int month = cal.get(Calendar.MONTH) + 1;
                int year  = cal.get(Calendar.YEAR);

                for (int i = 0; i < rows.size(); i++) {
                    String[] cols = rows.get(i);
                    try {
                        long timestamp = parseDate(safeGet(cols, cm[0]));
                        if (timestamp < 0) { skipped++; continue; }

                        double amount = parseAmount(safeGet(cols, cm[1] == cm[2] ? 1 : cm[2]));
                        if (amount <= 0) { skipped++; continue; }

                        String merchant  = safeGet(cols, cm[1]).trim();
                        if (merchant.isEmpty()) merchant = "Imported";

                        String catRaw    = safeGet(cols, cm[3]).trim();
                        String category  = catRaw.isEmpty()
                                ? CategoryEngine.classify(merchant, "")
                                : matchCategory(catRaw);
                        String payment   = safeGet(cols, cm[4]).trim();
                        String payMethod = parsePaymentMethod(payment);
                        String notes     = safeGet(cols, cm[5]).trim();

                        // Deduplicate: skip if same merchant+amount+day already exists
                        long dayStart = getDayStart(timestamp);
                        boolean duplicate = false;
                        List<Transaction> same = db.transactionDao().getByDateRange(dayStart, dayStart + 86400000L);
                        for (Transaction t : same) {
                            if (t.merchant.equalsIgnoreCase(merchant) && Math.abs(t.amount - amount) < 0.01) {
                                duplicate = true; break;
                            }
                        }
                        if (duplicate) { skipped++; continue; }

                        Transaction tx = new Transaction();
                        tx.merchant = merchant; tx.amount = amount;
                        tx.category = category; tx.categoryIcon = CategoryEngine.getInfo(category).icon;
                        tx.paymentMethod = payMethod; tx.paymentDetail = payment.isEmpty() ? payMethod : payment;
                        tx.timestamp = timestamp; tx.notes = notes;
                        tx.isManual = true; tx.isSelfTransfer = false;
                        tx.smsHash  = "csv_" + java.util.UUID.randomUUID().toString();
                        db.transactionDao().insert(tx);
                        imported++;

                    } catch (Exception e) { skipped++; }

                    if (cb != null && i % 20 == 0) cb.onProgress(i, total);
                }

                // Recalculate all budgets for current month
                Calendar recalCal = Calendar.getInstance();
                recalCal.set(Calendar.DAY_OF_MONTH, 1);
                recalCal.set(Calendar.HOUR_OF_DAY, 0); recalCal.set(Calendar.MINUTE, 0);
                recalCal.set(Calendar.SECOND, 0); recalCal.set(Calendar.MILLISECOND, 0);
                long rStart = recalCal.getTimeInMillis();
                recalCal.add(Calendar.MONTH, 1);
                long rEnd = recalCal.getTimeInMillis();
                db.budgetDao().recalcAllUsed(month, year, rStart, rEnd);

                final int fi = imported, fs = skipped;
                if (cb != null) cb.onComplete(fi, fs);

            } catch (Exception e) {
                if (cb != null) cb.onError("Import failed: " + e.getMessage());
            }
        });
    }

    /** Detect header row and map column names → indices */
    private static int[] detectColumns(String[] cols) {
        int dateIdx = -1, merchantIdx = -1, amountIdx = -1, categoryIdx = -1, paymentIdx = -1, notesIdx = -1;
        for (int i = 0; i < cols.length; i++) {
            String h = cols[i].toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z]", "");
            if (h.equals("date") || h.equals("txndate") || h.equals("transactiondate")) dateIdx = i;
            else if (h.contains("merchant") || h.contains("description") || h.contains("narration")
                    || h.contains("payee") || h.contains("name")) merchantIdx = i;
            else if (h.contains("amount") || h.contains("debit") || h.equals("amt")) amountIdx = i;
            else if (h.contains("category") || h.contains("type")) categoryIdx = i;
            else if (h.contains("payment") || h.contains("mode") || h.contains("method")) paymentIdx = i;
            else if (h.contains("note") || h.contains("remark")) notesIdx = i;
        }
        if (dateIdx == -1 && merchantIdx == -1 && amountIdx == -1) return null; // probably not a header
        // Use defaults for missing columns
        if (dateIdx == -1) dateIdx = 0;
        if (merchantIdx == -1) merchantIdx = 1;
        if (amountIdx == -1) amountIdx = 2;
        if (categoryIdx == -1) categoryIdx = 3;
        if (paymentIdx == -1) paymentIdx = 4;
        if (notesIdx == -1) notesIdx = 5;
        return new int[]{dateIdx, merchantIdx, amountIdx, categoryIdx, paymentIdx, notesIdx};
    }

    private static String[] splitCsvLine(String line) {
        List<String> cols = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (char c : line.toCharArray()) {
            if (c == '"') { inQuotes = !inQuotes; }
            else if (c == ',' && !inQuotes) { cols.add(sb.toString()); sb.setLength(0); }
            else sb.append(c);
        }
        cols.add(sb.toString());
        return cols.toArray(new String[0]);
    }

    private static String safeGet(String[] arr, int idx) {
        return (arr != null && idx >= 0 && idx < arr.length) ? arr[idx].trim().replaceAll("^\"|\"$", "") : "";
    }

    private static long parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return -1;
        // Remove common noise like T00:00:00
        dateStr = dateStr.split("T")[0].trim();
        for (SimpleDateFormat fmt : DATE_FORMATS) {
            try {
                fmt.setLenient(false);
                return fmt.parse(dateStr).getTime();
            } catch (ParseException ignored) {}
        }
        return -1;
    }

    private static double parseAmount(String amtStr) {
        if (amtStr == null || amtStr.isEmpty()) return -1;
        // Remove currency symbols and commas
        String cleaned = amtStr.replaceAll("[₹$£€,\\s]", "").replaceAll("[^0-9.]", "");
        try { return Double.parseDouble(cleaned); } catch (Exception e) { return -1; }
    }

    private static String parsePaymentMethod(String payment) {
        if (payment == null) return "MANUAL";
        String p = payment.toLowerCase(Locale.ROOT);
        if (p.contains("upi") || p.contains("gpay") || p.contains("phonepe") || p.contains("paytm")) return "UPI";
        if (p.contains("credit")) return "CREDIT_CARD";
        if (p.contains("debit")) return "DEBIT_CARD";
        if (p.contains("cash")) return "CASH";
        if (p.contains("bank") || p.contains("neft") || p.contains("imps") || p.contains("rtgs")) return "BANK";
        return "MANUAL";
    }

    /** Match a free-text category to one of our defined categories */
    private static String matchCategory(String raw) {
        String r = raw.toLowerCase(Locale.ROOT).trim();
        for (String cat : CategoryEngine.getCategoryNames()) {
            if (cat.toLowerCase(Locale.ROOT).contains(r) || r.contains(cat.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", ""))) return cat;
        }
        return CategoryEngine.classify(raw, "");
    }

    private static long getDayStart(long ts) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(ts);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}
