package com.spendtracker.pro;

import java.util.*;

/**
 * SpendPredictor v1.7
 *
 * Predicts month-end total spend based on:
 * 1. Current month run-rate  (amount spent ÷ days elapsed × days in month)
 * 2. Historical average      (last 3 months average as a baseline)
 * 3. Blended prediction      (70% run-rate + 30% history) — more stable
 *
 * Also provides per-category predictions and a savings opportunity estimate.
 */
public class SpendPredictor {

    public static class Prediction {
        public final double projectedTotal;      // blended month-end prediction
        public final double runRatePrediction;   // pure run-rate
        public final double historicalAvg;       // last 3 months average
        public final double currentSpend;        // spent so far this month
        public final double dailyAvg;            // avg daily spend this month
        public final int    daysElapsed;
        public final int    daysInMonth;
        public final double daysRemaining;
        public final String label;               // "On track" / "Overspending" / "Underspending"
        public final boolean isOnTrack;

        Prediction(double projected, double runRate, double histAvg,
                   double current, double dailyAvg,
                   int elapsed, int inMonth) {
            this.projectedTotal      = projected;
            this.runRatePrediction   = runRate;
            this.historicalAvg       = histAvg;
            this.currentSpend        = current;
            this.dailyAvg            = dailyAvg;
            this.daysElapsed         = elapsed;
            this.daysInMonth         = inMonth;
            this.daysRemaining       = inMonth - elapsed;

            if (histAvg > 0) {
                double pct = (projected - histAvg) / histAvg;
                if (pct > 0.20) {
                    this.label      = "📈 Overspending";
                    this.isOnTrack  = false;
                } else if (pct < -0.20) {
                    this.label      = "📉 Underspending";
                    this.isOnTrack  = true;
                } else {
                    this.label      = "✅ On track";
                    this.isOnTrack  = true;
                }
            } else {
                this.label     = "📊 Tracking";
                this.isOnTrack = true;
            }
        }

        /** Returns a display-ready summary string */
        public String getSummary() {
            return String.format("Projected month-end: ₹%.0f  (%s)",
                    projectedTotal, label);
        }

        /** How much user needs to spend per day to stay on historical avg */
        public String getDailyBudgetTip() {
            if (historicalAvg <= 0 || daysRemaining <= 0) return null;
            double remaining = historicalAvg - currentSpend;
            if (remaining <= 0) return String.format(
                    "⚠️ Already exceeded avg! Try to spend < ₹%.0f/day", dailyAvg * 0.5);
            return String.format("💡 Spend ≤ ₹%.0f/day for remaining %d days to match avg",
                    remaining / daysRemaining, (int) daysRemaining);
        }
    }

    /**
     * Generate a spend prediction for the current month.
     *
     * @param transactions Full transaction history
     * @return Prediction object, or null if no data
     */
    public static Prediction predict(List<Transaction> transactions) {
        if (transactions == null || transactions.isEmpty()) return null;

        long now           = System.currentTimeMillis();
        long thisMonthStart = monthStart(now, 0);

        Calendar cal = Calendar.getInstance();
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        int daysElapsed = cal.get(Calendar.DAY_OF_MONTH);
        if (daysElapsed < 1) daysElapsed = 1;

        // Current month spending (exclude self-transfers)
        double currentSpend = 0;
        for (Transaction t : transactions) {
            if (!t.isSelfTransfer && t.timestamp >= thisMonthStart) {
                currentSpend += t.amount;
            }
        }

        // Run-rate projection
        double dailyAvg       = currentSpend / daysElapsed;
        double runRatePrediction = dailyAvg * daysInMonth;

        // Historical average (last 3 complete months)
        double histAvg = computeHistoricalAvg(transactions, now, 3);

        // Blended prediction: 70% run-rate (current behaviour) + 30% history (regression)
        double projected;
        if (histAvg > 0 && daysElapsed >= 3) {
            projected = (runRatePrediction * 0.70) + (histAvg * 0.30);
        } else if (daysElapsed >= 3) {
            projected = runRatePrediction;
        } else {
            // Too early in month — use history as best estimate
            projected = histAvg > 0 ? histAvg : runRatePrediction;
        }

        return new Prediction(projected, runRatePrediction, histAvg,
                              currentSpend, dailyAvg, daysElapsed, daysInMonth);
    }

    private static double computeHistoricalAvg(List<Transaction> transactions,
                                               long now, int lookBackMonths) {
        double total = 0;
        int count = 0;
        for (int i = 1; i <= lookBackMonths; i++) {
            long start = monthStart(now, -i);
            long end   = monthStart(now, -(i - 1)) - 1;
            double sum = 0;
            boolean hasData = false;
            for (Transaction t : transactions) {
                if (!t.isSelfTransfer && t.timestamp >= start && t.timestamp <= end) {
                    sum += t.amount;
                    hasData = true;
                }
            }
            if (hasData) { total += sum; count++; }
        }
        return count > 0 ? total / count : 0;
    }

    private static long monthStart(long base, int offsetMonths) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(base);
        c.add(Calendar.MONTH, offsetMonths);
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}
