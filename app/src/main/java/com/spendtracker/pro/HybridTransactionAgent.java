package com.spendtracker.pro;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.Nullable;
import java.util.Calendar;
import java.util.List;

/**
 * HybridTransactionAgent — single entry point for all ML suggestions.
 *
 * Layer 1: SmartTransactionAgent  (Room frequency maps — works day 1)
 * Layer 2: TFLiteTransactionClassifier (base model + personalization)
 * Layer 3: CategoryEngine static map + NLP (always available fallback)
 */
public class HybridTransactionAgent {

    private static final String TAG = "HybridAgent";
    private static final float  FREQ_HIGH_CONFIDENCE = 0.7f;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static volatile HybridTransactionAgent INSTANCE;

    private final SmartTransactionAgent       frequencyAgent;
    private final TFLiteTransactionClassifier tflite;
    private final UserFeedbackDao             feedbackDao;

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
        public final String   category;
        public final String   paymentMethod;
        public final double   avgAmount;
        @Nullable public final String notesHint;
        public final float    confidence;
        public final Source   source;

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

    // ── Async suggest (safe to call from main thread) ─────────────────────

    public interface SuggestionCallback {
        void onResult(@Nullable HybridSuggestion suggestion);
    }

    public void suggestAsync(String merchantTyped, double amount, long timestamp,
                              String paymentMethod, SuggestionCallback callback) {
        AppExecutors.db().execute(() -> {
            HybridSuggestion s = getSuggestion(merchantTyped, amount, timestamp, paymentMethod);
            MAIN.post(() -> callback.onResult(s));
        });
    }

    // ── Synchronous suggest (background thread only) ──────────────────────

    @Nullable
    public HybridSuggestion getSuggestion(String merchantTyped, double amount,
                                           long timestamp, String paymentMethod) {
        if (merchantTyped == null || merchantTyped.trim().length() < 2) return null;

        String normKey = SmartTransactionAgent.normalise(merchantTyped);

        // Layer 1: frequency map
        SmartTransactionAgent.Suggestion freqSug = frequencyAgent.getSuggestion(merchantTyped);
        if (freqSug != null && freqSug.confidence >= FREQ_HIGH_CONFIDENCE) {
            return new HybridSuggestion(freqSug.category, freqSug.paymentMethod,
                    freqSug.avgAmount, freqSug.notesHint, freqSug.confidence,
                    HybridSuggestion.Source.FREQUENCY_MAP);
        }

        // Layer 2: TFLite
        if (tflite.isAvailable()) {
            // Build Calendar fields from timestamp for feature extraction
            long ts = timestamp > 0 ? timestamp : System.currentTimeMillis();
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(ts);
            int dow = ((cal.get(Calendar.DAY_OF_WEEK) - 2 + 7) % 7); // 0=Mon..6=Sun
            int dom = cal.get(Calendar.DAY_OF_MONTH);
            int hod = cal.get(Calendar.HOUR_OF_DAY);

            float[] features = TransactionFeatureExtractor.extract(
                    merchantTyped, amount, dow, dom, hod, paymentMethod);

            TFLiteTransactionClassifier.Prediction pred = tflite.predict(features, normKey);

            if (pred != null && pred.confidence >= TFLiteTransactionClassifier.MIN_CONFIDENCE) {
                String payHint  = freqSug != null ? freqSug.paymentMethod : paymentMethod;
                double avgAmt   = freqSug != null ? freqSug.avgAmount : 0.0;
                String notesHnt = freqSug != null ? freqSug.notesHint : null;
                HybridSuggestion.Source src = freqSug != null
                        ? HybridSuggestion.Source.TFLITE_WITH_FREQ_HINTS
                        : HybridSuggestion.Source.TFLITE;
                return new HybridSuggestion(pred.category, payHint, avgAmt,
                        notesHnt, pred.confidence, src);
            }
        }

        // Layer 3: low-confidence frequency map (better than nothing)
        if (freqSug != null) {
            return new HybridSuggestion(freqSug.category, freqSug.paymentMethod,
                    freqSug.avgAmount, freqSug.notesHint, freqSug.confidence,
                    HybridSuggestion.Source.FREQUENCY_MAP);
        }

        return null; // caller falls back to CategoryEngine
    }

    // ── Learn (background thread only) ────────────────────────────────────

    public void learn(Transaction tx, boolean wasCorrection) {
        if (tx == null || tx.isSelfTransfer) return;
        frequencyAgent.learn(tx);
        UserFeedbackRecord record = UserFeedbackRecord.from(tx, wasCorrection);
        feedbackDao.insert(record);
        tflite.updatePersonalization(record);
        Log.d(TAG, "Learned: " + tx.merchant + " -> " + tx.category
                + (wasCorrection ? " [correction]" : ""));
    }

    public void learnAsync(Transaction tx, boolean wasCorrection) {
        AppExecutors.db().execute(() -> learn(tx, wasCorrection));
    }

    // ── Autocomplete ──────────────────────────────────────────────────────

    public List<String> getAutocompleteSuggestions(String prefix, int limit) {
        return frequencyAgent.getAutocompleteSuggestions(prefix, limit);
    }

    public boolean isTFLiteAvailable() { return tflite.isAvailable(); }
    public int getFeedbackCount()       { return feedbackDao.count(); }
}
