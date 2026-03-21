package com.spendtracker.pro;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.*;

/**
 * SmartTransactionAgent v2.0  —  Hybrid ML + Room orchestrator
 *
 * Three-layer priority system for every suggestion:
 *
 *   Layer 1 — TFLite base model (semantic understanding, time-aware features)
 *             Trained on 418 merchants from merchant_dataset.json.
 *             Handles cold-start and pattern generalisation (e.g. same merchant
 *             on Fri = Food, on 1st = Business — detected via day-of-month feature).
 *
 *   Layer 2 — PersonalizationLayer (on-device SGD correction weights)
 *             Adjusts TFLite output using a learned weight matrix that updates
 *             instantly every time the user overrides a suggestion.
 *             Stored in SharedPreferences as ~7 KB float array.
 *
 *   Layer 3 — MerchantPattern (Room DB frequency maps)
 *             Supplies payment method, rolling average amount, notes hint,
 *             and autocomplete display names — all fields TFLite doesn't touch.
 *             Also provides a high-confidence override when freq ≥ 5 and
 *             one category has ≥ 80% share.
 *
 * Learning happens on every save/edit:
 *   learn(tx)           — called from AppExecutors.db() after insert/update
 *   recordCorrection()  — called when user explicitly changes the suggested category
 *
 * Suggestion flow for AddExpenseActivity:
 *   suggestAsync(merchant, amount, ..., callback)  — runs on bg thread, delivers on main
 */
public class SmartTransactionAgent {

    private static final String TAG = "SmartTxAgent";

    // Confidence threshold for Layer 1+2 combined to override Layer 3 frequency data
    private static final float TFLITE_TRUST_THRESHOLD  = 0.45f;
    // Frequency + dominance threshold for Layer 3 to override TFLite
    private static final int   FREQ_OVERRIDE_MIN       = 5;
    private static final float FREQ_OVERRIDE_DOMINANCE = 0.80f;

    private static volatile SmartTransactionAgent INSTANCE;

    private final TFLiteClassifier     tflite;
    private final PersonalizationLayer personalisation;
    private final MerchantPatternDao   patternDao;
    private final UserFeedbackDao      feedbackDao;

    // ─────────────────────────────────────────────────────────────────────────
    // Singleton
    // ─────────────────────────────────────────────────────────────────────────

    private SmartTransactionAgent(Context ctx) {
        tflite          = TFLiteClassifier.getInstance(ctx);
        personalisation = PersonalizationLayer.getInstance(ctx);
        patternDao      = AppDatabase.getInstance(ctx).merchantPatternDao();
        feedbackDao     = AppDatabase.getInstance(ctx).userFeedbackDao();
    }

    public static SmartTransactionAgent getInstance(Context ctx) {
        if (INSTANCE == null) {
            synchronized (SmartTransactionAgent.class) {
                if (INSTANCE == null)
                    INSTANCE = new SmartTransactionAgent(ctx.getApplicationContext());
            }
        }
        return INSTANCE;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Suggestion
    // ─────────────────────────────────────────────────────────────────────────

    public static class Suggestion {
        public final String  category;
        public final String  paymentMethod;
        public final double  avgAmount;
        @Nullable public final String notesHint;
        public final float   confidence;
        public final int     frequency;
        public final String  source;   // "tflite", "personalised", "learned", "frequency"

        Suggestion(String category, String paymentMethod, double avgAmount,
                   @Nullable String notesHint, float confidence, int frequency, String source) {
            this.category      = category;
            this.paymentMethod = paymentMethod;
            this.avgAmount     = avgAmount;
            this.notesHint     = notesHint;
            this.confidence    = confidence;
            this.frequency     = frequency;
            this.source        = source;
        }
    }

    /**
     * Async suggestion — delivers result on the main thread.
     * Safe to call from onCreate / TextWatcher on the UI thread.
     */
    public void suggestAsync(String merchant, double amount,
                              int dayOfWeek, int dayOfMonth, int hourOfDay,
                              String paymentMethod,
                              SuggestionCallback callback) {
        AppExecutors.db().execute(() -> {
            Suggestion s = getSuggestion(merchant, amount, dayOfWeek,
                                         dayOfMonth, hourOfDay, paymentMethod);
            AppExecutors.mainThread().execute(() -> callback.onResult(s));
        });
    }

    public interface SuggestionCallback {
        void onResult(@Nullable Suggestion suggestion);
    }

    /**
     * Synchronous suggestion — must be called on a background thread.
     */
    @Nullable
    public Suggestion getSuggestion(String merchant, double amount,
                                     int dayOfWeek, int dayOfMonth,
                                     int hourOfDay, String paymentMethod) {
        if (merchant == null || merchant.trim().length() < 2) return null;

        // ── Build feature vector (shared by Layer 1 + 2) ─────────────────
        float[] vec = TransactionFeatureExtractor.extract(
                merchant, amount, dayOfWeek, dayOfMonth, hourOfDay, paymentMethod);

        // ── Layer 3: Room frequency data ──────────────────────────────────
        String normKey = normalise(merchant);
        MerchantPattern pattern = patternDao.getByMerchant(normKey);
        if (pattern == null) {
            // Try prefix match
            List<MerchantPattern> prefixList = patternDao.getByPrefix(normKey + "%");
            if (!prefixList.isEmpty()) pattern = bestByFreq(prefixList);
        }

        // ── Layer 3 early exit: high frequency + dominant category ────────
        if (pattern != null && pattern.frequency >= FREQ_OVERRIDE_MIN) {
            float dominance = categoryDominance(pattern.categoryFreqJson, pattern.topCategory);
            if (dominance >= FREQ_OVERRIDE_DOMINANCE) {
                return buildSuggestion(pattern.topCategory, pattern, 
                                       dominance, "frequency");
            }
        }

        // ── Layer 1: TFLite inference ─────────────────────────────────────
        String tfliteCategory = null;
        float  tfliteConf     = 0f;
        float[] adjustedScores = null;

        TFLiteClassifier.InferenceResult ir = tflite.classifyPartial(
                merchant, amount, dayOfWeek, dayOfMonth, hourOfDay, paymentMethod);
        if (ir != null) {
            // ── Layer 2: PersonalizationLayer adjustment ──────────────────
            adjustedScores = personalisation.adjust(ir.scores, vec);
            int bestIdx = argmax(adjustedScores);
            tfliteCategory = TransactionFeatureExtractor.indexToCategory(bestIdx);
            tfliteConf     = adjustedScores[bestIdx];
        }

        // ── Decision: pick best category source ──────────────────────────
        String finalCategory;
        float  finalConf;
        String source;

        if (tfliteCategory != null && tfliteConf >= TFLITE_TRUST_THRESHOLD) {
            // Personalisation has adjusted TFLite output
            boolean wasAdjusted = ir != null
                    && !tfliteCategory.equals(
                            TransactionFeatureExtractor.indexToCategory(argmax(ir.scores)));
            finalCategory = tfliteCategory;
            finalConf     = tfliteConf;
            source        = wasAdjusted ? "personalised" : "tflite";
        } else if (pattern != null && pattern.topCategory != null) {
            // Fall back to learned frequency data
            finalCategory = pattern.topCategory;
            finalConf     = Math.min(0.6f, pattern.frequency / 10.0f);
            source        = "learned";
        } else if (tfliteCategory != null) {
            // Low-confidence TFLite still better than nothing
            finalCategory = tfliteCategory;
            finalConf     = tfliteConf;
            source        = "tflite";
        } else {
            return null; // No suggestion available
        }

        return buildSuggestion(finalCategory, pattern, finalConf, source);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Learning
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Record every saved/edited transaction.
     * Must be called from a background thread (AppExecutors.db()).
     */
    public void learn(@NonNull Transaction tx) {
        if (tx.merchant == null || tx.merchant.trim().isEmpty()) return;
        if (tx.isSelfTransfer) return;

        String key = normalise(tx.merchant);
        float[] vec = TransactionFeatureExtractor.extract(tx);

        // Update Room frequency map
        updatePattern(key, tx);

        // Reinforce PersonalizationLayer for this (merchant, category) pair
        personalisation.reinforce(vec, tx.category);
    }

    /**
     * Record an explicit user correction — the most valuable training signal.
     * Call this when the user changes the suggested category before saving.
     *
     * @param tx                 the transaction being saved (with corrected category)
     * @param previouslyShown    the category that was auto-suggested
     */
    public void recordCorrection(@NonNull Transaction tx, @Nullable String previouslyShown) {
        if (previouslyShown == null || previouslyShown.equals(tx.category)) return;

        float[] vec = TransactionFeatureExtractor.extract(tx);

        // Update PersonalizationLayer weights immediately
        personalisation.learn(vec, previouslyShown, tx.category);

        // Persist correction for future model retraining
        AppExecutors.db().execute(() -> {
            UserFeedback fb = new UserFeedback();
            fb.merchant          = normalise(tx.merchant);
            fb.displayName       = tx.merchant;
            fb.amount            = tx.amount;
            fb.predictedCategory = previouslyShown;
            fb.correctedCategory = tx.category;
            fb.paymentMethod     = tx.paymentDetail != null ? tx.paymentDetail : tx.paymentMethod;
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(tx.timestamp);
            fb.dayOfWeek  = ((cal.get(Calendar.DAY_OF_WEEK) - 2) + 7) % 7;
            fb.dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
            fb.hourOfDay  = cal.get(Calendar.HOUR_OF_DAY);
            fb.timestamp  = System.currentTimeMillis();
            fb.usedForRetrain = false;
            feedbackDao.insert(fb);
            Log.d(TAG, "Correction recorded: " + previouslyShown + " → " + tx.category
                    + " for " + fb.merchant);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Autocomplete
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns up to `limit` known merchant display names matching the typed prefix.
     * Must be called on a background thread.
     */
    public List<String> getAutocompleteSuggestions(String prefix, int limit) {
        if (prefix == null || prefix.trim().isEmpty()) return Collections.emptyList();
        List<MerchantPattern> rows = patternDao.getByPrefix(normalise(prefix) + "%");
        rows.sort((a, b) -> Integer.compare(b.frequency, a.frequency));
        List<String> names = new ArrayList<>();
        for (MerchantPattern p : rows) {
            if (names.size() >= limit) break;
            names.add(p.displayName != null ? p.displayName : p.merchant);
        }
        return names;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Privacy
    // ─────────────────────────────────────────────────────────────────────────

    public void resetAllLearning() {
        personalisation.reset();
        AppExecutors.db().execute(() -> {
            patternDao.deleteAll();
            feedbackDao.deleteAll();
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void updatePattern(String key, Transaction tx) {
        MerchantPattern p = patternDao.getByMerchant(key);
        if (p == null) {
            p = new MerchantPattern();
            p.merchant        = key;
            p.displayName     = tx.merchant.trim();
            p.topCategory     = tx.category;
            p.categoryFreqJson = singleEntryJson(tx.category);
            p.topPayment      = tx.paymentDetail != null ? tx.paymentDetail : tx.paymentMethod;
            p.paymentFreqJson = singleEntryJson(p.topPayment);
            p.avgAmount       = tx.amount;
            p.lastNotes       = tx.notes;
            p.frequency       = 1;
            p.lastUsed        = tx.timestamp;
        } else {
            p.displayName = tx.merchant.trim();
            p.categoryFreqJson = incrementFreq(p.categoryFreqJson, tx.category);
            p.topCategory      = topKey(p.categoryFreqJson);
            String payUsed = tx.paymentDetail != null ? tx.paymentDetail : tx.paymentMethod;
            p.paymentFreqJson = incrementFreq(p.paymentFreqJson, payUsed);
            p.topPayment      = topKey(p.paymentFreqJson);
            if (!tx.isCredit) p.avgAmount = rollingAvg(p.avgAmount, p.frequency, tx.amount);
            if (tx.notes != null && !tx.notes.trim().isEmpty()) p.lastNotes = tx.notes.trim();
            p.frequency++;
            p.lastUsed = Math.max(p.lastUsed, tx.timestamp);
        }
        patternDao.upsert(p);
    }

    private Suggestion buildSuggestion(String category, @Nullable MerchantPattern pattern,
                                        float confidence, String source) {
        String payMethod = pattern != null ? pattern.topPayment : null;
        double avgAmt    = pattern != null ? pattern.avgAmount  : 0;
        String notes     = pattern != null ? pattern.lastNotes  : null;
        int    freq      = pattern != null ? pattern.frequency  : 0;
        return new Suggestion(category, payMethod, avgAmt, notes, confidence, freq, source);
    }

    private float categoryDominance(String freqJson, String category) {
        if (freqJson == null || category == null) return 0f;
        try {
            JSONObject obj = new JSONObject(freqJson);
            int catCount  = obj.optInt(category, 0);
            int total = 0;
            Iterator<String> it = obj.keys();
            while (it.hasNext()) total += obj.optInt(it.next(), 0);
            return total > 0 ? catCount / (float) total : 0f;
        } catch (JSONException e) { return 0f; }
    }

    static String normalise(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).trim()
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ").trim();
    }

    private static String incrementFreq(String json, String key) {
        if (key == null || key.isEmpty()) return json != null ? json : "{}";
        try {
            JSONObject obj = (json != null && !json.isEmpty()) ? new JSONObject(json) : new JSONObject();
            obj.put(key, obj.optInt(key, 0) + 1);
            return obj.toString();
        } catch (JSONException e) { return singleEntryJson(key); }
    }

    private static String topKey(String json) {
        if (json == null) return null;
        try {
            JSONObject obj = new JSONObject(json);
            String best = null; int bestCount = -1;
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String k = keys.next(); int c = obj.optInt(k, 0);
                if (c > bestCount) { bestCount = c; best = k; }
            }
            return best;
        } catch (JSONException e) { return null; }
    }

    private static String singleEntryJson(String key) {
        try { return new JSONObject().put(key, 1).toString(); }
        catch (JSONException e) { return "{}"; }
    }

    private static double rollingAvg(double prevAvg, int prevCount, double newVal) {
        if (prevCount < 5) return (prevAvg * prevCount + newVal) / (prevCount + 1);
        return prevAvg * 0.8 + newVal * 0.2;
    }

    private static MerchantPattern bestByFreq(List<MerchantPattern> list) {
        MerchantPattern best = list.get(0);
        for (MerchantPattern p : list) if (p.frequency > best.frequency) best = p;
        return best;
    }

    private static int argmax(float[] arr) {
        int best = 0;
        for (int i = 1; i < arr.length; i++) if (arr[i] > arr[best]) best = i;
        return best;
    }
}
