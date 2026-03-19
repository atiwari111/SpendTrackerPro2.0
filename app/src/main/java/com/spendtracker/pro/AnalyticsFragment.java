package com.spendtracker.pro;

import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;
import com.github.mikephil.charting.charts.*;
import com.github.mikephil.charting.components.*;
import com.github.mikephil.charting.data.*;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import java.text.SimpleDateFormat;
import java.util.*;

public class AnalyticsFragment extends Fragment {

    private BarChart barChart;
    private BarChart barChartIncomeExpense;  // P4: income vs expense
    private PieChart pieChart;
    private LineChart lineChart;
    private TextView tvWeekTotal, tvAvgDaily, tvTopMerchant, tvInsights,
            tvMonthTotal, tvHealthScore, tvMerchantBreakdown;
    private TextView tvSelectedMonth, tvTotalIncome, tvTotalExpense, tvNetSavings; // P4
    private AppDatabase db;
    private Map<String, Double> catMapInstance = new LinkedHashMap<>();
    private List<Transaction> allTransactions = new ArrayList<>();
    private static final int[] COLORS = {
        0xFFFF6B6B, 0xFF4ECDC4, 0xFF45B7D1, 0xFF96CEB4, 0xFFFFBE0B,
        0xFFDDA0DD, 0xFFFF8C69, 0xFFA8E6CF, 0xFFFFD3B6, 0xFFB8E0FF,
        0xFFC3B1E1, 0xFF98D8C8
    };

    // P4: month navigation state — 0 = current month, -1 = last month, etc.
    private int monthOffset = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_analytics, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        db = AppDatabase.getInstance(requireContext());

        androidx.appcompat.widget.Toolbar tb = view.findViewById(R.id.toolbar);
        if (tb != null) tb.setTitle("Analytics");

        barChart  = view.findViewById(R.id.barChart);
        barChartIncomeExpense = view.findViewById(R.id.barChartIncomeExpense);
        pieChart  = view.findViewById(R.id.pieChart);
        lineChart = view.findViewById(R.id.lineChart);
        tvWeekTotal         = view.findViewById(R.id.tvWeekTotal);
        tvAvgDaily          = view.findViewById(R.id.tvAvgDaily);
        tvTopMerchant       = view.findViewById(R.id.tvTopMerchant);
        tvInsights          = view.findViewById(R.id.tvInsights);
        tvMonthTotal        = view.findViewById(R.id.tvMonthTotal);
        tvHealthScore       = view.findViewById(R.id.tvHealthScore);
        tvMerchantBreakdown = view.findViewById(R.id.tvMerchantBreakdown);
        tvSelectedMonth     = view.findViewById(R.id.tvSelectedMonth);
        tvTotalIncome       = view.findViewById(R.id.tvTotalIncome);
        tvTotalExpense      = view.findViewById(R.id.tvTotalExpense);
        tvNetSavings        = view.findViewById(R.id.tvNetSavings);

        // P4: month navigation
        view.findViewById(R.id.btnPrevMonth).setOnClickListener(v -> {
            monthOffset--;
            updateMonthLabel();
            loadData();
        });
        view.findViewById(R.id.btnNextMonth).setOnClickListener(v -> {
            if (monthOffset < 0) { monthOffset++; updateMonthLabel(); loadData(); }
        });
        updateMonthLabel();

        loadData();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadData(); // Refresh when tab becomes visible again
    }

    private void updateMonthLabel() {
        if (tvSelectedMonth == null) return;
        if (monthOffset == 0) {
            tvSelectedMonth.setText("This Month");
        } else {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.MONTH, monthOffset);
            tvSelectedMonth.setText(new java.text.SimpleDateFormat("MMMM yyyy",
                    Locale.getDefault()).format(c.getTime()));
        }
        // Disable "next" button when already on current month
        if (getView() != null) {
            android.widget.ImageButton btnNext = getView().findViewById(R.id.btnNextMonth);
            if (btnNext != null) btnNext.setAlpha(monthOffset < 0 ? 1f : 0.3f);
        }
    }

    /** Returns {monthStart, monthEnd} for the currently selected month offset. */
    private long[] getSelectedMonthRange() {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.MONTH, monthOffset);
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        long start = c.getTimeInMillis();
        c.add(Calendar.MONTH, 1);
        return new long[]{start, c.getTimeInMillis()};
    }

    private void loadData() {
        AppExecutors.db().execute(() -> {
            if (!isAdded()) return;
            List<Transaction> all = db.transactionDao().getAllSync();
            if (all.isEmpty()) {
                requireActivity().runOnUiThread(() -> {
                    if (isAdded() && tvInsights != null)
                        tvInsights.setText("No data yet. Scan your SMS!");
                });
                return;
            }

            long now = System.currentTimeMillis();
            long week = now - 7L * 86400000L;
            long[] selectedRange = getSelectedMonthRange();
            long monthStart = selectedRange[0];
            long monthEnd   = selectedRange[1];
            SimpleDateFormat dayFmt = new SimpleDateFormat("MM/dd", Locale.getDefault());

            Map<String, Double> daily = new LinkedHashMap<>();
            for (int i = 6; i >= 0; i--) {
                Calendar c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, -i);
                daily.put(dayFmt.format(c.getTime()), 0.0);
            }

            Map<String, Double> catMap = new LinkedHashMap<>(), merchantMap = new LinkedHashMap<>();
            allTransactions = all;
            double weekTotal = 0, monthExpense = 0, monthIncome = 0;

            for (Transaction t : all) {
                if (t.isSelfTransfer) continue;
                if (t.timestamp >= week && !t.isCredit) {
                    daily.merge(dayFmt.format(new Date(t.timestamp)), t.amount, Double::sum);
                    weekTotal += t.amount;
                }
                if (t.timestamp >= monthStart && t.timestamp < monthEnd) {
                    if (t.isCredit) {
                        monthIncome += t.amount;
                    } else {
                        monthExpense += t.amount;
                        catMap.merge(t.category, t.amount, Double::sum);
                        merchantMap.merge(t.merchant != null ? t.merchant : "Unknown",
                                t.amount, Double::sum);
                    }
                }
            }
            double monthTotal = monthExpense;

            // 30-day line chart
            SimpleDateFormat d30 = new SimpleDateFormat("dd", Locale.getDefault());
            Map<String, Double> last30 = new LinkedHashMap<>();
            for (int i = 29; i >= 0; i--) {
                Calendar c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, -i);
                last30.put(d30.format(c.getTime()), 0.0);
            }
            long month30 = now - 30L * 86400000L;
            for (Transaction t : all) {
                if (!t.isSelfTransfer && !t.isCredit && t.timestamp >= month30)
                    last30.merge(d30.format(new Date(t.timestamp)), t.amount, Double::sum);
            }

            String topMerchant = merchantMap.isEmpty() ? "N/A"
                    : Collections.max(merchantMap.entrySet(), Map.Entry.comparingByValue()).getKey()
                    + " ₹" + String.format("%.0f",
                    Collections.max(merchantMap.entrySet(), Map.Entry.comparingByValue()).getValue());

            // Only use current month budgets for health score
            Calendar now2 = Calendar.getInstance();
            List<Budget> budgets = db.budgetDao().getByMonthYearSync(
                    now2.get(Calendar.MONTH) + 1, now2.get(Calendar.YEAR));
            int score = InsightEngine.calcHealthScore(all, budgets);
            List<String> insights = InsightEngine.generateInsights(all);

            // Bar chart entries (daily 7-day spend)
            List<BarEntry> barEntries = new ArrayList<>();
            List<String> barLabels = new ArrayList<>(daily.keySet());
            int bi = 0;
            for (Double v : daily.values()) barEntries.add(new BarEntry(bi++, v.floatValue()));

            // Pie chart entries (selected month categories)
            List<PieEntry> pieEntries = new ArrayList<>();
            for (Map.Entry<String, Double> e : catMap.entrySet()) {
                String lbl = e.getKey().replaceAll("[^a-zA-Z\\s]", "").trim();
                if (!lbl.isEmpty()) pieEntries.add(new PieEntry(e.getValue().floatValue(), lbl));
            }

            // Line chart entries
            List<Entry> lineEntries = new ArrayList<>();
            int li = 0;
            for (Double v : last30.values()) lineEntries.add(new Entry(li++, v.floatValue()));

            // P4: income vs expense — last 6 months grouped bar
            List<BarEntry> incomeEntries = new ArrayList<>();
            List<BarEntry> expenseEntries = new ArrayList<>();
            List<String> monthLabels = new ArrayList<>();
            for (int i = 5; i >= 0; i--) {
                Calendar mc = Calendar.getInstance();
                mc.add(Calendar.MONTH, -i);
                mc.set(Calendar.DAY_OF_MONTH, 1);
                mc.set(Calendar.HOUR_OF_DAY, 0); mc.set(Calendar.MINUTE, 0);
                mc.set(Calendar.SECOND, 0); mc.set(Calendar.MILLISECOND, 0);
                long mS = mc.getTimeInMillis();
                mc.add(Calendar.MONTH, 1);
                long mE = mc.getTimeInMillis();
                double inc = 0, exp = 0;
                for (Transaction t : all) {
                    if (t.isSelfTransfer || t.timestamp < mS || t.timestamp >= mE) continue;
                    if (t.isCredit) inc += t.amount; else exp += t.amount;
                }
                int idx = 5 - i;
                incomeEntries.add(new BarEntry(idx, (float) inc));
                expenseEntries.add(new BarEntry(idx, (float) exp));
                monthLabels.add(new java.text.SimpleDateFormat("MMM", Locale.getDefault())
                        .format(mc.getTime() - 1));
            }

            // Merchant breakdown text
            StringBuilder mSb = new StringBuilder();
            merchantMap.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(10)
                    .forEach(e -> mSb.append(String.format("• %-18s  ₹%.0f\n", e.getKey(), e.getValue())));
            final String mBreakdown = mSb.toString().trim();

            final StringBuilder insightSb = new StringBuilder();
            for (String ins : insights) insightSb.append("• ").append(ins).append("\n\n");

            final double ft = weekTotal, fm = monthTotal, favg = weekTotal / 7.0;
            final String ftm = topMerchant;
            final int fscore = score;
            final Map<String, Double> finalCatMap = catMap;
            final double fIncome = monthIncome, fExpense = monthExpense;
            final List<BarEntry> fIncEntries = incomeEntries;
            final List<BarEntry> fExpEntries = expenseEntries;
            final List<String> fMonthLabels = monthLabels;

            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                if (tvWeekTotal  != null) tvWeekTotal.setText("₹" + String.format("%.0f", ft));
                if (tvMonthTotal != null) tvMonthTotal.setText("₹" + String.format("%.0f", fm));
                if (tvAvgDaily   != null) tvAvgDaily.setText("₹" + String.format("%.0f", favg));
                if (tvTopMerchant != null) tvTopMerchant.setText(ftm);
                if (tvMerchantBreakdown != null)
                    tvMerchantBreakdown.setText(mBreakdown.isEmpty() ? "No transactions yet" : mBreakdown);
                if (tvInsights   != null) tvInsights.setText(insightSb.toString().trim());
                if (tvHealthScore != null) {
                    tvHealthScore.setText("Health Score: " + fscore + "/100");
                    int sc = fscore >= 70 ? Color.parseColor("#10B981")
                           : fscore >= 40 ? Color.parseColor("#F59E0B")
                           : Color.parseColor("#EF4444");
                    tvHealthScore.setTextColor(sc);
                }
                // P4: income vs expense summary
                if (tvTotalIncome  != null) tvTotalIncome.setText("Income: ₹" + String.format("%.0f", fIncome));
                if (tvTotalExpense != null) tvTotalExpense.setText("Spent: ₹" + String.format("%.0f", fExpense));
                if (tvNetSavings  != null) {
                    double net = fIncome - fExpense;
                    tvNetSavings.setText((net >= 0 ? "Saved: ₹" : "Deficit: ₹") + String.format("%.0f", Math.abs(net)));
                    tvNetSavings.setTextColor(net >= 0 ? 0xFF10B981 : 0xFFEF4444);
                }
                catMapInstance = finalCatMap;
                setupBarChart(barEntries, barLabels);
                setupIncomeExpenseChart(fIncEntries, fExpEntries, fMonthLabels);
                setupPieChart(pieEntries);
                setupLineChart(lineEntries);
            });
        });
    }

    private void setupIncomeExpenseChart(List<BarEntry> incEntries, List<BarEntry> expEntries,
                                         List<String> labels) {
        if (barChartIncomeExpense == null) return;

        BarDataSet incDs = new BarDataSet(incEntries, "Income");
        incDs.setColor(0xFF10B981);
        incDs.setValueTextColor(Color.WHITE);
        incDs.setValueTextSize(8f);

        BarDataSet expDs = new BarDataSet(expEntries, "Expense");
        expDs.setColor(0xFFEF4444);
        expDs.setValueTextColor(Color.WHITE);
        expDs.setValueTextSize(8f);

        BarData data = new BarData(incDs, expDs);
        float groupSpace = 0.3f, barSpace = 0.05f, barWidth = 0.3f;
        data.setBarWidth(barWidth);

        barChartIncomeExpense.setData(data);
        barChartIncomeExpense.groupBars(0f, groupSpace, barSpace);
        styleChart(barChartIncomeExpense);

        XAxis x = barChartIncomeExpense.getXAxis();
        x.setValueFormatter(new IndexAxisValueFormatter(labels));
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setTextColor(Color.WHITE);
        x.setDrawGridLines(false);
        x.setCenterAxisLabels(true);
        x.setAxisMinimum(0f);
        x.setAxisMaximum(incEntries.size());
        x.setGranularity(1f);

        barChartIncomeExpense.getAxisLeft().setTextColor(Color.WHITE);
        barChartIncomeExpense.getAxisRight().setEnabled(false);
        barChartIncomeExpense.getLegend().setEnabled(false); // legend shown via custom views
        barChartIncomeExpense.setScaleEnabled(false);
        barChartIncomeExpense.animateY(800);
        barChartIncomeExpense.invalidate();
    }

    private void setupBarChart(List<BarEntry> entries, List<String> labels) {
        BarDataSet ds = new BarDataSet(entries, "Daily Spend ₹");
        ds.setColors(COLORS); ds.setValueTextColor(Color.WHITE); ds.setValueTextSize(9f);
        barChart.setData(new BarData(ds));
        styleChart(barChart);
        XAxis x = barChart.getXAxis();
        x.setValueFormatter(new IndexAxisValueFormatter(labels));
        x.setPosition(XAxis.XAxisPosition.BOTTOM); x.setTextColor(Color.WHITE); x.setDrawGridLines(false);
        barChart.getAxisLeft().setTextColor(Color.WHITE); barChart.getAxisRight().setEnabled(false);
        barChart.animateY(900); barChart.invalidate();
    }

    private void setupPieChart(List<PieEntry> entries) {
        if (entries.isEmpty()) return;
        PieDataSet ds = new PieDataSet(entries, "");
        ds.setColors(COLORS); ds.setValueTextColor(Color.WHITE); ds.setValueTextSize(10f); ds.setSliceSpace(2f);
        pieChart.setData(new PieData(ds));
        pieChart.setBackgroundColor(Color.parseColor("#0F172A"));
        pieChart.getDescription().setEnabled(false);
        pieChart.setDrawHoleEnabled(true); pieChart.setHoleColor(Color.parseColor("#0F172A"));
        pieChart.setHoleRadius(40f); pieChart.setCenterText("Category\nBreakdown");
        pieChart.setCenterTextColor(Color.WHITE); pieChart.setCenterTextSize(12f);
        pieChart.getLegend().setTextColor(Color.WHITE); pieChart.getLegend().setTextSize(10f);
        pieChart.animateY(1000);
        pieChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override public void onValueSelected(Entry e, Highlight h) {
                if (e instanceof PieEntry) showCategoryDrillDown(((PieEntry) e).getLabel());
            }
            @Override public void onNothingSelected() {}
        });
        pieChart.invalidate();
    }

    private void setupLineChart(List<Entry> entries) {
        LineDataSet ds = new LineDataSet(entries, "30-Day Trend");
        ds.setColor(Color.parseColor("#7C3AED")); ds.setCircleColor(Color.parseColor("#A78BFA"));
        ds.setLineWidth(2f); ds.setCircleRadius(3f); ds.setDrawFilled(true);
        ds.setFillColor(Color.parseColor("#4C1D95")); ds.setFillAlpha(80);
        ds.setValueTextColor(Color.WHITE); ds.setDrawValues(false);
        lineChart.setData(new LineData(ds));
        styleChart(lineChart);
        lineChart.getXAxis().setTextColor(Color.WHITE); lineChart.getXAxis().setDrawGridLines(false);
        lineChart.getAxisLeft().setTextColor(Color.WHITE); lineChart.getAxisRight().setEnabled(false);
        lineChart.animateX(800); lineChart.invalidate();
    }

    private void styleChart(com.github.mikephil.charting.charts.Chart chart) {
        chart.setBackgroundColor(Color.parseColor("#0F172A"));
        chart.getDescription().setEnabled(false);
        chart.getLegend().setTextColor(Color.WHITE);
    }

    private void showCategoryDrillDown(String categoryLabel) {
        String matchedCat = null;
        for (String key : catMapInstance.keySet()) {
            String stripped = key.replaceAll("[^a-zA-Z\\s]", "").trim();
            if (stripped.equalsIgnoreCase(categoryLabel)) { matchedCat = key; break; }
        }
        if (matchedCat == null) matchedCat = categoryLabel;

        long mStart = getMonthStart(System.currentTimeMillis());
        List<Transaction> filtered = new ArrayList<>();
        for (Transaction t : allTransactions) {
            if (!t.isSelfTransfer && t.timestamp >= mStart && matchedCat.equals(t.category))
                filtered.add(t);
        }

        View sheet = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_category, null);
        TextView tvTitle = sheet.findViewById(R.id.tvCatTitle);
        TextView tvTotal = sheet.findViewById(R.id.tvCatTotal);
        RecyclerView rv  = sheet.findViewById(R.id.rvCatTransactions);

        double total = 0; for (Transaction t : filtered) total += t.amount;
        tvTitle.setText(matchedCat);
        tvTotal.setText(String.format("₹%.0f · %d transactions", total, filtered.size()));
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        TransactionAdapter a = new TransactionAdapter(true);
        a.setTransactions(filtered); rv.setAdapter(a);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(
                requireContext(), R.style.AlertDialogDark).setView(sheet).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setGravity(android.view.Gravity.BOTTOM);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();
    }

    private long getMonthStart(long ts) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(ts);
        c.set(Calendar.DAY_OF_MONTH,1); c.set(Calendar.HOUR_OF_DAY,0);
        c.set(Calendar.MINUTE,0); c.set(Calendar.SECOND,0); c.set(Calendar.MILLISECOND,0);
        return c.getTimeInMillis();
    }
}
