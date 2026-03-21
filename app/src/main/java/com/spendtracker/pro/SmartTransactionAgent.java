package com.spendtracker.pro;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.*;

/**
 * SmartTransactionAgent — Room frequency-map layer (Layer 1).
 * Works from day 1 with no model file required.
 */
public class SmartTransactionAgent {

    private static final String TAG = "SmartTxAgent";
    private static volatile SmartTransactionAgent INSTANCE;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final MerchantPatternDao dao;

    private SmartTransactionAgent(Context ctx) {
        dao = AppDatabase.getInstance(ctx).merchantPatternDao();
    }

    public static SmartTransactionAgent getInstance(Context ctx) {
        if (INSTANCE == null) {
            synchronized (SmartTransactionAgent.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SmartTransactionAgent(ctx.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    // ── Suggestion result ─────────────────────────────────────────────────

    public static class Suggestion {
        public final String  category;
        public final String  paymentMethod;
        public final double  avgAmount;
        @Nullable public final String notesHint;
        public final float   confidence;
        public final int     frequency;

        Suggestion(String category, String paymentMethod,
                   double avgAmount, @Nullable String notesHint,
                   float confidence, int frequency) {
            this.category      = category;
            this.paymentMethod = paymentMethod;
            this.avgAmount     = avgAmount;
            this.notesHint     = notesHint;
            this.confidence    = confidence;
            this.frequency     = frequency;
        }
    }

    // ── Learn ─────────────────────────────────────────────────────────────

    public void learn(@NonNull Transaction tx) {
        if (tx.merchant == null || tx.merchant.trim().isEmpty()) return;
        if (tx.isSelfTransfer) return;

        String key = normalise(tx.merchant);
        MerchantPattern existing = dao.getByMerchant(key);

        if (existing == null) {
            MerchantPattern p  = new MerchantPattern();
            p.merchant         = key;
            p.displayName      = tx.merchant.trim();
            p.topCategory      = tx.category;
            p.categoryFreqJson = singleEntryJson(tx.category);
            p.topPayment       = tx.paymentDetail != null ? tx.paymentDetail : tx.paymentMethod;
            p.paymentFreqJson  = singleEntryJson(p.topPayment);
            p.avgAmount        = tx.amount;
            p.lastNotes        = tx.notes;
            p.frequency        = 1;
            p.lastUsed         = tx.timestamp;
            dao.upsert(p);
        } else {
            existing.displayName      = tx.merchant.trim();
            existing.categoryFreqJson = incrementFreq(existing.categoryFreqJson, tx.category);
            existing.topCategory      = topKey(existing.categoryFreqJson);

            String payUsed = tx.paymentDetail != null ? tx.paymentDetail : tx.paymentMethod;
            existing.paymentFreqJson  = incrementFreq(existing.paymentFreqJson, payUsed);
            existing.topPayment       = topKey(existing.paymentFreqJson);

            if (!tx.isCredit) {
                existing.avgAmount = rollingAvg(existing.avgAmount, existing.frequency, tx.amount);
            }
            if (tx.notes != null && !tx.notes.trim().isEmpty()) {
                existing.lastNotes = tx.notes.trim();
            }
            existing.frequency++;
            existing.lastUsed = Math.max(existing.lastUsed, tx.timestamp);
            dao.upsert(existing);
        }
        Log.d(TAG, "Learned: " + key + " -> " + tx.category);
    }

    // ── Suggest (background thread) ───────────────────────────────────────

    @Nullable
    public Suggestion getSuggestion(String merchantTyped) {
        if (merchantTyped == null || merchantTyped.trim().length() < 2) return null;
        String q = normalise(merchantTyped);

        MerchantPattern exact = dao.getByMerchant(q);
        if (exact != null) return toSuggestion(exact);

        List<MerchantPattern> prefixList = dao.getByPrefix(q + "%");
        if (!prefixList.isEmpty()) return toSuggestion(bestByFreq(prefixList));

        MerchantPattern tokenMatch = bestTokenMatch(q);
        if (tokenMatch != null) return toSuggestion(tokenMatch);

        return null;
    }

    public void suggestAsync(String merchantTyped, SuggestionCallback callback) {
        AppExecutors.db().execute(() -> {
            Suggestion s = getSuggestion(merchantTyped);
            MAIN.post(() -> callback.onResult(s));
        });
    }

    public interface SuggestionCallback {
        void onResult(@Nullable Suggestion suggestion);
    }

    // ── Autocomplete ──────────────────────────────────────────────────────

    public List<String> getAutocompleteSuggestions(String prefix, int limit) {
        if (prefix == null || prefix.trim().isEmpty()) return Collections.emptyList();
        String q = normalise(prefix);
        List<MerchantPattern> rows = dao.getByPrefix(q + "%");
        List<MerchantPattern> all  = new ArrayList<>(rows);
        for (MerchantPattern p : dao.getTopByFrequency(50)) {
            if (!all.contains(p) && tokenScore(q, p.merchant) > 0) all.add(p);
        }
        all.sort((a, b) -> Integer.compare(b.frequency, a.frequency));
        List<String> names = new ArrayList<>();
        for (MerchantPattern p : all) {
            if (names.size() >= limit) break;
            names.add(p.displayName != null ? p.displayName : p.merchant);
        }
        return names;
    }

    // ── Helpers (package-accessible) ──────────────────────────────────────

    public static String normalise(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).trim()
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ").trim();
    }

    static String incrementFreq(String json, String key) {
        if (key == null || key.isEmpty()) return json != null ? json : "{}";
        try {
            JSONObject obj = (json != null && !json.isEmpty())
                             ? new JSONObject(json) : new JSONObject();
            obj.put(key, obj.optInt(key, 0) + 1);
            return obj.toString();
        } catch (JSONException e) { return singleEntryJson(key); }
    }

    static String topKey(String json) {
        if (json == null || json.isEmpty()) return null;
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

    private Suggestion toSuggestion(MerchantPattern p) {
        float confidence = Math.min(1.0f, p.frequency / 10.0f);
        return new Suggestion(p.topCategory, p.topPayment, p.avgAmount,
                              p.lastNotes, confidence, p.frequency);
    }

    private static String singleEntryJson(String key) {
        try { return new JSONObject().put(key, 1).toString(); }
        catch (JSONException e) { return "{}"; }
    }

    private static double rollingAvg(double prevAvg, int prevCount, double newVal) {
        if (prevCount <= 0) return newVal;
        if (prevCount < 5) return (prevAvg * prevCount + newVal) / (prevCount + 1);
        return prevAvg * 0.8 + newVal * 0.2;
    }

    private MerchantPattern bestByFreq(List<MerchantPattern> list) {
        MerchantPattern best = list.get(0);
        for (MerchantPattern p : list) if (p.frequency > best.frequency) best = p;
        return best;
    }

    private static int tokenScore(String query, String stored) {
        if (query.isEmpty() || stored == null) return 0;
        int score = 0;
        for (String t : query.split(" ")) if (t.length() >= 3 && stored.contains(t)) score++;
        return score;
    }

    @Nullable
    private MerchantPattern bestTokenMatch(String query) {
        List<MerchantPattern> candidates = dao.getTopByFrequency(100);
        MerchantPattern best = null; int bestScore = 0;
        for (MerchantPattern p : candidates) {
            int score = tokenScore(query, p.merchant);
            if (score > bestScore) { bestScore = score; best = p; }
        }
        return bestScore > 0 ? best : null;
    }
}
