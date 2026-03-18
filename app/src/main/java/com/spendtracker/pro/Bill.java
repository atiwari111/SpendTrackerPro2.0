package com.spendtracker.pro;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Ignore;

/**
 * Bill — represents a bill/payment obligation detected from SMS or added manually.
 *
 * Status lifecycle:
 *   PENDING  → bill detected, not yet paid
 *   PAID     → payment confirmed (matching debit SMS found)
 *   OVERDUE  → dueDate passed without payment
 */
@Entity(tableName = "bills")
public class Bill {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String name;          // e.g. "Airtel Broadband", "Netflix", "Electricity"
    public String category;      // e.g. "🔌 Bills", "🎬 Entertainment"
    public String icon;          // emoji icon
    public double amount;        // bill amount
    public long   dueDate;       // timestamp of due date
    public long   detectedDate;  // when we first saw this bill
    public String status;        // PENDING / PAID / OVERDUE
    public long   paidDate;      // timestamp when paid (0 if not paid)
    public String sourceSmS;     // raw SMS that triggered this bill
    public boolean isRecurring;  // true = auto-add next cycle
    public String frequency;     // MONTHLY / QUARTERLY / YEARLY
    public String merchantId;    // for dedup: merchant name normalized

    public Bill() {}

    @Ignore
    public Bill(String name, String category, String icon,
                double amount, long dueDate, String status) {
        this.name          = name;
        this.category      = category;
        this.icon          = icon;
        this.amount        = amount;
        this.dueDate       = dueDate;
        this.status        = status;
        this.detectedDate  = System.currentTimeMillis();
        this.paidDate      = 0;
        this.isRecurring   = false;
    }

    public boolean isPending()  { return "PENDING".equals(status); }
    public boolean isPaid()     { return "PAID".equals(status); }
    public boolean isOverdue()  {
        return "PENDING".equals(status) && dueDate > 0
                && dueDate < System.currentTimeMillis();
    }

    /** Days until due (negative = overdue) */
    public int daysUntilDue() {
        if (dueDate <= 0) return Integer.MAX_VALUE;
        long diff = dueDate - System.currentTimeMillis();
        return (int)(diff / (1000L * 60 * 60 * 24));
    }

    public String getStatusLabel() {
        if (isPaid())    return "✅ Paid";
        if (isOverdue()) return "🔴 Overdue";
        int days = daysUntilDue();
        if (days == 0) return "⚠️ Due Today";
        if (days <= 3) return "🟠 Due in " + days + "d";
        return "🟢 Due in " + days + "d";
    }
}
