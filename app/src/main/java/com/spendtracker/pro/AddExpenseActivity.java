package com.spendtracker.pro;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.*;
import android.view.MenuItem;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.textfield.TextInputEditText;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * AddExpenseActivity v2.0
 * - Hybrid ML Agent: TFLite + frequency-map auto-suggestions
 * - Pre-fills category, payment method, amount hint, notes hint
 * - Learns from every save and edit (all 3 layers updated)
 */
public class AddExpenseActivity extends AppCompatActivity {

    public static final String EXTRA_TRANSACTION_ID = "txn_id";

    private TextInputEditText etAmount, etMerchant, etNotes;
    private Spinner spCategory, spPayment;
    private TextView tvDate;
    private SwitchCompat swSelfTransfer;
    private long selectedDate = System.currentTimeMillis();
    private AppDatabase db;
    private HybridTransactionAgent agent;
    private Transaction editingTransaction = null;

    // Track what auto-category suggested vs what user actually picked
    private String autoSuggestedCategory = null;
    private boolean userOverrodeCategory  = false;
    // Track original values so we can detect changes on edit-save
    private String originalMerchant  = null;
    private String originalCategory  = null;

    // OCR receipt scanner launcher
    private final ActivityResultLauncher<android.content.Intent> ocrLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    double amount = result.getData().getDoubleExtra(OcrScanActivity.EXTRA_AMOUNT, 0);
                    String merchant = result.getData().getStringExtra(OcrScanActivity.EXTRA_MERCHANT);
                    if (amount > 0) etAmount.setText(String.format(Locale.ROOT, "%.2f", amount));
                    if (merchant != null && !merchant.isEmpty()) etMerchant.setText(merchant);
                    Toast.makeText(this, "✅ Receipt scanned — check and save", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_add_expense);
        db    = AppDatabase.getInstance(this);
        agent = HybridTransactionAgent.getInstance(this);

        Toolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        etAmount       = findViewById(R.id.etAmount);
        etMerchant     = findViewById(R.id.etMerchant);
        etNotes        = findViewById(R.id.etNotes);
        tvDate         = findViewById(R.id.tvDate);
        spCategory     = findViewById(R.id.spCategory);
        spPayment      = findViewById(R.id.spPayment);
        swSelfTransfer = findViewById(R.id.swSelfTransfer);

        // ── Spinners ─────────────────────────────────────────────
        String[] cats = CategoryEngine.getSpendCategoryNames();
        spCategory.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, cats));

        String[] payments = {"UPI", "Credit Card", "Debit Card", "Cash", "Bank Transfer"};
        spPayment.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, payments));

        // ── Detect manual category override by user ───────────────
        spCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int pos, long id) {
                String selected = (String) parent.getItemAtPosition(pos);
                if (autoSuggestedCategory != null && !selected.equals(autoSuggestedCategory)) {
                    userOverrodeCategory = true;
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // ── Hybrid ML TextWatcher ─────────────────────────────────
        etMerchant.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (editingTransaction != null) return; // don't override when editing
                String typed = s.toString().trim();
                if (typed.length() < 2) return;

                // Parse current amount if entered (0 if empty)
                double currentAmount = 0;
                try {
                    String amtStr = etAmount.getText() != null
                                    ? etAmount.getText().toString().trim() : "";
                    if (!amtStr.isEmpty()) currentAmount = Double.parseDouble(amtStr);
                } catch (NumberFormatException ignored) {}

                String currentPayment = spPayment.getSelectedItem() != null
                                        ? spPayment.getSelectedItem().toString() : "UPI";

                // ── Ask Hybrid agent (TFLite + frequency map) ─────
                agent.suggestAsync(typed, currentAmount, selectedDate, currentPayment,
                    suggestion -> {
                        if (suggestion == null) {
                            // No ML data — fall back to static CategoryEngine
                            if (!userOverrodeCategory) {
                                String fallback = CategoryEngine.autoCategory(typed);
                                if (!fallback.equals("💼 Others")) {
                                    autoSuggestedCategory = fallback;
                                    selectCategory(fallback);
                                }
                            }
                            return;
                        }

                        // Apply category suggestion
                        if (!userOverrodeCategory && suggestion.category != null) {
                            autoSuggestedCategory = suggestion.category;
                            selectCategory(suggestion.category);
                        }

                        // Apply payment method suggestion
                        if (suggestion.paymentMethod != null) {
                            String[] pmList = {"UPI", "Credit Card", "Debit Card",
                                               "Cash", "Bank Transfer"};
                            for (int i = 0; i < pmList.length; i++) {
                                if (suggestion.paymentMethod.toLowerCase(Locale.ROOT)
                                        .contains(pmList[i].toLowerCase(Locale.ROOT))) {
                                    spPayment.setSelection(i);
                                    break;
                                }
                            }
                        }

                        // Show avg amount as hint (only when amount field is empty)
                        if (suggestion.avgAmount > 0
                                && (etAmount.getText() == null
                                    || etAmount.getText().toString().isEmpty())) {
                            etAmount.setHint(String.format(Locale.getDefault(), "~₹%.0f (avg)", suggestion.avgAmount));
                        }

                        // Show last notes as placeholder hint
                        if (suggestion.notesHint != null && !suggestion.notesHint.isEmpty()
                                && (etNotes.getText() == null
                                    || etNotes.getText().toString().isEmpty())) {
                            etNotes.setHint(suggestion.notesHint);
                        }
                    });
            }
        });

        // ── Date picker ──────────────────────────────────────────
        updateDateLabel();
        tvDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(selectedDate);
            new DatePickerDialog(this, (view, y, m, d) -> {
                Calendar sel = Calendar.getInstance();
                sel.set(y, m, d);
                selectedDate = sel.getTimeInMillis();
                updateDateLabel();
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });

        // ── Load for edit ────────────────────────────────────────
        int txnId = getIntent().getIntExtra(EXTRA_TRANSACTION_ID, -1);
        if (txnId != -1) {
            if (getSupportActionBar() != null) getSupportActionBar().setTitle("Edit Expense");
            loadForEditing(txnId);
        } else {
            if (getSupportActionBar() != null) getSupportActionBar().setTitle("Add Expense");
        }

        findViewById(R.id.btnSave).setOnClickListener(v -> saveExpense());

        // scan receipt button
        findViewById(R.id.btnScanReceipt).setOnClickListener(v ->
                ocrLauncher.launch(new android.content.Intent(this, OcrScanActivity.class)));
    }

    // ─────────────────────────────────────────────────────────────
    // LOAD FOR EDITING
    // ─────────────────────────────────────────────────────────────
    private void loadForEditing(int id) {
        AppExecutors.db().execute(() -> {
            List<Transaction> all = db.transactionDao().getAllSync();
            for (Transaction t : all) {
                if (t.id == id) { editingTransaction = t; break; }
            }
            if (editingTransaction == null) return;
            final Transaction t = editingTransaction;
            runOnUiThread(() -> {
                etAmount.setText(String.format(Locale.ROOT, "%.2f", t.amount));
                etMerchant.setText(t.merchant);
                etNotes.setText(t.notes != null ? t.notes : "");
                selectedDate = t.timestamp;
                updateDateLabel();
                swSelfTransfer.setChecked(t.isSelfTransfer);
                originalMerchant = t.merchant;
                originalCategory = t.category;

                String[] catList = CategoryEngine.getSpendCategoryNames();
                for (int i = 0; i < catList.length; i++) {
                    if (catList[i].equals(t.category)) { spCategory.setSelection(i); break; }
                }
                String[] pmList = {"UPI", "Credit Card", "Debit Card", "Cash", "Bank Transfer"};
                String pm = t.paymentDetail != null ? t.paymentDetail : "";
                for (int i = 0; i < pmList.length; i++) {
                    if (pm.toLowerCase(Locale.ROOT).contains(pmList[i].toLowerCase(Locale.ROOT))) {
                        spPayment.setSelection(i);
                        break;
                    }
                }
                ((Button) findViewById(R.id.btnSave)).setText("Update Expense");
            });
        });
    }

    // ─────────────────────────────────────────────────────────────
    // SAVE / UPDATE
    // ─────────────────────────────────────────────────────────────
    private void saveExpense() {
        String amtStr = etAmount.getText() != null ? etAmount.getText().toString().trim() : "";
        if (amtStr.isEmpty()) { etAmount.setError("Enter amount"); return; }
        double amount;
        try { amount = Double.parseDouble(amtStr); }
        catch (Exception e) { etAmount.setError("Invalid amount"); return; }

        String merchant = etMerchant.getText() != null ? etMerchant.getText().toString().trim() : "";
        if (merchant.isEmpty()) merchant = "Manual Entry";

        String category   = (String) spCategory.getSelectedItem();
        String paymentRaw = (String) spPayment.getSelectedItem();
        String paymentMethod = paymentRaw.equals("Credit Card") ? "CREDIT_CARD"
                             : paymentRaw.equals("Debit Card")  ? "DEBIT_CARD"
                             : paymentRaw.replace(" ", "_").toUpperCase(Locale.ROOT);
        String notes  = etNotes.getText() != null ? etNotes.getText().toString().trim() : "";
        boolean isSelf = swSelfTransfer.isChecked();

        // Keep existing CategoryEngine SharedPrefs learning for backward compat
        if (userOverrodeCategory && !merchant.equals("Manual Entry")) {
            CategoryEngine.learnMerchant(merchant, category);
        }
        if (editingTransaction != null && originalMerchant != null
                && !originalMerchant.equalsIgnoreCase(merchant)) {
            CategoryEngine.learnMerchantAlias(originalMerchant, merchant);
        }
        if (editingTransaction != null && originalCategory != null
                && !originalCategory.equals(category)
                && !merchant.equals("Manual Entry")) {
            CategoryEngine.learnMerchant(merchant, category);
        }

        if (editingTransaction != null) {
            editingTransaction.merchant      = merchant;
            editingTransaction.amount        = amount;
            editingTransaction.category      = category;
            editingTransaction.categoryIcon  = CategoryEngine.getInfo(category).icon;
            editingTransaction.paymentMethod = paymentMethod;
            editingTransaction.paymentDetail = paymentRaw;
            editingTransaction.timestamp     = selectedDate;
            editingTransaction.notes         = notes;
            editingTransaction.isSelfTransfer = isSelf;
            final boolean wasEdit = (originalCategory != null
                    && !originalCategory.equals(category));
            AppExecutors.db().execute(() -> {
                db.transactionDao().update(editingTransaction);
                agent.learn(editingTransaction, wasEdit);  // Hybrid learn — all 3 layers
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(editingTransaction.timestamp);
                int txMonth = cal.get(Calendar.MONTH) + 1;
                int txYear  = cal.get(Calendar.YEAR);
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);      cal.set(Calendar.MILLISECOND, 0);
                long mStart = cal.getTimeInMillis();
                cal.add(Calendar.MONTH, 1);
                long mEnd = cal.getTimeInMillis();
                db.budgetDao().recalcAllUsed(txMonth, txYear, mStart, mEnd);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Expense updated!", Toast.LENGTH_SHORT).show();
                    finish();
                });
            });
        } else {
            Transaction tx = new Transaction();
            tx.merchant      = merchant;
            tx.amount        = amount;
            tx.category      = category;
            tx.categoryIcon  = CategoryEngine.getInfo(category).icon;
            tx.paymentMethod = paymentMethod;
            tx.paymentDetail = paymentRaw;
            tx.timestamp     = selectedDate;
            tx.notes         = notes;
            tx.isManual       = true;
            tx.isSelfTransfer = isSelf;
            tx.smsHash        = "manual_" + java.util.UUID.randomUUID().toString();
            final boolean wasCorrection = userOverrodeCategory;
            AppExecutors.db().execute(() -> {
                db.transactionDao().insert(tx);
                agent.learn(tx, wasCorrection);  // Hybrid learn — all 3 layers
                if (!isSelf) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(selectedDate);
                    int txMonth = cal.get(Calendar.MONTH) + 1;
                    int txYear  = cal.get(Calendar.YEAR);
                    cal.set(Calendar.DAY_OF_MONTH, 1);
                    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0);
                    cal.set(Calendar.SECOND, 0);      cal.set(Calendar.MILLISECOND, 0);
                    long mStart = cal.getTimeInMillis();
                    cal.add(Calendar.MONTH, 1);
                    long mEnd = cal.getTimeInMillis();
                    db.budgetDao().recalcAllUsed(txMonth, txYear, mStart, mEnd);
                    Budget budget = db.budgetDao().getByCategoryMonthYear(
                            category, txMonth, txYear);
                    if (budget != null && budget.getProgress() >= 0.9f)
                        NotificationHelper.sendBudgetAlert(this, category,
                                budget.usedAmount, budget.limitAmount, budget.id);
                }
                runOnUiThread(() -> {
                    Toast.makeText(this, "Expense saved!", Toast.LENGTH_SHORT).show();
                    finish();
                });
            });
        }
    }

    // ── Helpers ───────────────────────────────────────────────────

    /** Select a category in the spinner without triggering the override listener. */
    private void selectCategory(String category) {
        String[] catList = CategoryEngine.getSpendCategoryNames();
        for (int i = 0; i < catList.length; i++) {
            if (catList[i].equals(category)) {
                userOverrodeCategory = false;
                spCategory.setSelection(i);
                userOverrodeCategory = false; // reset again after listener may have fired
                break;
            }
        }
    }

    private void updateDateLabel() {
        tvDate.setText(new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
                .format(new Date(selectedDate)));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { getOnBackPressedDispatcher().onBackPressed(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
