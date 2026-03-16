package com.spendtracker.pro;

import java.util.*;
import java.util.stream.*;

/**
 * AutoBudgetGenerator v1.7
 *
 * Analyses historical spending (last 3 months) per category and suggests
 * a sensible budget limit = average × 1.2 (20% buffer above past average).
 *
 * Minimum history required: at least 1 transaction in that category.
 * If only 1 month of data, uses that month's total directly.
 */
public class AutoBudgetGenerator {

    private static final double BUFFER_FACTOR = 1.20; // 20% above average
    private static final int    LOOK_BACK_MONTHS = 3;

    public static class BudgetSuggestion {
        public final String category;
        public final double suggestedLimit;  // rounded up to nearest 100
        public final double avgSpend;        // raw average
        public final int    monthsAnalysed;
        public final String rationale;       // human-readable explanation

        BudgetSuggestion(String category, double suggestedLimit,
                         double avgSpend, int monthsAnalysed) {
            this.category        = category;
            this.suggestedLimit  = suggestedLimit;
            this.avgSpend        = avgSpend;
            this.monthsAnalysed  = monthsAnalysed;
            this.rationale       = String.format(
                "Avg ₹%.0f/month over %d month%s → Suggested ₹%.0f",
                avgSpend, monthsAnalysed,
                monthsAnalysed == 1 ? "" : "s",
                suggestedLimit);
        }
    }

    /**
     * Generate budget suggestions for all categories that have past spending.
     * Skips categories with zero historical spend.
     * Results sorted by suggested limit descending.
     *
     * @param transactions  Full transaction history from DB
     * @return List of BudgetSuggestion, one per active category
     */
    public static List<BudgetSuggestion> generate(List<Transaction> transactions) {
        if (transactions == null || transactions.isEmpty()) return new ArrayList<>();

        long now = System.currentTimeMillis();

        // Build month-bucket boundaries for last LOOK_BACK_MONTHS months (excluding current)
        // e.g. if today is March, analyse Dec, Jan, Feb
        List<long[]> monthBuckets = new ArrayList<>();
        for (int i = 1; i <= LOOK_BACK_MONTHS; i++) {
            long start = monthStart(now, -i);
            long end   = monthStart(now, -(i - 1)) - 1;
            monthBuckets.add(new long[]{start, end});
        }

        // Map: category → list of monthly totals (one per non-zero month)
        Map<String, List<Double>> categoryMonthlyTotals = new LinkedHashMap<>();

        for (long[] bucket : monthBuckets) {
            // Sum per category within this month
            Map<String, Double> monthCatSum = new HashMap<>();
            for (Transaction t : transactions) {
                if (t.isSelfTransfer) continue;
                if (t.timestamp >= bucket[0] && t.timestamp <= bucket[1]) {
                    String cat = t.category != null ? t.category : "💼 Others";
                    monthCatSum.merge(cat, t.amount, Double::sum);
                }
            }
            // Accumulate into category list
            for (Map.Entry<String, Double> e : monthCatSum.entrySet()) {
                categoryMonthlyTotals
                    .computeIfAbsent(e.getKey(), k -> new ArrayList<>())
                    .add(e.getValue());
            }
        }

        List<BudgetSuggestion> suggestions = new ArrayList<>();
        for (Map.Entry<String, List<Double>> e : categoryMonthlyTotals.entrySet()) {
            List<Double> monthlies = e.getValue();
            if (monthlies.isEmpty()) continue;

            double avg = monthlies.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            if (avg < 10) continue; // skip trivial amounts

            double raw       = avg * BUFFER_FACTOR;
            double suggested = Math.ceil(raw / 100.0) * 100; // round up to nearest 100

            suggestions.add(new BudgetSuggestion(
                e.getKey(), suggested, avg, monthlies.size()));
        }

        // Sort by suggested limit descending
        suggestions.sort((a, b) -> Double.compare(b.suggestedLimit, a.suggestedLimit));
        return suggestions;
    }

    /**
     * Get suggestion for a single category (used when showing hint in dialog).
     * Returns null if no history available.
     */
    public static BudgetSuggestion getSuggestion(List<Transaction> transactions, String category) {
        return generate(transactions).stream()
                .filter(s -> s.category.equals(category))
                .findFirst().orElse(null);
    }

    // Start of month N months from now (negative = past)
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
