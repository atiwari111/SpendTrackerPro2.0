package com.spendtracker.pro;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.drawable.*;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.*;

/**
 * MerchantLogoProvider v1.6
 * - Loads merchant → logo filename from assets/merchant_logo_map.json
 * - Gracefully handles missing JSON file (uses empty map)
 * - getLogo(merchant): returns logo asset filename if mapped, null otherwise
 * - getInitialDrawable(merchant): colored-initial avatar fallback
 * - Fixed: deprecated BitmapDrawable(null, bmp) replaced with resources version
 */
public class MerchantLogoProvider {

    private static final Map<String, String> logoMap = new HashMap<>();
    private static boolean loaded = false;

    /** Call once from MainActivity or Application onCreate */
    public static void load(Context context) {
        if (loaded) return;
        try {
            InputStream is = context.getAssets().open("merchant_logo_map.json");
            byte[] buffer = new byte[is.available()];
            //noinspection ResultOfMethodCallIgnored
            is.read(buffer);
            is.close();
            JSONObject obj = new JSONObject(new String(buffer, "UTF-8"));
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                logoMap.put(key.toLowerCase(Locale.ROOT), obj.getString(key));
            }
        } catch (Exception e) {
            // merchant_logo_map.json not present in assets — silently use empty map.
            // App works fine without it; initial avatars are shown instead.
        }
        loaded = true; // mark loaded even on failure so we don't retry every time
    }

    /**
     * Returns the PNG asset filename for a merchant, or null if not mapped.
     */
    public static String getLogo(String merchant) {
        if (merchant == null || merchant.isEmpty()) return null;
        String m = merchant.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : logoMap.entrySet()) {
            if (m.contains(entry.getKey())) return entry.getValue();
        }
        return null;
    }

    /**
     * Returns a colored circle Drawable with the merchant's first initial.
     * Used as a reliable fallback when real PNG logos are not bundled.
     * Color is deterministic — same merchant always gets the same color.
     *
     * @param resources Pass context.getResources() to avoid deprecation warning.
     *                  May be null for backward compatibility (uses display metrics default).
     */
    public static Drawable getInitialDrawable(String merchant, Resources resources) {
        String display = (merchant != null && !merchant.isEmpty())
                ? String.valueOf(merchant.charAt(0)).toUpperCase(Locale.ROOT)
                : "?";

        int color = pickColor(merchant);

        int size = 88; // pixels (~44dp @ 2x)
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(color);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, bg);

        Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        txt.setColor(Color.WHITE);
        txt.setTextSize(size * 0.44f);
        txt.setTypeface(Typeface.DEFAULT_BOLD);
        txt.setTextAlign(Paint.Align.CENTER);
        float yPos = size / 2f - ((txt.descent() + txt.ascent()) / 2f);
        canvas.drawText(display, size / 2f, yPos, txt);

        // FIX: Use resources-aware constructor to avoid deprecation warning
        return new BitmapDrawable(resources, bmp);
    }

    /**
     * Convenience overload — used from TransactionAdapter where Context is available.
     */
    public static Drawable getInitialDrawable(String merchant) {
        return getInitialDrawable(merchant, Resources.getSystem());
    }

    // ── Color palette ─────────────────────────────────────────────

    private static final int[] PALETTE = {
        0xFF6366F1, 0xFF8B5CF6, 0xFFEC4899, 0xFF14B8A6,
        0xFFF59E0B, 0xFF10B981, 0xFFEF4444, 0xFF3B82F6,
        0xFFD97706, 0xFF059669, 0xFF7C3AED, 0xFFDB2777
    };

    private static int pickColor(String merchant) {
        if (merchant == null || merchant.isEmpty()) return PALETTE[0];
        int hash = 0;
        for (char c : merchant.toCharArray()) hash = hash * 31 + c;
        return PALETTE[Math.abs(hash) % PALETTE.length];
    }
}
