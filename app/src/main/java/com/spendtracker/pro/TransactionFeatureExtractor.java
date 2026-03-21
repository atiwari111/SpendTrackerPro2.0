package com.spendtracker.pro;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Calendar;

/**
 * TransactionFeatureExtractor
 *
 * Builds an 86-element float feature vector from transaction fields.
 * Layout is IDENTICAL to generate_model.py — any change here must be
 * mirrored in Python and a new .tflite model must be generated.
 *
 * VECTOR LAYOUT
 *  [0  ..63]  Char-trigram hashed bag-of-words  (64 buckets, L1-normalised)
 *  [64 ..71]  Amount bucket one-hot              (8 log-scale buckets)
 *  [72 ..78]  Day-of-week one-hot                (0=Mon … 6=Sun)
 *  [79]       Day-of-month normalised            (1/31 … 31/31)
 *  [80]       Hour-of-day normalised             (0/23 … 23/23)
 *  [81 ..85]  Payment method one-hot             (UPI/Credit/Debit/Cash/Bank)
 *
 * CATEGORY INDEX ORDER (must match generate_model.py CATEGORY_LABELS list)
 *  0:Food  1:Groceries  2:Transport  3:Fuel  4:Travel  5:Shopping
 *  6:Rent  7:Bills  8:Entertainment  9:Health  10:Medicine  11:Education
 *  12:Fitness  13:Investment  14:Gifts  15:Beauty  16:Income  17:Others
 */
public class TransactionFeatureExtractor {

    public static final int FEATURE_SIZE    = 86;
    public static final int TRIGRAM_BUCKETS = 64;
    public static final int NUM_CATEGORIES  = 18;

    /** CategoryEngine key in insertion order — index = model output index */
    public static final String[] CATEGORY_ORDER = {
        "🍽️ Cafe & Food Delivery", "🛒 Groceries", "🚗 Transport", "⛽ Fuel",
        "✈️ Travel", "🛍️ Shopping", "🏠 Rent", "🔌 Bills",
        "🎬 Entertainment", "🏥 Health", "💊 Medicine", "📚 Education",
        "💪 Fitness", "💰 Investment", "🎁 Gifts", "💄 Beauty & Salon", "💚 Income", "💼 Others"
    };

    private static final double[] AMOUNT_THRESHOLDS = {0, 50, 150, 300, 500, 1000, 3000};

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /** Build feature vector from a full Transaction object. */
    public static float[] extract(Transaction tx) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(tx.timestamp);
        int dow = cal.get(Calendar.DAY_OF_WEEK) - 2; // Calendar: 1=Sun → convert 0=Mon..6=Sun
        return extract(
            tx.merchant != null ? tx.merchant : "",
            tx.amount,
            ((dow % 7) + 7) % 7,
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.HOUR_OF_DAY),
            tx.paymentDetail != null ? tx.paymentDetail
                : (tx.paymentMethod != null ? tx.paymentMethod : "")
        );
    }

    /** Build feature vector for a partial entry (while user is still typing). */
    public static float[] extract(String merchant, double amount,
                                   int dayOfWeek, int dayOfMonth,
                                   int hourOfDay, String paymentMethod) {
        float[] vec = new float[FEATURE_SIZE];

        float[] trigrams = trigramFeatures(merchant);
        System.arraycopy(trigrams, 0, vec, 0, TRIGRAM_BUCKETS);

        float[] amountF = amountFeatures(amount);
        System.arraycopy(amountF, 0, vec, TRIGRAM_BUCKETS, 8);

        int dow = ((dayOfWeek % 7) + 7) % 7;
        vec[72 + dow] = 1.0f;

        vec[79] = Math.min(Math.max(dayOfMonth, 1), 31) / 31.0f;
        vec[80] = Math.min(Math.max(hourOfDay, 0), 23) / 23.0f;

        float[] payF = paymentFeatures(paymentMethod);
        System.arraycopy(payF, 0, vec, 81, 5);

        return vec;
    }

    /** Model output index → CategoryEngine category key */
    public static String indexToCategory(int index) {
        if (index < 0 || index >= CATEGORY_ORDER.length) return "💼 Others";
        return CATEGORY_ORDER[index];
    }

    /** CategoryEngine category key → model output index */
    public static int categoryToIndex(String category) {
        for (int i = 0; i < CATEGORY_ORDER.length; i++) {
            if (CATEGORY_ORDER[i].equals(category)) return i;
        }
        return NUM_CATEGORIES - 1;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Feature builders
    // ─────────────────────────────────────────────────────────────────────────

    /** Char-trigram bag-of-words, hashed to TRIGRAM_BUCKETS, L1-normalised. */
    static float[] trigramFeatures(String merchant) {
        float[] feats = new float[TRIGRAM_BUCKETS];
        if (merchant == null || merchant.isEmpty()) return feats;
        String m = merchant.toLowerCase()
                           .replaceAll("[^a-z0-9 ]", " ")
                           .trim().replaceAll("\\s+", " ");
        String padded = "  " + m + "  ";
        int count = 0;
        for (int i = 0; i <= padded.length() - 3; i++) {
            feats[md5Bucket(padded.substring(i, i + 3), TRIGRAM_BUCKETS)] += 1.0f;
            count++;
        }
        if (count > 0) {
            for (int i = 0; i < TRIGRAM_BUCKETS; i++) feats[i] /= count;
        }
        return feats;
    }

    /** Log-scale 8-bucket one-hot for transaction amount. */
    static float[] amountFeatures(double amount) {
        float[] feats = new float[8];
        int bucket = AMOUNT_THRESHOLDS.length;
        for (int i = 0; i < AMOUNT_THRESHOLDS.length; i++) {
            if (amount <= AMOUNT_THRESHOLDS[i]) { bucket = i; break; }
        }
        feats[bucket] = 1.0f;
        return feats;
    }

    /** Payment method one-hot: [UPI, Credit, Debit, Cash, Bank] */
    static float[] paymentFeatures(String paymentMethod) {
        float[] feats = new float[5];
        if (paymentMethod == null || paymentMethod.isEmpty()) { feats[0] = 1.0f; return feats; }
        String pm = paymentMethod.toLowerCase();
        if (pm.contains("upi"))                                       { feats[0] = 1.0f; }
        else if (pm.contains("credit"))                               { feats[1] = 1.0f; }
        else if (pm.contains("debit"))                                { feats[2] = 1.0f; }
        else if (pm.contains("cash"))                                 { feats[3] = 1.0f; }
        else if (pm.contains("bank")||pm.contains("neft")
              ||pm.contains("imps")||pm.contains("rtgs"))            { feats[4] = 1.0f; }
        else                                                          { feats[0] = 1.0f; }
        return feats;
    }

    /** MD5 of trigram → non-negative bucket (mirrors Python md5 approach). */
    private static int md5Bucket(String text, int buckets) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            long val = ((long)(hash[0] & 0xFF) << 24) | ((long)(hash[1] & 0xFF) << 16)
                     | ((long)(hash[2] & 0xFF) << 8)  |  (long)(hash[3] & 0xFF);
            return (int)(val % buckets);
        } catch (Exception e) {
            return Math.abs(text.hashCode()) % buckets;
        }
    }
}
