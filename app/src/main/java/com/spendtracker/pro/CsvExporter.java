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
        PrintWriter pw = new PrintWriter(new FileWriter(file));
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
        pw.close();
        return file;
    }
}
