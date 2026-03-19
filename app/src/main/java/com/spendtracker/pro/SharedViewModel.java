package com.spendtracker.pro;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

/**
 * Shared transactions stream for dashboard surfaces.
 */
public class SharedViewModel extends AndroidViewModel {

    private final LiveData<List<Transaction>> allTransactions;

    public SharedViewModel(@NonNull Application app) {
        super(app);
        AppDatabase db = AppDatabase.getInstance(app);
        allTransactions = db.transactionDao().getRecent(5000);
    }

    public LiveData<List<Transaction>> getAllTransactions() {
        return allTransactions;
    }
}
