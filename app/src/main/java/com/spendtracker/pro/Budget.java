package com.spendtracker.pro;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "budgets")
public class Budget {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String category;
    public double limitAmount;
    public double usedAmount;
    public int month; // 1-12
    public int year;
    public String icon;
    public int color; // ARGB int

    public Budget() {}

    @Ignore
    public Budget(String category, double limitAmount, int month, int year, String icon, int color) {
        this.category = category;
        this.limitAmount = limitAmount;
        this.month = month;
        this.year = year;
        this.icon = icon;
        this.color = color;
    }

    public float getProgress() {
        if (limitAmount <= 0) return 0f;
        return (float) Math.min(usedAmount / limitAmount, 1.0);
    }

    public boolean isOverBudget() { return usedAmount > limitAmount; }

    public double getRemaining() { return limitAmount - usedAmount; }

    public String getStatusEmoji() {
        float p = getProgress();
        if (p >= 1.0f) return "🔴";
        if (p >= 0.8f) return "🟠";
        if (p >= 0.5f) return "🟡";
        return "🟢";
    }
}
