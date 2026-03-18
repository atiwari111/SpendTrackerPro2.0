package com.spendtracker.pro;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Index;

@Entity(
        tableName = "transactions",
        indices = {
                @Index(value = {"timestamp"}),
                // smsHash is unique for SMS-imported rows; manual rows get a UUID so
                // the unique constraint never blocks multiple manual inserts.
                @Index(value = {"smsHash"}, unique = true)
        }
)
public class Transaction {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public double amount;

    public String merchant;

    public String category;

    public String categoryIcon;

    public String paymentMethod;

    public String paymentDetail;

    public String notes;

    public long timestamp;

    public boolean isManual;

    public boolean isSelfTransfer;

    public String rawSms;

    public String smsAddress;

    // For SMS rows: SHA-256 of (body+date). For manual/CSV rows: "csv_" + UUID.
    public String smsHash;

    // true = income (salary/cashback/refund/dividend), false = expense (debit)
    public boolean isCredit;
}
