package com.spendtracker.pro;

import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.*;
import androidx.activity.result.*;
import androidx.activity.result.contract.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import java.io.File;
import java.util.List;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {
    private SharedPreferences prefs;
    private Switch swBiometric;
    private AppDatabase db;
    private ActivityResultLauncher<String[]> csvPicker;

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

        findViewById(R.id.btnExportCsv).setOnClickListener(v -> exportCsv());
        findViewById(R.id.btnCsvImport).setOnClickListener(v ->
                csvPicker.launch(new String[]{"text/csv", "text/comma-separated-values",
                        "application/csv", "text/plain", "*/*"}));
        findViewById(R.id.btnRecurring).setOnClickListener(v ->
                startActivity(new Intent(this, RecurringActivity.class)));
        findViewById(R.id.btnNetWorth).setOnClickListener(v ->
                startActivity(new Intent(this, NetWorthActivity.class)));
        findViewById(R.id.btnClearData).setOnClickListener(v -> confirmClear());
    }

    private void exportCsv() {
        AppExecutors.db().execute(() -> {
            try {
                List<Transaction> all = db.transactionDao().getAllSync();
                File f = CsvExporter.export(this, all);
                Uri uri = androidx.core.content.FileProvider.getUriForFile(
                        this, getPackageName() + ".fileprovider", f);
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/csv");
                share.putExtra(Intent.EXTRA_STREAM, uri);
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                runOnUiThread(() -> startActivity(Intent.createChooser(share, "Export CSV via...")));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void confirmClear() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Clear All Data")
                .setMessage("This will permanently delete all transactions. Are you sure?")
                .setPositiveButton("Delete All", (d, w) ->
                        AppExecutors.db().execute(() -> {
                            db.transactionDao().deleteAll();
                            runOnUiThread(() -> Toast.makeText(this,
                                    "All data cleared", Toast.LENGTH_SHORT).show());
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
