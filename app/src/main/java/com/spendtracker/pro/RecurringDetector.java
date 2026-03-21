package com.spendtracker.pro;

import java.util.*;

/**
 * RecurringDetector — P2 auto-detection of recurring transactions.
 *
 * Logic:
 *  1. Loads all non-credit, non-self-transfer transactions from the last 90 days.
 *  2. Groups them by merchant name (case-insensitive).
 *  3. For each merchant group, checks if a similar amount (±15%) appears in
 *     2 or more distinct calendar months.
 *  4. If a pattern is found and no active recurring entry exists for that
 *     merchant, inserts one automatically.
 *
 * This is intentionally conservative — it only auto-inserts when the evidence
 * is clear (2+ months, same merchant, similar amount). The user can delete
 * any false positives from RecurringActivity.
 */
public class RecurringDetector {

    /** Amount tolerance: 15% — handles small variation (taxes, rounding, tips). */
    private static final double AMOUNT_TOLERANCE = 0.15;

    /** Minimum distinct months a merchant must appear in to be flagged. */
    private static final int MIN_MONTHS = 2;

    /** Look back 90 days (3 months) for pattern detection. */
    private static final long WINDOW_MS = 90L * 24 * 60 * 60 * 1000;

    public static class DetectedPattern {
        public final String merchant;
        public final double typicalAmount;   // median of matched amounts
        public final String category;
        public final String paymentMethod;
        public final int    monthsDetected;

        DetectedPattern(String merchant, double typicalAmount, String category,
                        String paymentMethod, int monthsDetected) {
            this.merchant      = merchant;
            this.typicalAmount = typicalAmount;
            this.category      = category;
            this.paymentMethod = paymentMethod;
            this.monthsDetected = monthsDetected;
        }
    }

    /**
     * Scans the transaction history and inserts new RecurringTransaction rows
     * for any merchant that shows a consistent monthly pattern.
     * Safe to call multiple times — skips merchants already in the recurring table.
     */
    public static int detectAndInsert(AppDatabase db) {
        long since = System.currentTimeMillis() - WINDOW_MS;
        List<Transaction> txns = db.transactionDao().getNonCreditSince(since);
        if (txns == null || txns.isEmpty()) return 0;

        List<DetectedPattern> patterns = detect(txns);
        if (patterns.isEmpty()) return 0;

        List<RecurringTransaction> existing = db.recurringDao().getActiveSync();
        Set<String> existingNames = new HashSet<>();
        for (RecurringTransaction r : existing) {
            if (r.name != null) existingNames.add(r.name.toLowerCase(Locale.ROOT).trim());
        }

        int inserted = 0;
        for (DetectedPattern p : patterns) {
            if (existingNames.contains(p.merchant.toLowerCase(Locale.ROOT).trim())) continue;

            RecurringTransaction r = new RecurringTransaction();
            r.name          = p.merchant;
            r.amount        = p.typicalAmount;
            r.category      = p.category;
            r.icon          = CategoryEngine.getInfo(p.category).icon;
            r.frequency     = "MONTHLY";
            r.dayOfMonth    = estimateDayOfMonth(txns, p.merchant);
            r.nextDueDate   = nextMonthDue(r.dayOfMonth);
            r.isActive      = true;
            r.paymentMethod = p.paymentMethod;
            r.notes         = "Auto-detected (" + p.monthsDetected + " months)";

            db.recurringDao().insert(r);
            inserted++;
        }
        return inserted;
    }

    /**
     * Pure detection — returns patterns without touching the DB.
     * Useful for previewing what would be auto-inserted.
     */
    public static List<DetectedPattern> detect(List<Transaction> txns) {
        // Group by merchant (case-insensitive)
        Map<String, List<Transaction>> byMerchant = new LinkedHashMap<>();
        for (Transaction t : txns) {
            if (t.merchant == null || t.merchant.isEmpty()) continue;
            String key = t.merchant.trim().toLowerCase(Locale.ROOT);
            if (!byMerchant.containsKey(key)) byMerchant.put(key, new ArrayList<>());
            byMerchant.get(key).add(t);
        }

        List<DetectedPattern> patterns = new ArrayList<>();

        for (Map.Entry<String, List<Transaction>> entry : byMerchant.entrySet()) {
            List<Transaction> group = entry.getValue();
            if (group.size() < MIN_MONTHS) continue;

            // Find a cluster of similar amounts within the group
            DetectedPattern p = analyseGroup(group);
            if (p != null) patterns.add(p);
        }

        // Sort by months detected descending — strongest patterns first
        patterns.sort((a, b) -> Integer.compare(b.monthsDetected, a.monthsDetected));
        return patterns;
    }

    // ── Private helpers ───────────────────────────────────────────

    private static DetectedPattern analyseGroup(List<Transaction> group) {
        // Sort by amount to find clusters
        group.sort(Comparator.comparingDouble(t -> t.amount));

        // Sliding window: find the largest cluster where max/min ≤ (1 + tolerance)
        int bestStart = 0, bestEnd = 0;
        for (int i = 0; i < group.size(); i++) {
            for (int j = i; j < group.size(); j++) {
                double ratio = group.get(j).amount / group.get(i).amount;
                if (ratio <= (1 + AMOUNT_TOLERANCE)) {
                    if ((j - i) > (bestEnd - bestStart)) {
                        bestStart = i;
                        bestEnd   = j;
                    }
                } else {
                    break;
                }
            }
        }

        List<Transaction> cluster = group.subList(bestStart, bestEnd + 1);

        // Count distinct months in the cluster
        Set<String> months = new HashSet<>();
        for (Transaction t : cluster) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(t.timestamp);
            months.add(c.get(Calendar.YEAR) + "-" + c.get(Calendar.MONTH));
        }

        if (months.size() < MIN_MONTHS) return null;

        double typicalAmount = median(cluster);
        String category      = mostCommon(cluster);
        String payment       = mostCommonPayment(cluster);
        String displayName   = cluster.get(0).merchant; // original casing

        return new DetectedPattern(displayName, typicalAmount, category,
                payment, months.size());
    }

    private static double median(List<Transaction> list) {
        List<Double> amounts = new ArrayList<>();
        for (Transaction t : list) amounts.add(t.amount);
        Collections.sort(amounts);
        int mid = amounts.size() / 2;
        return amounts.size() % 2 == 0
                ? (amounts.get(mid - 1) + amounts.get(mid)) / 2.0
                : amounts.get(mid);
    }

    private static String mostCommon(List<Transaction> list) {
        Map<String, Integer> freq = new HashMap<>();
        for (Transaction t : list) {
            String cat = t.category != null ? t.category : "💼 Others";
            freq.put(cat, freq.getOrDefault(cat, 0) + 1);
        }
        String best = "💼 Others";
        int max = 0;
        for (Map.Entry<String, Integer> e : freq.entrySet()) {
            if (e.getValue() > max) { max = e.getValue(); best = e.getKey(); }
        }
        return best;
    }

    private static String mostCommonPayment(List<Transaction> list) {
        Map<String, Integer> freq = new HashMap<>();
        for (Transaction t : list) {
            String pm = t.paymentMethod != null ? t.paymentMethod : "BANK";
            freq.put(pm, freq.getOrDefault(pm, 0) + 1);
        }
        String best = "BANK";
        int max = 0;
        for (Map.Entry<String, Integer> e : freq.entrySet()) {
            if (e.getValue() > max) { max = e.getValue(); best = e.getKey(); }
        }
        return best;
    }

    /**
     * Estimates the typical day-of-month for a merchant's transactions.
     * Returns the median day across all occurrences.
     */
    private static int estimateDayOfMonth(List<Transaction> all, String merchant) {
        List<Integer> days = new ArrayList<>();
        for (Transaction t : all) {
            if (t.merchant == null) continue;
            if (!t.merchant.trim().equalsIgnoreCase(merchant.trim())) continue;
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(t.timestamp);
            days.add(c.get(Calendar.DAY_OF_MONTH));
        }
        if (days.isEmpty()) return 1;
        Collections.sort(days);
        return days.get(days.size() / 2); // median day
    }

    private static long nextMonthDue(int dayOfMonth) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.MONTH, 1);
        int maxDay = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        c.set(Calendar.DAY_OF_MONTH, Math.min(dayOfMonth, maxDay));
        c.set(Calendar.HOUR_OF_DAY, 9);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}
