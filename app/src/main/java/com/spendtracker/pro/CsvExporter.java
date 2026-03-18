package com.spendtracker.pro;

import android.content.Context;
import android.os.Environment;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class CsvExporter {
    public static File export(Context ctx, List<Transaction> transactions) throws IOException {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault());
        File dir = new File(ctx.getExternalFilesDir(null), "SpendTracker");
        if (!dir.exists()) dir.mkdirs();
        String fname = "SpendTracker_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(new Date()) + ".csv";
        File file = new File(dir, fname);
        // try-with-resources guarantees the writer is closed even if an exception is thrown
        // mid-export (e.g. out of disk space), preventing a file handle leak and corrupt output.
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file)))) {
            pw.println("Date,Merchant,Amount,Category,Payment Method,Payment Detail,Notes,Manual");
            for (Transaction t : transactions) {
                pw.printf("\"%s\",\"%s\",%.2f,\"%s\",\"%s\",\"%s\",\"%s\",%s%n",
                        sdf.format(new Date(t.timestamp)),
                        t.merchant != null ? t.merchant : "",
                        t.amount,
                        t.category != null ? t.category : "",
                        t.paymentMethod != null ? t.paymentMethod : "",
                        t.paymentDetail != null ? t.paymentDetail : "",
                        t.notes != null ? t.notes : "",
                        t.isManual ? "Yes" : "No");
            }
        } // auto-closed — flushes and closes even on IOException
        return file;
    }
}
