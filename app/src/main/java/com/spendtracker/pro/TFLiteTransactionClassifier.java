package com.spendtracker.pro;

import android.content.Context;
import android.util.Log;
import androidx.annotation.Nullable;
import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Iterator;

/**
 * TFLiteTransactionClassifier — Layer 2 inference + on-device personalization.
 *
 * Fixes vs previous version:
 *  1. loadModelBuffer keeps FileInputStream + channel open (never close them —
 *     closing the channel invalidates the MappedByteBuffer on some devices).
 *  2. Constructor catches ALL Exception types, not just IOException — TFLite
 *     Interpreter() can throw IllegalArgumentException / RuntimeException if
 *     the model shape doesn't match, and those were crashing the app.
 *  3. getInstance() is safe to call from the main thread — model loading is
 *     fast (<50ms for an 80KB quantized model).
 */
public class TFLiteTransactionClassifier {

    private static final String TAG         = "TFLiteClassifier";
    private static final String MODEL_ASSET = "base_model.tflite";
    private static final String PREFS_NAME  = "tflite_personalization";

    private static final float LEARNING_RATE     = 0.15f;
    private static final float CORRECTION_WEIGHT = 3.0f;
    public  static final float MIN_CONFIDENCE    = 0.55f;

    // Sourced from TransactionFeatureExtractor so there is one source of truth
    private static final int FEATURE_SIZE = TransactionFeatureExtractor.FEATURE_SIZE;
    private static final int NUM_CLASSES  = TransactionFeatureExtractor.NUM_CATEGORIES;

    private static volatile TFLiteTransactionClassifier INSTANCE;

    private Interpreter tflite;
    private final android.content.SharedPreferences prefs;
    private final boolean modelLoaded;

    // Keep these alive so the MappedByteBuffer stays valid
    private FileInputStream    modelStream;
    private FileChannel        modelChannel;

    // ── Singleton ─────────────────────────────────────────────────────────

    private TFLiteTransactionClassifier(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean loaded = false;
        try {
            MappedByteBuffer model = loadModelBuffer(ctx, MODEL_ASSET);
            Interpreter.Options opts = new Interpreter.Options().setNumThreads(2);
            tflite = new Interpreter(model, opts);
            loaded = true;
            Log.i(TAG, "TFLite model loaded — features=" + FEATURE_SIZE
                    + " classes=" + NUM_CLASSES);
        } catch (Exception e) {
            // Catch ALL exceptions — not just IOException.
            // TFLite Interpreter() throws IllegalArgumentException if the model
            // input/output shape doesn't match, which was crashing the app.
            Log.w(TAG, "TFLite disabled: " + e.getMessage());
            tflite = null;
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
        public final float  confidence;
        public final int    labelIndex;

        Prediction(int idx, float confidence) {
            this.labelIndex = idx;
            this.category   = TransactionFeatureExtractor.indexToCategory(idx);
            this.confidence = confidence;
        }
    }

    @Nullable
    public Prediction predict(float[] features, String merchant) {
        if (!isAvailable() || features == null || features.length != FEATURE_SIZE) return null;

        float[][] input  = new float[1][FEATURE_SIZE];
        float[][] output = new float[1][NUM_CLASSES];
        System.arraycopy(features, 0, input[0], 0, features.length);

        try {
            synchronized (this) { tflite.run(input, output); }
        } catch (Exception e) {
            Log.e(TAG, "Inference failed: " + e.getMessage());
            return null;
        }

        float[] logits = output[0].clone();

        // Apply per-merchant personalization delta
        float[] delta = loadWeights(merchant);
        if (delta != null) {
            for (int i = 0; i < NUM_CLASSES; i++) logits[i] += delta[i];
        }

        float[] probs = softmax(logits);
        int best = 0;
        for (int i = 1; i < NUM_CLASSES; i++) if (probs[i] > probs[best]) best = i;
        return new Prediction(best, probs[best]);
    }

    // ── Personalization update ─────────────────────────────────────────────

    public void updatePersonalization(UserFeedbackRecord record) {
        if (!isAvailable() || record == null) return;
        int correctIdx = TransactionFeatureExtractor.categoryToIndex(record.category);
        if (correctIdx < 0) return;

        float[] delta = loadWeights(record.merchant);
        if (delta == null) delta = new float[NUM_CLASSES];

        float lr = LEARNING_RATE * (record.isCorrection ? CORRECTION_WEIGHT : 1.0f);
        delta[correctIdx] += lr;
        float decay = lr / (NUM_CLASSES - 1);
        for (int i = 0; i < NUM_CLASSES; i++) {
            if (i != correctIdx) delta[i] -= decay;
            delta[i] = Math.max(-3.0f, Math.min(3.0f, delta[i]));
        }
        saveWeights(record.merchant, delta);
    }

    // ── Private helpers ────────────────────────────────────────────────────

    /**
     * Load the TFLite model file into a MappedByteBuffer.
     *
     * IMPORTANT: modelStream and modelChannel are intentionally NOT closed.
     * Closing the FileChannel invalidates the MappedByteBuffer on some Android
     * devices, causing a crash inside the TFLite Interpreter. The references
     * are stored as instance fields to prevent GC and keep the mapping alive.
     */
    private MappedByteBuffer loadModelBuffer(Context ctx, String assetName) throws IOException {
        android.content.res.AssetFileDescriptor afd = ctx.getAssets().openFd(assetName);
        modelStream  = new FileInputStream(afd.getFileDescriptor());
        modelChannel = modelStream.getChannel();
        return modelChannel.map(
                FileChannel.MapMode.READ_ONLY,
                afd.getStartOffset(),
                afd.getDeclaredLength());
    }

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

    @Nullable
    private float[] loadWeights(String merchant) {
        if (merchant == null || merchant.isEmpty()) return null;
        String json = prefs.getString(merchant, null);
        if (json == null) return null;
        try {
            JSONObject obj = new JSONObject(json);
            float[] delta = new float[NUM_CLASSES];
            for (int i = 0; i < NUM_CLASSES; i++)
                delta[i] = (float) obj.optDouble(String.valueOf(i), 0.0);
            return delta;
        } catch (JSONException e) { return null; }
    }

    private void saveWeights(String merchant, float[] delta) {
        try {
            JSONObject obj = new JSONObject();
            for (int i = 0; i < delta.length; i++)
                if (Math.abs(delta[i]) > 0.001f) obj.put(String.valueOf(i), delta[i]);
            prefs.edit().putString(merchant, obj.toString()).apply();
        } catch (JSONException ignored) {}
    }
}
