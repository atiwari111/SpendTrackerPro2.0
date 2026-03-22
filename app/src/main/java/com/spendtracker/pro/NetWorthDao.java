package com.spendtracker.pro;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import java.util.List;

@Dao
public interface NetWorthDao {
    @Insert void insert(NetWorthItem i);
    @Update void update(NetWorthItem i);
    @Delete void delete(NetWorthItem i);
    @Query("SELECT * FROM net_worth ORDER BY type, name")
    LiveData<List<NetWorthItem>> getAll();
    @Query("SELECT * FROM net_worth ORDER BY type, name")
    List<NetWorthItem> getAllSync();
    @Query("SELECT COALESCE(SUM(amount), 0) FROM net_worth WHERE type = 'ASSET'")
    double getTotalAssets();
    @Query("SELECT COALESCE(SUM(amount), 0) FROM net_worth WHERE type = 'LIABILITY'")
    double getTotalLiabilities();

    @androidx.room.Query("DELETE FROM net_worth")
    void deleteAll();
}
