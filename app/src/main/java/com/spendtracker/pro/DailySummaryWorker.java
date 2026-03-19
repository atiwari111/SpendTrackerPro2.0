package com.spendtracker.pro;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * DailySummaryWorker — runs once per day at ~8 PM and sends a smart
 * spending summary notification like Google Pay / CRED do.
 *
 * Examples:
 *  "You spent ₹1,240 today · 3 transactions"
 *  "⚠️ ₹3,800 spent today — 58% more than your daily average"
 *  "✅ Great day! Only ₹420 spent — well below your average"
 */
public class DailySummaryWorker extends Worker {

    public static final String WORK_TAG = "daily_summary";

    public DailySummaryWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();

        if (!NotificationHelper.canNotify(ctx)) return Result.success();

        try {
            AppDatabase db = AppDatabase.getInstance(ctx);

            long now        = System.currentTimeMillis();
            long todayStart = getDayStart(now);
            // Scoped query: only the last 8 days — avoids loading entire history
            long weekAgo    = getDayStart(now - (7L * 86400000L));

            List<Transaction> recent = db.transactionDao().getSpendingInRange(weekAgo, now);
            if (recent == null || recent.isEmpty()) {
                sendNotification(ctx,
                        "💚 No spending today!",
                        "You haven't spent anything today. Keep it up!",
                        0);
                return Result.success();
            }

            // ── Today's spend ──────────────────────────────────────
            double todayTotal  = 0;
            int    todayCount  = 0;
            String topMerchant = null;
            Map<String, Double> merchantMap = new HashMap<>();

            for (Transaction t : recent) {
                if (t.isCredit) continue;
                if (t.timestamp >= todayStart) {
                    todayTotal += t.amount;
                    todayCount++;
                    String m = t.merchant != null ? t.merchant : "Unknown";
                    merchantMap.merge(m, t.amount, Double::sum);
                }
            }

            if (todayCount == 0) {
                sendNotification(ctx,
                        "💚 No spending today!",
                        "You haven't spent anything today. Keep it up!",
                        0);
                return Result.success();
            }

            // ── Daily average (last 7 days excluding today) ────────
            double weekTotal = 0;
            int    weekDays  = 0;
            for (int i = 1; i <= 7; i++) {
                long dayStart = getDayStart(now - (i * 86400000L));
                long dayEnd   = dayStart + 86400000L;
                double daySum = 0;
                boolean hasData = false;
                for (Transaction t : recent) {
                    if (!t.isCredit && t.timestamp >= dayStart && t.timestamp < dayEnd) {
                        daySum += t.amount;
                        hasData = true;
                    }
                }
                if (hasData) { weekTotal += daySum; weekDays++; }
            }

            double dailyAvg = weekDays > 0 ? weekTotal / weekDays : 0;

            // Top merchant today
            if (!merchantMap.isEmpty()) {
                topMerchant = Collections.max(
                        merchantMap.entrySet(), Map.Entry.comparingByValue()).getKey();
            }

            // ── Build notification ─────────────────────────────────
            String title, body;

            if (dailyAvg > 0) {
                double pctDiff = ((todayTotal - dailyAvg) / dailyAvg) * 100;

                if (pctDiff > 40) {
                    title = String.format("⚠️ High spend day — ₹%.0f", todayTotal);
                    body  = String.format("%.0f%% more than your daily average of ₹%.0f",
                            pctDiff, dailyAvg);
                } else if (pctDiff < -30) {
                    title = String.format("✅ Great day! Only ₹%.0f spent", todayTotal);
                    body  = String.format("%.0f%% below your daily average — well done!",
                            Math.abs(pctDiff));
                } else {
                    title = String.format("📊 Today's spend: ₹%.0f", todayTotal);
                    body  = String.format("%d transaction%s · avg ₹%.0f/day",
                            todayCount, todayCount == 1 ? "" : "s", dailyAvg);
                }
            } else {
                title = String.format("📊 Today: ₹%.0f spent", todayTotal);
                body  = String.format("%d transaction%s today",
                        todayCount, todayCount == 1 ? "" : "s");
            }

            if (topMerchant != null && todayCount > 1) {
                body += " · Top: " + topMerchant;
            }

            sendNotification(ctx, title, body, todayCount);

        } catch (Exception e) {
            android.util.Log.e("DailySummaryWorker", "Error: " + e.getMessage());
        }

        return Result.success();
    }

    private void sendNotification(Context ctx, String title, String body, int txnCount) {
        if (!NotificationHelper.canNotify(ctx)) return;
        try {
            Intent intent = new Intent(ctx, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi = PendingIntent.getActivity(ctx, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder b = new NotificationCompat.Builder(
                    ctx, NotificationHelper.CH_INSIGHTS)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);

            NotificationManagerCompat.from(ctx).notify(7001, b.build());
        } catch (SecurityException ignored) {}
    }

    // ── Scheduling helpers ─────────────────────────────────────────

    /**
     * Schedule daily summary at 8 PM every day.
     * Call this from SplashActivity / MainActivity once on first launch.
     */
    public static void schedule(Context ctx) {
        // Calculate delay until next 8 PM
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, 20);
        target.set(Calendar.MINUTE, 0);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        if (target.getTimeInMillis() <= System.currentTimeMillis()) {
            // Already past 8 PM today — schedule for tomorrow
            target.add(Calendar.DAY_OF_YEAR, 1);
        }

        long delay = target.getTimeInMillis() - System.currentTimeMillis();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                DailySummaryWorker.class, 24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag(WORK_TAG)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build())
                .build();

        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                WORK_TAG,
                ExistingPeriodicWorkPolicy.KEEP,   // don't reset if already scheduled
                request);
    }

    /** Cancel the daily summary notification. */
    public static void cancel(Context ctx) {
        WorkManager.getInstance(ctx).cancelAllWorkByTag(WORK_TAG);
    }

    private long getDayStart(long ts) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ts);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}
