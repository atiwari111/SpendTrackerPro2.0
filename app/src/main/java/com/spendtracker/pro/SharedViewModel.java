package com.spendtracker.pro;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

/**

        allTransactions = db.transactionDao().getRecent(5000);
    }

    public LiveData<List<Transaction>> getAllTransactions() {
        return allTransactions;
    }

