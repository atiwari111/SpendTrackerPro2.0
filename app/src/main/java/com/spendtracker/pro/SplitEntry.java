package com.spendtracker.pro;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * P5: SplitEntry — one person's share of a split transaction.
 *
 * Each row links to a Transaction via transactionId and tracks how much
 * that contact owes and whether they've paid.
 */
@Entity(
    tableName = "split_entries",
    indices = { @Index("transactionId") }
)
public class SplitEntry {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public int    transactionId;  // FK to transactions.id (not enforced by Room)
    public String contactName;    // person's name or phone
    public double amountOwed;     // their share of the total
    public boolean isPaid;        // have they paid back?
    public long   createdAt;
    public long   paidAt;         // 0 if not yet paid
    public String notes;

    public SplitEntry() {}
}
