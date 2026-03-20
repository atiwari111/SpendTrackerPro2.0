package com.spendtracker.pro;

import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import android.widget.RadioGroup;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import java.io.File;
import java.util.List;

public class SettingsFragment extends Fragment {

    private SharedPreferences prefs;
    private Switch swBiometric;
    private AppDatabase db;

    private ActivityResultLauncher<String[]> csvPicker;
    // P4: backup restore launcher
    private ActivityResultLauncher<String[]> restorePicker;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        csvPicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> { if (uri != null) importCsv(uri); });
        // P4: restore picker — accepts our .stpbak extension
        restorePicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> { if (uri != null) doRestore(uri); });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        db    = AppDatabase.getInstance(requireContext());
        prefs = requireContext().getSharedPreferences("stp_prefs", Context.MODE_PRIVATE);

        androidx.appcompat.widget.Toolbar tb = view.findViewById(R.id.toolbar);
        if (tb != null) {
            tb.setTitle("Settings");
            tb.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
            tb.setNavigationOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        }

        swBiometric = view.findViewById(R.id.swBiometric);
        swBiometric.setChecked(prefs.getBoolean("bio_enabled", false));
        swBiometric.setOnCheckedChangeListener((v, checked) ->
                prefs.edit().putBoolean("bio_enabled", checked).apply());

        // ── Theme selector ────────────────────────────────────────
        // Previously the RadioGroup had no listener, so selecting Light/Dark
        // had no effect — the pref was never written and AppCompatDelegate was
        // never called.  Fix: restore the saved selection on load, then apply
        // + persist whenever the user changes it.
        RadioGroup rgTheme = view.findViewById(R.id.rgTheme);
        if (rgTheme != null) {
            // Restore saved selection (default: dark, matching app default)
            String savedMode = prefs.getString("theme_mode", "dark");
            rgTheme.check("light".equals(savedMode) ? R.id.rbThemeLight : R.id.rbThemeDark);

            rgTheme.setOnCheckedChangeListener((group, checkedId) -> {
                boolean isLight = (checkedId == R.id.rbThemeLight);
                String mode = isLight ? "light" : "dark";

                // Persist immediately so SplashActivity reads the right value on next launch
                prefs.edit().putString("theme_mode", mode).apply();

                // Apply at runtime — no restart required
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                        isLight
                                ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                                : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
            });
        }

        Switch swDailySummary = view.findViewById(R.id.swDailySummary);
        if (swDailySummary != null) {
            swDailySummary.setChecked(prefs.getBoolean("daily_summary_enabled", true));
            swDailySummary.setOnCheckedChangeListener((v, checked) -> {
                prefs.edit().putBoolean("daily_summary_enabled", checked).apply();
                if (checked) DailySummaryWorker.schedule(requireContext());
                else         DailySummaryWorker.cancel(requireContext());
            });
        }

        view.findViewById(R.id.btnExportCsv).setOnClickListener(v -> exportCsv());
        view.findViewById(R.id.btnCsvImport).setOnClickListener(v ->
                csvPicker.launch(new String[]{"text/csv", "text/comma-separated-values",
                        "application/csv", "text/plain", "*/*"}));
        view.findViewById(R.id.btnRecurring).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), RecurringActivity.class)));
        view.findViewById(R.id.btnNetWorth).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), NetWorthActivity.class)));
        view.findViewById(R.id.btnClearData).setOnClickListener(v -> confirmClear());

        // P4: Backup & Restore
        view.findViewById(R.id.btnBackup).setOnClickListener(v -> doBackup());
        view.findViewById(R.id.btnRestore).setOnClickListener(v ->
                restorePicker.launch(new String[]{"*/*"}));
    }

    // ── P4: Backup ────────────────────────────────────────────────
    private void doBackup() {
        Toast.makeText(requireContext(), "Creating backup...", Toast.LENGTH_SHORT).show();
        BackupManager.backup(requireContext(), new BackupManager.Callback() {
            @Override public void onSuccess(String message) {
                if (!isAdded()) return;
                // message format: "backup_uri:<uri>|<filename>"
                String[] parts = message.replace("backup_uri:", "").split("\\|");
                android.net.Uri uri = android.net.Uri.parse(parts[0]);
                String filename = parts.length > 1 ? parts[1] : "backup.stpbak";

                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("application/octet-stream");
                share.putExtra(Intent.EXTRA_STREAM, uri);
                share.putExtra(Intent.EXTRA_SUBJECT, "SpendTracker Pro Backup");
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                requireActivity().runOnUiThread(() ->
                        startActivity(Intent.createChooser(share, "Save backup via...")));
            }
            @Override public void onError(String message) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), "❌ " + message, Toast.LENGTH_LONG).show());
            }
        });
    }

    // ── P4: Restore ───────────────────────────────────────────────
    private void doRestore(android.net.Uri uri) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.AlertDialogDark)
                .setTitle("Restore Backup?")
                .setMessage("This will replace all current data with the backup. The app will close to apply changes.")
                .setPositiveButton("Restore", (d, w) -> {
                    Toast.makeText(requireContext(), "Restoring...", Toast.LENGTH_SHORT).show();
                    BackupManager.restore(requireContext(), uri, new BackupManager.Callback() {
                        @Override public void onSuccess(String message) {
                            if (!isAdded()) return;
                            requireActivity().runOnUiThread(() ->
                                    new androidx.appcompat.app.AlertDialog.Builder(
                                            requireContext(), R.style.AlertDialogDark)
                                            .setTitle("✅ Restore Complete")
                                            .setMessage(message)
                                            .setPositiveButton("Close App", (d2, w2) ->
                                                    requireActivity().finishAffinity())
                                            .setCancelable(false)
                                            .show());
                        }
                        @Override public void onError(String message) {
                            if (!isAdded()) return;
                            requireActivity().runOnUiThread(() ->
                                    Toast.makeText(requireContext(),
                                            "❌ " + message, Toast.LENGTH_LONG).show());
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void exportCsv() {
        String[] options = {"Transactions", "Credit Cards", "Bank Accounts"};
        new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.AlertDialogDark)
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
            if (!isAdded()) return;
            try {
                List<Transaction> all = db.transactionDao().getAllSync();
                File f = CsvExporter.export(requireContext(), all);
                shareFile(f, "Export Transactions via...");
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(),
                        "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void exportCreditCardsCsv() {
        AppExecutors.db().execute(() -> {
            if (!isAdded()) return;
            try {
                List<CreditCard> cards = db.creditCardDao().getAllSync();
                File f = CsvExporter.exportCreditCards(requireContext(), cards);
                shareFile(f, "Export Credit Cards via...");
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(),
                        "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void exportBankAccountsCsv() {
        AppExecutors.db().execute(() -> {
            if (!isAdded()) return;
            try {
                List<BankAccount> accounts = db.bankAccountDao().getAllSync();
                File f = CsvExporter.exportBankAccounts(requireContext(), accounts);
                shareFile(f, "Export Bank Accounts via...");
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(),
                        "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void shareFile(File f, String title) {
        if (!isAdded()) return;
        Uri uri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(), requireContext().getPackageName() + ".fileprovider", f);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/csv");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        requireActivity().runOnUiThread(() -> startActivity(Intent.createChooser(share, title)));
    }

    private void confirmClear() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Clear All Data")
                .setMessage("This will permanently delete all transactions. Are you sure?")
                .setPositiveButton("Delete All", (d, w) ->
                        AppExecutors.db().execute(() -> {
                            db.transactionDao().deleteAll();
                            if (!isAdded()) return;
                            requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(),
                                    "All data cleared", Toast.LENGTH_SHORT).show());
                        }))
                .setNegativeButton("Cancel", null).show();
    }

    private void importCsv(Uri uri) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), "Importing CSV...", Toast.LENGTH_SHORT).show();
        CsvImporter.importFromUri(requireContext(), uri, new CsvImporter.Callback() {
            public void onProgress(int done, int total) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(),
                        "Importing... " + done + "/" + total, Toast.LENGTH_SHORT).show());
            }
            public void onComplete(int imported, int skipped) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    String msg = "✅ Imported " + imported + " records.";
                    if (skipped > 0) msg += " Skipped " + skipped + " (duplicates/invalid).";
                    new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                            .setTitle("CSV Import Complete").setMessage(msg)
                            .setPositiveButton("OK", null).show();
                });
            }
            public void onError(String msg) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(),
                        "❌ " + msg, Toast.LENGTH_LONG).show());
            }
        });
    }
}
