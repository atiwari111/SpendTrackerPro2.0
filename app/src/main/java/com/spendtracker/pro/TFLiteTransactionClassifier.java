package com.spendtracker.pro;

import android.content.Context;
import android.util.Log;
import androidx.annotation.Nullable;
import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.io.FileInputStream;
import java.util.Iterator;

/**
 * TFLiteTransactionClassifier — Layer 2 inference + on-device personalization.
 *
 * Loads base_model.tflite from assets. If the file is missing the class
 * degrades gracefully (isAvailable() returns false) and HybridTransactionAgent
 * skips this layer automatically.
 *
 * Feature vector layout must match TransactionFeatureExtractor:
 *   FEATURE_SIZE = 86, NUM_CLASSES = 20
 */
public class TFLiteTransactionClassifier {

    private static final String TAG        = "TFLiteClassifier";
    private static final String MODEL_ASSET = "base_model.tflite";
    private static final String PREFS_NAME  = "tflite_personalization";

    private static final float LEARNING_RATE    = 0.15f;
    private static final float CORRECTION_WEIGHT = 3.0f;
    public  static final float MIN_CONFIDENCE   = 0.55f;

    // Must match TransactionFeatureExtractor.FEATURE_SIZE and CATEGORY_ORDER
    private static final int FEATURE_SIZE = TransactionFeatureExtractor.FEATURE_SIZE;
    private static final int NUM_CLASSES  = TransactionFeatureExtractor.NUM_CATEGORIES;

    private static volatile TFLiteTransactionClassifier INSTANCE;

    private Interpreter tflite;
    private final android.content.SharedPreferences prefs;
    private final boolean modelLoaded;

    // ── Singleton ─────────────────────────────────────────────────────────

    private TFLiteTransactionClassifier(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean loaded = false;
        try {
            ByteBuffer model = loadModelBuffer(ctx, MODEL_ASSET);
            Interpreter.Options opts = new Interpreter.Options().setNumThreads(2);
            tflite = new Interpreter(model, opts);
            loaded = true;
            Log.i(TAG, "TFLite model loaded successfully");
        } catch (IOException e) {
            Log.w(TAG, "base_model.tflite not found in assets — TFLite disabled. "
                     + "Run train_base_model.py and place output in app/src/main/assets/");
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

        synchronized (this) { tflite.run(input, output); }
        float[] logits = output[0].clone();

        // Apply personalization delta
        float[] delta = loadWeights(merchant);
        if (delta != null) {
            for (int i = 0; i < NUM_CLASSES; i++) logits[i] += delta[i];
        }

        float[] probs = softmax(logits);
        int best = 0;
        for (int i = 1; i < NUM_CLASSES; i++) if (probs[i] > probs[best]) best = i;
        return new Prediction(best, probs[best]);
    }

    // ── Personalization update ────────────────────────────────────────────

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

    // ── Private helpers ───────────────────────────────────────────────────

    private static float[] softmax(float[] logits) {
        float max = logits[0];
        for (float v : logits) if (v > max) max = v;
        float sum = 0f;
        float[] exp = new float[logits.length];
        for (int i = 0; i < logits.length; i++) { exp[i] = (float) Math.exp(logits[i] - max); sum += exp[i]; }
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
            for (int i = 0; i < NUM_CLASSES; i++) delta[i] = (float) obj.optDouble(String.valueOf(i), 0.0);
            return delta;
        } catch (JSONException e) { return null; }
    }

    private void saveWeights(String merchant, float[] delta) {
        try {
            JSONObject obj = new JSONObject();
            for (int i = 0; i < delta.length; i++) {
                if (Math.abs(delta[i]) > 0.001f) obj.put(String.valueOf(i), delta[i]);
            }
            prefs.edit().putString(merchant, obj.toString()).apply();
        } catch (JSONException ignored) {}
    }

    private static ByteBuffer loadModelBuffer(Context ctx, String assetName) throws IOException {
        android.content.res.AssetFileDescriptor afd = ctx.getAssets().openFd(assetName);
        try (FileInputStream fis = new FileInputStream(afd.getFileDescriptor())) {
            return fis.getChannel().map(FileChannel.MapMode.READ_ONLY,
                    afd.getStartOffset(), afd.getDeclaredLength());
        }
    }
}
