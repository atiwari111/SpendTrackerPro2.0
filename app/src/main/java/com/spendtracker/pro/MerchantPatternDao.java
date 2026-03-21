package com.spendtracker.pro;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface MerchantPatternDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(MerchantPattern pattern);

    @Query("SELECT * FROM merchant_patterns WHERE merchant = :key LIMIT 1")
    MerchantPattern getByMerchant(String key);

    /** Prefix search for autocomplete — pass "typed%" */
    @Query("SELECT * FROM merchant_patterns WHERE merchant LIKE :prefix ORDER BY frequency DESC LIMIT 20")
    List<MerchantPattern> getByPrefix(String prefix);

    @Query("SELECT * FROM merchant_patterns ORDER BY frequency DESC LIMIT :n")
    List<MerchantPattern> getTopByFrequency(int n);

    @Query("SELECT * FROM merchant_patterns ORDER BY lastUsed DESC LIMIT :n")
    List<MerchantPattern> getRecentlyUsed(int n);

    @Query("SELECT COUNT(*) FROM merchant_patterns")
    int count();

    @Query("DELETE FROM merchant_patterns WHERE merchant = :key")
    void deleteByMerchant(String key);

    @Query("DELETE FROM merchant_patterns")
    void deleteAll();
}
