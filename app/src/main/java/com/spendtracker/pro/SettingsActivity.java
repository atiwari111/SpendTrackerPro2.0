package com.spendtracker.pro;

import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;
import androidx.activity.result.*;
import androidx.activity.result.contract.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import java.io.File;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {
    private SharedPreferences prefs;
    private Switch swBiometric;
    private AppDatabase db;
    private ActivityResultLauncher<String[]> csvPicker;
    private boolean csvAccessPromptShown;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        // registerForActivityResult MUST be called before setContentView (Fragment/Activity lifecycle requirement)
        csvPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> { if (uri != null) importCsv(uri); }
        );
        setContentView(R.layout.activity_settings);
        db = AppDatabase.getInstance(this);
        prefs = getSharedPreferences("stp_prefs", MODE_PRIVATE);

        Toolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Settings");
        }

        swBiometric = findViewById(R.id.swBiometric);
        swBiometric.setChecked(prefs.getBoolean("bio_enabled", false));
        swBiometric.setOnCheckedChangeListener((v, checked) ->
                prefs.edit().putBoolean("bio_enabled", checked).apply());

        // Daily summary notification toggle
        Switch swDailySummary = (Switch) findViewById(R.id.swDailySummary);
        if (swDailySummary != null) {
            swDailySummary.setChecked(prefs.getBoolean("daily_summary_enabled", true));
            swDailySummary.setOnCheckedChangeListener((v, checked) -> {
                prefs.edit().putBoolean("daily_summary_enabled", checked).apply();
                if (checked) DailySummaryWorker.schedule(this);
                else         DailySummaryWorker.cancel(this);
            });
        }

        // Theme mode selector (Light / Dark)
        RadioGroup rgTheme = findViewById(R.id.rgTheme);
        RadioButton rbLight = findViewById(R.id.rbThemeLight);
        RadioButton rbDark  = findViewById(R.id.rbThemeDark);
        if (rgTheme != null && rbLight != null && rbDark != null) {
            String mode = prefs.getString("theme_mode", "dark");
            if ("light".equals(mode)) rbLight.setChecked(true); else rbDark.setChecked(true);
            rgTheme.setOnCheckedChangeListener((group, checkedId) -> {
                boolean dark = checkedId == R.id.rbThemeDark;
                prefs.edit()
                        .putString("theme_mode", dark ? "dark" : "light")
                        .putBoolean("dark_theme_enabled", dark) // backward compatibility
                        .apply();
                AppCompatDelegate.setDefaultNightMode(
                        dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            });
        }

        findViewById(R.id.btnExportCsv).setOnClickListener(v -> exportCsv());
        findViewById(R.id.btnCsvImport).setOnClickListener(v -> requestCsvPicker());
        findViewById(R.id.btnRecurring).setOnClickListener(v ->
                startActivity(new Intent(this, RecurringActivity.class)));
        findViewById(R.id.btnNetWorth).setOnClickListener(v ->
                startActivity(new Intent(this, NetWorthActivity.class)));
        findViewById(R.id.btnCreditCards).setOnClickListener(v ->
                startActivity(new Intent(this, CreditCardActivity.class)));
        View btnBankAccounts = findViewById(R.id.btnBankAccounts);
        if (btnBankAccounts != null) btnBankAccounts.setVisibility(View.GONE);
        findViewById(R.id.btnClearData).setOnClickListener(v -> confirmClear());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCreditCardSpent();
    }

    private void requestCsvPicker() {
        if (prefs.getBoolean("csv_access_prompt_seen", false) || csvAccessPromptShown) {
            launchCsvPicker();
            return;
        }
        csvAccessPromptShown = true;
        new androidx.appcompat.app.AlertDialog.Builder(this, R.style.AlertDialogDark)
                .setTitle("Allow file access")
                .setMessage("To import CSV, choose a file from your device storage.")
                .setPositiveButton("Continue", (d, w) -> {
                    prefs.edit().putBoolean("csv_access_prompt_seen", true).apply();
                    launchCsvPicker();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void launchCsvPicker() {
        csvPicker.launch(new String[]{"text/csv", "text/comma-separated-values",
                "application/csv", "text/plain", "*/*"});
    }

    /** Recompute credit card spend from transactions so totals stay updated from Settings flow too. */
    private void refreshCreditCardSpent() {
        AppExecutors.db().execute(() -> {
            List<CreditCard> cards = db.creditCardDao().getAllSync();
            long now = System.currentTimeMillis();
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.set(java.util.Calendar.DAY_OF_MONTH, 1);
            c.set(java.util.Calendar.HOUR_OF_DAY, 0);
            c.set(java.util.Calendar.MINUTE, 0);
            c.set(java.util.Calendar.SECOND, 0);
            c.set(java.util.Calendar.MILLISECOND, 0);
            long monthStart = c.getTimeInMillis();
            for (CreditCard card : cards) {
                long cycleStart = card.billingCycleStart > 0 ? card.billingCycleStart : monthStart;
                double spent = db.creditCardDao().getCreditSpendInRange(cycleStart, now);
                db.creditCardDao().updateSpent(card.id, spent, now);
            }
        });
    }

    private void exportCsv() {
        String[] options = {"Transactions", "Credit Cards", "Bank Accounts"};
        new androidx.appcompat.app.AlertDialog.Builder(this, R.style.AlertDialogDark)
                .setTitle("Export as CSV")
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0: exportTransactions(); break;
                        case 1: exportCreditCardsCsv(); break;
                        case 2: exportBankAccountsCsv(); break;
                    }
                }).show();
    }

    private void exportTransactions() {
        AppExecutors.db().execute(() -> {
            try {
                List<Transaction> all = db.transactionDao().getAllSync();
                File f = CsvExporter.export(this, all);
                shareFile(f, "Export Transactions via...");
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void exportCreditCardsCsv() {
        AppExecutors.db().execute(() -> {
            try {
                List<CreditCard> cards = db.creditCardDao().getAllSync();
                File f = CsvExporter.exportCreditCards(this, cards);
                shareFile(f, "Export Credit Cards via...");
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void exportBankAccountsCsv() {
        AppExecutors.db().execute(() -> {
            try {
                List<BankAccount> accounts = db.bankAccountDao().getAllSync();
                File f = CsvExporter.exportBankAccounts(this, accounts);
                shareFile(f, "Export Bank Accounts via...");
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void shareFile(File f, String title) {
        Uri uri = androidx.core.content.FileProvider.getUriForFile(
                this, getPackageName() + ".fileprovider", f);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/csv");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        runOnUiThread(() -> startActivity(Intent.createChooser(share, title)));
    }

    private void confirmClear() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Clear Transactions")
                .setMessage("This will permanently delete all transactions on this device. Are you sure?")
                .setPositiveButton("Delete Transactions", (d, w) ->
                        AppExecutors.db().execute(() -> {
                            db.transactionDao().deleteAll();
                            runOnUiThread(() -> Toast.makeText(this,
                                    "All transactions cleared", Toast.LENGTH_SHORT).show());
                        }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void importCsv(Uri uri) {
        Toast.makeText(this, "Importing CSV...", Toast.LENGTH_SHORT).show();
        CsvImporter.importFromUri(this, uri, new CsvImporter.Callback() {
            public void onProgress(int done, int total) {
                runOnUiThread(() -> Toast.makeText(SettingsActivity.this,
                        "Importing... " + done + "/" + total, Toast.LENGTH_SHORT).show());
            }
            public void onComplete(int imported, int skipped) {
                runOnUiThread(() -> {
                    String msg = "✅ Imported " + imported + " records.";
                    if (skipped > 0) msg += " Skipped " + skipped + " (duplicates/invalid).";
                    new androidx.appcompat.app.AlertDialog.Builder(SettingsActivity.this)
                            .setTitle("CSV Import Complete")
                            .setMessage(msg)
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
            public void onError(String msg) {
                runOnUiThread(() -> Toast.makeText(SettingsActivity.this,
                        "❌ " + msg, Toast.LENGTH_LONG).show());
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { getOnBackPressedDispatcher().onBackPressed(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
