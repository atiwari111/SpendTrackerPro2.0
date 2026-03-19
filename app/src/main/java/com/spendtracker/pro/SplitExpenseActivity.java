package com.spendtracker.pro;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.*;
import com.google.android.material.textfield.TextInputEditText;
import java.util.*;

/**
 * P5: Split Expense screen.
 *
 * Launched from TransactionAdapter long-press or a "Split" button on the
 * transaction detail. Receives EXTRA_TXN_ID — loads that transaction,
 * shows existing splits, and lets the user add more contacts + amounts.
 *
 * Equal-split shortcut divides the remaining unallocated amount evenly
 * across however many names the user enters.
 */
public class SplitExpenseActivity extends AppCompatActivity {

    public static final String EXTRA_TXN_ID = "split_txn_id";

    private TextView tvTxnSummary, tvTxnAmount, tvYourShare, tvTotalOwed;
    private TextInputEditText etContactName, etSplitAmount;
    private RecyclerView rvSplits;
    private SplitAdapter adapter;
    private AppDatabase db;
    private Transaction txn;
    private final List<SplitEntry> entries = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_split_expense);
        db = AppDatabase.getInstance(this);

        Toolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Split Expense");
        }

        tvTxnSummary  = findViewById(R.id.tvTxnSummary);
        tvTxnAmount   = findViewById(R.id.tvTxnAmount);
        tvYourShare   = findViewById(R.id.tvYourShare);
        tvTotalOwed   = findViewById(R.id.tvTotalOwed);
        etContactName = findViewById(R.id.etContactName);
        etSplitAmount = findViewById(R.id.etSplitAmount);
        rvSplits      = findViewById(R.id.rvSplits);

        adapter = new SplitAdapter(entries, this::onMarkPaid, this::onDeleteSplit);
        rvSplits.setLayoutManager(new LinearLayoutManager(this));
        rvSplits.setAdapter(adapter);

        int txnId = getIntent().getIntExtra(EXTRA_TXN_ID, -1);
        if (txnId == -1) { finish(); return; }

        AppExecutors.db().execute(() -> {
            List<Transaction> all = db.transactionDao().getAllSync();
            for (Transaction t : all) {
                if (t.id == txnId) { txn = t; break; }
            }
            if (txn == null) { runOnUiThread(this::finish); return; }

            List<SplitEntry> existing = db.splitEntryDao().getForTransaction(txnId);
            entries.addAll(existing);

            runOnUiThread(() -> {
                tvTxnSummary.setText(txn.merchant != null ? txn.merchant : "Transaction");
                tvTxnAmount.setText(String.format("₹%.2f", txn.amount));
                updateSummary();
                adapter.notifyDataSetChanged();
            });
        });

        findViewById(R.id.btnAddSplit).setOnClickListener(v -> addSplit());
        findViewById(R.id.btnEqualSplit).setOnClickListener(v -> suggestEqualSplit());
    }

    private void addSplit() {
        if (txn == null) return;
        String name = etContactName.getText() != null
                ? etContactName.getText().toString().trim() : "";
        String amtStr = etSplitAmount.getText() != null
                ? etSplitAmount.getText().toString().trim() : "";

        if (name.isEmpty()) { etContactName.setError("Enter a name"); return; }

        double amount;
        try {
            amount = Double.parseDouble(amtStr);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            etSplitAmount.setError("Enter a valid amount");
            return;
        }

        SplitEntry entry = new SplitEntry();
        entry.transactionId = txn.id;
        entry.contactName   = name;
        entry.amountOwed    = amount;
        entry.isPaid        = false;
        entry.createdAt     = System.currentTimeMillis();

        AppExecutors.db().execute(() -> {
            db.splitEntryDao().insert(entry);
            // Reload from DB to get the auto-generated id
            List<SplitEntry> updated = db.splitEntryDao().getForTransaction(txn.id);
            runOnUiThread(() -> {
                entries.clear();
                entries.addAll(updated);
                adapter.notifyDataSetChanged();
                etContactName.setText("");
                etSplitAmount.setText("");
                updateSummary();
            });
        });
    }

    private void suggestEqualSplit() {
        if (txn == null) return;
        String name = etContactName.getText() != null
                ? etContactName.getText().toString().trim() : "";
        if (name.isEmpty()) { etContactName.setError("Enter contact name first"); return; }

        // Remaining = total - already allocated
        double allocated = 0;
        for (SplitEntry e : entries) allocated += e.amountOwed;
        double remaining = txn.amount - allocated;
        if (remaining <= 0) {
            Toast.makeText(this, "Total already fully split", Toast.LENGTH_SHORT).show();
            return;
        }
        // Split remaining equally between user + this contact (÷ 2)
        double share = Math.round(remaining / 2.0 * 100.0) / 100.0;
        etSplitAmount.setText(String.format("%.2f", share));
    }

    private void onMarkPaid(SplitEntry entry) {
        entry.isPaid = true;
        entry.paidAt = System.currentTimeMillis();
        AppExecutors.db().execute(() -> {
            db.splitEntryDao().markPaid(entry.id, entry.paidAt);
            runOnUiThread(() -> {
                adapter.notifyDataSetChanged();
                updateSummary();
            });
        });
    }

    private void onDeleteSplit(SplitEntry entry) {
        new androidx.appcompat.app.AlertDialog.Builder(this, R.style.AlertDialogDark)
                .setTitle("Remove split?")
                .setMessage(entry.contactName + " owes ₹" +
                        String.format("%.2f", entry.amountOwed))
                .setPositiveButton("Remove", (d, w) -> {
                    AppExecutors.db().execute(() -> {
                        db.splitEntryDao().delete(entry);
                        entries.remove(entry);
                        runOnUiThread(() -> {
                            adapter.notifyDataSetChanged();
                            updateSummary();
                        });
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateSummary() {
        if (txn == null) return;
        double totalOwed = 0, allocated = 0;
        for (SplitEntry e : entries) {
            allocated += e.amountOwed;
            if (!e.isPaid) totalOwed += e.amountOwed;
        }
        double yourShare = txn.amount - allocated;
        tvYourShare.setText(String.format("Your share: ₹%.2f  |  Allocated: ₹%.2f",
                yourShare, allocated));
        tvTotalOwed.setText(String.format("₹%.2f", totalOwed));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed(); return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ── Inline adapter ────────────────────────────────────────────

    interface Action<T> { void run(T item); }

    static class SplitAdapter extends RecyclerView.Adapter<SplitAdapter.VH> {
        private final List<SplitEntry> list;
        private final Action<SplitEntry> onPaid, onDelete;

        SplitAdapter(List<SplitEntry> list, Action<SplitEntry> onPaid,
                     Action<SplitEntry> onDelete) {
            this.list = list; this.onPaid = onPaid; this.onDelete = onDelete;
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            SplitEntry e = list.get(pos);
            h.text1.setText(e.contactName + "  ₹" + String.format("%.2f", e.amountOwed));
            h.text1.setTextColor(e.isPaid ? 0xFF9CA3AF : 0xFFFFFFFF);
            h.text2.setText(e.isPaid ? "✅ Paid" : "⏳ Pending");
            h.text2.setTextColor(e.isPaid ? 0xFF10B981 : 0xFFF59E0B);

            h.itemView.setOnClickListener(v -> {
                if (!e.isPaid) onPaid.run(e);
                else Toast.makeText(v.getContext(), "Already marked paid", Toast.LENGTH_SHORT).show();
            });
            h.itemView.setOnLongClickListener(v -> { onDelete.run(e); return true; });
        }

        @Override public int getItemCount() { return list.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView text1, text2;
            VH(View v) {
                super(v);
                text1 = v.findViewById(android.R.id.text1);
                text2 = v.findViewById(android.R.id.text2);
                text1.setTextColor(0xFFFFFFFF);
                text2.setTextColor(0xFFF59E0B);
            }
        }
    }
}
