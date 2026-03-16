package com.spendtracker.pro;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import java.util.List;

@Dao
public interface BudgetDao {
    @Insert void insert(Budget b);
    @Update void update(Budget b);
    @Delete void delete(Budget b);

    @Query("SELECT * FROM budgets WHERE month = :month AND year = :year")
    LiveData<List<Budget>> getByMonthYear(int month, int year);

    @Query("SELECT * FROM budgets WHERE month = :month AND year = :year")
    List<Budget> getByMonthYearSync(int month, int year);

    @Query("SELECT * FROM budgets WHERE category = :cat AND month = :month AND year = :year LIMIT 1")
    Budget getByCategoryMonthYear(String cat, int month, int year);

    @Query("UPDATE budgets SET usedAmount = usedAmount + :amount WHERE category = :cat AND month = :month AND year = :year")
    void addUsed(String cat, double amount, int month, int year);

    /**
     * Recalculate usedAmount using millisecond timestamp range (timezone-safe).
     * Uses device-local month boundaries, not SQLite's UTC strftime.
     */
    @Query("UPDATE budgets SET usedAmount = (SELECT COALESCE(SUM(t.amount),0) FROM transactions t WHERE t.category = budgets.category AND t.isSelfTransfer = 0 AND t.timestamp >= :start AND t.timestamp < :end) WHERE month = :month AND year = :year")
    void recalcAllUsed(int month, int year, long start, long end);
}
