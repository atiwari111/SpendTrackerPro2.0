package com.spendtracker.pro;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Instrumented DAO tests for {@link TransactionDao}.
 *
 * Uses an in-memory Room database so no device storage is touched and each
 * test starts with a clean slate.
 *
 * Covers:
 *  - Basic insert and retrieval
 *  - SMS dedup: IGNORE conflict strategy on smsHash unique index
 *  - Date-range queries (getByDateRange, getSpendingInRange)
 *  - Category aggregation (getCategorySums)
 *  - existsHash fast lookup
 *  - deleteAll
 */
@RunWith(AndroidJUnit4.class)
public class TransactionDaoTest {

    private AppDatabase db;
    private TransactionDao dao;

    @Before
    public void createDb() {
        Context ctx = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = db.transactionDao();
    }

    @After
    public void closeDb() {
        db.close();
    }

    // ── Helpers ───────────────────────────────────────────────────

    private Transaction makeTransaction(double amount, String merchant, String category,
                                        long timestamp, String smsHash, boolean isCredit) {
        Transaction t = new Transaction();
        t.amount       = amount;
        t.merchant     = merchant;
        t.category     = category;
        t.timestamp    = timestamp;
        t.smsHash      = smsHash;
        t.isCredit     = isCredit;
        t.isManual     = smsHash == null;
        t.isSelfTransfer = false;
        return t;
    }

    // ── Basic insert / retrieve ───────────────────────────────────

    @Test
    public void insertAndRetrieve() {
        Transaction t = makeTransaction(500.0, "SWIGGY", "Food & Dining",
                System.currentTimeMillis(), "hash_001", false);
        dao.insert(t);

        List<Transaction> all = dao.getAllSync();
        assertEquals(1, all.size());
        assertEquals("SWIGGY", all.get(0).merchant);
        assertEquals(500.0, all.get(0).amount, 0.001);
    }

    // ── SMS dedup ─────────────────────────────────────────────────

    @Test
    public void duplicateSmsHashIsIgnored() {
        Transaction t1 = makeTransaction(300.0, "ZOMATO", "Food & Dining",
                1_000_000L, "hash_dupe", false);
        Transaction t2 = makeTransaction(300.0, "ZOMATO", "Food & Dining",
                1_000_001L, "hash_dupe", false); // same hash

        dao.insert(t1);
        dao.insert(t2); // OnConflictStrategy.IGNORE — must be silently dropped

        List<Transaction> all = dao.getAllSync();
        assertEquals("Duplicate smsHash must be ignored; only 1 row expected", 1, all.size());
    }

    @Test
    public void existsHashReturnsTrueForKnownHash() {
        dao.insert(makeTransaction(100.0, "MERCHANT", "Others", 1_000L, "hash_exists", false));
        assertTrue(dao.existsHash("hash_exists"));
    }

    @Test
    public void existsHashReturnsFalseForUnknownHash() {
        assertFalse(dao.existsHash("hash_not_inserted"));
    }

    @Test
    public void nullSmsHashDoesNotTriggerUniqueConflict() {
        // Manual transactions use null smsHash — multiple nulls must not conflict
        Transaction t1 = makeTransaction(200.0, "MANUAL_1", "Others", 1_000L, null, false);
        Transaction t2 = makeTransaction(300.0, "MANUAL_2", "Others", 2_000L, null, false);

        dao.insert(t1);
        dao.insert(t2);

        assertEquals("Both null-hash manual transactions must be stored", 2, dao.getAllSync().size());
    }

    // ── Date-range queries ────────────────────────────────────────

    @Test
    public void getByDateRange_returnsOnlyInRangeRows() {
        dao.insert(makeTransaction(100.0, "A", "Food", 1_000L, "h1", false));
        dao.insert(makeTransaction(200.0, "B", "Travel", 5_000L, "h2", false));
        dao.insert(makeTransaction(300.0, "C", "Shopping", 10_000L, "h3", false));

        List<Transaction> range = dao.getByDateRange(2_000L, 8_000L);
        assertEquals("Only 1 transaction falls in range [2000, 8000]", 1, range.size());
        assertEquals("B", range.get(0).merchant);
    }

    @Test
    public void getSpendingInRange_excludesSelfTransfersAndCredits() {
        long now = System.currentTimeMillis();
        long start = now - 1000;
        long end   = now + 1000;

        Transaction spend = makeTransaction(500.0, "AMAZON", "Shopping", now, "h_spend", false);
        Transaction credit = makeTransaction(1000.0, "SALARY", "Income", now, "h_credit", true);
        Transaction self = makeTransaction(2000.0, "OWN ACCOUNT", "Transfer", now, "h_self", false);
        self.isSelfTransfer = true;

        dao.insert(spend);
        dao.insert(credit);
        dao.insert(self);

        List<Transaction> spending = dao.getSpendingInRange(start, end);
        assertEquals("Only the spend transaction must appear", 1, spending.size());
        assertEquals("AMAZON", spending.get(0).merchant);
    }

    // ── Category aggregation ──────────────────────────────────────

    @Test
    public void getCategorySums_aggregatesCorrectly() {
        long now = System.currentTimeMillis();
        long start = now - 1000;
        long end   = now + 1000;

        dao.insert(makeTransaction(200.0, "SWIGGY",   "Food & Dining", now, "h1", false));
        dao.insert(makeTransaction(300.0, "ZOMATO",   "Food & Dining", now, "h2", false));
        dao.insert(makeTransaction(150.0, "OLA",      "Travel",        now, "h3", false));
        dao.insert(makeTransaction(500.0, "EMPLOYER", "Salary",        now, "h4", true)); // credit — excluded

        List<CategorySum> sums = dao.getCategorySums(start, end);

        // Must have 2 categories: Food & Dining and Travel (credit excluded)
        assertEquals("Expected 2 spend categories", 2, sums.size());

        CategorySum food = sums.stream()
                .filter(s -> "Food & Dining".equals(s.category))
                .findFirst()
                .orElse(null);
        assertNotNull("Food & Dining category must be present", food);
        assertEquals("Food & Dining total must be 500.0", 500.0, food.amount, 0.001);
    }

    // ── Delete ────────────────────────────────────────────────────

    @Test
    public void deleteAll_removesAllRows() {
        dao.insert(makeTransaction(10.0, "A", "Food", 1L, "hA", false));
        dao.insert(makeTransaction(20.0, "B", "Food", 2L, "hB", false));

        dao.deleteAll();

        assertEquals("All rows must be deleted", 0, dao.getAllSync().size());
    }
}
