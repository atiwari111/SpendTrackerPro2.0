package com.spendtracker.pro;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

/**
 * SharedViewModel
 *
 * Single source of truth for transactions across the app.
 * Uses AndroidViewModel to safely access database without manual init.
 */
public class SharedViewModel extends AndroidViewModel {

    private final LiveData<List<Transaction>> allTransactions;

    public SharedViewModel(@NonNull Application app) {
        super(app);
        AppDatabase db = AppDatabase.getInstance(app);

        // Use recent limit for performance (avoid loading huge dataset)
        allTransactions = db.transactionDao().getRecent(5000);
    }

    public LiveData<List<Transaction>> getAllTransactions() {
        return allTransactions;
    }
}
