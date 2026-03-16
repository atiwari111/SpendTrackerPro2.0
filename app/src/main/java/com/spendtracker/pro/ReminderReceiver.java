package com.spendtracker.pro;

import android.content.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        String name = intent.getStringExtra("name");
        double amount = intent.getDoubleExtra("amount", 0);
        long due = intent.getLongExtra("due", 0);
        String dueDate = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(new Date(due));
        NotificationHelper.sendBillReminder(ctx, name != null ? name : "Bill", amount, dueDate);
    }
}
