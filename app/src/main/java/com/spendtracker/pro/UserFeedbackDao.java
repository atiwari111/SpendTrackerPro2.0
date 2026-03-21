package com.spendtracker.pro;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

/**
 * UserFeedbackDao — Room DAO for UserFeedbackRecord.
 * All methods are synchronous; callers must use AppExecutors.db().
 */
@Dao
public interface UserFeedbackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(UserFeedbackRecord record);

    /** Recent records for a specific merchant — used for personalization weight update. */
    @Query("SELECT * FROM user_feedback WHERE merchant = :key ORDER BY recordedAt DESC LIMIT :n")
    List<UserFeedbackRecord> getRecentForMerchant(String key, int n);

    /** All correction records (user overrode suggestion) — highest-signal training data. */
    @Query("SELECT * FROM user_feedback WHERE isCorrection = 1 ORDER BY recordedAt DESC")
    List<UserFeedbackRecord> getAllCorrections();

    /**
     * All records newer than a given epoch — used to compute personalization weights
     * and to export for offline retraining.
     */
    @Query("SELECT * FROM user_feedback WHERE recordedAt > :sinceEpoch ORDER BY recordedAt DESC")
    List<UserFeedbackRecord> getSince(long sinceEpoch);

    /** Count of all records — used to decide when to suggest model retraining. */
    @Query("SELECT COUNT(*) FROM user_feedback")
    int count();

    /** Count of correction records since a given epoch. */
    @Query("SELECT COUNT(*) FROM user_feedback WHERE isCorrection = 1 AND recordedAt > :sinceEpoch")
    int correctionCountSince(long sinceEpoch);

    /** Export all records as JSON-friendly list for offline Python retraining. */
    @Query("SELECT * FROM user_feedback ORDER BY recordedAt ASC")
    List<UserFeedbackRecord> getAll();

    @Query("DELETE FROM user_feedback")
    void deleteAll();
}
