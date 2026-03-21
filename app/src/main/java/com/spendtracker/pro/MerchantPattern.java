package com.spendtracker.pro;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

/**
 * MerchantPattern — Room entity (table: merchant_patterns)
 *
 * Per-merchant frequency maps, rolling averages, and hints.
 * Powers Layer 3 of SmartTransactionAgent — the fast, personalised lookup
 * that delivers payment method, amount hints, and notes alongside the TFLite
 * category suggestion.
 *
 * Added in DB migration v5 → v6.
 */
@Entity(tableName = "merchant_patterns")
public class MerchantPattern {

    @PrimaryKey @NonNull
    public String merchant = "";     // normalised key (lowercase, stripped)

    public String displayName;       // user-visible name (most recent)

    public String topCategory;       // most-frequently used category
    public String categoryFreqJson;  // JSON: {"🍔 Food":8, "🛒 Groceries":1}

    public String topPayment;        // most-frequently used payment method
    public String paymentFreqJson;   // JSON: {"UPI":5, "Credit Card":2}

    public double avgAmount;         // rolling average debit amount (EMA)
    public String lastNotes;         // last non-empty notes entered

    public int    frequency;         // total learned observations
    public long   lastUsed;          // epoch ms of most recent transaction
}
