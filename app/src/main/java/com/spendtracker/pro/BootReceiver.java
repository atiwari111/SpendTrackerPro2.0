package com.spendtracker.pro;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.*;
import android.os.Build;
import java.util.List;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Re-schedule recurring reminders after boot on a background thread
            AppExecutors.db().execute(() -> {
                AppDatabase db = AppDatabase.getInstance(ctx);

                // Re-schedule recurring bill reminders
                List<RecurringTransaction> list = db.recurringDao().getActiveSync();
                for (RecurringTransaction r : list) {
                    scheduleReminder(ctx, r);
                }

                // Send bill due notifications for bills due within 3 days
                db.billDao().markOverdue(System.currentTimeMillis());
                long now   = System.currentTimeMillis();
                long in3   = now + (3L * 24 * 60 * 60 * 1000);
                List<Bill> dueSoon = db.billDao().getDueInRange(now, in3);
                for (Bill bill : dueSoon) {
                    NotificationHelper.sendBillDueReminder(ctx, bill);
                }
            });
        }
    }

    public static void scheduleReminder(Context ctx, RecurringTransaction r) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        long remindAt = r.nextDueDate - 86400000L; // 1 day before due
        if (remindAt <= System.currentTimeMillis()) return; // already past

        Intent i = new Intent(ctx, ReminderReceiver.class);
        i.putExtra("name", r.name);
        i.putExtra("amount", r.amount);
        i.putExtra("due", r.nextDueDate);
        PendingIntent pi = PendingIntent.getBroadcast(
                ctx, r.id, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // API 31+: must check canScheduleExactAlarms() before calling setExact
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, remindAt, pi);
                } else {
                    // Exact alarm permission not granted — fall back to inexact alarm
                    // (reminder may fire a few minutes late but will still fire)
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, remindAt, pi);
                }
            } else {
                // API 26-30: setExactAndAllowWhileIdle is safe without permission check
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, remindAt, pi);
            }
        } catch (SecurityException e) {
            // Permission revoked between check and set — fall back to inexact
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, remindAt, pi);
        }
    }
}
