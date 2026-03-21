// ═══════════════════════════════════════════════════════════════════════════
// AppDatabase.java — DIFF for Hybrid ML Agent (v5 → v6 → v7)
//
// If you already applied the previous patch (SmartTransactionAgent only),
// your DB is at v6. Apply CHANGE 1b/2b/3b/4b below.
// If starting fresh from the original v5 codebase, apply CHANGE 1a/2a/3a/4a.
// ═══════════════════════════════════════════════════════════════════════════


// ══════════════════════════════════════════════════════════════════
// ── FRESH INSTALL (original v5 codebase) ──────────────────────────
// CHANGE 1a: Add both new entities
// ══════════════════════════════════════════════════════════════════

// FIND:
@Database(
        entities = {
                Transaction.class,
                Budget.class,
                RecurringTransaction.class,
                NetWorthItem.class,
                Bill.class,
                CreditCard.class,
                BankAccount.class,
                SplitEntry.class
        },
        version = 5,
        exportSchema = true
)

// REPLACE WITH:
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
                MerchantPattern.class,     // Layer 1: frequency maps
                UserFeedbackRecord.class   // Layer 2: TFLite training examples
        },
        version = 7,
        exportSchema = true
)


// ══════════════════════════════════════════════════════════════════
// ── UPGRADE (already on v6) ────────────────────────────────────────
// CHANGE 1b: Add UserFeedbackRecord entity and bump to v7
// ══════════════════════════════════════════════════════════════════

// FIND:
        entities = {
                // ... existing entities ...,
                MerchantPattern.class
        },
        version = 6,

// REPLACE WITH:
        entities = {
                // ... existing entities ...,
                MerchantPattern.class,
                UserFeedbackRecord.class
        },
        version = 7,


// ══════════════════════════════════════════════════════════════════
// CHANGE 2: Add DAO abstract methods (applies to BOTH fresh and upgrade)
// Add after the existing DAO methods
// ══════════════════════════════════════════════════════════════════

// ADD these two lines after the existing abstract DAO methods:
public abstract MerchantPatternDao merchantPatternDao();
public abstract UserFeedbackDao    userFeedbackDao();


// ══════════════════════════════════════════════════════════════════
// CHANGE 3a: (FRESH INSTALL only) Add migrations v5→v6 AND v5/v6→v7
// ══════════════════════════════════════════════════════════════════

    /** Migration v5 → v6: merchant_patterns table for frequency learning */
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

    /** Migration v5 → v7: combined path for fresh-install upgrade (skips v6) */
    public static final Migration MIGRATION_5_7 = new Migration(5, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            MIGRATION_5_6.migrate(db);
            MIGRATION_6_7.migrate(db);
        }
    };


// ══════════════════════════════════════════════════════════════════
// CHANGE 3b: (UPGRADE from v6 only) Add migration v6→v7
// ══════════════════════════════════════════════════════════════════
// Same MIGRATION_6_7 block as above — just add it after MIGRATION_5_6.


// ══════════════════════════════════════════════════════════════════
// CHANGE 4: Register all migrations in the Room builder
// Find the .addMigrations() call and replace with the full set:
// ══════════════════════════════════════════════════════════════════

// FOR FRESH INSTALL (was v5):
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_5_7)

// FOR UPGRADE (was v6):
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
