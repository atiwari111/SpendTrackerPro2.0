package com.spendtracker.pro;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class BankAccountActivity extends AppCompatActivity {

    private AppDatabase db;
    private BankAccountAdapter adapter;
    private TextView tvTotalBalance, tvAccountCount, tvTotalLabel, tvEmptyState;
    private RecyclerView rvAccounts;

    // Color palette matching the screenshot (SBI blue, ICICI red, PNB dark-red, etc.)
    private static final int[] CARD_COLORS = {
        Color.parseColor("#1565C0"),   // SBI blue
        Color.parseColor("#B71C1C"),   // ICICI deep red
        Color.parseColor("#880E4F"),   // PNB dark red/maroon
        Color.parseColor("#1B5E20"),   // green (Axis savings)
        Color.parseColor("#4A148C"),   // purple (Kotak)
        Color.parseColor("#E65100"),   // orange (Yes Bank)
        Color.parseColor("#006064"),   // teal (BOI)
        Color.parseColor("#1A237E"),   // indigo (HDFC savings)
    };

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_bank_account);
        db = AppDatabase.getInstance(this);

        Toolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Bank Accounts");
        }

        tvTotalBalance = findViewById(R.id.tvTotalBalance);
        tvAccountCount = findViewById(R.id.tvAccountCount);
        tvTotalLabel   = findViewById(R.id.tvTotalLabel);
        tvEmptyState   = findViewById(R.id.tvEmptyState);
        rvAccounts     = findViewById(R.id.rvAccounts);

        adapter = new BankAccountAdapter(acc -> showAccountOptions(acc));
        rvAccounts.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvAccounts.setAdapter(adapter);

        // Snap like the screenshot
        PagerSnapHelper snap = new PagerSnapHelper();
        snap.attachToRecyclerView(rvAccounts);

        observeAccounts();

        // Fix 2.38: clean up any duplicate rows (same lastFour) that were
        // inserted before this bug was patched — keeps the highest updatedAt row.
        AppExecutors.db().execute(() -> db.bankAccountDao().deleteDuplicatesByLastFour());

        View fab = findViewById(R.id.fabAddAccount);
        if (fab != null) fab.setVisibility(View.GONE);
    }

    private void observeAccounts() {
        db.bankAccountDao().getActive().observe(this, accounts -> {
            if (accounts == null) accounts = new ArrayList<>();
            boolean empty = accounts.isEmpty();
            tvEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            rvAccounts.setVisibility(empty ? View.GONE : View.VISIBLE);

            adapter.setAccounts(accounts);

            // Total balance
            double total = 0;
            for (BankAccount a : accounts) total += a.balance;
            tvTotalBalance.setText(String.format(Locale.getDefault(), "₹%,.2f", total));

            // "X of Y accounts" label
            final int activeCount = accounts.size();
            AppExecutors.db().execute(() -> {
                int totalCount = db.bankAccountDao().getTotalCount();
                final String countLabel = "(" + activeCount + " of " + totalCount + " accounts)";
                runOnUiThread(() -> tvAccountCount.setText(countLabel));
            });
        });
    }

    private void showAccountOptions(BankAccount acc) {
        String[] options = {"Update balance", "Edit account", "Delete account"};
        new AlertDialog.Builder(this, R.style.AlertDialogDark)
                .setTitle(acc.bankName + " — " + acc.getMaskedAccount())
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0: showUpdateBalanceDialog(acc);  break;
                        case 1: showAccountFormDialog(acc);    break;
                        case 2: confirmDelete(acc);            break;
                    }
                }).show();
    }

    private void showUpdateBalanceDialog(BankAccount acc) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_update_balance, null);
        EditText etBalance  = v.findViewById(R.id.etBalance);
        TextView tvLastUpdated = v.findViewById(R.id.tvLastUpdated);

        etBalance.setText(String.valueOf(acc.balance));

        if (acc.updatedAt > 0) {
            String ts = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
                    .format(new Date(acc.updatedAt));
            tvLastUpdated.setText("Last updated: " + ts);
        } else {
            tvLastUpdated.setText("Balance not set yet");
        }

        new AlertDialog.Builder(this, R.style.AlertDialogDark)
                .setTitle("Update balance — " + acc.bankName)
                .setView(v)
                .setPositiveButton("Save", (d, w) -> {
                    String val = etBalance.getText().toString().trim();
                    if (!val.isEmpty()) {
                        try {
                            double bal = Double.parseDouble(val);
                            AppExecutors.db().execute(() ->
                                    db.bankAccountDao().updateBalance(
                                            acc.id, bal, System.currentTimeMillis()));
                            Toast.makeText(this,
                                    acc.bankName + " balance updated", Toast.LENGTH_SHORT).show();
                        } catch (NumberFormatException e) {
                            Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddAccountDialog() {
        showAccountFormDialog(null);
    }

    private void showAccountFormDialog(BankAccount existing) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_add_bank_account, null);
        EditText etBank    = v.findViewById(R.id.etBankName);
        EditText etLabel   = v.findViewById(R.id.etAccountLabel);
        EditText etLastFour= v.findViewById(R.id.etLastFour);
        EditText etBalance = v.findViewById(R.id.etBalance);
        Spinner  spType    = v.findViewById(R.id.spAccountType);
        Spinner  spColor   = v.findViewById(R.id.spCardColor);

        String[] types  = {"SAVINGS", "CURRENT", "SALARY", "RD", "FD"};
        spType.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, types));

        String[] colorLabels = {"SBI Blue", "ICICI Red", "PNB Maroon", "Green",
                                "Purple", "Orange", "Teal", "Indigo"};
        spColor.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, colorLabels));

        boolean isEdit = existing != null;
        if (isEdit) {
            etBank.setText(existing.bankName);
            etLabel.setText(existing.accountLabel);
            etLastFour.setText(existing.lastFour);
            etBalance.setText(String.valueOf(existing.balance));
            for (int i = 0; i < types.length; i++) {
                if (types[i].equals(existing.accountType)) { spType.setSelection(i); break; }
            }
        }

        new AlertDialog.Builder(this, R.style.AlertDialogDark)
                .setTitle(isEdit ? "Edit Account" : "Add Bank Account")
                .setView(v)
                .setPositiveButton(isEdit ? "Save" : "Add", (d, w) -> {
                    String bank   = etBank.getText().toString().trim();
                    String label  = etLabel.getText().toString().trim();
                    String last4  = etLastFour.getText().toString().trim();
                    String balStr = etBalance.getText().toString().trim();
                    String type   = (String) spType.getSelectedItem();
                    int colorIdx  = spColor.getSelectedItemPosition();

                    if (bank.isEmpty()) {
                        Toast.makeText(this, "Bank name required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (last4.length() != 4) {
                        Toast.makeText(this, "Enter last 4 digits of account number", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    double bal = 0;
                    if (!balStr.isEmpty()) {
                        try { bal = Double.parseDouble(balStr); }
                        catch (NumberFormatException ignored) {}
                    }

                    BankAccount acc = isEdit ? existing : new BankAccount();
                    acc.bankName     = bank;
                    acc.accountLabel = label.isEmpty() ? bank + " " + type : label;
                    acc.lastFour     = last4;
                    acc.accountType  = type;
                    acc.balance      = bal;
                    acc.cardColor    = CARD_COLORS[colorIdx % CARD_COLORS.length];
                    acc.isActive     = true;
                    acc.updatedAt    = System.currentTimeMillis();

                    AppExecutors.db().execute(() -> {
                        if (isEdit) db.bankAccountDao().update(acc);
                        else        db.bankAccountDao().insert(acc);
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDelete(BankAccount acc) {
        new AlertDialog.Builder(this, R.style.AlertDialogDark)
                .setTitle("Delete account?")
                .setMessage("Remove " + acc.bankName + " " + acc.getMaskedAccount() + "?")
                .setPositiveButton("Delete", (d, w) ->
                        AppExecutors.db().execute(() -> db.bankAccountDao().delete(acc)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
