package com.spendtracker.pro;

import java.util.Locale;
import java.util.List;

/**
 * SpendingAnomalyDetector v1.5
 * Flags transactions that are unusually high compared to recent history.
 *
 * Logic:
 * - Needs at least 5 past transactions to establish a baseline
 * - Uses average of past amounts as the baseline
 * - Transaction is anomalous if it is > 2x the average
 * - Also handles edge cases: zero avg, empty/null history
 */
public class SpendingAnomalyDetector {

    private static final int    MIN_HISTORY  = 5;    // minimum samples needed
    private static final double ANOMALY_MULT = 2.0;  // flag if > 2x average
    private static final double MIN_AMOUNT   = 100;  // don't flag tiny amounts

    /**
     * Returns true if the given amount is anomalously high vs history.
     *
     * @param amount  The new transaction amount
     * @param history List of recent transaction amounts (same category recommended)
     */
    public static boolean isAnomalous(double amount, List<Double> history) {
        if (amount < MIN_AMOUNT) return false;
        if (history == null || history.size() < MIN_HISTORY) return false;

        double avg = average(history);
        if (avg <= 0) return false;

        return amount > avg * ANOMALY_MULT;
    }

    /**
     * Returns a human-readable reason string if anomalous, null otherwise.
     * Useful for building notification messages.
     */
    public static String getAnomalyReason(double amount, List<Double> history) {
        if (!isAnomalous(amount, history)) return null;
        double avg = average(history);
        int multiplier = (int) Math.round(amount / avg);
        return String.format(Locale.getDefault(), "%.0fx higher than your usual ₹%.0f", (double) multiplier, avg);
    }

    private static double average(List<Double> values) {
        if (values == null || values.isEmpty()) return 0;
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.size();
    }
}
