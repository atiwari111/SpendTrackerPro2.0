package com.spendtracker.pro;

import java.util.Locale;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import java.util.Calendar;
import java.util.List;

/**
 * P4: Home screen widget — shows today's spend and bank balance at a glance.
 *
 * Updates every 30 minutes (updatePeriodMillis = 1 800 000 ms in widget_info.xml).
 * Tapping the widget opens MainActivity.
 * Data is read synchronously from Room on a background thread via AppExecutors.
 */
public class SpendWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] widgetIds) {
        for (int id : widgetIds) {
            updateWidget(context, manager, id);
        }
    }

    public static void updateWidget(Context context, AppWidgetManager manager, int widgetId) {
        AppExecutors.db().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context);

                // Today's expense total
                long now        = System.currentTimeMillis();
                long todayStart = getDayStart(now);
                long monthStart = getMonthStart(now);

                // Priority 1: use scoped range query — avoids full-table scan on large datasets
                List<Transaction> monthTxns = db.transactionDao().getSpendingInRange(monthStart, now);
                double todayTotal = 0, monthTotal = 0;
                for (Transaction t : monthTxns) {
                    if (t.isSelfTransfer || t.isCredit) continue;
                    monthTotal += t.amount;
                    if (t.timestamp >= todayStart) todayTotal += t.amount;
                }

                // Bank balance total
                double bankBalance = db.bankAccountDao().getTotalBalanceSync();

                // Build RemoteViews
                RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_spend);
                views.setTextViewText(R.id.tvWidgetToday,
                        String.format(Locale.getDefault(), "₹%.0f", todayTotal));
                views.setTextViewText(R.id.tvWidgetMonth,
                        String.format(Locale.getDefault(), "₹%.0f", monthTotal));
                views.setTextViewText(R.id.tvWidgetBalance,
                        bankBalance > 0 ? String.format(Locale.getDefault(), "₹%.0f", bankBalance) : "₹—");

                // Tap to open app
                Intent intent = new Intent(context, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                PendingIntent pi = PendingIntent.getActivity(context, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                views.setOnClickPendingIntent(R.id.tvWidgetTitle, pi);

                manager.updateAppWidget(widgetId, views);
            } catch (Exception e) {
                android.util.Log.e("SpendWidget", "Update failed: " + e.getMessage());
            }
        });
    }

    /** Called when all widgets of this type are removed — nothing to clean up. */
    @Override
    public void onDisabled(Context context) {}

    private static long getDayStart(long ts) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(ts);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static long getMonthStart(long ts) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(ts);
        c.set(Calendar.DAY_OF_MONTH, 1); c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}
