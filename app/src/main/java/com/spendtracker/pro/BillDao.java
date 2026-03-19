package com.spendtracker.pro;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import java.util.List;

@Dao
public interface BillDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(Bill b);

    @Update void update(Bill b);
    @Delete void delete(Bill b);

    @Query("SELECT * FROM bills ORDER BY dueDate ASC")
    LiveData<List<Bill>> getAll();

    @Query("SELECT * FROM bills ORDER BY dueDate ASC")
    List<Bill> getAllSync();

    @Query("SELECT * FROM bills WHERE status = 'PENDING' ORDER BY dueDate ASC")
    LiveData<List<Bill>> getPending();

    @Query("SELECT * FROM bills WHERE status = 'PAID' ORDER BY paidDate DESC")
    LiveData<List<Bill>> getPaid();

    @Query("SELECT * FROM bills WHERE status = 'PENDING' AND dueDate BETWEEN :start AND :end")
    List<Bill> getDueInRange(long start, long end);

    @Query("SELECT * FROM bills WHERE merchantId = :merchantId AND status = 'PENDING' LIMIT 1")
    Bill findPendingByMerchant(String merchantId);

    // Fix 2.7: dedup credit card bills by amount — same card + same amount = same statement
    @Query("SELECT * FROM bills WHERE merchantId = :merchantId AND ABS(amount - :amount) < 1.0 AND status = 'PENDING' LIMIT 1")
    Bill findByMerchantAndAmount(String merchantId, double amount);

    @Query("SELECT COUNT(*) FROM bills WHERE status = 'PENDING'")
    LiveData<Integer> getPendingCount();

    @Query("UPDATE bills SET status = 'OVERDUE' WHERE status = 'PENDING' AND dueDate < :now")
    void markOverdue(long now);
}
