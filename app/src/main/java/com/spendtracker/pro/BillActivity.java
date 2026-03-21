package com.spendtracker.pro;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;

public class BillActivity extends AppCompatActivity {

    private RecyclerView rvPending, rvPaid, rvRecurring;
    private TextView tvPendingCount, tvPaidCount, tvEmptyPending;
    private AppDatabase db;
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_bill);
        db = AppDatabase.getInstance(this);

        Toolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Bills");
        }

        tvPendingCount = findViewById(R.id.tvPendingCount);
        tvPaidCount    = findViewById(R.id.tvPaidCount);
        tvEmptyPending = findViewById(R.id.tvEmptyPending);
        rvPending      = findViewById(R.id.rvPending);
        rvPaid         = findViewById(R.id.rvPaid);

        rvPending.setLayoutManager(new LinearLayoutManager(this));
        rvPaid.setLayoutManager(new LinearLayoutManager(this));

        // Update overdue status every time screen opens
        AppExecutors.db().execute(() ->
                db.billDao().markOverdue(System.currentTimeMillis()));

        observeBills();

        findViewById(R.id.btnAddBill).setOnClickListener(v -> showAddBillDialog());
        findViewById(R.id.btnScanBills).setOnClickListener(v -> scanSmsForBills());
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppExecutors.db().execute(() ->
                db.billDao().markOverdue(System.currentTimeMillis()));
    }

    private void observeBills() {
        // Pending bills
        db.billDao().getPending().observe(this, pending -> {
            if (pending == null) pending = new ArrayList<>();
            final List<Bill> list = pending;
            boolean empty = list.isEmpty();
            tvEmptyPending.setVisibility(empty ? View.VISIBLE : View.GONE);
            rvPending.setVisibility(empty ? View.GONE : View.VISIBLE);
            tvPendingCount.setText(list.size() + " pending");

            BillAdapter adapter = new BillAdapter(list, bill -> showBillOptions(bill));
            rvPending.setAdapter(adapter);
        });

        // Paid bills
        db.billDao().getPaid().observe(this, paid -> {
            if (paid == null) paid = new ArrayList<>();
            tvPaidCount.setText(paid.size() + " paid");
            BillAdapter adapter = new BillAdapter(paid, bill -> showBillOptions(bill));
            rvPaid.setAdapter(adapter);
        });
    }

    private void showBillOptions(Bill bill) {
        String[] options;
        if (bill.isPending() || bill.isOverdue()) {
            options = new String[]{"✅ Mark as Paid", "✏️ Edit", "🗑️ Delete"};
        } else {
            options = new String[]{"✏️ Edit", "🗑️ Delete"};
        }

        new AlertDialog.Builder(this, R.style.AlertDialogDark)
                .setTitle(bill.name)
                .setItems(options, (d, which) -> {
                    if (bill.isPending() || bill.isOverdue()) {
                        if (which == 0) markPaid(bill);
                        else if (which == 1) showEditBillDialog(bill);
                        else confirmDelete(bill);
                    } else {
                        if (which == 0) showEditBillDialog(bill);
                        else confirmDelete(bill);
                    }
                }).show();
    }

    private void markPaid(Bill bill) {
        bill.status   = "PAID";
        bill.paidDate = System.currentTimeMillis();
        AppExecutors.db().execute(() -> db.billDao().update(bill));

        // Schedule next occurrence if recurring
        if (bill.isRecurring && bill.frequency != null) {
            AppExecutors.db().execute(() -> {
                Bill next = new Bill(bill.name, bill.category, bill.icon,
                        bill.amount, nextDueDate(bill), "PENDING");
                next.isRecurring = true;
                next.frequency   = bill.frequency;
                next.merchantId  = bill.merchantId;
                db.billDao().insert(next);
            });
        }
    }

    private long nextDueDate(Bill bill) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(bill.dueDate > 0 ? bill.dueDate : System.currentTimeMillis());
        if ("YEARLY".equals(bill.frequency))       c.add(Calendar.YEAR, 1);
        else if ("QUARTERLY".equals(bill.frequency)) c.add(Calendar.MONTH, 3);
        else                                          c.add(Calendar.MONTH, 1);
        return c.getTimeInMillis();
    }

    private void confirmDelete(Bill bill) {
        new AlertDialog.Builder(this, R.style.AlertDialogDark)
                .setTitle("Delete Bill")
                .setMessage("Delete \"" + bill.name + "\"?")
                .setPositiveButton("Delete", (d, w) ->
                        AppExecutors.db().execute(() -> db.billDao().delete(bill)))
                .setNegativeButton("Cancel", null).show();
    }

    private void showAddBillDialog() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_add_bill, null);
        EditText etName    = v.findViewById(R.id.etBillName);
        EditText etAmount  = v.findViewById(R.id.etBillAmount);
        EditText etDueDate = v.findViewById(R.id.etBillDueDate);
        Spinner  spCat     = v.findViewById(R.id.spBillCategory);
        CheckBox cbRecurring = v.findViewById(R.id.cbRecurring);
        Spinner  spFreq    = v.findViewById(R.id.spFrequency);

        String[] cats = CategoryEngine.getCategoryNames();
        spCat.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, cats));
        spFreq.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"MONTHLY","QUARTERLY","YEARLY"}));

        new AlertDialog.Builder(this, R.style.AlertDialogDark)
                .setTitle("Add Bill")
                .setView(v)
                .setPositiveButton("Save", (d, w) -> {
                    String name    = etName.getText().toString().trim();
                    String amtStr  = etAmount.getText().toString().trim();
                    String dateStr = etDueDate.getText().toString().trim();
                    if (name.isEmpty() || amtStr.isEmpty()) return;
                    double amt;
                    try { amt = Double.parseDouble(amtStr); }
                    catch (NumberFormatException e) {
                        android.widget.Toast.makeText(this, "Invalid amount: " + amtStr, android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        String cat     = (String) spCat.getSelectedItem();
                        long dueDate   = parseInputDate(dateStr);
                        Bill bill      = new Bill(name, cat,
                                CategoryEngine.getInfo(cat).icon, amt, dueDate, "PENDING");
                        bill.isRecurring = cbRecurring.isChecked();
                        bill.frequency   = (String) spFreq.getSelectedItem();
                        bill.merchantId  = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");
                        AppExecutors.db().execute(() -> db.billDao().insert(bill));
                    } catch (Exception e) {
                        android.util.Log.e("BillActivity", "Add bill DB insert failed: " + e.getMessage());
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void showEditBillDialog(Bill bill) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_add_bill, null);
        EditText etName    = v.findViewById(R.id.etBillName);
        EditText etAmount  = v.findViewById(R.id.etBillAmount);
        EditText etDueDate = v.findViewById(R.id.etBillDueDate);
        CheckBox cbRecurring = v.findViewById(R.id.cbRecurring);

        etName.setText(bill.name);
        etAmount.setText(String.valueOf((int) bill.amount));
        if (bill.dueDate > 0)
            etDueDate.setText(dateFmt.format(new Date(bill.dueDate)));
        cbRecurring.setChecked(bill.isRecurring);

        new AlertDialog.Builder(this, R.style.AlertDialogDark)
                .setTitle("Edit Bill")
                .setView(v)
                .setPositiveButton("Update", (d, w) -> {
                    bill.name = etName.getText().toString().trim();
                    try { bill.amount = Double.parseDouble(etAmount.getText().toString()); }
                    catch (NumberFormatException e) {
                        android.util.Log.w("BillActivity", "Edit bill: invalid amount '" + etAmount.getText() + "'");
                    }
                    bill.isRecurring = cbRecurring.isChecked();
                    String dateStr   = etDueDate.getText().toString().trim();
                    if (!dateStr.isEmpty()) bill.dueDate = parseInputDate(dateStr);
                    AppExecutors.db().execute(() -> db.billDao().update(bill));
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void scanSmsForBills() {
        Toast.makeText(this, "Scanning SMS for bills...", Toast.LENGTH_SHORT).show();
        AppExecutors.db().execute(() -> {
            int found = 0;
            try {
                android.database.Cursor cursor = getContentResolver().query(
                        android.provider.Telephony.Sms.Inbox.CONTENT_URI,
                        new String[]{"body", "address", "date"},
                        "date >= ?",
                        new String[]{String.valueOf(System.currentTimeMillis() - 90L * 86400000L)},
                        "date DESC");

                if (cursor != null) {
                    int colBody   = cursor.getColumnIndexOrThrow("body");
                    int colAddr   = cursor.getColumnIndexOrThrow("address");
                    while (cursor.moveToNext()) {
                        String body   = cursor.getString(colBody);
                        String sender = cursor.getString(colAddr);
                        BillSmsDetector.BillSmsResult result =
                                BillSmsDetector.detect(body, sender);
                        if (result != null) {
                            // Avoid duplicate pending bills for same merchant
                            Bill existing = db.billDao().findPendingByMerchant(result.merchantId);
                            if (existing == null) {
                                Bill bill = new Bill(result.name, result.category, result.icon,
                                        result.amount, result.dueDate, result.status);
                                bill.isRecurring = result.isRecurring;
                                bill.merchantId  = result.merchantId;
                                bill.sourceSmS   = body;
                                db.billDao().insert(bill);
                                found++;
                            }
                        }
                    }
                    cursor.close();
                }
            } catch (Exception e) {
                android.util.Log.e("BillActivity", "Scan error: " + e.getMessage());
            }
            final int count = found;
            runOnUiThread(() -> Toast.makeText(this,
                    count > 0 ? "Found " + count + " bills!" : "No new bills found",
                    Toast.LENGTH_SHORT).show());
        });
    }

    private long parseInputDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return 0;
        String[] formats = {"dd MMM yyyy","dd/MM/yyyy","dd-MM-yyyy","dd MMM yy"};
        for (String fmt : formats) {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(fmt, Locale.ENGLISH);
                java.util.Date d = sdf.parse(dateStr.trim());
                if (d != null) return d.getTime();
            } catch (java.text.ParseException ignored) {
                // Expected — trying next format
            }
        }
        return 0;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed(); return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
