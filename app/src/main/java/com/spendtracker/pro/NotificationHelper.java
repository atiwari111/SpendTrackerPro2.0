package com.spendtracker.pro;

import java.util.Locale;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;
import java.util.concurrent.atomic.AtomicInteger;

public class NotificationHelper {
    public static final String CH_ALERTS   = "stp_alerts";
    public static final String CH_BILLS    = "stp_bills";
    public static final String CH_BUDGET   = "stp_budget";
    public static final String CH_INSIGHTS = "stp_insights";

    // Monotonically increasing counter — eliminates timestamp-based ID collisions.
    // Transient IDs (spend alerts, anomaly alerts) use nextId().
    // Stable IDs (budget, bill) use a deterministic offset so tapping always opens
    // the right notification even if the counter resets after process death.
    private static final AtomicInteger ID_COUNTER = new AtomicInteger(1);
    private static int nextId() { return ID_COUNTER.getAndIncrement(); }

    // Stable ID ranges — must not overlap with each other or with nextId() range.
    // Bills:   bill.id + 50000  (max ~65535 bills before wraparound, safe in practice)
    // Budgets: budget.id + 2000
    // Insight: fixed 9999
    // Daily summary: fixed 7001 (in DailySummaryWorker)

    public static void createChannels(Context ctx) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH_ALERTS,   "Spend Alerts",    NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(new NotificationChannel(CH_BILLS,    "Bill Reminders",  NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(new NotificationChannel(CH_BUDGET,   "Budget Warnings", NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(new NotificationChannel(CH_INSIGHTS, "Smart Insights",  NotificationManager.IMPORTANCE_LOW));
    }

    /**
     * Returns true if POST_NOTIFICATIONS permission is granted (required on API 33+).
     * Always returns true on API < 33 since the permission didn't exist yet.
     */
    static boolean canNotify(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ActivityCompat.checkSelfPermission(ctx,
                    android.Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    /**
     * Creates a properly stacked PendingIntent using TaskStackBuilder.
     * When notification is tapped: opens `target` activity with MainActivity as parent in back stack.
     */
    private static PendingIntent makeStackedIntent(Context ctx, Class<?> target, int requestCode) {
        TaskStackBuilder stack = TaskStackBuilder.create(ctx);
        stack.addNextIntent(new Intent(ctx, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        if (target != MainActivity.class) {
            stack.addNextIntent(new Intent(ctx, target));
        }
        return stack.getPendingIntent(requestCode,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void sendBudgetAlert(Context ctx, String category, double used, double limit, int notifId) {
        int pct = (int)((used / limit) * 100);
        String emoji = pct >= 100 ? "🔴" : "🟠";
        String title = emoji + " Budget " + (pct >= 100 ? "Exceeded" : "Warning") + ": " + category;
        String msg = pct >= 100
                ? String.format("You've exceeded your ₹%.0f budget! Spent: ₹%.0f", limit, used)
                : String.format("You've used %d%% of your ₹%.0f budget. Spent: ₹%.0f", pct, limit, used);
        int id = notifId + 2000; // stable: one per budget row
        send(ctx, CH_BUDGET, id, title, msg, makeStackedIntent(ctx, BudgetActivity.class, id));
    }

    public static void sendSpendAlert(Context ctx, String merchant, double amount, String category) {
        int id = nextId(); // transient: each SMS gets its own unique slot
        send(ctx, CH_ALERTS, id,
                "💸 New Transaction",
                String.format(Locale.getDefault(), "₹%.0f at %s · %s", amount, merchant, category),
                makeStackedIntent(ctx, MainActivity.class, id));
    }

    public static void sendBillReminder(Context ctx, String name, double amount, String dueDate) {
        int id = nextId(); // transient reminder
        send(ctx, CH_BILLS, id,
                "📅 Bill Due: " + name,
                String.format(Locale.getDefault(), "₹%.0f due on %s", amount, dueDate),
                makeStackedIntent(ctx, MainActivity.class, id));
    }

    public static void sendInsight(Context ctx, String insight) {
        send(ctx, CH_INSIGHTS, 9999, "🧠 Spending Insight", insight,
                makeStackedIntent(ctx, AnalyticsActivity.class, 9999));
    }

    public static void sendBillDueReminder(Context ctx, Bill bill) {
        if (!canNotify(ctx)) return;
        int days = bill.daysUntilDue();
        String title = days <= 0 ? "🔴 Bill Overdue: " + bill.name
                     : days == 0 ? "⚠️ Bill Due Today: " + bill.name
                     : "📋 Bill Due in " + days + " day(s): " + bill.name;
        String msg = String.format(Locale.getDefault(), "₹%.0f due %s",
                bill.amount,
                days <= 0 ? "NOW" : "on " + new java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault())
                        .format(new java.util.Date(bill.dueDate)));
        int id = bill.id + 50000; // stable: one per bill row
        send(ctx, CH_BILLS, id, title, msg, makeStackedIntent(ctx, BillActivity.class, id));
    }

    public static void sendAnomalyAlert(Context ctx, String merchant, double amount, String category, String reason) {
        int id = nextId(); // transient: each anomaly event is unique
        send(ctx, CH_ALERTS, id,
                "⚠️ Unusual Spend Detected",
                String.format(Locale.getDefault(), "₹%.0f at %s · %s (%s)", amount, merchant, category, reason),
                makeStackedIntent(ctx, MainActivity.class, id));
    }

    private static void send(Context ctx, String channel, int id, String title, String msg, PendingIntent pi) {
        if (!canNotify(ctx)) return;
        try {
            NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, channel)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(msg)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(msg))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);
            NotificationManagerCompat.from(ctx).notify(id, b.build());
        } catch (SecurityException e) {
            android.util.Log.w("NotificationHelper", "Notification permission denied: " + e.getMessage());
        } catch (Exception e) {
            android.util.Log.e("NotificationHelper", "Failed to send notification id=" + id + ": " + e.getMessage());
        }
    }
}
