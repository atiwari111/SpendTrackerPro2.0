package com.spendtracker.pro;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import java.util.List;

@Dao
public interface RecurringDao {
    @Insert long insert(RecurringTransaction r);
    @Update void update(RecurringTransaction r);
    @Delete void delete(RecurringTransaction r);
    @Query("SELECT * FROM recurring WHERE isActive = 1 ORDER BY nextDueDate ASC")
    LiveData<List<RecurringTransaction>> getActive();
    @Query("SELECT * FROM recurring WHERE isActive = 1 ORDER BY nextDueDate ASC")
    List<RecurringTransaction> getActiveSync();
    @Query("SELECT * FROM recurring WHERE nextDueDate <= :timestamp AND isActive = 1")
    List<RecurringTransaction> getDue(long timestamp);

    @androidx.room.Query("DELETE FROM recurring")
    void deleteAll();
}
