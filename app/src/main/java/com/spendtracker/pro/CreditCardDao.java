package com.spendtracker.pro;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import java.util.List;

@Dao
public interface CreditCardDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(CreditCard card);

    @Update
    void update(CreditCard card);

    @Delete
    void delete(CreditCard card);

    @Query("SELECT * FROM credit_cards ORDER BY bankName ASC")
    LiveData<List<CreditCard>> getAll();

    @Query("SELECT * FROM credit_cards ORDER BY bankName ASC")
    List<CreditCard> getAllSync();

    @Query("SELECT * FROM credit_cards WHERE id = :id LIMIT 1")
    CreditCard getById(int id);

    @Query("UPDATE credit_cards SET currentSpent = :spent, updatedAt = :ts WHERE id = :id")
    void updateSpent(int id, double spent, long ts);

    @Query("UPDATE credit_cards SET statementAmount = :amount, updatedAt = :ts WHERE id = :id")
    void updateStatement(int id, double amount, long ts);

    @Query("UPDATE credit_cards SET billingDay = :day, billingCycleStart = :cycleStart, updatedAt = :ts WHERE id = :id")
    void updateBillingCycle(int id, int day, long cycleStart, long ts);

    /** Sum of all credit card spending in a date range, used for cycle recalculation. */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM transactions t WHERE t.paymentMethod = 'CREDIT_CARD' AND t.timestamp BETWEEN :start AND :end AND t.isSelfTransfer = 0 AND t.isCredit = 0")
    double getCreditSpendInRange(long start, long end);

    @Query("SELECT COUNT(*) FROM credit_cards")
    int getCount();
}
