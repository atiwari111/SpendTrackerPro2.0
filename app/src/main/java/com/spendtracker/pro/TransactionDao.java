package com.spendtracker.pro;

import androidx.lifecycle.LiveData;
import androidx.room.*;

import java.util.List;

@Dao
public interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(Transaction transaction);

    @Update
    void update(Transaction transaction);

    @Delete
    void delete(Transaction transaction);

    @Query("DELETE FROM transactions")
    void deleteAll();

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    LiveData<List<Transaction>> getAll();

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT :limit")
    LiveData<List<Transaction>> getRecent(int limit);

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    List<Transaction> getAllSync();

    @Query("SELECT COUNT(*) FROM transactions")
    LiveData<Integer> getTotalCount();

    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :start AND :end")
    List<Transaction> getByDateRange(long start, long end);

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE category = :category AND timestamp BETWEEN :start AND :end")
    double getSumForCategoryBetween(String category, long start, long end);

    @Query("SELECT * FROM transactions WHERE rawSms = :sms LIMIT 1")
    Transaction findBySms(String sms);

    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE smsHash = :hash)")
    boolean existsHash(String hash);
}
