package com.spendtracker.pro;

import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.view.MenuItem;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.*;
import com.github.mikephil.charting.charts.*;
import com.github.mikephil.charting.components.*;
import com.github.mikephil.charting.data.*;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import java.text.SimpleDateFormat;
import java.util.*;

public class AnalyticsActivity extends AppCompatActivity {
    private BarChart barChart;
    private PieChart pieChart;
    private LineChart lineChart;
    private TextView tvWeekTotal, tvAvgDaily, tvTopMerchant, tvInsights, tvMonthTotal, tvHealthScore, tvMerchantBreakdown;
    private AppDatabase db;
    // Instance vars shared with pie chart drill-down listener
    private Map<String, Double> catMapInstance = new LinkedHashMap<>();
    private List<Transaction> allTransactions = new ArrayList<>();
    private static final int[] COLORS = {0xFFFF6B6B, 0xFF4ECDC4, 0xFF45B7D1, 0xFF96CEB4, 0xFFFFBE0B, 0xFFDDA0DD, 0xFFFF8C69, 0xFFA8E6CF, 0xFFFFD3B6, 0xFFB8E0FF, 0xFFC3B1E1, 0xFF98D8C8};

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_analytics);
        db = AppDatabase.getInstance(this);
        Toolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) { getSupportActionBar().setDisplayHomeAsUpEnabled(true); getSupportActionBar().setTitle("Analytics"); }
        barChart = findViewById(R.id.barChart);
        pieChart = findViewById(R.id.pieChart);
        lineChart = findViewById(R.id.lineChart);
        tvWeekTotal   = findViewById(R.id.tvWeekTotal);
        tvAvgDaily    = findViewById(R.id.tvAvgDaily);
        tvTopMerchant = findViewById(R.id.tvTopMerchant);
        tvInsights    = findViewById(R.id.tvInsights);
        tvMonthTotal  = findViewById(R.id.tvMonthTotal);
        tvHealthScore = findViewById(R.id.tvHealthScore);
        tvMerchantBreakdown = findViewById(R.id.tvMerchantBreakdown);
        loadData();
    }

    private void loadData() {
        AppExecutors.db().execute(() -> {
            long now = System.currentTimeMillis();
            long week = now - 7L * 86400000L;
            long monthStart = getMonthStart(now);
            long month30 = now - 30L * 86400000L;
            // Query only spending rows needed for current analytics windows.
            List<Transaction> all = db.transactionDao().getSpendingInRange(month30, now);
            if (all.isEmpty()) { runOnUiThread(() -> tvInsights.setText("No data yet. Scan your SMS!")); return; }

            SimpleDateFormat dayFmt = new SimpleDateFormat("MM/dd", Locale.getDefault());

            // Daily bar data (last 7 days)
            Map<String, Double> daily = new LinkedHashMap<>();
            for (int i = 6; i >= 0; i--) {
                Calendar c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, -i);
                daily.put(dayFmt.format(c.getTime()), 0.0);
            }

            // Category + merchant + month totals
            Map<String, Double> catMap = new LinkedHashMap<>(), merchantMap = new LinkedHashMap<>();
            allTransactions = all; // Store for drill-down
            double weekTotal = 0, monthTotal = 0;

            for (Transaction t : all) {
                if (t.isSelfTransfer || t.isCredit) continue;
                if (t.timestamp >= week) {
                    daily.merge(dayFmt.format(new Date(t.timestamp)), t.amount, Double::sum);
                    weekTotal += t.amount;
                }
                if (t.timestamp >= monthStart) {
                    catMap.merge(t.category, t.amount, Double::sum);
                    merchantMap.merge(t.merchant != null ? t.merchant : "Unknown", t.amount, Double::sum);
                    monthTotal += t.amount;
                }
            }

            // Line chart data (last 30 days)
            SimpleDateFormat d30 = new SimpleDateFormat("dd", Locale.getDefault());
            Map<String, Double> last30 = new LinkedHashMap<>();
            for (int i = 29; i >= 0; i--) {
                Calendar c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, -i);
                last30.put(d30.format(c.getTime()), 0.0);
            }
            for (Transaction t : all) {
                if (!t.isSelfTransfer && !t.isCredit && t.timestamp >= month30)
                    last30.merge(d30.format(new Date(t.timestamp)), t.amount, Double::sum);
            }

            String topMerchant = merchantMap.isEmpty() ? "N/A" : Collections.max(merchantMap.entrySet(), Map.Entry.comparingByValue()).getKey() + " ₹" + String.format("%.0f", Collections.max(merchantMap.entrySet(), Map.Entry.comparingByValue()).getValue());

            List<String> insights = InsightEngine.generateInsights(all);
            List<Budget> budgets = db.budgetDao().getByMonthYearSync(Calendar.getInstance().get(Calendar.MONTH)+1, Calendar.getInstance().get(Calendar.YEAR));
            int score = InsightEngine.calcHealthScore(all, budgets);

            // Build chart entries
            List<BarEntry> barEntries = new ArrayList<>();
            List<String> barLabels = new ArrayList<>(daily.keySet());
            int bi = 0;
            for (Double v : daily.values()) barEntries.add(new BarEntry(bi++, v.floatValue()));

            List<PieEntry> pieEntries = new ArrayList<>();
            for (Map.Entry<String, Double> e : catMap.entrySet()) {
                String lbl = e.getKey().replaceAll("[^a-zA-Z\\s]", "").trim();
                if (!lbl.isEmpty()) pieEntries.add(new PieEntry(e.getValue().floatValue(), lbl));
            }

            List<Entry> lineEntries = new ArrayList<>();
            int li = 0;
            for (Double v : last30.values()) lineEntries.add(new Entry(li++, v.floatValue()));

            // Build merchant breakdown text
            StringBuilder mSb = new StringBuilder();
            merchantMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> mSb.append(String.format("• %-18s  ₹%.0f\n", e.getKey(), e.getValue())));
            final String merchantBreakdownStr = mSb.toString().trim();

            final double ft = weekTotal, fm = monthTotal, favg = weekTotal / 7.0;
            final String ftm = topMerchant;
            final StringBuilder insightSb = new StringBuilder();
            for (String ins : insights) insightSb.append("• ").append(ins).append("\n\n");
            final int fscore = score;

            runOnUiThread(() -> {
                tvWeekTotal.setText("₹" + String.format("%.0f", ft));
                tvMonthTotal.setText("₹" + String.format("%.0f", fm));
                tvAvgDaily.setText("₹" + String.format("%.0f", favg));
                tvTopMerchant.setText(ftm);
                tvMerchantBreakdown.setText(merchantBreakdownStr.isEmpty() ? "No transactions yet" : merchantBreakdownStr);
                tvInsights.setText(insightSb.toString().trim());
                tvHealthScore.setText("Health Score: " + fscore + "/100");
                int sc = fscore >= 70 ? Color.parseColor("#10B981") : fscore >= 40 ? Color.parseColor("#F59E0B") : Color.parseColor("#EF4444");
                tvHealthScore.setTextColor(sc);
                catMapInstance = catMap; // Store for drill-down listener
                setupBarChart(barEntries, barLabels);
                setupPieChart(pieEntries);
                setupLineChart(lineEntries);
            });
        });
    }

    private void setupBarChart(List<BarEntry> entries, List<String> labels) {
        BarDataSet ds = new BarDataSet(entries, "Daily Spend ₹");
        ds.setColors(COLORS);
        ds.setValueTextColor(Color.WHITE);
        ds.setValueTextSize(9f);
        barChart.setData(new BarData(ds));
        styleChart(barChart);
        XAxis x = barChart.getXAxis();
        x.setValueFormatter(new IndexAxisValueFormatter(labels));
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setTextColor(Color.WHITE);
        x.setDrawGridLines(false);
        barChart.getAxisLeft().setTextColor(Color.WHITE);
        barChart.getAxisRight().setEnabled(false);
        barChart.animateY(900);
        barChart.invalidate();
    }

    private void setupPieChart(List<PieEntry> entries) {
        if (entries.isEmpty()) return;
        PieDataSet ds = new PieDataSet(entries, "");
        ds.setColors(COLORS);
        ds.setValueTextColor(Color.WHITE);
        ds.setValueTextSize(10f);
        ds.setSliceSpace(2f);
        pieChart.setData(new PieData(ds));
        pieChart.setBackgroundColor(Color.parseColor("#0F172A"));
        pieChart.getDescription().setEnabled(false);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(Color.parseColor("#0F172A"));
        pieChart.setHoleRadius(40f);
        pieChart.setCenterText("Category\nBreakdown");
        pieChart.setCenterTextColor(Color.WHITE);
        pieChart.setCenterTextSize(12f);
        pieChart.getLegend().setTextColor(Color.WHITE);
        pieChart.getLegend().setTextSize(10f);
        pieChart.animateY(1000);
        pieChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                if (e instanceof PieEntry) {
                    String label = ((PieEntry) e).getLabel();
                    showCategoryDrillDown(label);
                }
            }
            @Override public void onNothingSelected() {}
        });
        pieChart.invalidate();
    }

    private void setupLineChart(List<Entry> entries) {
        LineDataSet ds = new LineDataSet(entries, "30-Day Trend");
        ds.setColor(Color.parseColor("#7C3AED"));
        ds.setCircleColor(Color.parseColor("#A78BFA"));
        ds.setLineWidth(2f);
        ds.setCircleRadius(3f);
        ds.setDrawFilled(true);
        ds.setFillColor(Color.parseColor("#4C1D95"));
        ds.setFillAlpha(80);
        ds.setValueTextColor(Color.WHITE);
        ds.setDrawValues(false);
        lineChart.setData(new LineData(ds));
        styleChart(lineChart);
        lineChart.getXAxis().setTextColor(Color.WHITE);
        lineChart.getXAxis().setDrawGridLines(false);
        lineChart.getAxisLeft().setTextColor(Color.WHITE);
        lineChart.getAxisRight().setEnabled(false);
        lineChart.animateX(800);
        lineChart.invalidate();
    }

    private void styleChart(Chart chart) {
        chart.setBackgroundColor(Color.parseColor("#0F172A"));
        chart.getDescription().setEnabled(false);
        chart.getLegend().setTextColor(Color.WHITE);
    }

    /** Show a bottom sheet with all transactions for the tapped pie slice category */
    private void showCategoryDrillDown(String categoryLabel) {
        // Match full category name from catMapInstance (label has emoji stripped)
        String matchedCat = null;
        for (String key : catMapInstance.keySet()) {
            String stripped = key.replaceAll("[^a-zA-Z\\s]", "").trim();
            if (stripped.equalsIgnoreCase(categoryLabel)) { matchedCat = key; break; }
        }
        if (matchedCat == null) matchedCat = categoryLabel;

        // Filter transactions for this category this month
        long monthStart = getMonthStart(System.currentTimeMillis());
        List<Transaction> filtered = new ArrayList<>();
        for (Transaction t : allTransactions) {
            if (!t.isSelfTransfer && !t.isCredit && t.timestamp >= monthStart
                    && matchedCat.equals(t.category)) {
                filtered.add(t);
            }
        }

        final String cat = matchedCat;
        final List<Transaction> txns = filtered;

        // Build and show bottom sheet dialog
        View sheet = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_category, null);
        TextView tvTitle  = sheet.findViewById(R.id.tvCatTitle);
        TextView tvTotal  = sheet.findViewById(R.id.tvCatTotal);
        RecyclerView rv   = sheet.findViewById(R.id.rvCatTransactions);

        double total = 0;
        for (Transaction t : txns) total += t.amount;

        tvTitle.setText(cat);
        tvTotal.setText(String.format("₹%.0f · %d transactions", total, txns.size()));

        rv.setLayoutManager(new LinearLayoutManager(this));
        TransactionAdapter adapter = new TransactionAdapter(true);
        adapter.setTransactions(txns);
        rv.setAdapter(adapter);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this, R.style.AlertDialogDark)
                .setView(sheet)
                .create();
        // Make it appear like a bottom sheet
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setGravity(android.view.Gravity.BOTTOM);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();
    }

    private long getMonthStart(long ts) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(ts);
        c.set(Calendar.DAY_OF_MONTH,1); c.set(Calendar.HOUR_OF_DAY,0); c.set(Calendar.MINUTE,0); c.set(Calendar.SECOND,0); c.set(Calendar.MILLISECOND,0);
        return c.getTimeInMillis();
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { getOnBackPressedDispatcher().onBackPressed(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
