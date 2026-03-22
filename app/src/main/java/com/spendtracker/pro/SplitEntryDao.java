package com.spendtracker.pro;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import java.util.List;

@Dao
public interface SplitEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(SplitEntry entry);

    @Update
    void update(SplitEntry entry);

    @Delete
    void delete(SplitEntry entry);

    @Query("SELECT * FROM split_entries WHERE transactionId = :txnId ORDER BY createdAt ASC")
    List<SplitEntry> getForTransaction(int txnId);

    @Query("SELECT * FROM split_entries WHERE transactionId = :txnId ORDER BY createdAt ASC")
    LiveData<List<SplitEntry>> observeForTransaction(int txnId);

    @Query("SELECT * FROM split_entries WHERE isPaid = 0 ORDER BY createdAt DESC")
    LiveData<List<SplitEntry>> getPendingSettlements();

    @Query("SELECT COALESCE(SUM(amountOwed), 0) FROM split_entries WHERE isPaid = 0")
    LiveData<Double> getTotalOwed();

    @Query("UPDATE split_entries SET isPaid = 1, paidAt = :paidAt WHERE id = :id")
    void markPaid(int id, long paidAt);

    @androidx.room.Query("DELETE FROM split_entries")
    void deleteAll();
}
