package com.spendtracker.pro;

/**
 * Lightweight projection returned by TransactionDao.getCategorySums().
 * Avoids loading full Transaction rows just to compute per-category totals.
 */
public class CategorySum {
    public String category;
    public double amount;
}
