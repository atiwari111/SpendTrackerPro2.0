package com.spendtracker.pro;

import android.content.Context;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class CsvExporter {

    // ── Transactions ─────────────────────────────────────────────────────────

    public static File export(Context ctx, List<Transaction> transactions) throws IOException {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault());
        File dir = getExportDir(ctx);
        String fname = "SpendTracker_Txns_"
                + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(new Date())
                + ".csv";
        File file = new File(dir, fname);
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file)))) {
            pw.println("Date,Merchant,Amount,Category,Payment Method,Payment Detail,Notes,Manual");
            for (Transaction t : transactions) {
                pw.printf("\"%s\",\"%s\",%.2f,\"%s\",\"%s\",\"%s\",\"%s\",%s%n",
                        sdf.format(new Date(t.timestamp)),
                        safe(t.merchant),
                        t.amount,
                        safe(t.category),
                        safe(t.paymentMethod),
                        safe(t.paymentDetail),
                        safe(t.notes),
                        t.isManual ? "Yes" : "No");
            }
        }
        return file;
    }

    // ── Credit cards ──────────────────────────────────────────────────────────

    public static File exportCreditCards(Context ctx, List<CreditCard> cards) throws IOException {
        File dir = getExportDir(ctx);
        String fname = "SpendTracker_CreditCards_"
                + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(new Date())
                + ".csv";
        File file = new File(dir, fname);
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file)))) {
            pw.println("Bank,Card Label,Last Four,Network,Credit Limit,Spent This Cycle,"
                    + "Statement Amount,Billing Day,Utilisation %");
            for (CreditCard c : cards) {
                int util = (int)(c.getUtilisation() * 100);
                pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",%.2f,%.2f,%.2f,%d,%d%%%n",
                        safe(c.bankName),
                        safe(c.cardLabel),
                        safe(c.lastFour),
                        safe(c.network),
                        c.creditLimit,
                        c.currentSpent,
                        c.statementAmount,
                        c.billingDay,
                        util);
            }
        }
        return file;
    }

    // ── Bank accounts ─────────────────────────────────────────────────────────

    public static File exportBankAccounts(Context ctx, List<BankAccount> accounts) throws IOException {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault());
        File dir = getExportDir(ctx);
        String fname = "SpendTracker_BankAccounts_"
                + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(new Date())
                + ".csv";
        File file = new File(dir, fname);
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file)))) {
            pw.println("Bank,Account Label,Last Four,Account Type,Balance,Last Updated,Active");
            for (BankAccount a : accounts) {
                pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",%.2f,\"%s\",%s%n",
                        safe(a.bankName),
                        safe(a.accountLabel),
                        safe(a.lastFour),
                        safe(a.accountType),
                        a.balance,
                        a.updatedAt > 0
                                ? sdf.format(new Date(a.updatedAt))
                                : "—",
                        a.isActive ? "Yes" : "No");
            }
        }
        return file;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static File getExportDir(Context ctx) {
        File dir = new File(ctx.getExternalFilesDir(null), "SpendTracker");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /** Null-safe, quote-safe field value. */
    private static String safe(String s) {
        if (s == null) return "";
        // Escape embedded double-quotes by doubling them (RFC 4180)
        return s.replace("\"", "\"\"");
    }
}
