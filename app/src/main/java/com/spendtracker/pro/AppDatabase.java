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
                SplitEntry.class,
                MerchantPattern.class,       // ML Agent Layer 1: frequency maps
                UserFeedbackRecord.class     // ML Agent Layer 2: TFLite training examples
        },
        version = 7,
        exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase {

    /**
     * Migration v1 → v2
     */
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            try { db.execSQL("ALTER TABLE transactions ADD COLUMN isSelfTransfer INTEGER NOT NULL DEFAULT 0"); }
            catch (Exception e) { android.util.Log.w("AppDatabase", "MIGRATION_1_2 isSelfTransfer: " + e.getMessage()); }
            try { db.execSQL("ALTER TABLE transactions ADD COLUMN categoryIcon TEXT"); }
            catch (Exception e) { android.util.Log.w("AppDatabase", "MIGRATION_1_2 categoryIcon: " + e.getMessage()); }
            try { db.execSQL("ALTER TABLE transactions ADD COLUMN smsHash TEXT"); }
            catch (Exception e) { android.util.Log.w("AppDatabase", "MIGRATION_1_2 smsHash: " + e.getMessage()); }
            try { db.execSQL("ALTER TABLE transactions ADD COLUMN smsAddress TEXT"); }
            catch (Exception e) { android.util.Log.w("AppDatabase", "MIGRATION_1_2 smsAddress: " + e.getMessage()); }
            try { db.execSQL("ALTER TABLE transactions ADD COLUMN rawSms TEXT"); }
            catch (Exception e) { android.util.Log.w("AppDatabase", "MIGRATION_1_2 rawSms: " + e.getMessage()); }
            try { db.execSQL("ALTER TABLE transactions ADD COLUMN paymentDetail TEXT"); }
            catch (Exception e) { android.util.Log.w("AppDatabase", "MIGRATION_1_2 paymentDetail: " + e.getMessage()); }
            try { db.execSQL("ALTER TABLE transactions ADD COLUMN notes TEXT"); }
            catch (Exception e) { android.util.Log.w("AppDatabase", "MIGRATION_1_2 notes: " + e.getMessage()); }
            try {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_timestamp` ON `transactions` (`timestamp`)");
            } catch (Exception e) {
                android.util.Log.w("AppDatabase", "MIGRATION_1_2 index timestamp: " + e.getMessage());
            }
            try {
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_smsHash` ON `transactions` (`smsHash`)");
            } catch (Exception e) {
                android.util.Log.w("AppDatabase", "MIGRATION_1_2 unique smsHash index: " + e.getMessage());
            }
        }
    };

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
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
            try { db.execSQL("ALTER TABLE transactions ADD COLUMN isCredit INTEGER NOT NULL DEFAULT 0"); }
            catch (Exception e) {
                android.util.Log.w("AppDatabase", "MIGRATION_2_3 isCredit: " + e.getMessage());
            }
        }
    };

    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
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

    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
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

    /** Migration v5 → v6: merchant_patterns table for frequency-map learning */
    public static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `merchant_patterns` ("
                + "`merchant` TEXT NOT NULL, "
                + "`displayName` TEXT, "
                + "`topCategory` TEXT, "
                + "`categoryFreqJson` TEXT, "
                + "`topPayment` TEXT, "
                + "`paymentFreqJson` TEXT, "
                + "`avgAmount` REAL NOT NULL DEFAULT 0, "
                + "`lastNotes` TEXT, "
                + "`frequency` INTEGER NOT NULL DEFAULT 0, "
                + "`lastUsed` INTEGER NOT NULL DEFAULT 0, "
                + "PRIMARY KEY(`merchant`))"
            );
        }
    };

    /** Migration v6 → v7: user_feedback table for TFLite training examples */
    public static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `user_feedback` ("
                + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                + "`merchant` TEXT, "
                + "`displayName` TEXT, "
                + "`category` TEXT, "
                + "`amount` REAL NOT NULL DEFAULT 0, "
                + "`timestamp` INTEGER NOT NULL DEFAULT 0, "
                + "`paymentMethod` TEXT, "
                + "`isCredit` INTEGER NOT NULL DEFAULT 0, "
                + "`isCorrection` INTEGER NOT NULL DEFAULT 0, "
                + "`recordedAt` INTEGER NOT NULL DEFAULT 0)"
            );
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_feedback_merchant` ON `user_feedback` (`merchant`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_feedback_recorded` ON `user_feedback` (`recordedAt`)");
        }
    };

    /** Migration v5 → v7: combined path — skips v6 for fresh installs upgrading from v5 directly */
    public static final Migration MIGRATION_5_7 = new Migration(5, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            MIGRATION_5_6.migrate(db);
            MIGRATION_6_7.migrate(db);
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
    public abstract SplitEntryDao splitEntryDao();
    public abstract MerchantPatternDao merchantPatternDao();   // ML Agent Layer 1
    public abstract UserFeedbackDao    userFeedbackDao();      // ML Agent Layer 2

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "spendtracker.db"
                    )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_5_7)
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
