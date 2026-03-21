package com.spendtracker.pro;

import java.util.Locale;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * BankAccount — stores a user's bank account with masked account number and balance.
 *
 * Only the last 4 digits of the account number are ever persisted.
 * Balance is updated manually by the user or inferred from SMS credit/debit patterns.
 */
@Entity(tableName = "bank_accounts")
public class BankAccount {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String bankName;      // e.g. "SBI", "ICICI", "HDFC", "PNB"
    public String accountLabel;  // user label e.g. "Salary Account"
    public String lastFour;      // last 4 digits of account number
    public String accountType;   // SAVINGS / CURRENT / SALARY / RD / FD
    public double balance;       // current balance
    public long   updatedAt;     // last updated timestamp
    public int    cardColor;     // ARGB background color for the card
    public boolean isActive;     // false = hidden from total

    // Bank logo / emoji for display
    public String bankEmoji;     // e.g. "🏦", or bank-specific emoji

    public BankAccount() {}

    @Ignore
    public BankAccount(String bankName, String accountLabel, String lastFour,
                       String accountType, double balance, int cardColor) {
        this.bankName     = bankName;
        this.accountLabel = accountLabel;
        this.lastFour     = lastFour;
        this.accountType  = accountType;
        this.balance      = balance;
        this.cardColor    = cardColor;
        this.isActive     = true;
        this.updatedAt    = System.currentTimeMillis();
    }

    /** Masked account number display e.g. "A/c No: 2253" */
    public String getMaskedAccount() {
        return "A/c No: " + (lastFour != null ? lastFour : "????");
    }

    /** Short bank identifier for logo lookup */
    public String getBankKey() {
        if (bankName == null) return "";
        return bankName.toUpperCase(Locale.ROOT).trim();
    }
}
