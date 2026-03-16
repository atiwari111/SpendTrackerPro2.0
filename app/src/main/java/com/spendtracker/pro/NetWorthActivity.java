package com.spendtracker.pro;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.*;
import java.util.List;
import java.util.concurrent.Executors;

public class NetWorthActivity extends AppCompatActivity {
    private TextView tvNetWorth, tvAssets, tvLiabilities;
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
        if (getSupportActionBar() != null) { getSupportActionBar().setDisplayHomeAsUpEnabled(true); getSupportActionBar().setTitle("Net Worth"); }

        tvNetWorth    = findViewById(R.id.tvNetWorth);
        tvAssets      = findViewById(R.id.tvAssets);
        tvLiabilities = findViewById(R.id.tvLiabilities);
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
    }

    private void showAddDialog(String type) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_networth, null);
        EditText etName = v.findViewById(R.id.etName);
        EditText etAmount = v.findViewById(R.id.etAmount);
        Spinner spIcon = v.findViewById(R.id.spIcon);
        String[] icons = type.equals("ASSET") ? new String[]{"💰","🏠","🚗","📈","🏦","💎"} : new String[]{"🏦","💳","🏠","📋","💸"};
        spIcon.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, icons));

        new AlertDialog.Builder(this, R.style.AlertDialogDark)
                .setTitle("Add " + (type.equals("ASSET") ? "Asset" : "Liability"))
                .setView(v)
                .setPositiveButton("Save", (d, w) -> {
                    String name = etName.getText().toString().trim();
                    String amtStr = etAmount.getText().toString().trim();
                    if (name.isEmpty() || amtStr.isEmpty()) return;
                    try {
                        NetWorthItem item = new NetWorthItem(name, Double.parseDouble(amtStr), type, (String)spIcon.getSelectedItem());
                        AppExecutors.db().execute(() -> db.netWorthDao().insert(item));
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void showEditDialog(NetWorthItem item) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_networth, null);
        EditText etName = v.findViewById(R.id.etName);
        EditText etAmount = v.findViewById(R.id.etAmount);
        Spinner spIcon = v.findViewById(R.id.spIcon);
        String[] icons = item.type.equals("ASSET") ? new String[]{"💰","🏠","🚗","📈","🏦","💎"} : new String[]{"🏦","💳","🏠","📋","💸"};
        spIcon.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, icons));
        etName.setText(item.name);
        etAmount.setText(String.valueOf((int)item.amount));

        new AlertDialog.Builder(this, R.style.AlertDialogDark)
                .setTitle("Edit Item")
                .setView(v)
                .setPositiveButton("Update", (d, w) -> {
                    item.name = etName.getText().toString().trim();
                    try { item.amount = Double.parseDouble(etAmount.getText().toString().trim()); } catch (Exception ignored) {}
                    item.icon = (String) spIcon.getSelectedItem();
                    item.updatedAt = System.currentTimeMillis();
                    AppExecutors.db().execute(() -> db.netWorthDao().update(item));
                })
                .setNegativeButton("Delete", (d, w) -> AppExecutors.db().execute(() -> db.netWorthDao().delete(item)))
                .setNeutralButton("Cancel", null).show();
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { getOnBackPressedDispatcher().onBackPressed(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
