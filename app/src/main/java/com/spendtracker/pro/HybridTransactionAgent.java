package com.spendtracker.pro;

import android.content.Context;
import android.util.Log;
import androidx.annotation.Nullable;
import java.util.List;

/**
 * HybridTransactionAgent v1.0
 *
 * The single entry point for all ML-driven transaction suggestions.
 * Replaces direct usage of SmartTransactionAgent in AddExpenseActivity.
 *
 * ── Suggestion chain (highest priority → lowest) ─────────────────────────
 *  1. SmartTransactionAgent (Room frequency maps)
 *     → if confidence ≥ 0.7 (≥7 prior observations): return immediately
 *  2. TFLiteTransactionClassifier (base model + personalization)
 *     → if TFLite confidence ≥ MIN_CONFIDENCE: return TFLite result
 *       but MERGE payment / amount hints from frequency map if available
 *  3. SmartTransactionAgent result (even low confidence) — better than nothing
 *  4. null → caller falls back to CategoryEngine static map + NLP
 *
 * ── Learning chain (every save/edit) ─────────────────────────────────────
 *  All three systems are updated in parallel on every transaction:
 *  • SmartTransactionAgent.learn()       — updates frequency maps + rolling avg
 *  • UserFeedbackDao.insert()            — stores labeled example for retraining
 *  • TFLiteTransactionClassifier.update() — updates per-merchant delta weights
 *
 * ── Thread safety ─────────────────────────────────────────────────────────
 *  suggestAsync() and learnAsync() are safe to call from the main thread —
 *  they dispatch internally to AppExecutors.db(). All other methods
 *  (getSuggestion, learn) must be called on a background thread.
 */
public class HybridTransactionAgent {

    private static final String TAG = "HybridAgent";

    // Confidence threshold above which the frequency map's result is used directly
    private static final float FREQ_MAP_HIGH_CONFIDENCE = 0.7f; // ≥7 observations

    private static volatile HybridTransactionAgent INSTANCE;

    private final SmartTransactionAgent     frequencyAgent;
    private final TFLiteTransactionClassifier tflite;
    private final UserFeedbackDao           feedbackDao;

    // ── Singleton ─────────────────────────────────────────────────────────

    private HybridTransactionAgent(Context ctx) {
        frequencyAgent = SmartTransactionAgent.getInstance(ctx);
        tflite         = TFLiteTransactionClassifier.getInstance(ctx);
        feedbackDao    = AppDatabase.getInstance(ctx).userFeedbackDao();
    }

    public static HybridTransactionAgent getInstance(Context ctx) {
        if (INSTANCE == null) {
            synchronized (HybridTransactionAgent.class) {
                if (INSTANCE == null) {
                    INSTANCE = new HybridTransactionAgent(ctx.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    // ── Suggestion result ─────────────────────────────────────────────────

    public static class HybridSuggestion {
        /** Category string matching CategoryEngine.CATEGORIES key */
        public final String category;
        /** Best payment method to pre-select */
        public final String paymentMethod;
        /** Rolling average amount for hint display */
        public final double avgAmount;
        /** Last notes string as placeholder hint */
        @Nullable public final String notesHint;
        /** 0.0–1.0 confidence */
        public final float confidence;
        /** Which layer produced this suggestion */
        public final Source source;

        public enum Source { FREQUENCY_MAP, TFLITE, TFLITE_WITH_FREQ_HINTS }

        HybridSuggestion(String category, String paymentMethod, double avgAmount,
                          @Nullable String notesHint, float confidence, Source source) {
            this.category      = category;
            this.paymentMethod = paymentMethod;
            this.avgAmount     = avgAmount;
            this.notesHint     = notesHint;
            this.confidence    = confidence;
            this.source        = source;
        }
    }

    // ── Async entry point (call from main thread) ─────────────────────────

    public interface SuggestionCallback {
        void onResult(@Nullable HybridSuggestion suggestion);
    }

    /**
     * Async suggestion — safe to call from the UI thread (main thread).
     * Runs inference on background thread, delivers result on main thread.
     *
     * @param merchantTyped  The merchant name as currently typed
     * @param amount         Current amount (0 if not yet entered)
     * @param timestamp      Transaction timestamp (epoch ms)
     * @param paymentMethod  Currently selected payment method string
     */
    public void suggestAsync(String merchantTyped, double amount, long timestamp,
                              String paymentMethod, SuggestionCallback callback) {
        AppExecutors.db().execute(() -> {
            HybridSuggestion s = getSuggestion(merchantTyped, amount, timestamp, paymentMethod);
            AppExecutors.mainThread().execute(() -> callback.onResult(s));
        });
    }

    // ── Synchronous suggestion (background thread only) ───────────────────

    @Nullable
    public HybridSuggestion getSuggestion(String merchantTyped, double amount,
                                           long timestamp, String paymentMethod) {
        if (merchantTyped == null || merchantTyped.trim().length() < 2) return null;

        String normKey = SmartTransactionAgent.normalise(merchantTyped);

        // ── Layer 1: Frequency map ─────────────────────────────────────────
        SmartTransactionAgent.Suggestion freqSug = frequencyAgent.getSuggestion(merchantTyped);

        if (freqSug != null && freqSug.confidence >= FREQ_MAP_HIGH_CONFIDENCE) {
            // Strong frequency signal — use it directly, no need to run TFLite
            return new HybridSuggestion(
                freqSug.category,
                freqSug.paymentMethod,
                freqSug.avgAmount,
                freqSug.notesHint,
                freqSug.confidence,
                HybridSuggestion.Source.FREQUENCY_MAP
            );
        }

        // ── Layer 2: TFLite ────────────────────────────────────────────────
        if (tflite.isAvailable()) {
            long ts = timestamp > 0 ? timestamp : System.currentTimeMillis();
            float[] features = TransactionFeatureExtractor.extract(
                merchantTyped, amount, ts, paymentMethod);

            TFLiteTransactionClassifier.Prediction pred = tflite.predict(features, normKey);

            if (pred != null && pred.confidence >= TFLiteTransactionClassifier.MIN_CONFIDENCE) {
                // TFLite gave a confident answer — but enrich with frequency hints
                // for payment method, avg amount, notes if we have any prior data
                String payHint  = freqSug != null ? freqSug.paymentMethod : paymentMethod;
                double avgAmt   = freqSug != null ? freqSug.avgAmount : 0.0;
                String notesHnt = freqSug != null ? freqSug.notesHint : null;

                HybridSuggestion.Source src = freqSug != null
                    ? HybridSuggestion.Source.TFLITE_WITH_FREQ_HINTS
                    : HybridSuggestion.Source.TFLITE;

                return new HybridSuggestion(
                    pred.category,
                    payHint,
                    avgAmt,
                    notesHnt,
                    pred.confidence,
                    src
                );
            }
        }

        // ── Layer 3: Low-confidence frequency map result (better than nothing) ──
        if (freqSug != null) {
            return new HybridSuggestion(
                freqSug.category,
                freqSug.paymentMethod,
                freqSug.avgAmount,
                freqSug.notesHint,
                freqSug.confidence,
                HybridSuggestion.Source.FREQUENCY_MAP
            );
        }

        // No data from either layer — caller falls back to CategoryEngine
        return null;
    }

    // ── Learning (background thread only) ─────────────────────────────────

    /**
     * Record a transaction in all three learning systems.
     * Must be called on a background thread (AppExecutors.db()).
     *
     * @param tx            The saved/updated transaction
     * @param wasCorrection true if the user explicitly overrode the auto-suggested category
     */
    public void learn(Transaction tx, boolean wasCorrection) {
        if (tx == null || tx.isSelfTransfer) return;

        // 1. Update Room frequency maps
        frequencyAgent.learn(tx);

        // 2. Store labeled example for offline retraining
        UserFeedbackRecord record = UserFeedbackRecord.from(tx, wasCorrection);
        feedbackDao.insert(record);

        // 3. Update TFLite personalization weights
        tflite.updatePersonalization(record);

        Log.d(TAG, "Learned: " + tx.merchant + " → " + tx.category
                + (wasCorrection ? " [correction]" : ""));
    }

    /** Async learn — safe to call from the main thread. */
    public void learnAsync(Transaction tx, boolean wasCorrection) {
        AppExecutors.db().execute(() -> learn(tx, wasCorrection));
    }

    // ── Autocomplete (background thread only) ─────────────────────────────

    /**
     * Returns up to `limit` known merchant display names matching the typed prefix.
     * Combines frequency-map matches with recently-used merchants.
     */
    public List<String> getAutocompleteSuggestions(String prefix, int limit) {
        return frequencyAgent.getAutocompleteSuggestions(prefix, limit);
    }

    // ── Stats ──────────────────────────────────────────────────────────────

    /** Returns the total number of user feedback records stored. */
    public int getFeedbackCount() {
        return feedbackDao.count();
    }

    /** Returns true when TFLite model is loaded and inference is available. */
    public boolean isTFLiteAvailable() {
        return tflite.isAvailable();
    }
}
