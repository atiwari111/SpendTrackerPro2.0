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
import java.util.concurrent.Executors;

public class RecurringActivity extends AppCompatActivity {
    private RecyclerView rv;
    private RecurringAdapter adapter;
    private AppDatabase db;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_recurring);
        db = AppDatabase.getInstance(this);
        Toolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) { getSupportActionBar().setDisplayHomeAsUpEnabled(true); getSupportActionBar().setTitle("Recurring Bills"); }

        rv = findViewById(R.id.rv);
        adapter = new RecurringAdapter(r -> showEditDialog(r));
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        db.recurringDao().getActive().observe(this, list -> {
            if (list != null) adapter.setItems(list);
        });

        findViewById(R.id.btnAdd).setOnClickListener(v -> showAddDialog());
    }

    private void showAddDialog() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_recurring, null);
        EditText etName = v.findViewById(R.id.etName);
        EditText etAmount = v.findViewById(R.id.etAmount);
        EditText etDay = v.findViewById(R.id.etDayOfMonth);
        Spinner spCategory = v.findViewById(R.id.spCategory);
        Spinner spFreq = v.findViewById(R.id.spFrequency);
        spCategory.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, CategoryEngine.getCategoryNames()));
        spFreq.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"MONTHLY", "WEEKLY", "YEARLY"}));

        new AlertDialog.Builder(this, R.style.AlertDialogDark)
                .setTitle("Add Recurring Bill")
                .setView(v)
                .setPositiveButton("Save", (d, w) -> {
                    String name = etName.getText().toString().trim();
                    String amtStr = etAmount.getText().toString().trim();
                    String dayStr = etDay.getText().toString().trim();
                    if (name.isEmpty() || amtStr.isEmpty()) return;
                    try {
                        RecurringTransaction r = new RecurringTransaction();
                        r.name = name;
                        r.amount = Double.parseDouble(amtStr);
                        r.category = (String) spCategory.getSelectedItem();
                        r.icon = CategoryEngine.getInfo(r.category).icon;
                        r.frequency = (String) spFreq.getSelectedItem();
                        r.dayOfMonth = dayStr.isEmpty() ? 1 : Integer.parseInt(dayStr);
                        r.isActive = true;
                        r.nextDueDate = calcNextDue(r.dayOfMonth, r.frequency);
                        AppExecutors.db().execute(() -> {
                            long insertedId = db.recurringDao().insert(r);
                            // Use the real DB-assigned ID so each alarm has a unique requestCode
                            r.id = (int) insertedId;
                            BootReceiver.scheduleReminder(this, r);
                        });
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void showEditDialog(RecurringTransaction r) {
        new AlertDialog.Builder(this, R.style.AlertDialogDark)
                .setTitle(r.name)
                .setMessage(String.format(Locale.getDefault(), "₹%.0f %s\nNext due: %s", r.amount, r.frequency, new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(new Date(r.nextDueDate))))
                .setPositiveButton("Deactivate", (d, w) -> { r.isActive = false; AppExecutors.db().execute(() -> db.recurringDao().update(r)); })
                .setNegativeButton("Delete", (d, w) -> AppExecutors.db().execute(() -> db.recurringDao().delete(r)))
                .setNeutralButton("Close", null).show();
    }

    private long calcNextDue(int day, String freq) {
        Calendar c = Calendar.getInstance();
        if ("MONTHLY".equals(freq)) {
            c.set(Calendar.DAY_OF_MONTH, Math.min(day, c.getActualMaximum(Calendar.DAY_OF_MONTH)));
            if (c.getTimeInMillis() <= System.currentTimeMillis()) c.add(Calendar.MONTH, 1);
        } else if ("WEEKLY".equals(freq)) {
            c.add(Calendar.WEEK_OF_YEAR, 1);
        } else {
            c.add(Calendar.YEAR, 1);
        }
        return c.getTimeInMillis();
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { getOnBackPressedDispatcher().onBackPressed(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
