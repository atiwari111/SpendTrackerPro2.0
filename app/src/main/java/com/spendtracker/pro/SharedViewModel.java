package com.spendtracker.pro;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

public class SharedViewModel extends AndroidViewModel {

    private final AppDatabase db;
    private final LiveData<List<Transaction>> allTransactions;

    public SharedViewModel(@NonNull Application application) {
        super(application);
        db = AppDatabase.getInstance(application);
        allTransactions = db.transactionDao().getRecent(5000);
    }

    public LiveData<List<Transaction>> getAllTransactions() {
        return allTransactions;
    }
}

