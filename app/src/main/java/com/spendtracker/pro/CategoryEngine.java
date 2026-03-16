package com.spendtracker.pro;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

public class CategoryEngine {

    public static class CategoryInfo {
        public final String name, icon;
        public final int color;

        public CategoryInfo(String name, String icon, int color) {
            this.name = name;
            this.icon = icon;
            this.color = color;
        }
    }

    //────────────────────────────
    // CATEGORY LIST
    //────────────────────────────

    public static final Map<String, CategoryInfo> CATEGORIES = new LinkedHashMap<>();

    static {

        CATEGORIES.put("🍔 Food", new CategoryInfo("🍔 Food","🍔",0xFFFF6B6B));
        CATEGORIES.put("🛒 Groceries", new CategoryInfo("🛒 Groceries","🛒",0xFF4ECDC4));
        CATEGORIES.put("🚗 Transport", new CategoryInfo("🚗 Transport","🚗",0xFF45B7D1));
        CATEGORIES.put("⛽ Fuel", new CategoryInfo("⛽ Fuel","⛽",0xFFFFBE0B));
        CATEGORIES.put("✈️ Travel", new CategoryInfo("✈️ Travel","✈️",0xFF96CEB4));
        CATEGORIES.put("🛍️ Shopping", new CategoryInfo("🛍️ Shopping","🛍️",0xFFDDA0DD));
        CATEGORIES.put("🏠 Rent", new CategoryInfo("🏠 Rent","🏠",0xFFFF8C69));
        CATEGORIES.put("🔌 Bills", new CategoryInfo("🔌 Bills","🔌",0xFFA8E6CF));
        CATEGORIES.put("🎬 Entertainment", new CategoryInfo("🎬 Entertainment","🎬",0xFFFFD3B6));
        CATEGORIES.put("🏥 Health", new CategoryInfo("🏥 Health","🏥",0xFFFF6B9D));
        CATEGORIES.put("💊 Medicine", new CategoryInfo("💊 Medicine","💊",0xFFB8E0FF));
        CATEGORIES.put("📚 Education", new CategoryInfo("📚 Education","📚",0xFFC3B1E1));
        CATEGORIES.put("💪 Fitness", new CategoryInfo("💪 Fitness","💪",0xFF98D8C8));
        CATEGORIES.put("💰 Investment", new CategoryInfo("💰 Investment","💰",0xFFFFD700));
        CATEGORIES.put("🎁 Gifts", new CategoryInfo("🎁 Gifts","🎁",0xFFFF9AA2));

        // income
        CATEGORIES.put("💵 Salary", new CategoryInfo("💵 Salary","💵",0xFF4CAF50));
        CATEGORIES.put("🎉 Cashback", new CategoryInfo("🎉 Cashback","🎉",0xFF81C784));
        CATEGORIES.put("📈 Investment Return", new CategoryInfo("📈 Investment Return","📈",0xFF66BB6A));
        CATEGORIES.put("↩️ Refund", new CategoryInfo("↩️ Refund","↩️",0xFF64B5F6));

        CATEGORIES.put("💼 Others", new CategoryInfo("💼 Others","💼",0xFFB0BEC5));
    }

    //────────────────────────────
    // MERCHANT MAP (Expanded)
    //────────────────────────────

    public static final Map<String,String> MERCHANT_MAP = new LinkedHashMap<>();

    static {

        MERCHANT_MAP.put("swiggy","🍔 Food");
        MERCHANT_MAP.put("zomato","🍔 Food");
        MERCHANT_MAP.put("dominos","🍔 Food");
        MERCHANT_MAP.put("pizza","🍔 Food");
        MERCHANT_MAP.put("kfc","🍔 Food");
        MERCHANT_MAP.put("mcdonald","🍔 Food");

        MERCHANT_MAP.put("amazon","🛍️ Shopping");
        MERCHANT_MAP.put("flipkart","🛍️ Shopping");
        MERCHANT_MAP.put("myntra","🛍️ Shopping");
        MERCHANT_MAP.put("meesho","🛍️ Shopping");
        MERCHANT_MAP.put("ajio","🛍️ Shopping");

        MERCHANT_MAP.put("uber","🚗 Transport");
        MERCHANT_MAP.put("ola","🚗 Transport");
        MERCHANT_MAP.put("rapido","🚗 Transport");

        MERCHANT_MAP.put("hp petrol","⛽ Fuel");
        MERCHANT_MAP.put("indianoil","⛽ Fuel");
        MERCHANT_MAP.put("shell","⛽ Fuel");

        MERCHANT_MAP.put("netflix","🎬 Entertainment");
        MERCHANT_MAP.put("spotify","🎬 Entertainment");
        MERCHANT_MAP.put("youtube","🎬 Entertainment");

        MERCHANT_MAP.put("zerodha","💰 Investment");
        MERCHANT_MAP.put("groww","💰 Investment");
        MERCHANT_MAP.put("upstox","💰 Investment");

        MERCHANT_MAP.put("paytm","🔌 Bills");
        MERCHANT_MAP.put("phonepe","🔌 Bills");
        MERCHANT_MAP.put("gpay","🔌 Bills");
        MERCHANT_MAP.put("googlepay","🔌 Bills");
    }

    //────────────────────────────
    // MERCHANT LEARNING
    //────────────────────────────

    private static final String PREFS_NAME="merchant_learning";
    private static SharedPreferences learningPrefs;

    public static void init(Context ctx){
        learningPrefs=ctx.getSharedPreferences(PREFS_NAME,Context.MODE_PRIVATE);
    }

    public static void learnMerchant(String merchant,String category){

        if(learningPrefs==null||merchant==null||category==null) return;

        learningPrefs.edit()
                .putString(merchant.toLowerCase().trim(),category)
                .apply();
    }

    private static String getLearnedCategory(String merchant){

        if(learningPrefs==null||merchant==null) return null;

        return learningPrefs.getString(merchant.toLowerCase().trim(),null);
    }

    //────────────────────────────
    // SMART UPI MERCHANT CLEANER
    //────────────────────────────

    public static String resolveUpiMerchant(String merchant){

        if(merchant==null) return "Unknown";

        String m = merchant.toLowerCase();

        m = m.replaceAll("paytmqr","paytm");
        m = m.replaceAll("@.*","");

        if(m.contains("amazon")) return "Amazon";
        if(m.contains("flipkart")) return "Flipkart";
        if(m.contains("swiggy")) return "Swiggy";
        if(m.contains("zomato")) return "Zomato";
        if(m.contains("uber")) return "Uber";
        if(m.contains("ola")) return "Ola";
        if(m.contains("paytm")) return "Paytm";
        if(m.contains("phonepe")) return "PhonePe";
        if(m.contains("gpay")) return "Google Pay";

        return merchant;
    }

    //────────────────────────────
    // SIMPLE AUTO CATEGORY
    //────────────────────────────

    public static String autoCategory(String merchant){

        if(merchant==null) return "💼 Others";

        String m = merchant.toLowerCase();

        for(Map.Entry<String,String> e:MERCHANT_MAP.entrySet()){

            if(m.contains(e.getKey()))
                return e.getValue();
        }

        // NLP fallback for unknown merchants
        String nlp = NlpCategorizer.predictWithSource(merchant);
        if (nlp != null) return nlp;

        return "💼 Others";
    }

    //────────────────────────────
    // CLASSIFICATION ENGINE
    //────────────────────────────

    public static String classify(String merchant,String smsBody){

        if(merchant==null) merchant="";

        merchant = resolveUpiMerchant(merchant).toLowerCase();

        String body = smsBody!=null ? smsBody.toLowerCase() : "";

        // income detection

        if(body.contains("salary"))
            return "💵 Salary";

        if(body.contains("cashback"))
            return "🎉 Cashback";

        if(body.contains("refund"))
            return "↩️ Refund";

        if(body.contains("interest") || body.contains("dividend"))
            return "📈 Investment Return";

        // learned merchants

        String learned=getLearnedCategory(merchant);

        if(learned!=null) return learned;

        // merchant map

        for(Map.Entry<String,String> e:MERCHANT_MAP.entrySet()){

            if(merchant.contains(e.getKey()))
                return e.getValue();
        }

        // NLP fallback: decompose merchant into tokens and match semantic word groups
        // Only fires when no exact keyword match was found above
        String nlpResult = NlpCategorizer.predictWithSource(merchant);
        if (nlpResult != null) return nlpResult;

        // fallback

        return "💼 Others";
    }

    public static CategoryInfo getInfo(String category){

        CategoryInfo info=CATEGORIES.get(category);

        return info!=null?info:CATEGORIES.get("💼 Others");
    }

    public static String[] getCategoryNames(){

        return CATEGORIES.keySet().toArray(new String[0]);
    }
}
