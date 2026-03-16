package com.spendtracker.pro;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "recurring")
public class RecurringTransaction {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String name;
    public double amount;
    public String category;
    public String icon;
    public String frequency; // MONTHLY, WEEKLY, YEARLY
    public int dayOfMonth;   // 1-31 for monthly
    public long nextDueDate;
    public boolean isActive;
    public String paymentMethod;
    public String notes;

    public RecurringTransaction() {}
}
