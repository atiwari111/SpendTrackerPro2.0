package com.spendtracker.pro;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import java.text.SimpleDateFormat;
import java.util.*;

public class HomeFragment extends Fragment {

    private TextView tvGreeting, tvDate, tvTodayAmt, tvMonthAmt, tvBudgetLeft,
            tvTopCat, tvTransCount, tvHealthScore, tvImportStatus, tvPrediction,
            tvCreditCardSpent, tvTotalBankBalance, tvBillsBadge, tvPeriodLabel;
    private RecyclerView rvRecent;
    private TransactionAdapter adapter;
    private ProgressBar progressBar;
    private Button btnScan;
    private AppDatabase db;
    private SharedViewModel sharedVm;

    // Time range for the summary stat cards — default to today
    private long timeRangeMs = 0; // 0 = today, set on chip selection
    private String timeRangeLabel = "Today";

    // Permission launcher — must be registered before onStart
    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        Boolean smsGranted = result.get(Manifest.permission.READ_SMS);
                        if (Boolean.TRUE.equals(smsGranted)) startImport();
                        else Toast.makeText(requireContext(),
                                "SMS permission required to scan transactions",
                                Toast.LENGTH_LONG).show();
                    });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        db = AppDatabase.getInstance(requireContext());
        sharedVm = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);

        bindViews(view);
        setupClickListeners();
        setGreeting();
        observeData();
    }

    private void bindViews(View v) {
        tvGreeting     = v.findViewById(R.id.tvGreeting);
        tvDate         = v.findViewById(R.id.tvDate);
        tvTodayAmt     = v.findViewById(R.id.tvTodayAmt);
        tvMonthAmt     = v.findViewById(R.id.tvMonthAmt);
        tvBudgetLeft   = v.findViewById(R.id.tvBudgetLeft);
        tvTopCat       = v.findViewById(R.id.tvTopCat);
        tvTransCount   = v.findViewById(R.id.tvTransCount);
        tvHealthScore  = v.findViewById(R.id.tvHealthScore);
        tvImportStatus = v.findViewById(R.id.tvImportStatus);
        tvPrediction   = v.findViewById(R.id.tvPrediction);
        tvCreditCardSpent   = v.findViewById(R.id.tvCreditCardSpent);
        tvTotalBankBalance  = v.findViewById(R.id.tvTotalBankBalance);
        tvBillsBadge        = v.findViewById(R.id.tvBillsBadge);
        tvPeriodLabel       = v.findViewById(R.id.tvPeriodLabel);
        progressBar    = v.findViewById(R.id.progressBar);
        btnScan        = v.findViewById(R.id.btnScan);
        rvRecent       = v.findViewById(R.id.rvRecent);

        // Time range chip toggle
        ChipGroup chipTimeRange = v.findViewById(R.id.chipTimeRange);
        if (chipTimeRange != null) {
            chipTimeRange.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds.isEmpty()) return;
                int id = checkedIds.get(0);
                if      (id == R.id.chipToday) { timeRangeMs = 0;                       timeRangeLabel = "Today"; }
                else if (id == R.id.chipWeek)  { timeRangeMs = 7L * 86400_000L;         timeRangeLabel = "This Week"; }
                else if (id == R.id.chipMonth) { timeRangeMs = 30L * 86400_000L;        timeRangeLabel = "30 Days"; }
                else if (id == R.id.chipYear)  { timeRangeMs = 365L * 86400_000L;       timeRangeLabel = "This Year"; }
                if (tvPeriodLabel != null) tvPeriodLabel.setText(timeRangeLabel);
                // Refresh stats with new window — data already in SharedViewModel
                AppExecutors.db().execute(() -> {
                    if (!isAdded()) return;
                    List<Transaction> all = db.transactionDao().getAllSync();
                    if (all != null) updateStats(all);
                });
            });
        }

        adapter = new TransactionAdapter(false);
        rvRecent.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvRecent.setAdapter(adapter);
        rvRecent.setNestedScrollingEnabled(false);
    }

    private void setupClickListeners() {
        btnScan.setOnClickListener(v -> {
            if (!hasSmsPermission()) requestSmsPermission();
            else startImport();
        });

        requireView().findViewById(R.id.fabAdd).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AddExpenseActivity.class)));
        requireView().findViewById(R.id.cardInsights).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AnalyticsActivity.class)));
        requireView().findViewById(R.id.cardBudget).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), BudgetActivity.class)));
        requireView().findViewById(R.id.cardNetWorth).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), NetWorthActivity.class)));
        requireView().findViewById(R.id.cardToday).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), TransactionsActivity.class)));
        requireView().findViewById(R.id.cardMonth).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), TransactionsActivity.class)));
        requireView().findViewById(R.id.cardBudgetLeft).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), BudgetActivity.class)));
        requireView().findViewById(R.id.cardTopCat).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AnalyticsActivity.class)));
        requireView().findViewById(R.id.cardBills).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), BillActivity.class)));
        requireView().findViewById(R.id.cardCreditCards).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), CreditCardActivity.class)));
        requireView().findViewById(R.id.cardBankAccounts).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), BankAccountActivity.class)));
        tvHealthScore.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AnalyticsActivity.class)));
        tvPrediction.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AnalyticsActivity.class)));
    }

    private void setGreeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String g = h < 12 ? "Good Morning ☀️" : h < 17 ? "Good Afternoon 🌤️" : "Good Evening 🌙";
        tvGreeting.setText(g);
        tvDate.setText(new SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault())
                .format(new Date()));
    }

    private void observeData() {
        // Shared LiveData — no extra DB hit
        sharedVm.getAllTransactions().observe(getViewLifecycleOwner(), list -> {
            if (list == null) return;
            List<Transaction> recent = list.size() > 6 ? list.subList(0, 6) : list;
            adapter.setTransactions(recent);
            AppExecutors.db().execute(() -> {
                if (!isAdded()) return;
                List<Transaction> all = db.transactionDao().getAllSync();
                if (all != null) updateStats(all);
            });
        });

        db.transactionDao().getTotalCount().observe(getViewLifecycleOwner(),
                count -> tvTransCount.setText((count != null ? count : 0) + " total"));

        db.billDao().getPendingCount().observe(getViewLifecycleOwner(), count -> {
            if (tvBillsBadge == null) return;
            if (count != null && count > 0) {
                tvBillsBadge.setText(count + " pending");
                tvBillsBadge.setVisibility(View.VISIBLE);
            } else {
                tvBillsBadge.setVisibility(View.GONE);
            }
        });

        db.creditCardDao().getAll().observe(getViewLifecycleOwner(), cards -> {
            if (tvCreditCardSpent == null) return;
            if (cards != null && !cards.isEmpty()) {
                double total = 0;
                for (CreditCard c : cards) total += c.currentSpent;
                tvCreditCardSpent.setText(String.format("₹%.0f spent", total));
                tvCreditCardSpent.setVisibility(View.VISIBLE);
            } else {
                tvCreditCardSpent.setVisibility(View.GONE);
            }
        });

        db.bankAccountDao().getTotalBalance().observe(getViewLifecycleOwner(), total -> {
            if (tvTotalBankBalance == null) return;
            if (total != null && total > 0) {
                tvTotalBankBalance.setText(String.format("₹%.0f", total));
                tvTotalBankBalance.setVisibility(View.VISIBLE);
            } else {
                tvTotalBankBalance.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        setGreeting();
        if (tvImportStatus != null && progressBar != null
                && progressBar.getVisibility() != View.VISIBLE) {
            tvImportStatus.setVisibility(View.GONE);
        }
    }

    public void startImport() {
        if (!isAdded()) return;
        progressBar.setVisibility(View.VISIBLE);
        tvImportStatus.setVisibility(View.VISIBLE);
        btnScan.setEnabled(false);
        tvImportStatus.setText("🔍 Scanning SMS messages...");

        SmsImporter.importAll(requireContext(), new SmsImporter.Callback() {
            public void onProgress(int d, int t) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() ->
                        tvImportStatus.setText("Scanning... " + d + " found"));
            }
            public void onComplete(int count) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnScan.setEnabled(true);
                    tvImportStatus.setText(count > 0
                            ? "✅ Imported " + count + " transactions"
                            : "ℹ️ No new transactions found");
                });
            }
            public void onError(String msg) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnScan.setEnabled(true);
                    tvImportStatus.setText("❌ " + msg);
                });
            }
        });
    }

    private void updateStats(List<Transaction> list) {
        long now        = System.currentTimeMillis();
        long todayStart = getDayStart(now);
        long monthStart = getMonthStart(now);

        // Resolve the "primary" window from the chip selection
        long primaryStart;
        if (timeRangeMs == 0) {
            primaryStart = todayStart;              // Today chip
        } else {
            primaryStart = now - timeRangeMs;       // Week / 30-day / Year chips
        }

        double primaryTotal = 0, monthTotal = 0;
        Map<String, Double> catMap = new HashMap<>();

        for (Transaction t : list) {
            if (t.isSelfTransfer) continue;
            if (t.timestamp >= primaryStart) primaryTotal += t.amount;
            if (t.timestamp >= monthStart) {
                monthTotal += t.amount;
                catMap.merge(t.category != null ? t.category : "Others", t.amount, Double::sum);
            }
        }
        String topCat = catMap.isEmpty() ? "—"
                : Collections.max(catMap.entrySet(), Map.Entry.comparingByValue()).getKey();

        List<Budget> budgets = db.budgetDao().getByMonthYearSync(
                getCalField(now, Calendar.MONTH) + 1, getCalField(now, Calendar.YEAR));
        double totalLimit = 0, totalUsed = 0;
        for (Budget b : budgets) { totalLimit += b.limitAmount; totalUsed += b.usedAmount; }
        final double fLimit = totalLimit, fLeft = totalLimit - totalUsed;
        final int score = InsightEngine.calcHealthScore(list, budgets);
        SpendPredictor.Prediction pred = SpendPredictor.predict(list);
        final String predText = pred != null ? pred.getSummary() : "";
        final double fPrimary = primaryTotal, fm = monthTotal;
        final String topCatFinal = topCat;
        final String periodLbl = timeRangeLabel;

        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            if (!isAdded()) return;
            // Left stat card — reflects current chip selection
            if (tvTodayAmt   != null) tvTodayAmt.setText(String.format("₹%.0f", fPrimary));
            if (tvPeriodLabel != null) tvPeriodLabel.setText(periodLbl);
            // Right stat card — always month total for budget context
            if (tvMonthAmt   != null) tvMonthAmt.setText(String.format("₹%.0f", fm));
            if (tvTopCat     != null) tvTopCat.setText(topCatFinal);
            if (tvPrediction != null) tvPrediction.setText(predText);
            if (tvBudgetLeft != null) {
                if (fLimit <= 0) {
                    tvBudgetLeft.setText("No budget set");
                    tvBudgetLeft.setTextColor(0xFFB0BEC5);
                } else {
                    tvBudgetLeft.setText(String.format("₹%.0f left", fLeft));
                    tvBudgetLeft.setTextColor(fLeft >= 0 ? 0xFF10B981 : 0xFFEF4444);
                }
            }
            if (tvHealthScore != null) {
                tvHealthScore.setText(InsightEngine.getHealthScoreLabel(score) + " · " + score + "/100");
                int color = score >= 70 ? 0xFF10B981 : score >= 40 ? 0xFFF59E0B : 0xFFEF4444;
                tvHealthScore.setTextColor(color);
            }
        });
    }

    private boolean hasSmsPermission() {
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestSmsPermission() {
        List<String> perms = new ArrayList<>();
        if (!hasSmsPermission()) perms.add(Manifest.permission.READ_SMS);
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECEIVE_SMS)
                != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.RECEIVE_SMS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(requireContext(),
                        Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED)
            perms.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!perms.isEmpty()) permissionLauncher.launch(perms.toArray(new String[0]));
    }

    private long getDayStart(long ts) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(ts);
        c.set(Calendar.HOUR_OF_DAY,0); c.set(Calendar.MINUTE,0);
        c.set(Calendar.SECOND,0); c.set(Calendar.MILLISECOND,0);
        return c.getTimeInMillis();
    }
    private long getMonthStart(long ts) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(ts);
        c.set(Calendar.DAY_OF_MONTH,1); c.set(Calendar.HOUR_OF_DAY,0);
        c.set(Calendar.MINUTE,0); c.set(Calendar.SECOND,0); c.set(Calendar.MILLISECOND,0);
        return c.getTimeInMillis();
    }
    private int getCalField(long ts, int field) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(ts); return c.get(field);
    }
}
