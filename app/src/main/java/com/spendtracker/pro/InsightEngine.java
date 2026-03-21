package com.spendtracker.pro;

import java.util.*;
import java.util.stream.*;

public class InsightEngine {

    public static List<String> generateInsights(List<Transaction> allTransactions) {
        List<String> insights = new ArrayList<>();
        if (allTransactions == null || allTransactions.isEmpty()) return insights;

        long now = System.currentTimeMillis();
        long thisMonthStart = getMonthStart(now, 0);
        long lastMonthStart = getMonthStart(now, -1);
        long lastMonthEnd   = thisMonthStart - 1;

        Map<String, Double> thisMonth   = new HashMap<>();
        Map<String, Double> lastMonth   = new HashMap<>();
        Map<String, Double> merchantMap = new HashMap<>();
        Map<String, Double> dayOfWeek   = new HashMap<>();
        double thisTotal = 0, lastTotal = 0;

        for (Transaction t : allTransactions) {
            if (t.isSelfTransfer || t.isCredit) continue;
            if (t.timestamp >= thisMonthStart) {
                thisMonth.merge(t.category != null ? t.category : "Others", t.amount, Double::sum);
                merchantMap.merge(t.merchant != null ? t.merchant : "Unknown", t.amount, Double::sum);
                thisTotal += t.amount;
                Calendar c = Calendar.getInstance(); c.setTimeInMillis(t.timestamp);
                dayOfWeek.merge(getDayName(c.get(Calendar.DAY_OF_WEEK)), t.amount, Double::sum);
            } else if (t.timestamp >= lastMonthStart && t.timestamp <= lastMonthEnd) {
                lastMonth.merge(t.category != null ? t.category : "Others", t.amount, Double::sum);
                lastTotal += t.amount;
            }
        }

        // Sort categories by amount descending
        List<Map.Entry<String, Double>> sortedCats = thisMonth.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(Collectors.toList());

        // Insight 1: Top spending category
        if (sortedCats.size() >= 1) {
            Map.Entry<String, Double> top = sortedCats.get(0);
            double pct = thisTotal > 0 ? (top.getValue() / thisTotal) * 100 : 0;
            insights.add(String.format(Locale.getDefault(), "🥇 Top spend: %s at ₹%.0f (%.0f%% of total)", top.getKey(), top.getValue(), pct));
        }

        // Insight 2: 2nd highest category
        if (sortedCats.size() >= 2) {
            Map.Entry<String, Double> second = sortedCats.get(1);
            double pct = thisTotal > 0 ? (second.getValue() / thisTotal) * 100 : 0;
            insights.add(String.format(Locale.getDefault(), "🥈 2nd highest: %s at ₹%.0f (%.0f%%)", second.getKey(), second.getValue(), pct));
        }

        // Insight 3: 3rd highest category
        if (sortedCats.size() >= 3) {
            Map.Entry<String, Double> third = sortedCats.get(2);
            double pct = thisTotal > 0 ? (third.getValue() / thisTotal) * 100 : 0;
            insights.add(String.format(Locale.getDefault(), "🥉 3rd highest: %s at ₹%.0f (%.0f%%)", third.getKey(), third.getValue(), pct));
        }

        // Insight 4: Top 3 merchants
        List<Map.Entry<String, Double>> sortedMerchants = merchantMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(3)
                .collect(Collectors.toList());

        if (!sortedMerchants.isEmpty()) {
            StringBuilder sb = new StringBuilder("🏪 Top merchants this month:\n");
            int rank = 1;
            for (Map.Entry<String, Double> e : sortedMerchants) {
                String medal = rank == 1 ? "🥇" : rank == 2 ? "🥈" : "🥉";
                sb.append(String.format("  %s %s — ₹%.0f\n", medal, e.getKey(), e.getValue()));
                rank++;
            }
            insights.add(sb.toString().trim());
        }

        // Insight 5: Month vs last month
        if (lastTotal > 0 && thisTotal > 0) {
            double change = ((thisTotal - lastTotal) / lastTotal) * 100;
            if (Math.abs(change) > 10) {
                String dir = change > 0 ? "more" : "less";
                insights.add(String.format(Locale.getDefault(), "📊 You spent %.0f%% %s than last month (₹%.0f vs ₹%.0f)",
                        Math.abs(change), dir, thisTotal, lastTotal));
            }
        }

        // Insight 6: Category changes vs last month
        for (Map.Entry<String, Double> e : thisMonth.entrySet()) {
            Double prev = lastMonth.get(e.getKey());
            if (prev != null && prev > 0) {
                double diff = e.getValue() - prev;
                double pct = (diff / prev) * 100;
                if (pct > 30)
                    insights.add(String.format(Locale.getDefault(), "⚠️ %s up ₹%.0f (%.0f%%) vs last month", e.getKey(), diff, pct));
                else if (pct < -20)
                    insights.add(String.format(Locale.getDefault(), "✅ %s down ₹%.0f (%.0f%%) vs last month", e.getKey(), Math.abs(diff), Math.abs(pct)));
            }
        }

        // Insight 7: Highest spending day
        if (!dayOfWeek.isEmpty()) {
            String topDay = Collections.max(dayOfWeek.entrySet(), Map.Entry.comparingByValue()).getKey();
            insights.add("📅 Highest spending day this month: " + topDay);
        }

        // Insight 8: Prediction
        Calendar cal = Calendar.getInstance();
        int dom = cal.get(Calendar.DAY_OF_MONTH);
        int dim = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        if (dom > 3 && thisTotal > 0) {
            double predicted = (thisTotal / dom) * dim;
            insights.add(String.format(Locale.getDefault(), "🔮 Projected month-end spend: ₹%.0f", predicted));
        }

        // Insight 9: Save 10% tip
        if (thisTotal > 0) {
            insights.add(String.format(Locale.getDefault(), "💡 Cut 10%% = save ₹%.0f this month", thisTotal * 0.1));
        }

        return insights;
    }

    /**
     * Fix 1.7: Enhanced 100-point Financial Health Score
     *
     * Points breakdown:
     *   Budget adherence     : 0–40 pts  (most important)
     *   Spending consistency : 0–20 pts  (no wild spikes)
     *   Month-over-month     : 0–20 pts  (is spending improving?)
     *   Category diversity   : 0–10 pts  (not putting all eggs in one basket)
     *   Investment presence  : 0–10 pts  (saving/investing at all)
     */
    public static int calcHealthScore(List<Transaction> txns, List<Budget> budgets) {
        if (txns == null || txns.isEmpty()) return 50;

        int score = 0;
        long now           = System.currentTimeMillis();
        long thisMonthStart = getMonthStart(now, 0);
        long lastMonthStart = getMonthStart(now, -1);
        long lastMonthEnd   = thisMonthStart - 1;

        // ── 1. Budget adherence (0–40 pts) ────────────────────────
        if (budgets != null && !budgets.isEmpty()) {
            int withinBudget = 0;
            for (Budget b : budgets) {
                if (!b.isOverBudget()) withinBudget++;
            }
            score += (int)(40.0 * withinBudget / budgets.size());
        } else {
            score += 20; // no budgets set — neutral
        }

        // ── 2. Spending consistency (0–20 pts) ────────────────────
        // Fewer spike days = more consistent = better score
        Map<Integer, Double> dayMap = new HashMap<>();
        for (Transaction t : txns) {
            if (t.isSelfTransfer || t.isCredit) continue;
            Calendar c = Calendar.getInstance(); c.setTimeInMillis(t.timestamp);
            dayMap.merge(c.get(Calendar.DAY_OF_YEAR), t.amount, Double::sum);
        }
        if (!dayMap.isEmpty()) {
            double avg = dayMap.values().stream().mapToDouble(d -> d).average().orElse(0);
            long spikeDays = dayMap.values().stream().filter(v -> v > avg * 2.5).count();
            score += Math.max(0, 20 - (int)(spikeDays * 4));
        } else {
            score += 10;
        }

        // ── 3. Month-over-month improvement (0–20 pts) ────────────
        double thisTotal = 0, lastTotal = 0;
        for (Transaction t : txns) {
            if (t.isSelfTransfer || t.isCredit) continue;
            if (t.timestamp >= thisMonthStart)                                  thisTotal += t.amount;
            if (t.timestamp >= lastMonthStart && t.timestamp <= lastMonthEnd)   lastTotal += t.amount;
        }
        if (lastTotal > 0 && thisTotal > 0) {
            double changePct = (thisTotal - lastTotal) / lastTotal;
            if      (changePct < -0.20) score += 20; // reduced by 20%+
            else if (changePct < -0.05) score += 15; // slightly down
            else if (changePct < 0.10)  score += 10; // roughly stable
            else if (changePct < 0.25)  score +=  5; // slightly up
            // else: big increase — 0 pts
        } else {
            score += 10; // not enough history — neutral
        }

        // ── 4. Category diversity (0–10 pts) ─────────────────────
        Set<String> thisMonthCats = new HashSet<>();
        for (Transaction t : txns) {
            if (!t.isSelfTransfer && !t.isCredit && t.timestamp >= thisMonthStart && t.category != null)
                thisMonthCats.add(t.category);
        }
        // Reward if spending spread across 3+ categories (balanced lifestyle)
        int catCount = thisMonthCats.size();
        if      (catCount >= 6) score += 10;
        else if (catCount >= 4) score +=  7;
        else if (catCount >= 2) score +=  4;

        // ── 5. Investment presence (0–10 pts) ─────────────────────
        boolean hasInvestment = txns.stream().anyMatch(t ->
                !t.isSelfTransfer && !t.isCredit && t.timestamp >= thisMonthStart
                && "💰 Investment".equals(t.category));
        if (hasInvestment) score += 10;

        return Math.min(100, Math.max(0, score));
    }

    /** Returns a label for the health score */
    public static String getHealthScoreLabel(int score) {
        if (score >= 85) return "Excellent 🌟";
        if (score >= 70) return "Good 👍";
        if (score >= 50) return "Fair ⚡";
        if (score >= 30) return "Needs Work ⚠️";
        return "Critical 🔴";
    }

    private static long getMonthStart(long ts, int offset) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(ts); c.add(Calendar.MONTH, offset);
        c.set(Calendar.DAY_OF_MONTH,1); c.set(Calendar.HOUR_OF_DAY,0);
        c.set(Calendar.MINUTE,0); c.set(Calendar.SECOND,0); c.set(Calendar.MILLISECOND,0);
        return c.getTimeInMillis();
    }

    private static String getDayName(int dow) {
        String[] d = {"","Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"};
        return dow >= 1 && dow <= 7 ? d[dow] : "Unknown";
    }
    /**
     * Fix 1.6: Category-level month-over-month insight.
     * Returns a single insight string for one category.
     * e.g. "⚠ Your Food spending increased 32% this month"
     */
    public static String generateCategoryInsight(String category,
                                                  double lastMonth, double thisMonth) {
        if (lastMonth <= 0 && thisMonth <= 0) return null;
        if (lastMonth <= 0) {
            return String.format(Locale.getDefault(), "🆕 New spend in %s: ₹%.0f this month", category, thisMonth);
        }
        double changePct = ((thisMonth - lastMonth) / lastMonth) * 100;
        if (changePct > 30) {
            return String.format(Locale.getDefault(), "⚠️ Your %s spending increased %.0f%% this month (₹%.0f → ₹%.0f)",
                    category, changePct, lastMonth, thisMonth);
        } else if (changePct < -20) {
            return String.format(Locale.getDefault(), "✅ Your %s spending dropped %.0f%% — great job! (₹%.0f → ₹%.0f)",
                    category, Math.abs(changePct), lastMonth, thisMonth);
        } else if (Math.abs(changePct) <= 10) {
            return String.format(Locale.getDefault(), "📊 %s spending stable this month (₹%.0f)", category, thisMonth);
        }
        return null; // minor change — not worth showing
    }

    /**
     * Fix 1.6: Overall month-over-month insight.
     * e.g. "⚠ Spending increased by 22% compared to last month"
     */
    public static String generateOverallInsight(double lastMonthTotal, double thisMonthTotal) {
        if (lastMonthTotal <= 0) return null;
        double changePct = ((thisMonthTotal - lastMonthTotal) / lastMonthTotal) * 100;
        if (changePct > 30) {
            return String.format(Locale.getDefault(), "⚠️ Spending increased by %.0f%% compared to last month", changePct);
        } else if (changePct < -20) {
            return String.format(Locale.getDefault(), "✅ Great! Spending reduced by %.0f%% this month", Math.abs(changePct));
        }
        return "📊 Spending stable this month";
    }

    /**
     * Fix 1.6: Simple single-line insight method (exact spec signature).
     * Delegates to generateOverallInsight internally.
     *
     * Usage example:
     *   String msg = InsightEngine.generateInsight(lastMonthTotal, thisMonthTotal);
     */
    public static String generateInsight(double lastMonth, double thisMonth) {
        return generateOverallInsight(lastMonth, thisMonth);
    }
}
