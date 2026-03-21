package com.spendtracker.pro;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.*;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.text.SimpleDateFormat;
import java.util.*;
public class MainActivity extends AppCompatActivity {

    private static final int PERM_CODE = 100;

    private TextView tvGreeting, tvDate, tvTodayAmt, tvMonthAmt, tvBudgetLeft,
            tvTopCat, tvTransCount, tvHealthScore, tvImportStatus, tvPrediction,
            tvCreditCardSpent, tvTotalBankBalance;

    private RecyclerView rvRecent;
    private TransactionAdapter adapter;

    private ProgressBar progressBar;
    private Button btnScan;
    private TextView tvBillsBadge;

    private AppDatabase db;

    @Override
    protected void onCreate(Bundle s) {

        super.onCreate(s);
        setContentView(R.layout.activity_main);

        db = AppDatabase.getInstance(this);

        CategoryEngine.init(this);
        MerchantLogoProvider.load(this);

        initViews();
        setupNav();
        setGreeting();
        observeData();

        if (!hasSmsPermission()) {
            requestSmsPermission();
        }
    }

    private void initViews() {

        tvGreeting     = findViewById(R.id.tvGreeting);
        tvDate         = findViewById(R.id.tvDate);
        tvTodayAmt     = findViewById(R.id.tvTodayAmt);
        tvMonthAmt     = findViewById(R.id.tvMonthAmt);
        tvBudgetLeft   = findViewById(R.id.tvBudgetLeft);
        tvTopCat       = findViewById(R.id.tvTopCat);
        tvTransCount   = findViewById(R.id.tvTransCount);
        tvHealthScore  = findViewById(R.id.tvHealthScore);
        tvImportStatus = findViewById(R.id.tvImportStatus);
        tvPrediction   = findViewById(R.id.tvPrediction);
        tvCreditCardSpent   = findViewById(R.id.tvCreditCardSpent);
        tvTotalBankBalance  = findViewById(R.id.tvTotalBankBalance);

        progressBar    = findViewById(R.id.progressBar);
        tvBillsBadge   = findViewById(R.id.tvBillsBadge);
        btnScan     = findViewById(R.id.btnScan);
        rvRecent    = findViewById(R.id.rvRecent);

        // Fix: pass false so tap-to-edit works on dashboard transactions too
        adapter = new TransactionAdapter(false);
        rvRecent.setLayoutManager(new LinearLayoutManager(this));
        rvRecent.setAdapter(adapter);
        rvRecent.setNestedScrollingEnabled(false);

        btnScan.setOnClickListener(v -> {
            if (!hasSmsPermission()) {
                requestSmsPermission();
                return;
            }
            startImport();
        });

        findViewById(R.id.fabAdd).setOnClickListener(v ->
                startActivity(new Intent(this, AddExpenseActivity.class)));

        // ── Quick action cards ────────────────────────────────────
        findViewById(R.id.cardInsights).setOnClickListener(v ->
                startActivity(new Intent(this, AnalyticsActivity.class)));

        findViewById(R.id.cardBudget).setOnClickListener(v ->
                startActivity(new Intent(this, BudgetActivity.class)));

        findViewById(R.id.cardNetWorth).setOnClickListener(v ->
                startActivity(new Intent(this, NetWorthActivity.class)));

        // ── Stat cards — whole card is tappable for better UX ───────
        findViewById(R.id.cardToday).setOnClickListener(v ->
                startActivity(new Intent(this, TransactionsActivity.class)));

        findViewById(R.id.cardMonth).setOnClickListener(v ->
                startActivity(new Intent(this, TransactionsActivity.class)));

        findViewById(R.id.cardBudgetLeft).setOnClickListener(v ->
                startActivity(new Intent(this, BudgetActivity.class)));

        findViewById(R.id.cardTopCat).setOnClickListener(v ->
                startActivity(new Intent(this, AnalyticsActivity.class)));

        tvHealthScore.setOnClickListener(v ->
                startActivity(new Intent(this, AnalyticsActivity.class)));

        tvPrediction.setOnClickListener(v ->
                startActivity(new Intent(this, AnalyticsActivity.class)));

        findViewById(R.id.cardBills).setOnClickListener(v ->
                startActivity(new Intent(this, BillActivity.class)));

        findViewById(R.id.cardCreditCards).setOnClickListener(v ->
                startActivity(new Intent(this, CreditCardActivity.class)));

        findViewById(R.id.cardBankAccounts).setOnClickListener(v ->
                startActivity(new Intent(this, BankAccountActivity.class)));
    }

    private void setupNav() {

        BottomNavigationView nav = findViewById(R.id.bottomNav);

        nav.setSelectedItemId(R.id.nav_home);

        nav.setOnItemSelectedListener(item -> {

            int id = item.getItemId();

            if (id == R.id.nav_txn) {
                startActivity(new Intent(this, TransactionsActivity.class));
                return true;
            }

            if (id == R.id.nav_analytics) {
                startActivity(new Intent(this, AnalyticsActivity.class));
                return true;
            }

            if (id == R.id.nav_budget) {
                startActivity(new Intent(this, BudgetActivity.class));
                return true;
            }

            if (id == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }

            return true;
        });
    }

    private void setGreeting() {

        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);

        String g = h < 12 ? "Good Morning ☀️"
                : h < 17 ? "Good Afternoon 🌤️"
                : "Good Evening 🌙";

        tvGreeting.setText(g);

        tvDate.setText(
                new SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.ENGLISH).format(new Date())
        );
    }

    private void observeData() {

        db.transactionDao().getRecent(50).observe(this, list -> {

            if (list == null) return;

            List<Transaction> recent =
                    list.size() > 6 ? list.subList(0, 6) : list;

            adapter.setTransactions(recent);

            // Priority 1: replaced getAllSync() full scan with month-scoped query
            AppExecutors.db().execute(() -> {
                long now = System.currentTimeMillis();
                long monthStart = getMonthStart(now);
                List<Transaction> monthData = db.transactionDao().getSpendingInRange(monthStart, now);
                if (monthData != null) updateStats(monthData);
            });
        });

        db.transactionDao().getTotalCount().observe(this,
                count -> tvTransCount.setText((count != null ? count : 0) + " total"));

        // Show pending bill count badge
        db.billDao().getPendingCount().observe(this, count -> {
            if (tvBillsBadge != null) {
                if (count != null && count > 0) {
                    tvBillsBadge.setText(count + " pending");
                    tvBillsBadge.setVisibility(android.view.View.VISIBLE);
                } else {
                    tvBillsBadge.setVisibility(android.view.View.GONE);
                }
            }
        });

        // Credit card total spent badge
        db.creditCardDao().getAll().observe(this, cards -> {
            if (tvCreditCardSpent == null) return;
            if (cards != null && !cards.isEmpty()) {
                double total = 0;
                for (com.spendtracker.pro.CreditCard c : cards) total += c.currentSpent;
                tvCreditCardSpent.setText(String.format(Locale.getDefault(), "₹%.0f spent", total));
                tvCreditCardSpent.setVisibility(android.view.View.VISIBLE);
            } else {
                tvCreditCardSpent.setVisibility(android.view.View.GONE);
            }
        });

        // Bank total balance badge
        db.bankAccountDao().getTotalBalance().observe(this, total -> {
            if (tvTotalBankBalance == null) return;
            if (total != null && total > 0) {
                tvTotalBankBalance.setText(String.format(Locale.getDefault(), "₹%.0f", total));
                tvTotalBankBalance.setVisibility(android.view.View.VISIBLE);
            } else {
                tvTotalBankBalance.setVisibility(android.view.View.GONE);
            }
        });
    }

    private void startImport() {

        progressBar.setVisibility(View.VISIBLE);
        tvImportStatus.setVisibility(View.VISIBLE);

        btnScan.setEnabled(false);

        tvImportStatus.setText("🔍 Scanning SMS messages...");

        SmsImporter.importAll(this, new SmsImporter.Callback() {

            public void onProgress(int d, int t) {

                runOnUiThread(() ->
                        tvImportStatus.setText("Scanning... " + d + " found"));
            }

            public void onComplete(int count) {

                runOnUiThread(() -> {

                    progressBar.setVisibility(View.GONE);
                    btnScan.setEnabled(true);

                    if (count > 0) {
                        tvImportStatus.setText("✅ Imported " + count + " transactions");
                    } else {
                        tvImportStatus.setText("ℹ️ No new transactions found");
                    }

                    loadDashboard();
                });
            }

            public void onError(String msg) {

                runOnUiThread(() -> {

                    progressBar.setVisibility(View.GONE);
                    btnScan.setEnabled(true);

                    tvImportStatus.setText("❌ " + msg);
                });
            }
        });
    }

    private boolean hasSmsPermission() {

        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestSmsPermission() {

        List<String> permissions = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED) {

            permissions.add(Manifest.permission.READ_SMS);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                != PackageManager.PERMISSION_GRANTED) {

            permissions.add(Manifest.permission.RECEIVE_SMS);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {

            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (!permissions.isEmpty()) {

            ActivityCompat.requestPermissions(
                    this,
                    permissions.toArray(new String[0]),
                    PERM_CODE
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERM_CODE) {

            boolean granted = false;

            for (int i = 0; i < permissions.length; i++) {

                if (Manifest.permission.READ_SMS.equals(permissions[i])
                        && grantResults.length > i
                        && grantResults[i] == PackageManager.PERMISSION_GRANTED) {

                    granted = true;
                    break;
                }
            }

            if (granted) {

                Toast.makeText(this,
                        "SMS permission granted",
                        Toast.LENGTH_SHORT).show();

                startImport();

            } else {

                Toast.makeText(this,
                        "SMS permission required to scan transactions",
                        Toast.LENGTH_LONG).show();

                btnScan.setEnabled(true);
            }
        }
    }

    @Override
    protected void onResume() {

        super.onResume();

        setGreeting();
        loadDashboard();
    }

    private void loadDashboard() {

        AppExecutors.db().execute(() -> {
            long now = System.currentTimeMillis();
            long monthStart = getMonthStart(now);
            List<Transaction> all = db.transactionDao().getSpendingInRange(monthStart, now);

            if (all != null) updateStats(all);
        });

        // Clear stale scan status message when navigating back to home
        if (tvImportStatus != null && progressBar != null
                && progressBar.getVisibility() != View.VISIBLE) {
            tvImportStatus.setVisibility(View.GONE);
        }
    }

    private void updateStats(List<Transaction> list) {
        long now        = System.currentTimeMillis();
        long todayStart = getDayStart(now);
        long monthStart = getMonthStart(now);

        double todayTotal = 0, monthTotal = 0;
        Map<String, Double> catMap = new HashMap<>();

        for (Transaction t : list) {
            if (t.isSelfTransfer) continue;
            if (t.isCredit) continue;
            if (t.timestamp >= todayStart)  todayTotal  += t.amount;
            if (t.timestamp >= monthStart) {
                monthTotal += t.amount;
                catMap.merge(t.category != null ? t.category : "Others", t.amount, Double::sum);
            }
        }

        // Top category
        String topCat = catMap.isEmpty() ? "—" :
                Collections.max(catMap.entrySet(), Map.Entry.comparingByValue()).getKey();

        // Budget left for this month
        List<Budget> budgets = db.budgetDao().getByMonthYearSync(
                getCalendarField(now, java.util.Calendar.MONTH) + 1,
                getCalendarField(now, java.util.Calendar.YEAR));
        double totalLimit = 0, totalUsed = 0;
        for (Budget b : budgets) { totalLimit += b.limitAmount; totalUsed += b.usedAmount; }
        // Copy to final locals so they can be captured by the lambda below
        final double fTotalLimit = totalLimit;
        final double budgetLeft  = totalLimit - totalUsed;

        // Health score
        final int score = InsightEngine.calcHealthScore(list, budgets);

        // Spend prediction
        SpendPredictor.Prediction pred = SpendPredictor.predict(list);
        final String predText = pred != null ? pred.getSummary() : "";

        final double ft = todayTotal, fm = monthTotal;
        final String topCatFinal = topCat;

        runOnUiThread(() -> {
            if (tvTodayAmt    != null) tvTodayAmt.setText(String.format(Locale.getDefault(), "₹%.0f", ft));
            if (tvMonthAmt    != null) tvMonthAmt.setText(String.format(Locale.getDefault(), "₹%.0f", fm));
            if (tvTopCat      != null) tvTopCat.setText(topCatFinal);
            if (tvPrediction  != null) tvPrediction.setText(predText);

            if (tvBudgetLeft != null) {
                if (fTotalLimit <= 0) {
                    tvBudgetLeft.setText("No budget set");
                    tvBudgetLeft.setTextColor(ContextCompat.getColor(this, R.color.text_hint));
                } else {
                    tvBudgetLeft.setText(String.format(Locale.getDefault(), "₹%.0f left", budgetLeft));
                    tvBudgetLeft.setTextColor(ContextCompat.getColor(this,
                            budgetLeft >= 0 ? R.color.green : R.color.red));
                }
            }

            if (tvHealthScore != null) {
                tvHealthScore.setText(InsightEngine.getHealthScoreLabel(score) + " · " + score + "/100");
                int color = ContextCompat.getColor(this,
                        score >= 70 ? R.color.green : score >= 40 ? R.color.amber : R.color.red);
                tvHealthScore.setTextColor(color);
            }
        });
    }

    // ── Date helpers ─────────────────────────────────────────────
    private long getDayStart(long ts) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(ts);
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private long getMonthStart(long ts) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(ts);
        c.set(java.util.Calendar.DAY_OF_MONTH, 1);
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private int getCalendarField(long ts, int field) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(ts);
        return c.get(field);
    }
}
