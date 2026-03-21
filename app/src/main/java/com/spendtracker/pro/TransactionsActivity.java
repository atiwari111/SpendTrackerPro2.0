package com.spendtracker.pro;

import android.content.Intent;
import android.graphics.*;
import com.airbnb.lottie.LottieAnimationView;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.*;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.*;
import androidx.core.content.ContextCompat;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class TransactionsActivity extends AppCompatActivity {
    private RecyclerView rv;
    private TransactionAdapter adapter;
    private View layoutEmptyState;
    private LottieAnimationView lottieEmpty;
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
        attachSwipeHelper();
        layoutEmptyState = findViewById(R.id.emptyState);
        if (layoutEmptyState != null) lottieEmpty = layoutEmptyState.findViewById(R.id.lottieEmpty);

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
            chip.setTextColor(getResources().getColor(R.color.text_primary, null));
            chip.setOnClickListener(v -> { currentMethod = f[1]; applyFilter(); });
            chipPayment.addView(chip);
        }
    }

    private void setupCategoryChips() {
        // "All" chip
        Chip allChip = new Chip(this);
        allChip.setText("All Categories"); allChip.setCheckable(true); allChip.setChecked(true);
        allChip.setChipBackgroundColorResource(R.color.bg_card);
        allChip.setTextColor(getResources().getColor(R.color.text_primary, null));
        allChip.setOnClickListener(v -> { currentCat = ""; applyFilter(); });
        chipCategory.addView(allChip);

        for (String cat : CategoryEngine.getCategoryNames()) {
            Chip chip = new Chip(this);
            chip.setText(cat); chip.setCheckable(true);
            chip.setChipBackgroundColorResource(R.color.bg_card);
            chip.setTextColor(getResources().getColor(R.color.text_primary, null));
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
        String q = etSearch.getText().toString().toLowerCase(Locale.ROOT).trim();
        List<Transaction> filtered = all.stream()
                .filter(t -> currentMethod.isEmpty() || currentMethod.equals(t.paymentMethod))
                .filter(t -> currentCat.isEmpty() || currentCat.equals(t.category))
                .filter(t -> currentMerchant.isEmpty() || currentMerchant.equals(t.merchant))
                .filter(t -> q.isEmpty()
                        || (t.merchant != null && t.merchant.toLowerCase(Locale.ROOT).contains(q))
                        || (t.category != null && t.category.toLowerCase(Locale.ROOT).contains(q))
                        || (t.paymentDetail != null && t.paymentDetail.toLowerCase(Locale.ROOT).contains(q)))
                .collect(Collectors.toList());
        adapter.setTransactions(filtered);
        if (layoutEmptyState != null) {
            boolean empty = filtered.isEmpty();
            layoutEmptyState.setVisibility(empty ? android.view.View.VISIBLE : android.view.View.GONE);
            rv.setVisibility(empty ? android.view.View.GONE : android.view.View.VISIBLE);
            if (empty && lottieEmpty != null) lottieEmpty.playAnimation();
        }

        // Update summary
        double total = filtered.stream().filter(t -> !t.isSelfTransfer).mapToDouble(t -> t.amount).sum();
        if (tvCount != null) tvCount.setText(filtered.size() + " transactions");
        if (tvTotal != null) tvTotal.setText(String.format(Locale.getDefault(), "₹%.0f", total));
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { getOnBackPressedDispatcher().onBackPressed(); return true; }
        return super.onOptionsItemSelected(item);
    }
    /**
     * Swipe gestures on transaction list:
     *   LEFT  → delete transaction (with undo snackbar)
     *   RIGHT → toggle self-transfer flag
     */
    private void attachSwipeHelper() {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            @Override
            public boolean onMove(@androidx.annotation.NonNull RecyclerView rv,
                    @androidx.annotation.NonNull RecyclerView.ViewHolder vh,
                    @androidx.annotation.NonNull RecyclerView.ViewHolder t) {
                return false;
            }

            @Override
            public void onSwiped(@androidx.annotation.NonNull RecyclerView.ViewHolder vh, int dir) {
                int pos = vh.getAdapterPosition();
                if (pos < 0 || pos >= adapter.getTransactions().size()) return;
                Transaction t = adapter.getTransactions().get(pos);

                if (dir == ItemTouchHelper.LEFT) {
                    // ── Delete ──────────────────────────────────────
                    AppExecutors.db().execute(() -> db.transactionDao().delete(t));
                    com.google.android.material.snackbar.Snackbar
                            .make(rv, "Transaction deleted", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                            .setAction("UNDO", v -> AppExecutors.db().execute(() ->
                                    db.transactionDao().insert(t)))
                            .setActionTextColor(0xFFA78BFA)
                            .setBackgroundTint(0xFF1E293B)
                            .setTextColor(0xFFF1F5F9)
                            .show();
                } else {
                    // ── Toggle self-transfer ────────────────────────
                    t.isSelfTransfer = !t.isSelfTransfer;
                    AppExecutors.db().execute(() -> db.transactionDao().update(t));
                    String msg = t.isSelfTransfer ? "Marked as self-transfer" : "Unmarked as self-transfer";
                    com.google.android.material.snackbar.Snackbar
                            .make(rv, msg, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                            .setBackgroundTint(0xFF1E293B)
                            .setTextColor(0xFFF1F5F9)
                            .show();
                }
            }

            @Override
            public void onChildDraw(@androidx.annotation.NonNull Canvas c,
                    @androidx.annotation.NonNull RecyclerView rv,
                    @androidx.annotation.NonNull RecyclerView.ViewHolder vh,
                    float dX, float dY, int actionState, boolean active) {

                if (actionState != ItemTouchHelper.ACTION_STATE_SWIPE) {
                    super.onChildDraw(c, rv, vh, dX, dY, actionState, active);
                    return;
                }

                View item = vh.itemView;
                Paint paint  = new Paint();
                Paint textPt = new Paint();
                textPt.setColor(Color.WHITE);
                textPt.setTextSize(36f);
                textPt.setAntiAlias(true);

                if (dX < 0) {
                    // Swiping LEFT → red background + delete icon
                    paint.setColor(Color.parseColor("#EF4444"));
                    c.drawRect(item.getRight() + dX, item.getTop(),
                            item.getRight(), item.getBottom(), paint);
                    c.drawText("🗑️", item.getRight() + dX + 24,
                            item.getTop() + (item.getHeight() / 2f) + 14, textPt);
                } else if (dX > 0) {
                    // Swiping RIGHT → blue background + transfer icon
                    paint.setColor(Color.parseColor("#0EA5E9"));
                    c.drawRect(item.getLeft(), item.getTop(),
                            item.getLeft() + dX, item.getBottom(), paint);
                    c.drawText("🔄", item.getLeft() + 24,
                            item.getTop() + (item.getHeight() / 2f) + 14, textPt);
                }

                super.onChildDraw(c, rv, vh, dX, dY, actionState, active);
            }
        }).attachToRecyclerView(rv);
    }

}
