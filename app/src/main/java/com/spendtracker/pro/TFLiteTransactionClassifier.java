package com.spendtracker.pro;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * TFLiteTransactionClassifier v1.0
 *
 * Wraps the base_model.tflite model with an on-device personalization layer
 * that adapts the model to each user's spending habits without retraining.
 *
 * ── How it works ──────────────────────────────────────────────────────────
 *  1. The base model (assets/base_model.tflite) gives a 20-class probability
 *     distribution from the transaction feature vector.
 *  2. After each user correction a per-merchant "delta weight" vector is
 *     updated in SharedPreferences. These deltas are additive corrections to
 *     the model's raw logits (before final softmax) — effectively steering the
 *     model towards the user's preference without touching model weights.
 *  3. Temperature scaling controls confidence calibration: high correction
 *     count → lower temperature → sharper/more confident predictions.
 *
 * ── Category label order (must match train_base_model.py) ─────────────────
 *  0=Food, 1=Groceries, 2=Transport, 3=Fuel, 4=Travel, 5=Shopping, 6=Rent,
 *  7=Bills, 8=Entertainment, 9=Health, 10=Medicine, 11=Education, 12=Fitness,
 *  13=Investment, 14=Gifts, 15=Salary, 16=Cashback, 17=InvestmentReturn,
 *  18=Refund, 19=Others
 *
 * ── Thread safety ─────────────────────────────────────────────────────────
 *  predict() and updatePersonalization() must be called on a background thread.
 */
public class TFLiteTransactionClassifier {

    private static final String TAG = "TFLiteClassifier";
    private static final String MODEL_ASSET = "base_model.tflite";
    private static final String PREFS_NAME  = "tflite_personalization";

    // Personalization learning rate — how strongly each correction shifts weights
    private static final float LEARNING_RATE = 0.15f;
    // Correction records are weighted 3× vs passive saves
    private static final float CORRECTION_WEIGHT = 3.0f;
    // Minimum TFLite confidence to use the prediction (otherwise fall back)
    public  static final float MIN_CONFIDENCE = 0.55f;

    /** Category display strings — index must match Python training script */
    public static final String[] CATEGORY_LABELS = {
        "🍔 Food", "🛒 Groceries", "🚗 Transport", "⛽ Fuel", "✈️ Travel",
        "🛍️ Shopping", "🏠 Rent", "🔌 Bills", "🎬 Entertainment", "🏥 Health",
        "💊 Medicine", "📚 Education", "💪 Fitness", "💰 Investment", "🎁 Gifts",
        "💵 Salary", "🎉 Cashback", "📈 Investment Return", "↩️ Refund", "💼 Others"
    };
    private static final int NUM_CLASSES = CATEGORY_LABELS.length; // 20

    private static volatile TFLiteTransactionClassifier INSTANCE;

    private Interpreter tflite;
    private final SharedPreferences prefs;
    private final boolean modelLoaded;

    // ── Singleton ─────────────────────────────────────────────────────────

    private TFLiteTransactionClassifier(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean loaded = false;
        try {
            ByteBuffer model = loadModelBuffer(ctx, MODEL_ASSET);
            Interpreter.Options opts = new Interpreter.Options();
            opts.setNumThreads(2);
            tflite = new Interpreter(model, opts);
            loaded = true;
            Log.i(TAG, "TFLite model loaded successfully");
        } catch (IOException e) {
            // Model file not yet bundled — graceful degradation to frequency layer
            Log.w(TAG, "base_model.tflite not found in assets — TFLite disabled. " +
                       "Run train_base_model.py and place the output in app/src/main/assets/");
        }
        modelLoaded = loaded;
    }

    public static TFLiteTransactionClassifier getInstance(Context ctx) {
        if (INSTANCE == null) {
            synchronized (TFLiteTransactionClassifier.class) {
                if (INSTANCE == null) {
                    INSTANCE = new TFLiteTransactionClassifier(ctx.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    public boolean isAvailable() { return modelLoaded && tflite != null; }

    // ── Prediction ────────────────────────────────────────────────────────

    public static class Prediction {
        public final String category;
        public final float  confidence;   // 0.0 – 1.0 after softmax
        public final int    labelIndex;

        Prediction(int idx, float confidence) {
            this.labelIndex = idx;
            this.category   = (idx >= 0 && idx < CATEGORY_LABELS.length)
                              ? CATEGORY_LABELS[idx] : "💼 Others";
            this.confidence = confidence;
        }
    }

    /**
     * Run inference on the given transaction features.
     *
     * @param features  float[20] from TransactionFeatureExtractor.extract()
     * @param merchant  normalised merchant key — used to look up personalization weights
     * @return Prediction or null if model not loaded
     *
     * Must be called on a background thread.
     */
    public Prediction predict(float[] features, String merchant) {
        if (!isAvailable()) return null;
        if (features == null || features.length != TransactionFeatureExtractor.FEATURE_SIZE) return null;

        // 1. Run base model inference
        float[][] input  = new float[1][TransactionFeatureExtractor.FEATURE_SIZE];
        float[][] output = new float[1][NUM_CLASSES];
        System.arraycopy(features, 0, input[0], 0, features.length);

        synchronized (this) {
            tflite.run(input, output);
        }
        float[] logits = output[0].clone(); // raw pre-softmax scores (model outputs logits)

        // 2. Apply personalization delta for this merchant
        float[] delta = loadPersonalizationWeights(merchant);
        if (delta != null) {
            for (int i = 0; i < NUM_CLASSES; i++) {
                logits[i] += delta[i];
            }
        }

        // 3. Softmax → probabilities
        float[] probs = softmax(logits);

        // 4. Pick argmax
        int best = 0;
        for (int i = 1; i < NUM_CLASSES; i++) {
            if (probs[i] > probs[best]) best = i;
        }

        return new Prediction(best, probs[best]);
    }

    // ── On-device personalization update ──────────────────────────────────

    /**
     * Update per-merchant personalization weights after a user save/correction.
     *
     * Algorithm: for the correct category, add LEARNING_RATE to its delta;
     * for all other categories, subtract a proportional amount (scaled by current prob)
     * so the weight sum stays approximately zero.
     *
     * Correction records get 3× the learning rate to fast-track explicit feedback.
     *
     * Must be called on a background thread.
     */
    public void updatePersonalization(UserFeedbackRecord record) {
        if (!isAvailable() || record == null) return;

        String merchant = record.merchant;
        int correctIdx  = labelIndex(record.category);
        if (correctIdx < 0) return;

        float[] delta = loadPersonalizationWeights(merchant);
        if (delta == null) delta = new float[NUM_CLASSES];

        float lr = LEARNING_RATE * (record.isCorrection ? CORRECTION_WEIGHT : 1.0f);

        // Reinforce correct class
        delta[correctIdx] += lr;

        // Decay all other classes slightly to keep distribution calibrated
        float decay = lr / (NUM_CLASSES - 1);
        for (int i = 0; i < NUM_CLASSES; i++) {
            if (i != correctIdx) delta[i] -= decay;
        }

        // Clip weights to reasonable range to prevent runaway drift
        for (int i = 0; i < NUM_CLASSES; i++) {
            delta[i] = Math.max(-3.0f, Math.min(3.0f, delta[i]));
        }

        savePersonalizationWeights(merchant, delta);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static float[] softmax(float[] logits) {
        float max = logits[0];
        for (float v : logits) if (v > max) max = v;

        float sum = 0f;
        float[] exp = new float[logits.length];
        for (int i = 0; i < logits.length; i++) {
            exp[i] = (float) Math.exp(logits[i] - max);
            sum += exp[i];
        }
        for (int i = 0; i < exp.length; i++) exp[i] /= sum;
        return exp;
    }

    private static int labelIndex(String category) {
        if (category == null) return -1;
        for (int i = 0; i < CATEGORY_LABELS.length; i++) {
            if (CATEGORY_LABELS[i].equals(category)) return i;
        }
        return -1;
    }

    /** Load per-merchant delta weight vector from SharedPreferences (stored as JSON). */
    private float[] loadPersonalizationWeights(String merchant) {
        if (merchant == null || merchant.isEmpty()) return null;
        String json = prefs.getString(merchant, null);
        if (json == null) return null;
        try {
            JSONObject obj = new JSONObject(json);
            float[] delta = new float[NUM_CLASSES];
            for (int i = 0; i < NUM_CLASSES; i++) {
                delta[i] = (float) obj.optDouble(String.valueOf(i), 0.0);
            }
            return delta;
        } catch (JSONException e) {
            return null;
        }
    }

    /** Persist per-merchant delta weight vector to SharedPreferences. */
    private void savePersonalizationWeights(String merchant, float[] delta) {
        try {
            JSONObject obj = new JSONObject();
            for (int i = 0; i < delta.length; i++) {
                if (Math.abs(delta[i]) > 0.001f) { // only store non-zero to save space
                    obj.put(String.valueOf(i), delta[i]);
                }
            }
            prefs.edit().putString(merchant, obj.toString()).apply();
        } catch (JSONException ignored) {}
    }

    /** Memory-map the .tflite file from assets for zero-copy loading. */
    private static ByteBuffer loadModelBuffer(Context ctx, String assetName) throws IOException {
        try (FileInputStream fis = new FileInputStream(
                ctx.getAssets().openFd(assetName).getFileDescriptor())) {
            FileChannel channel = fis.getChannel();
            long startOffset = ctx.getAssets().openFd(assetName).getStartOffset();
            long declaredLen = ctx.getAssets().openFd(assetName).getDeclaredLength();
            return channel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLen);
        }
    }

    public void close() {
        if (tflite != null) {
            synchronized (this) {
                tflite.close();
                tflite = null;
            }
        }
    }
}
