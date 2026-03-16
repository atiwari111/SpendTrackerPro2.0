package com.spendtracker.pro;

import android.content.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Re-schedule recurring reminders after boot
            new Thread(() -> {
                AppDatabase db = AppDatabase.getInstance(ctx);
                List<RecurringTransaction> list = db.recurringDao().getActiveSync();
                for (RecurringTransaction r : list) {
                    scheduleReminder(ctx, r);
                }
            }).start();
        }
    }

    public static void scheduleReminder(Context ctx, RecurringTransaction r) {
        // Use AlarmManager for reminders (simplified)
        android.app.AlarmManager am = (android.app.AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(ctx, ReminderReceiver.class);
        i.putExtra("name", r.name);
        i.putExtra("amount", r.amount);
        i.putExtra("due", r.nextDueDate);
        android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(
                ctx, r.id, i, android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
        // Remind 1 day before due
        long remindAt = r.nextDueDate - 86400000L;
        if (remindAt > System.currentTimeMillis() && am != null) {
            am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, remindAt, pi);
        }
    }
}
