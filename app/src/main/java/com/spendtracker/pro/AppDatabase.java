package com.spendtracker.pro;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {
                Transaction.class,
                Budget.class,
                RecurringTransaction.class,
                NetWorthItem.class
        },
        version = 2,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    // ── Migration: v1 → v2 ───────────────────────────────────────
    // Add new columns introduced in v2 with safe defaults so existing
    // user data is never destroyed on upgrade.
    static final androidx.room.migration.Migration MIGRATION_1_2 =
            new androidx.room.migration.Migration(1, 2) {
                @Override
                public void migrate(@androidx.annotation.NonNull androidx.sqlite.db.SupportSQLiteDatabase db) {
                    // Example: add isSelfTransfer column if it didn't exist in v1
                    try { db.execSQL("ALTER TABLE transactions ADD COLUMN isSelfTransfer INTEGER NOT NULL DEFAULT 0"); } catch (Exception ignored) {}
                    try { db.execSQL("ALTER TABLE transactions ADD COLUMN categoryIcon TEXT"); } catch (Exception ignored) {}
                    try { db.execSQL("ALTER TABLE transactions ADD COLUMN smsHash TEXT"); } catch (Exception ignored) {}
                    try { db.execSQL("ALTER TABLE transactions ADD COLUMN smsAddress TEXT"); } catch (Exception ignored) {}
                    try { db.execSQL("ALTER TABLE transactions ADD COLUMN rawSms TEXT"); } catch (Exception ignored) {}
                    try { db.execSQL("ALTER TABLE transactions ADD COLUMN paymentDetail TEXT"); } catch (Exception ignored) {}
                    try { db.execSQL("ALTER TABLE transactions ADD COLUMN notes TEXT"); } catch (Exception ignored) {}
                    // Add more ALTER TABLE statements here if you bump version again
                }
            };

    private static volatile AppDatabase INSTANCE;

    public abstract TransactionDao transactionDao();

    public abstract BudgetDao budgetDao();

    public abstract RecurringDao recurringDao();

    public abstract NetWorthDao netWorthDao();

    public static AppDatabase getInstance(Context context) {

        if (INSTANCE == null) {

            synchronized (AppDatabase.class) {

                if (INSTANCE == null) {

                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "spendtracker.db"
                    )
                    // Safe migration — user data is NEVER wiped on schema change
                    .addMigrations(MIGRATION_1_2)
                    .build();
                }
            }
        }

        return INSTANCE;
    }
}
