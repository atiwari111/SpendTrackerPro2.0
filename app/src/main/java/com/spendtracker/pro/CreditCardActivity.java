package com.spendtracker.pro;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.*;
import java.util.*;

public class CreditCardActivity extends AppCompatActivity {

    private AppDatabase db;
    private CreditCardAdapter adapter;
    private TextView tvTotalSpent, tvCardCount, tvEmptyState;
    private RecyclerView rvCards;

    // Card color palette — matches the dark-blue screenshot aesthetic
    private static final int[] CARD_COLORS = {
        Color.parseColor("#1A3A8F"),   // deep blue (HDFC)
        Color.parseColor("#1A5276"),   // dark teal (SBI)
        Color.parseColor("#922B21"),   // dark red (ICICI)
        Color.parseColor("#1B4332"),   // dark green
        Color.parseColor("#512E5F"),   // deep purple
        Color.parseColor("#1F3A5F"),   // navy
        Color.parseColor("#784212"),   // bronze
        Color.parseColor("#0B3954"),   // midnight blue
    };

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_credit_card);
        db = AppDatabase.getInstance(this);

        Toolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Credit Cards");
        }

        tvTotalSpent = findViewById(R.id.tvTotalSpent);
        tvCardCount  = findViewById(R.id.tvCardCount);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        rvCards      = findViewById(R.id.rvCards);

        adapter = new CreditCardAdapter(card -> showCardOptions(card));
        rvCards.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvCards.setAdapter(adapter);

        // Snap-to-card behavior like the screenshot
        PagerSnapHelper snap = new PagerSnapHelper();
        snap.attachToRecyclerView(rvCards);

        observeCards();

        findViewById(R.id.fabAddCard).setOnClickListener(v -> showAddCardDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSpentAmounts();
    }

    private void observeCards() {
        db.creditCardDao().getAll().observe(this, cards -> {
            if (cards == null) cards = new ArrayList<>();
            boolean empty = cards.isEmpty();

            tvEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            rvCards.setVisibility(empty ? View.GONE : View.VISIBLE);

            adapter.setCards(cards);
            tvCardCount.setText(cards.size() + " card" + (cards.size() == 1 ? "" : "s"));

            double total = 0;
            for (CreditCard c : cards) total += c.currentSpent;
            tvTotalSpent.setText(String.format("₹%.0f", total));
        });
    }

    /** Recalculates currentSpent for every card based on the billing cycle window. */
    private void refreshSpentAmounts() {
        AppExecutors.db().execute(() -> {
            List<CreditCard> cards = db.creditCardDao().getAllSync();
            long now = System.currentTimeMillis();
            for (CreditCard card : cards) {
                long cycleStart = card.billingCycleStart > 0
                        ? card.billingCycleStart
                        : getMonthStart(now);
                double spent = db.creditCardDao().getCreditSpendInRange(cycleStart, now);
                db.creditCardDao().updateSpent(card.id, spent, now);
            }
        });
    }

    private void showCardOptions(CreditCard card) {
        String[] options = {"Edit card", "Set billing cycle", "Update statement", "Delete card"};
        new AlertDialog.Builder(this, R.style.AlertDialogDark)
                .setTitle(card.cardLabel != null ? card.cardLabel : card.bankName)
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0: showEditCardDialog(card); break;
                        case 1: showBillingCycleDialog(card); break;
                        case 2: showStatementDialog(card); break;
                        case 3: confirmDelete(card); break;
                    }
                }).show();
    }

    private void showAddCardDialog() {
        showCardFormDialog(null);
    }

    private void showEditCardDialog(CreditCard existing) {
        showCardFormDialog(existing);
    }

    private void showCardFormDialog(CreditCard existing) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_add_credit_card, null);
        EditText etBank    = v.findViewById(R.id.etBankName);
        EditText etLabel   = v.findViewById(R.id.etCardLabel);
        EditText etLastFour= v.findViewById(R.id.etLastFour);
        EditText etLimit   = v.findViewById(R.id.etCreditLimit);
        Spinner  spNetwork = v.findViewById(R.id.spNetwork);
        Spinner  spColor   = v.findViewById(R.id.spCardColor);

        String[] networks = {"VISA", "MASTERCARD", "RUPAY", "AMEX"};
        spNetwork.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, networks));

        String[] colorLabels = {"Deep Blue", "Dark Teal", "Dark Red", "Dark Green",
                                "Deep Purple", "Navy", "Bronze", "Midnight Blue"};
        spColor.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, colorLabels));

        if (existing != null) {
            etBank.setText(existing.bankName);
            etLabel.setText(existing.cardLabel);
            etLastFour.setText(existing.lastFour);
            if (existing.creditLimit > 0)
                etLimit.setText(String.valueOf((int) existing.creditLimit));
            // Set spinner selections
            for (int i = 0; i < networks.length; i++) {
                if (networks[i].equals(existing.network)) { spNetwork.setSelection(i); break; }
            }
        }

        boolean isEdit = existing != null;
        new AlertDialog.Builder(this, R.style.AlertDialogDark)
                .setTitle(isEdit ? "Edit Card" : "Add Credit Card")
                .setView(v)
                .setPositiveButton(isEdit ? "Save" : "Add", (d, w) -> {
                    String bank   = etBank.getText().toString().trim();
                    String label  = etLabel.getText().toString().trim();
                    String last4  = etLastFour.getText().toString().trim();
                    String limitStr = etLimit.getText().toString().trim();
                    String network= (String) spNetwork.getSelectedItem();
                    int    colorIdx = spColor.getSelectedItemPosition();

                    if (bank.isEmpty()) { Toast.makeText(this, "Bank name required", Toast.LENGTH_SHORT).show(); return; }
                    if (last4.length() != 4) { Toast.makeText(this, "Enter 4-digit card ending", Toast.LENGTH_SHORT).show(); return; }

                    double limit = 0;
                    if (!limitStr.isEmpty()) {
                        try { limit = Double.parseDouble(limitStr); } catch (NumberFormatException ignored) {}
                    }

                    CreditCard card = isEdit ? existing : new CreditCard();
                    card.bankName    = bank;
                    card.cardLabel   = label.isEmpty() ? bank + " Credit" : label;
                    card.lastFour    = last4;
                    card.network     = network;
                    card.creditLimit = limit;
                    card.cardColor   = CARD_COLORS[colorIdx % CARD_COLORS.length];
                    card.updatedAt   = System.currentTimeMillis();

                    AppExecutors.db().execute(() -> {
                        if (isEdit) db.creditCardDao().update(card);
                        else        db.creditCardDao().insert(card);
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showBillingCycleDialog(CreditCard card) {
        String[] days = new String[28];
        for (int i = 0; i < 28; i++) days[i] = (i + 1) + (i == 0 ? "st" : i == 1 ? "nd" : i == 2 ? "rd" : "th");

        new AlertDialog.Builder(this, R.style.AlertDialogDark)
                .setTitle("Billing cycle resets on which day?")
                .setItems(days, (d, which) -> {
                    int day = which + 1;
                    long cycleStart = getCycleStart(day);
                    AppExecutors.db().execute(() ->
                            db.creditCardDao().updateBillingCycle(
                                    card.id, day, cycleStart, System.currentTimeMillis()));
                    Toast.makeText(this, "Billing day set to " + days[which], Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void showStatementDialog(CreditCard card) {
        EditText et = new EditText(this);
        et.setHint("Statement amount (₹)");
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (card.statementAmount > 0) et.setText(String.valueOf((int) card.statementAmount));

        new AlertDialog.Builder(this, R.style.AlertDialogDark)
                .setTitle("Update statement balance")
                .setView(et)
                .setPositiveButton("Save", (d, w) -> {
                    String val = et.getText().toString().trim();
                    if (!val.isEmpty()) {
                        double amt = Double.parseDouble(val);
                        AppExecutors.db().execute(() ->
                                db.creditCardDao().updateStatement(card.id, amt, System.currentTimeMillis()));
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDelete(CreditCard card) {
        new AlertDialog.Builder(this, R.style.AlertDialogDark)
                .setTitle("Delete card?")
                .setMessage("Remove " + card.cardLabel + "? This cannot be undone.")
                .setPositiveButton("Delete", (d, w) ->
                        AppExecutors.db().execute(() -> db.creditCardDao().delete(card)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Returns the start timestamp of the billing cycle for a given billing day. */
    private long getCycleStart(int billingDay) {
        Calendar c = Calendar.getInstance();
        int today = c.get(Calendar.DAY_OF_MONTH);
        if (today >= billingDay) {
            c.set(Calendar.DAY_OF_MONTH, billingDay);
        } else {
            c.add(Calendar.MONTH, -1);
            c.set(Calendar.DAY_OF_MONTH, billingDay);
        }
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private long getMonthStart(long ts) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ts);
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
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
