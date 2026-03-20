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

    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    List<Transaction> getByDateRange(long start, long end);

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE category = :category AND timestamp BETWEEN :start AND :end")
    double getSumForCategoryBetween(String category, long start, long end);

    // ── Scoped queries — use these instead of getAllSync() wherever possible ──

    /** Non-self-transfer spend transactions from the current month only. */
    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :start AND :end AND isSelfTransfer = 0 AND isCredit = 0 ORDER BY timestamp DESC")
    List<Transaction> getSpendingInRange(long start, long end);

    /** Recent N transactions in a given category, for anomaly baseline. */
    @Query("SELECT * FROM transactions WHERE category = :category AND isSelfTransfer = 0 ORDER BY timestamp DESC LIMIT :limit")
    List<Transaction> getRecentByCategory(String category, int limit);

    /** Aggregate monthly spend per category — avoids loading full rows for analytics. */
    @Query("SELECT category, SUM(amount) as amount FROM transactions WHERE timestamp BETWEEN :start AND :end AND isSelfTransfer = 0 AND isCredit = 0 GROUP BY category ORDER BY amount DESC")
    List<CategorySum> getCategorySums(long start, long end);

    /** Exists check — faster than findBySms full table scan. */
    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE smsHash = :hash)")
    boolean existsHash(String hash);

    /**
     * P2 recurring auto-detection: returns all non-credit, non-self-transfer transactions
     * from the last 90 days, ordered by merchant then timestamp.
     * RecurringDetector groups these in Java to find repeated merchant+amount patterns.
     */
    @Query("SELECT * FROM transactions WHERE isCredit = 0 AND isSelfTransfer = 0 " +
           "AND timestamp >= :since ORDER BY merchant ASC, timestamp DESC")
    List<Transaction> getNonCreditSince(long since);

}
