package com.spendtracker.pro;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import java.util.List;

/**
 * SharedViewModel — P3 single source of truth for transaction list.
 *
 * Fragments call getAllTransactions().observe(getViewLifecycleOwner(), ...)
 * so the Room LiveData pipeline is shared across tabs with no duplicate DB hits.
 * The ViewModel survives config changes so the list is never re-fetched on rotation.
 */
public class SharedViewModel extends ViewModel {

    private LiveData<List<Transaction>> allTransactions;
    private AppDatabase db;

    /** Called once from MainActivity before fragments are attached. */
    public void init(AppDatabase db) {
        if (this.db == null) this.db = db;
    }

    /**
     * Returns the single Room-backed LiveData for all transactions.
     * Lazily initialised on first call so init() must be called first.
     */
    public LiveData<List<Transaction>> getAllTransactions() {
        if (allTransactions == null && db != null) {
            allTransactions = db.transactionDao().getAll();
        }
        return allTransactions;
    }
}
