package com.spendtracker.pro;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.room.migration.Migration;

@Database(
        entities = {
                Transaction.class,
                Budget.class,
                RecurringTransaction.class,
                NetWorthItem.class,
                Bill.class,
                CreditCard.class,
                BankAccount.class,
                SplitEntry.class      // P5: split expenses
        },
        version = 5,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    /**
     * Migration v1 → v2
     *
     * Adds all new columns AND the two indices that Room's @Entity declares:
     *   - index_transactions_timestamp  (non-unique)
     *   - index_transactions_smsHash    (unique)
     *
     * Without the CREATE INDEX statements Room throws:
     *   IllegalStateException: Migration didn't properly handle...
     * because it validates the full schema after running migrations.
     */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // ── Add new columns (safe — ignored if column already exists) ──
            try { db.execSQL("ALTER TABLE transactions ADD COLUMN isSelfTransfer INTEGER NOT NULL DEFAULT 0"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE transactions ADD COLUMN categoryIcon TEXT"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE transactions ADD COLUMN smsHash TEXT"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE transactions ADD COLUMN smsAddress TEXT"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE transactions ADD COLUMN rawSms TEXT"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE transactions ADD COLUMN paymentDetail TEXT"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE transactions ADD COLUMN notes TEXT"); } catch (Exception ignored) {}

            // ── Create indices that Room @Entity declares ──────────────────
            // These MUST match the index names Room auto-generates:
            // format: index_<tableName>_<columnName>
            try {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_timestamp` ON `transactions` (`timestamp`)");
            } catch (Exception ignored) {}

            try {
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_smsHash` ON `transactions` (`smsHash`)");
            } catch (Exception ignored) {}
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `bills` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                    + "`name` TEXT, `category` TEXT, `icon` TEXT,"
                    + "`amount` REAL NOT NULL DEFAULT 0,"
                    + "`dueDate` INTEGER NOT NULL DEFAULT 0,"
                    + "`detectedDate` INTEGER NOT NULL DEFAULT 0,"
                    + "`status` TEXT, `paidDate` INTEGER NOT NULL DEFAULT 0,"
                    + "`sourceSmS` TEXT, `isRecurring` INTEGER NOT NULL DEFAULT 0,"
                    + "`frequency` TEXT, `merchantId` TEXT)");
            // Add isCredit field to transactions
            try { db.execSQL("ALTER TABLE transactions ADD COLUMN isCredit INTEGER NOT NULL DEFAULT 0"); }
            catch (Exception ignored) {}
        }
    };

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // ── credit_cards table ────────────────────────────────────────
            db.execSQL("CREATE TABLE IF NOT EXISTS `credit_cards` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                    + "`bankName` TEXT,"
                    + "`cardLabel` TEXT,"
                    + "`lastFour` TEXT,"
                    + "`network` TEXT,"
                    + "`creditLimit` REAL NOT NULL DEFAULT 0,"
                    + "`currentSpent` REAL NOT NULL DEFAULT 0,"
                    + "`statementAmount` REAL NOT NULL DEFAULT 0,"
                    + "`billingDay` INTEGER NOT NULL DEFAULT 0,"
                    + "`billingCycleStart` INTEGER NOT NULL DEFAULT 0,"
                    + "`updatedAt` INTEGER NOT NULL DEFAULT 0,"
                    + "`cardColor` INTEGER NOT NULL DEFAULT 0)");

            // ── bank_accounts table ───────────────────────────────────────
            db.execSQL("CREATE TABLE IF NOT EXISTS `bank_accounts` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                    + "`bankName` TEXT,"
                    + "`accountLabel` TEXT,"
                    + "`lastFour` TEXT,"
                    + "`accountType` TEXT,"
                    + "`balance` REAL NOT NULL DEFAULT 0,"
                    + "`updatedAt` INTEGER NOT NULL DEFAULT 0,"
                    + "`cardColor` INTEGER NOT NULL DEFAULT 0,"
                    + "`isActive` INTEGER NOT NULL DEFAULT 1,"
                    + "`bankEmoji` TEXT)");
        }
    };

    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `split_entries` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                    + "`transactionId` INTEGER NOT NULL DEFAULT 0,"
                    + "`contactName` TEXT,"
                    + "`amountOwed` REAL NOT NULL DEFAULT 0,"
                    + "`isPaid` INTEGER NOT NULL DEFAULT 0,"
                    + "`createdAt` INTEGER NOT NULL DEFAULT 0,"
                    + "`paidAt` INTEGER NOT NULL DEFAULT 0,"
                    + "`notes` TEXT)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_split_entries_transactionId`"
                    + " ON `split_entries` (`transactionId`)");
        }
    };

    private static volatile AppDatabase INSTANCE;

    public abstract TransactionDao transactionDao();
    public abstract BudgetDao budgetDao();
    public abstract RecurringDao recurringDao();
    public abstract NetWorthDao netWorthDao();
    public abstract BillDao billDao();
    public abstract CreditCardDao creditCardDao();
    public abstract BankAccountDao bankAccountDao();
    public abstract SplitEntryDao splitEntryDao();   // P5

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "spendtracker.db"
                    )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
