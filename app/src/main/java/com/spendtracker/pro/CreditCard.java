package com.spendtracker.pro;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * CreditCard — stores a user's credit card with masked number, limit, billing cycle,
 * and a running spent amount computed from the transactions table.
 *
 * cardColor is an ARGB int stored as INTEGER for RecyclerView rendering.
 * lastFour is the only part of the card number ever persisted — full PAN is never stored.
 */
@Entity(tableName = "credit_cards")
public class CreditCard {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String bankName;       // e.g. "HDFC", "SBI", "ICICI"
    public String cardLabel;      // user-facing label e.g. "HDFC Credit"
    public String lastFour;       // last 4 digits of card number
    public String network;        // VISA / MASTERCARD / RUPAY / AMEX
    public double creditLimit;    // total credit limit (0 = unknown)
    public double currentSpent;   // spent this billing cycle (updated on observe)
    public double statementAmount;// last statement balance (0 = not available)
    public int    billingDay;     // day of month billing cycle resets (1-31, 0 = not set)
    public long   billingCycleStart; // timestamp of current cycle start
    public long   updatedAt;
    public int    cardColor;      // ARGB card background color

    public CreditCard() {}

    @Ignore
    public CreditCard(String bankName, String cardLabel, String lastFour,
                      String network, double creditLimit, int billingDay, int cardColor) {
        this.bankName     = bankName;
        this.cardLabel    = cardLabel;
        this.lastFour     = lastFour;
        this.network      = network;
        this.creditLimit  = creditLimit;
        this.billingDay   = billingDay;
        this.cardColor    = cardColor;
        this.updatedAt    = System.currentTimeMillis();
    }

    /** Utilisation as a 0–1 fraction. Returns 0 if limit is unknown. */
    public float getUtilisation() {
        if (creditLimit <= 0) return 0f;
        return (float) Math.min(currentSpent / creditLimit, 1.0);
    }

    /** Formatted masked card number e.g. "XXXX XXXX XXXX 6956" */
    public String getMaskedNumber() {
        String last = (lastFour != null && lastFour.length() == 4) ? lastFour : "????";
        return "XXXX XXXX XXXX " + last;
    }

    /** Available credit. Returns -1 if limit unknown. */
    public double getAvailableCredit() {
        if (creditLimit <= 0) return -1;
        return creditLimit - currentSpent;
    }
}
