package com.spendtracker.pro;

import android.content.Intent;
import android.os.Bundle;
import android.text.*;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.*;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class TransactionsActivity extends AppCompatActivity {
    private RecyclerView rv;
    private TransactionAdapter adapter;
    private EditText etSearch;
    private ChipGroup chipPayment, chipCategory;
    private TextView tvCount, tvTotal;
    private Spinner spMerchant;
    private List<Transaction> all = new ArrayList<>();
    private String currentCat = "", currentMethod = "", currentMerchant = "";
    private AppDatabase db;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_transactions);
        db = AppDatabase.getInstance(this);

        Toolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Transactions");
        }

        rv           = findViewById(R.id.rv);
        etSearch     = findViewById(R.id.etSearch);
        chipPayment  = findViewById(R.id.chipGroup);
        chipCategory = findViewById(R.id.chipCategory);
        tvCount      = findViewById(R.id.tvCount);
        tvTotal      = findViewById(R.id.tvTotal);
        spMerchant   = findViewById(R.id.spMerchant);

        adapter = new TransactionAdapter(false); // false = full list, tap-to-edit enabled
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        setupPaymentChips();
        setupCategoryChips();

        etSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) { applyFilter(); }
            public void afterTextChanged(Editable s) {}
        });

        db.transactionDao().getAll().observe(this, list -> {
            all = list != null ? list : new ArrayList<>();
            updateMerchantSpinner();
            applyFilter();
        });

        findViewById(R.id.fabAdd).setOnClickListener(v ->
                startActivity(new Intent(this, AddExpenseActivity.class)));
    }

    private void setupPaymentChips() {
        String[][] filters = {{"All",""},{"UPI","UPI"},{"Credit","CREDIT_CARD"},{"Debit","DEBIT_CARD"},{"Cash","CASH"}};
        for (String[] f : filters) {
            Chip chip = new Chip(this);
            chip.setText(f[0]); chip.setCheckable(true); chip.setChecked(f[1].isEmpty());
            chip.setChipBackgroundColorResource(R.color.bg_card);
            chip.setTextColor(getResources().getColor(R.color.white, null));
            chip.setOnClickListener(v -> { currentMethod = f[1]; applyFilter(); });
            chipPayment.addView(chip);
        }
    }

    private void setupCategoryChips() {
        // "All" chip
        Chip allChip = new Chip(this);
        allChip.setText("All Categories"); allChip.setCheckable(true); allChip.setChecked(true);
        allChip.setChipBackgroundColorResource(R.color.bg_card);
        allChip.setTextColor(getResources().getColor(R.color.white, null));
        allChip.setOnClickListener(v -> { currentCat = ""; applyFilter(); });
        chipCategory.addView(allChip);

        for (String cat : CategoryEngine.getCategoryNames()) {
            Chip chip = new Chip(this);
            chip.setText(cat); chip.setCheckable(true);
            chip.setChipBackgroundColorResource(R.color.bg_card);
            chip.setTextColor(getResources().getColor(R.color.white, null));
            chip.setOnClickListener(v -> { currentCat = cat; applyFilter(); });
            chipCategory.addView(chip);
        }
    }

    private void updateMerchantSpinner() {
        if (spMerchant == null) return;
        Set<String> merchants = new LinkedHashSet<>();
        merchants.add("All Merchants");
        for (Transaction t : all) { if (t.merchant != null && !t.merchant.isEmpty()) merchants.add(t.merchant); }
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new ArrayList<>(merchants));
        spMerchant.setAdapter(a);
        spMerchant.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                currentMerchant = pos == 0 ? "" : (String) p.getItemAtPosition(pos);
                applyFilter();
            }
            public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private void applyFilter() {
        String q = etSearch.getText().toString().toLowerCase().trim();
        List<Transaction> filtered = all.stream()
                .filter(t -> currentMethod.isEmpty() || currentMethod.equals(t.paymentMethod))
                .filter(t -> currentCat.isEmpty() || currentCat.equals(t.category))
                .filter(t -> currentMerchant.isEmpty() || currentMerchant.equals(t.merchant))
                .filter(t -> q.isEmpty()
                        || (t.merchant != null && t.merchant.toLowerCase().contains(q))
                        || (t.category != null && t.category.toLowerCase().contains(q))
                        || (t.paymentDetail != null && t.paymentDetail.toLowerCase().contains(q)))
                .collect(Collectors.toList());
        adapter.setTransactions(filtered);

        // Update summary
        double total = filtered.stream().filter(t -> !t.isSelfTransfer).mapToDouble(t -> t.amount).sum();
        if (tvCount != null) tvCount.setText(filtered.size() + " transactions");
        if (tvTotal != null) tvTotal.setText(String.format("₹%.0f", total));
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { getOnBackPressedDispatcher().onBackPressed(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
