package com.spendtracker.pro;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Instrumented migration tests for {@link AppDatabase}.
 *
 * Each test verifies:
 *  1. The migration runs without throwing.
 *  2. The resulting schema exactly matches what Room expects (MigrationTestHelper
 *     calls validateMigration() which compares exported JSON schema against the
 *     live database — any column / index mismatch fails the test).
 *
 * Prerequisites:
 *  - room.schemaLocation must be set in build.gradle (already configured).
 *  - Exported schema JSON files must exist under app/schemas/.
 *
 * Run with: ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4.class)
public class MigrationTest {

    private static final String TEST_DB = "migration-test";

    @Rule
    public final MigrationTestHelper helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase.class
    );

    // ── 1 → 2 ────────────────────────────────────────────────────

    @Test
    public void migrate1To2_addsColumnsAndIndices() throws IOException {
        // Create v1 database
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 1);

        // Insert a row so we can verify data survives the migration
        db.execSQL("INSERT INTO transactions (amount, merchant, category, timestamp, isManual, isSelfTransfer) "
                 + "VALUES (500.0, 'SWIGGY', 'Food', 1711000000000, 0, 0)");
        db.close();

        // Run migration and validate schema
        db = helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2);

        // Verify new columns exist with correct defaults
        var cursor = db.query("SELECT isSelfTransfer, smsHash, isCredit FROM transactions LIMIT 1");
        assertTrue("Migrated row must be queryable with new columns", cursor.moveToFirst());
        assertEquals("isSelfTransfer default must be 0", 0, cursor.getInt(0));
        assertNull("smsHash default must be NULL", cursor.getString(1));
        db.close();
    }

    @Test
    public void migrate1To2_isIdempotentOnDuplicateColumns() throws IOException {
        // Simulate a partially-applied migration by pre-creating one of the new columns.
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB + "_idem", 1);
        db.execSQL("ALTER TABLE transactions ADD COLUMN isSelfTransfer INTEGER NOT NULL DEFAULT 0");
        db.close();

        // Migration must complete without crashing even though isSelfTransfer already exists.
        // The try/catch in MIGRATION_1_2 handles the duplicate-column SQLite error.
        db = helper.runMigrationsAndValidate(TEST_DB + "_idem", 2, true, AppDatabase.MIGRATION_1_2);
        db.close();
    }

    // ── 2 → 3 ────────────────────────────────────────────────────

    @Test
    public void migrate2To3_createsBillsTableAndAddsIsCredit() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB + "_2to3", 2);
        db.close();

        db = helper.runMigrationsAndValidate(TEST_DB + "_2to3", 3, true, AppDatabase.MIGRATION_2_3);

        // Verify bills table was created
        var cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='bills'");
        assertTrue("bills table must exist after migration 2→3", cursor.moveToFirst());

        // Verify isCredit column exists in transactions
        cursor = db.query("SELECT isCredit FROM transactions LIMIT 0");
        assertNotNull("isCredit column must exist in transactions", cursor);
        db.close();
    }

    // ── 3 → 4 ────────────────────────────────────────────────────

    @Test
    public void migrate3To4_createsCreditCardsAndBankAccountsTables() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB + "_3to4", 3);
        db.close();

        db = helper.runMigrationsAndValidate(TEST_DB + "_3to4", 4, true, AppDatabase.MIGRATION_3_4);

        var cursor = db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('credit_cards','bank_accounts')");
        int count = 0;
        while (cursor.moveToNext()) count++;
        assertEquals("Both credit_cards and bank_accounts must exist", 2, count);
        db.close();
    }

    // ── 4 → 5 ────────────────────────────────────────────────────

    @Test
    public void migrate4To5_createsSplitEntriesTableWithIndex() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB + "_4to5", 4);
        db.close();

        db = helper.runMigrationsAndValidate(TEST_DB + "_4to5", 5, true, AppDatabase.MIGRATION_4_5);

        var cursor = db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='split_entries'");
        assertTrue("split_entries table must exist", cursor.moveToFirst());

        cursor = db.query(
                "SELECT name FROM sqlite_master WHERE type='index' "
              + "AND name='index_split_entries_transactionId'");
        assertTrue("transactionId index on split_entries must exist", cursor.moveToFirst());
        db.close();
    }

    // ── Full chain migration ──────────────────────────────────────

    @Test
    public void migrateFullChain_1To5() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB + "_full", 1);

        // Seed data in v1 schema
        db.execSQL("INSERT INTO transactions (amount, merchant, category, timestamp, isManual, isSelfTransfer) "
                 + "VALUES (1234.5, 'AMAZON', 'Shopping', 1711000000001, 1, 0)");
        db.close();

        // Run all migrations in sequence
        db = helper.runMigrationsAndValidate(TEST_DB + "_full", 5, true,
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5);

        // Original data must survive the full migration chain
        var cursor = db.query("SELECT amount, merchant FROM transactions");
        assertTrue("Seeded transaction must survive full migration", cursor.moveToFirst());
        assertEquals(1234.5, cursor.getDouble(0), 0.001);
        assertEquals("AMAZON", cursor.getString(1));
        db.close();
    }
}
