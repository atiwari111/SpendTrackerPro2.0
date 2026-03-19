package com.spendtracker.pro;

import java.util.*;

/**
 * P5: Spending streak calculator.
 *
 * A "streak day" is any calendar day where the user's total spend
 * was at or below their daily budget target.
 *
 * Daily budget target = (sum of all monthly budget limits) / 30.
 * If no budgets are set, falls back to a default of ₹1,000/day.
 *
 * Streak logic:
 *  - Walk backwards from yesterday (today is still in progress).
 *  - Count consecutive days that are under-budget.
 *  - Stop on the first day that is over-budget.
 *  - Today is shown as "in progress" — a bonus +1 if already under-budget.
 */
public class StreakManager {

    private static final double DEFAULT_DAILY_LIMIT = 1_000.0;

    public static class StreakResult {
        public final int  currentStreak;   // consecutive days under-budget (not counting today)
        public final int  longestStreak;   // all-time best
        public final boolean todayOnTrack; // true if today's spend < daily limit
        public final double dailyLimit;
        public final double todaySpend;
        public final String label;         // e.g. "🔥 7-day streak!"

        StreakResult(int current, int longest, boolean todayOnTrack,
                     double dailyLimit, double todaySpend) {
            this.currentStreak  = current;
            this.longestStreak  = longest;
            this.todayOnTrack   = todayOnTrack;
            this.dailyLimit     = dailyLimit;
            this.todaySpend     = todaySpend;

            if (current == 0 && !todayOnTrack) {
                this.label = "Start your streak today!";
            } else if (current == 0 && todayOnTrack) {
                this.label = "Day 1 — keep going! 🌱";
            } else if (current >= 30) {
                this.label = "🏆 " + current + "-day streak!";
            } else if (current >= 7) {
                this.label = "🔥 " + current + "-day streak!";
            } else {
                this.label = "⚡ " + current + "-day streak";
            }
        }
    }

    /**
     * Computes the current streak from a list of transactions and budgets.
     * Call this off the main thread (no DB access inside — data passed in).
     */
    public static StreakResult compute(List<Transaction> transactions, List<Budget> budgets) {
        double monthlyLimit = 0;
        for (Budget b : budgets) monthlyLimit += b.limitAmount;
        double dailyLimit = monthlyLimit > 0 ? monthlyLimit / 30.0 : DEFAULT_DAILY_LIMIT;

        // Bucket spend by calendar day (timestamp → day-start → total spend)
        Map<Long, Double> spendByDay = new TreeMap<>();
        long now = System.currentTimeMillis();
        long todayStart = getDayStart(now);

        for (Transaction t : transactions) {
            if (t.isSelfTransfer || t.isCredit) continue;
            long dayStart = getDayStart(t.timestamp);
            spendByDay.merge(dayStart, t.amount, Double::sum);
        }

        // Today's spend
        double todaySpend = spendByDay.getOrDefault(todayStart, 0.0);
        boolean todayOnTrack = todaySpend <= dailyLimit;

        // Walk backwards from yesterday to count streak
        int currentStreak = 0;
        long day = todayStart - 86_400_000L; // start from yesterday
        while (true) {
            double spend = spendByDay.getOrDefault(day, 0.0);
            // A day with no transactions counts as a streak day (₹0 < limit)
            if (spend <= dailyLimit) {
                currentStreak++;
                day -= 86_400_000L;
            } else {
                break;
            }
            // Safety cap — don't walk back more than 2 years
            if (currentStreak > 730) break;
        }

        // Longest streak (all-time) — walk the entire history
        int longestStreak = 0, runningStreak = 0;
        // Get all days from earliest to todayStart-1
        List<Long> allDays = new ArrayList<>(spendByDay.keySet());
        if (!allDays.isEmpty()) {
            long first = allDays.get(0);
            long last  = todayStart - 86_400_000L;
            for (long d = first; d <= last; d += 86_400_000L) {
                double spend = spendByDay.getOrDefault(d, 0.0);
                if (spend <= dailyLimit) {
                    runningStreak++;
                    longestStreak = Math.max(longestStreak, runningStreak);
                } else {
                    runningStreak = 0;
                }
            }
        }
        longestStreak = Math.max(longestStreak, currentStreak);

        return new StreakResult(currentStreak, longestStreak,
                todayOnTrack, dailyLimit, todaySpend);
    }

    private static long getDayStart(long ts) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ts);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}
