package com.spendtracker.pro;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import java.util.List;

@Dao
public interface BankAccountDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(BankAccount account);

    @Update
    void update(BankAccount account);

    @Delete
    void delete(BankAccount account);

    @Query("SELECT * FROM bank_accounts ORDER BY bankName ASC")
    LiveData<List<BankAccount>> getAll();

    @Query("SELECT * FROM bank_accounts WHERE isActive = 1 ORDER BY bankName ASC")
    LiveData<List<BankAccount>> getActive();

    @Query("SELECT * FROM bank_accounts ORDER BY bankName ASC")
    List<BankAccount> getAllSync();

    @Query("SELECT * FROM bank_accounts WHERE id = :id LIMIT 1")
    BankAccount getById(int id);

    @Query("UPDATE bank_accounts SET balance = :balance, updatedAt = :ts WHERE id = :id")
    void updateBalance(int id, double balance, long ts);

    @Query("SELECT COALESCE(SUM(balance), 0) FROM bank_accounts WHERE isActive = 1")
    LiveData<Double> getTotalBalance();

    @Query("SELECT COALESCE(SUM(balance), 0) FROM bank_accounts WHERE isActive = 1")
    double getTotalBalanceSync();

    @Query("SELECT COUNT(*) FROM bank_accounts WHERE isActive = 1")
    LiveData<Integer> getActiveCount();

    @Query("SELECT COUNT(*) FROM bank_accounts")
    int getTotalCount();
}
