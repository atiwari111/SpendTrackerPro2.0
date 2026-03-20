package com.spendtracker.pro;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.*;
import java.util.List;

public class NetWorthActivity extends AppCompatActivity {
    private TextView tvNetWorth, tvAssets, tvLiabilities, tvSyncStatus;
    private RecyclerView rvItems;
    private NetWorthAdapter adapter;
    private AppDatabase db;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_net_worth);
        db = AppDatabase.getInstance(this);
        Toolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Net Worth");
        }

        tvNetWorth    = findViewById(R.id.tvNetWorth);
        tvAssets      = findViewById(R.id.tvAssets);
        tvLiabilities = findViewById(R.id.tvLiabilities);
        tvSyncStatus  = findViewById(R.id.tvSyncStatus);
        rvItems       = findViewById(R.id.rvItems);

        adapter = new NetWorthAdapter(item -> showEditDialog(item));
        rvItems.setLayoutManager(new LinearLayoutManager(this));
        rvItems.setAdapter(adapter);

        db.netWorthDao().getAll().observe(this, list -> {
            if (list == null) return;
            adapter.setItems(list);
            AppExecutors.db().execute(() -> {
                double assets = db.netWorthDao().getTotalAssets();
                double liabs  = db.netWorthDao().getTotalLiabilities();
                double net    = assets - liabs;
                runOnUiThread(() -> {
                    tvAssets.setText(String.format("₹%.0f", assets));
                    tvLiabilities.setText(String.format("₹%.0f", liabs));
                    tvNetWorth.setText(String.format("₹%.0f", net));
                    tvNetWorth.setTextColor(net >= 0 ? 0xFF10B981 : 0xFFEF4444);
                });
            });
        });

        findViewById(R.id.btnAddAsset).setOnClickListener(v -> showAddDialog("ASSET"));
        findViewById(R.id.btnAddLiability).setOnClickListener(v -> showAddDialog("LIABILITY"));

        // ── Sync bank accounts → net worth assets ────────────────
        // Pulls live balances from bank_accounts table and upserts them
        // as ASSET rows so the net worth total stays current automatically.
        findViewById(R.id.btnSyncBankAccounts).setOnClickListener(v -> syncBankAccountsToNetWorth());

        // Auto-sync on every open so net worth reflects latest balances
        syncBankAccountsToNetWorth();
    }

    /**
     * Syncs all active BankAccounts into NetWorthItem ASSET rows.
     * Fix 2.29: Deduplicates by lastFour — if multiple BankAccount rows exist
     * for the same account number, only the most recently updated one is used.
     * Stale duplicate net-worth rows from old bank IDs are deleted automatically.
     */
    private void syncBankAccountsToNetWorth() {
        AppExecutors.db().execute(() -> {
            // Fix 2.29: clean up duplicate BankAccount rows before syncing
            db.bankAccountDao().deleteDuplicatesByLastFour();

            // ── Bank accounts → ASSET ─────────────────────────────
            List<BankAccount> bankAccounts = db.bankAccountDao().getAllSync();

            // Fix 2.29: deduplicate by lastFour — keep the row with the highest updatedAt
            java.util.Map<String, BankAccount> uniqueAccounts = new java.util.LinkedHashMap<>();
            for (BankAccount acc : bankAccounts) {
                if (!acc.isActive) continue;
                String key = acc.lastFour != null ? acc.lastFour : String.valueOf(acc.id);
                BankAccount existing = uniqueAccounts.get(key);
                if (existing == null || acc.updatedAt > existing.updatedAt) {
                    uniqueAccounts.put(key, acc);
                }
            }

            // Fix 2.29: collect all valid bank keys from deduplicated accounts
            java.util.Set<String> validKeys = new java.util.HashSet<>();
            for (BankAccount acc : uniqueAccounts.values()) {
                validKeys.add("bank:" + acc.id);
            }

            // Fix 2.29: delete any stale NetWorthItem rows whose bank:X key is NOT in validKeys
            List<NetWorthItem> allItems = db.netWorthDao().getAllSync();
            for (NetWorthItem item : allItems) {
                if (item.name != null && item.name.startsWith("bank:") && item.name.contains("||")) {
                    String itemKey = item.name.substring(0, item.name.indexOf("||"));
                    if (!validKeys.contains(itemKey)) {
                        db.netWorthDao().delete(item);
                    }
                }
            }

            // Upsert one NetWorthItem per unique account
            int synced = 0;
            for (BankAccount acc : uniqueAccounts.values()) {
                String key  = "bank:" + acc.id;
                String name = acc.bankName + " (A/c " + (acc.lastFour != null ? acc.lastFour : "????") + ")";

                NetWorthItem existingItem = findByKey(key);
                if (existingItem != null) {
                    existingItem.name      = key + "||" + name;
                    existingItem.amount    = acc.balance;
                    existingItem.icon      = "🏦";
                    existingItem.updatedAt = System.currentTimeMillis();
                    db.netWorthDao().update(existingItem);
                } else {
                    NetWorthItem item = new NetWorthItem(name, acc.balance, "ASSET", "🏦");
                    item.name = key + "||" + name;
                    db.netWorthDao().insert(item);
                }
                synced++;
            }

            // ── Credit cards → LIABILITY (outstanding spent) ──────
            List<CreditCard> cards = db.creditCardDao().getAllSync();
            for (CreditCard card : cards) {
                if (card.currentSpent <= 0) continue;
                String key  = "cc:" + card.id;
                String name = (card.cardLabel != null ? card.cardLabel : card.bankName)
                        + " (outstanding)";
                NetWorthItem existing = findByKey(key);
                if (existing != null) {
                    existing.amount    = card.currentSpent;
                    existing.updatedAt = System.currentTimeMillis();
                    db.netWorthDao().update(existing);
                } else {
                    NetWorthItem item = new NetWorthItem(name, card.currentSpent, "LIABILITY", "💳");
                    item.name = key + "||" + name;
                    db.netWorthDao().insert(item);
                }
            }

            final int count = synced;
            runOnUiThread(() -> {
                if (tvSyncStatus != null) {
                    tvSyncStatus.setText("Synced " + count + " account" + (count == 1 ? "" : "s"));
                    tvSyncStatus.setVisibility(View.VISIBLE);
                }
                Toast.makeText(this,
                        "Net worth synced from bank accounts", Toast.LENGTH_SHORT).show();
            });
        });
    }

    /** Find a NetWorthItem whose name starts with the given key prefix. */
    private NetWorthItem findByKey(String key) {
        List<NetWorthItem> all = db.netWorthDao().getAllSync();
        for (NetWorthItem item : all) {
            if (item.name != null && item.name.startsWith(key + "||")) return item;
        }
        return null;
    }

    private void showAddDialog(String type) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_networth, null);
        EditText etName = v.findViewById(R.id.etName);
        EditText etAmount = v.findViewById(R.id.etAmount);
        Spinner spIcon = v.findViewById(R.id.spIcon);
        String[] icons = type.equals("ASSET")
                ? new String[]{"💰","🏠","🚗","📈","🏦","💎"}
                : new String[]{"🏦","💳","🏠","📋","💸"};
        spIcon.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, icons));

        new AlertDialog.Builder(this, R.style.AlertDialogDark)
                .setTitle("Add " + (type.equals("ASSET") ? "Asset" : "Liability"))
                .setView(v)
                .setPositiveButton("Save", (d, w) -> {
                    String name = etName.getText().toString().trim();
                    String amtStr = etAmount.getText().toString().trim();
                    if (name.isEmpty() || amtStr.isEmpty()) return;
                    try {
                        NetWorthItem item = new NetWorthItem(
                                name, Double.parseDouble(amtStr), type,
                                (String) spIcon.getSelectedItem());
                        AppExecutors.db().execute(() -> db.netWorthDao().insert(item));
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void showEditDialog(NetWorthItem item) {
        // Strip the internal key prefix for display in the edit form
        String displayName = item.name != null && item.name.contains("||")
                ? item.name.substring(item.name.indexOf("||") + 2)
                : item.name;

        View v = LayoutInflater.from(this).inflate(R.layout.dialog_networth, null);
        EditText etName = v.findViewById(R.id.etName);
        EditText etAmount = v.findViewById(R.id.etAmount);
        Spinner spIcon = v.findViewById(R.id.spIcon);
        String[] icons = item.type.equals("ASSET")
                ? new String[]{"💰","🏠","🚗","📈","🏦","💎"}
                : new String[]{"🏦","💳","🏠","📋","💸"};
        spIcon.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, icons));
        etName.setText(displayName);
        etAmount.setText(String.valueOf((int) item.amount));

        new AlertDialog.Builder(this, R.style.AlertDialogDark)
                .setTitle("Edit Item")
                .setView(v)
                .setPositiveButton("Update", (d, w) -> {
                    String newName = etName.getText().toString().trim();
                    // Preserve the key prefix if it's a synced row
                    if (item.name != null && item.name.contains("||")) {
                        String prefix = item.name.substring(0, item.name.indexOf("||") + 2);
                        item.name = prefix + newName;
                    } else {
                        item.name = newName;
                    }
                    try { item.amount = Double.parseDouble(etAmount.getText().toString().trim()); }
                    catch (Exception ignored) {}
                    item.icon      = (String) spIcon.getSelectedItem();
                    item.updatedAt = System.currentTimeMillis();
                    AppExecutors.db().execute(() -> db.netWorthDao().update(item));
                })
                .setNegativeButton("Delete", (d, w) ->
                        AppExecutors.db().execute(() -> db.netWorthDao().delete(item)))
                .setNeutralButton("Cancel", null).show();
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
