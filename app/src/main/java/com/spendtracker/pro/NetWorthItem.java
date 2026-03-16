package com.spendtracker.pro;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "net_worth")
public class NetWorthItem {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String name;
    public double amount;
    public String type;   // ASSET or LIABILITY
    public String icon;
    public long updatedAt;

    public NetWorthItem() {}

    @Ignore
    public NetWorthItem(String name, double amount, String type, String icon) {
        this.name = name;
        this.amount = amount;
        this.type = type;
        this.icon = icon;
        this.updatedAt = System.currentTimeMillis();
    }
}
