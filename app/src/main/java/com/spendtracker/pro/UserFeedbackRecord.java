package com.spendtracker.pro;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Index;

/**
 * UserFeedbackRecord — Room entity (table: user_feedback)
 *
 * Stores every transaction the user saves/edits as a labeled training example.
 * These records serve two purposes:
 *   1. Offline retraining: export to the Python train script to regenerate base_model.tflite
 *   2. On-device personalization: TFLiteTransactionClassifier reads recent records to
 *      compute per-merchant correction weights applied on top of model output.
 *
 * Added in DB migration v6 → v7.
 */
@Entity(
    tableName = "user_feedback",
    indices = { @Index("merchant"), @Index("recordedAt") }
)
public class UserFeedbackRecord {

    @PrimaryKey(autoGenerate = true)
    public int id;

    /** Normalised merchant key (lowercase, stripped) — matches MerchantPattern.merchant */
    public String merchant;

    /** Display name at time of save */
    public String displayName;

    /** The category the user actually chose (ground truth label) */
    public String category;

    /** Transaction amount */
    public double amount;

    /** Transaction timestamp (epoch ms) — used for time-feature extraction */
    public long timestamp;

    /** Payment method string used for payment-feature extraction */
    public String paymentMethod;

    /** true = income transaction, false = expense */
    public boolean isCredit;

    /**
     * Whether this record came from an explicit user correction
     * (user overrode the auto-suggested category) vs a passive save.
     * Corrections are weighted 3× in personalization updates.
     */
    public boolean isCorrection;

    /** Epoch ms when this record was written */
    public long recordedAt;

    // ── Factory ──────────────────────────────────────────────────────────────

    public static UserFeedbackRecord from(Transaction tx, boolean wasCorrection) {
        UserFeedbackRecord r = new UserFeedbackRecord();
        r.merchant      = SmartTransactionAgent.normalise(tx.merchant);
        r.displayName   = tx.merchant != null ? tx.merchant.trim() : "";
        r.category      = tx.category;
        r.amount        = tx.amount;
        r.timestamp     = tx.timestamp;
        r.paymentMethod = tx.paymentDetail != null ? tx.paymentDetail : tx.paymentMethod;
        r.isCredit      = tx.isCredit;
        r.isCorrection  = wasCorrection;
        r.recordedAt    = System.currentTimeMillis();
        return r;
    }
}
