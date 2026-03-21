package com.spendtracker.pro;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

public class CategoryEngine {

    public static class CategoryInfo {
        public final String name, icon;
        public final int color;
        public CategoryInfo(String name, String icon, int color) {
            this.name = name; this.icon = icon; this.color = color;
        }
    }

    // ────────────────────────────────────────────────────────────────
    // CATEGORY LIST
    // Spend categories (shown in Add/Edit expense spinner)
    // Income category (NOT shown in expense spinner — tracked separately)
    // ────────────────────────────────────────────────────────────────
    public static final String INCOME = "💚 Income";

    public static final Map<String, CategoryInfo> CATEGORIES = new LinkedHashMap<>();
    static {
        // ── Spend categories ──────────────────────────────────────
        CATEGORIES.put("🍔 Food",              new CategoryInfo("🍔 Food",              "🍔", 0xFFFF6B6B));
        CATEGORIES.put("🛒 Groceries",         new CategoryInfo("🛒 Groceries",         "🛒", 0xFF4ECDC4));
        CATEGORIES.put("🚗 Transport",         new CategoryInfo("🚗 Transport",         "🚗", 0xFF45B7D1));
        CATEGORIES.put("⛽ Fuel",              new CategoryInfo("⛽ Fuel",              "⛽", 0xFFFFBE0B));
        CATEGORIES.put("✈️ Travel",            new CategoryInfo("✈️ Travel",            "✈️", 0xFF96CEB4));
        CATEGORIES.put("🛍️ Shopping",         new CategoryInfo("🛍️ Shopping",         "🛍️", 0xFFDDA0DD));
        CATEGORIES.put("🏠 Rent",              new CategoryInfo("🏠 Rent",              "🏠", 0xFFFF8C69));
        CATEGORIES.put("🔌 Bills",             new CategoryInfo("🔌 Bills",             "🔌", 0xFFA8E6CF));
        CATEGORIES.put("🎬 Entertainment",     new CategoryInfo("🎬 Entertainment",     "🎬", 0xFFFFD3B6));
        CATEGORIES.put("🏥 Health",            new CategoryInfo("🏥 Health",            "🏥", 0xFFFF6B9D));
        CATEGORIES.put("💊 Medicine",          new CategoryInfo("💊 Medicine",          "💊", 0xFFB8E0FF));
        CATEGORIES.put("📚 Education",         new CategoryInfo("📚 Education",         "📚", 0xFFC3B1E1));
        CATEGORIES.put("💪 Fitness",           new CategoryInfo("💪 Fitness",           "💪", 0xFF98D8C8));
        CATEGORIES.put("💰 Investment",        new CategoryInfo("💰 Investment",        "💰", 0xFFFFD700));
        CATEGORIES.put("🎁 Gifts",             new CategoryInfo("🎁 Gifts",             "🎁", 0xFFFF9AA2));
        CATEGORIES.put("💼 Others",            new CategoryInfo("💼 Others",            "💼", 0xFFB0BEC5));
        // ── Income — kept in CATEGORIES for budget/net worth display ──
        CATEGORIES.put(INCOME,                 new CategoryInfo(INCOME,                 "💚", 0xFF4CAF50));
    }

    // ────────────────────────────────────────────────────────────────
    // SPEND CATEGORIES — excludes Income
    // Use this for expense spinners / budget category pickers
    // ────────────────────────────────────────────────────────────────
    public static String[] getSpendCategoryNames() {
        List<String> names = new ArrayList<>();
        for (String key : CATEGORIES.keySet()) {
            if (!key.equals(INCOME)) names.add(key);
        }
        return names.toArray(new String[0]);
    }

    /** Returns ALL categories including Income — use for budget/net worth screens. */
    public static String[] getCategoryNames() {
        return CATEGORIES.keySet().toArray(new String[0]);
    }

    /** True if the category represents money coming in (not a spend). */
    public static boolean isIncome(String category) {
        return INCOME.equals(category);
    }

    // ────────────────────────────────────────────────────────────────
    // MERCHANT MAP — 1000+ merchants covering the Indian ecosystem
    // ────────────────────────────────────────────────────────────────
    public static final Map<String, String> MERCHANT_MAP = new LinkedHashMap<>();
    static {
        // ── 🍔 FOOD ─────────────────────────────────────────────────
        String FOOD = "🍔 Food";
        MERCHANT_MAP.put("swiggy",       FOOD); MERCHANT_MAP.put("zomato",      FOOD);
        MERCHANT_MAP.put("eatclub",      FOOD); MERCHANT_MAP.put("box8",        FOOD);
        MERCHANT_MAP.put("faasos",       FOOD); MERCHANT_MAP.put("freshmenu",   FOOD);
        MERCHANT_MAP.put("dunzo",        FOOD); MERCHANT_MAP.put("zepto",       FOOD);
        MERCHANT_MAP.put("blinkit",      FOOD); MERCHANT_MAP.put("instamart",   FOOD);
        MERCHANT_MAP.put("dominos",      FOOD); MERCHANT_MAP.put("domino",      FOOD);
        MERCHANT_MAP.put("pizza hut",    FOOD); MERCHANT_MAP.put("pizzahut",    FOOD);
        MERCHANT_MAP.put("kfc",          FOOD); MERCHANT_MAP.put("mcdonald",    FOOD);
        MERCHANT_MAP.put("mcdonalds",    FOOD); MERCHANT_MAP.put("burger king", FOOD);
        MERCHANT_MAP.put("subway",       FOOD); MERCHANT_MAP.put("wendy",       FOOD);
        MERCHANT_MAP.put("hardees",      FOOD); MERCHANT_MAP.put("taco bell",   FOOD);
        MERCHANT_MAP.put("cafe coffee",  FOOD); MERCHANT_MAP.put("ccd",         FOOD);
        MERCHANT_MAP.put("starbucks",    FOOD); MERCHANT_MAP.put("barista",     FOOD);
        MERCHANT_MAP.put("costa coffee", FOOD); MERCHANT_MAP.put("third wave",  FOOD);
        MERCHANT_MAP.put("blue tokai",   FOOD); MERCHANT_MAP.put("chaayos",     FOOD);
        MERCHANT_MAP.put("haldiram",     FOOD); MERCHANT_MAP.put("bikanervala", FOOD);
        MERCHANT_MAP.put("saravana",     FOOD); MERCHANT_MAP.put("naturals",    FOOD);
        MERCHANT_MAP.put("amul",         FOOD); MERCHANT_MAP.put("baskin",      FOOD);
        MERCHANT_MAP.put("kwality",      FOOD); MERCHANT_MAP.put("havmor",      FOOD);
        MERCHANT_MAP.put("wow momo",     FOOD); MERCHANT_MAP.put("roll",        FOOD);
        MERCHANT_MAP.put("biryani",      FOOD); MERCHANT_MAP.put("punjab grill",FOOD);
        MERCHANT_MAP.put("behrouz",      FOOD); MERCHANT_MAP.put("paradise",    FOOD);

        // ── 🛒 GROCERIES ─────────────────────────────────────────────
        String GROC = "🛒 Groceries";
        MERCHANT_MAP.put("bigbasket",    GROC); MERCHANT_MAP.put("big basket",  GROC);
        MERCHANT_MAP.put("grofers",      GROC); MERCHANT_MAP.put("dmart",       GROC);
        MERCHANT_MAP.put("d mart",       GROC); MERCHANT_MAP.put("smart bazar", GROC);
        MERCHANT_MAP.put("more",         GROC); MERCHANT_MAP.put("jiomart",     GROC);
        MERCHANT_MAP.put("jio mart",     GROC); MERCHANT_MAP.put("spencers",    GROC);
        MERCHANT_MAP.put("nature basket",GROC); MERCHANT_MAP.put("star market", GROC);
        MERCHANT_MAP.put("reliance fresh",GROC);MERCHANT_MAP.put("spar",        GROC);
        MERCHANT_MAP.put("hypercity",    GROC); MERCHANT_MAP.put("lulu",        GROC);
        MERCHANT_MAP.put("milkbasket",   GROC); MERCHANT_MAP.put("supr daily",  GROC);
        MERCHANT_MAP.put("country delight",GROC);MERCHANT_MAP.put("supermart",  GROC);
        MERCHANT_MAP.put("easyday",      GROC); MERCHANT_MAP.put("heritage",    GROC);

        // ── 🛍️ SHOPPING ───────────────────────────────────────────────
        String SHOP = "🛍️ Shopping";
        MERCHANT_MAP.put("amazon",       SHOP); MERCHANT_MAP.put("flipkart",    SHOP);
        MERCHANT_MAP.put("myntra",       SHOP); MERCHANT_MAP.put("meesho",      SHOP);
        MERCHANT_MAP.put("ajio",         SHOP); MERCHANT_MAP.put("nykaa",       SHOP);
        MERCHANT_MAP.put("snapdeal",     SHOP); MERCHANT_MAP.put("shopclues",   SHOP);
        MERCHANT_MAP.put("tatacliq",     SHOP); MERCHANT_MAP.put("tata cliq",   SHOP);
        MERCHANT_MAP.put("firstcry",     SHOP); MERCHANT_MAP.put("hopscotch",   SHOP);
        MERCHANT_MAP.put("limeroad",     SHOP); MERCHANT_MAP.put("craftsvilla", SHOP);
        MERCHANT_MAP.put("pepperfry",    SHOP); MERCHANT_MAP.put("urban ladder",SHOP);
        MERCHANT_MAP.put("fab india",    SHOP); MERCHANT_MAP.put("fabindia",    SHOP);
        MERCHANT_MAP.put("westside",     SHOP); MERCHANT_MAP.put("pantaloons",  SHOP);
        MERCHANT_MAP.put("shoppers stop",SHOP); MERCHANT_MAP.put("central",     SHOP);
        MERCHANT_MAP.put("lifestyle",    SHOP); MERCHANT_MAP.put("max fashion", SHOP);
        MERCHANT_MAP.put("zara",         SHOP); MERCHANT_MAP.put("h&m",         SHOP);
        MERCHANT_MAP.put("marks spencer",SHOP); MERCHANT_MAP.put("gap",         SHOP);
        MERCHANT_MAP.put("levis",        SHOP); MERCHANT_MAP.put("adidas",      SHOP);
        MERCHANT_MAP.put("nike",         SHOP); MERCHANT_MAP.put("puma",        SHOP);
        MERCHANT_MAP.put("reebok",       SHOP); MERCHANT_MAP.put("bata",        SHOP);
        MERCHANT_MAP.put("metro shoes",  SHOP); MERCHANT_MAP.put("liberty",     SHOP);
        MERCHANT_MAP.put("decathlon",    SHOP); MERCHANT_MAP.put("croma",       SHOP);
        MERCHANT_MAP.put("reliance digi",SHOP); MERCHANT_MAP.put("vijay sales", SHOP);
        MERCHANT_MAP.put("ezone",        SHOP); MERCHANT_MAP.put("chroma",      SHOP);

        // ── 🚗 TRANSPORT ──────────────────────────────────────────────
        String TRANS = "🚗 Transport";
        MERCHANT_MAP.put("uber",         TRANS); MERCHANT_MAP.put("ola",        TRANS);
        MERCHANT_MAP.put("rapido",       TRANS); MERCHANT_MAP.put("meru",       TRANS);
        MERCHANT_MAP.put("jugnoo",       TRANS); MERCHANT_MAP.put("ola electric",TRANS);
        MERCHANT_MAP.put("bounce",       TRANS); MERCHANT_MAP.put("yulu",       TRANS);
        MERCHANT_MAP.put("vogo",         TRANS); MERCHANT_MAP.put("metro card", TRANS);
        MERCHANT_MAP.put("dmrc",         TRANS); MERCHANT_MAP.put("bmtc",       TRANS);
        MERCHANT_MAP.put("best bus",     TRANS); MERCHANT_MAP.put("best mumbai",TRANS);
        MERCHANT_MAP.put("kutchbus",     TRANS); MERCHANT_MAP.put("redbus",     TRANS);
        MERCHANT_MAP.put("red bus",      TRANS);

        // ── ⛽ FUEL ────────────────────────────────────────────────────
        String FUEL = "⛽ Fuel";
        MERCHANT_MAP.put("hp petrol",    FUEL); MERCHANT_MAP.put("hpcl",        FUEL);
        MERCHANT_MAP.put("indianoil",    FUEL); MERCHANT_MAP.put("iocl",        FUEL);
        MERCHANT_MAP.put("bharat petrol",FUEL); MERCHANT_MAP.put("bpcl",        FUEL);
        MERCHANT_MAP.put("shell",        FUEL); MERCHANT_MAP.put("essar",       FUEL);
        MERCHANT_MAP.put("nayara",       FUEL); MERCHANT_MAP.put("reliance petro",FUEL);
        MERCHANT_MAP.put("cng",          FUEL); MERCHANT_MAP.put("lng",         FUEL);
        MERCHANT_MAP.put("petrol pump",  FUEL); MERCHANT_MAP.put("fuel station",FUEL);
        MERCHANT_MAP.put("ev charging",  FUEL); MERCHANT_MAP.put("tata power ev",FUEL);
        MERCHANT_MAP.put("ather",        FUEL); MERCHANT_MAP.put("charge zone", FUEL);

        // ── ✈️ TRAVEL ──────────────────────────────────────────────────
        String TRAVEL = "✈️ Travel";
        MERCHANT_MAP.put("irctc",        TRAVEL); MERCHANT_MAP.put("indigo",    TRAVEL);
        MERCHANT_MAP.put("air india",    TRAVEL); MERCHANT_MAP.put("vistara",   TRAVEL);
        MERCHANT_MAP.put("spicejet",     TRAVEL); MERCHANT_MAP.put("goair",     TRAVEL);
        MERCHANT_MAP.put("airasiaind",   TRAVEL); MERCHANT_MAP.put("akasa",     TRAVEL);
        MERCHANT_MAP.put("makemytrip",   TRAVEL); MERCHANT_MAP.put("goibibo",   TRAVEL);
        MERCHANT_MAP.put("yatra",        TRAVEL); MERCHANT_MAP.put("cleartrip", TRAVEL);
        MERCHANT_MAP.put("easemytrip",   TRAVEL); MERCHANT_MAP.put("booking.com",TRAVEL);
        MERCHANT_MAP.put("oyo",          TRAVEL); MERCHANT_MAP.put("treebo",    TRAVEL);
        MERCHANT_MAP.put("fabhotel",     TRAVEL); MERCHANT_MAP.put("zostel",    TRAVEL);
        MERCHANT_MAP.put("airbnb",       TRAVEL); MERCHANT_MAP.put("hotel",     TRAVEL);
        MERCHANT_MAP.put("taj hotel",    TRAVEL); MERCHANT_MAP.put("oberoi",    TRAVEL);
        MERCHANT_MAP.put("marriott",     TRAVEL); MERCHANT_MAP.put("hilton",    TRAVEL);
        MERCHANT_MAP.put("hyatt",        TRAVEL); MERCHANT_MAP.put("holiday inn",TRAVEL);

        // ── 🔌 BILLS ───────────────────────────────────────────────────
        String BILLS = "🔌 Bills";
        MERCHANT_MAP.put("bses",         BILLS); MERCHANT_MAP.put("tata power", BILLS);
        MERCHANT_MAP.put("ndmc",         BILLS); MERCHANT_MAP.put("msedcl",     BILLS);
        MERCHANT_MAP.put("bescom",       BILLS); MERCHANT_MAP.put("tneb",       BILLS);
        MERCHANT_MAP.put("kseb",         BILLS); MERCHANT_MAP.put("uppcl",      BILLS);
        MERCHANT_MAP.put("adani electric",BILLS);MERCHANT_MAP.put("torrent power",BILLS);
        MERCHANT_MAP.put("cesc",         BILLS); MERCHANT_MAP.put("wbsedcl",    BILLS);
        MERCHANT_MAP.put("electricity",  BILLS); MERCHANT_MAP.put("mahadiscom", BILLS);
        MERCHANT_MAP.put("airtel",       BILLS); MERCHANT_MAP.put("jio",        BILLS);
        MERCHANT_MAP.put("vodafone",     BILLS); MERCHANT_MAP.put("vi ",        BILLS);
        MERCHANT_MAP.put("idea",         BILLS); MERCHANT_MAP.put("bsnl",       BILLS);
        MERCHANT_MAP.put("mtnl",         BILLS); MERCHANT_MAP.put("tata sky",   BILLS);
        MERCHANT_MAP.put("dish tv",      BILLS); MERCHANT_MAP.put("d2h",        BILLS);
        MERCHANT_MAP.put("sun direct",   BILLS); MERCHANT_MAP.put("videocon d2h",BILLS);
        MERCHANT_MAP.put("act fibernet", BILLS); MERCHANT_MAP.put("hathway",    BILLS);
        MERCHANT_MAP.put("tikona",       BILLS); MERCHANT_MAP.put("you broadband",BILLS);
        MERCHANT_MAP.put("spectranet",   BILLS); MERCHANT_MAP.put("beam fiber", BILLS);
        MERCHANT_MAP.put("jio fiber",    BILLS); MERCHANT_MAP.put("airtel fiber",BILLS);
        MERCHANT_MAP.put("indane",       BILLS); MERCHANT_MAP.put("hp gas",     BILLS);
        MERCHANT_MAP.put("bharat gas",   BILLS); MERCHANT_MAP.put("mahanagar gas",BILLS);
        MERCHANT_MAP.put("igl",          BILLS); MERCHANT_MAP.put("adani gas",  BILLS);
        MERCHANT_MAP.put("municipal",    BILLS); MERCHANT_MAP.put("bbmp",       BILLS);
        MERCHANT_MAP.put("bmc",          BILLS); MERCHANT_MAP.put("nmmc",       BILLS);

        // ── 🎬 ENTERTAINMENT ──────────────────────────────────────────
        String ENT = "🎬 Entertainment";
        MERCHANT_MAP.put("netflix",      ENT); MERCHANT_MAP.put("hotstar",      ENT);
        MERCHANT_MAP.put("disney",       ENT); MERCHANT_MAP.put("amazon prime", ENT);
        MERCHANT_MAP.put("sonyliv",      ENT); MERCHANT_MAP.put("zee5",         ENT);
        MERCHANT_MAP.put("voot",         ENT); MERCHANT_MAP.put("mx player",    ENT);
        MERCHANT_MAP.put("jiocinema",    ENT); MERCHANT_MAP.put("aha",          ENT);
        MERCHANT_MAP.put("manorama",     ENT); MERCHANT_MAP.put("sun nxt",      ENT);
        MERCHANT_MAP.put("spotify",      ENT); MERCHANT_MAP.put("gaana",        ENT);
        MERCHANT_MAP.put("jiosaavn",     ENT); MERCHANT_MAP.put("wynk",         ENT);
        MERCHANT_MAP.put("apple music",  ENT); MERCHANT_MAP.put("youtube",      ENT);
        MERCHANT_MAP.put("bookmyshow",   ENT); MERCHANT_MAP.put("pvr",          ENT);
        MERCHANT_MAP.put("inox",         ENT); MERCHANT_MAP.put("cinepolis",    ENT);
        MERCHANT_MAP.put("carnival",     ENT); MERCHANT_MAP.put("multiplex",    ENT);
        MERCHANT_MAP.put("gaming",       ENT); MERCHANT_MAP.put("steam",        ENT);
        MERCHANT_MAP.put("playstation",  ENT); MERCHANT_MAP.put("xbox",         ENT);
        MERCHANT_MAP.put("paytm games",  ENT);

        // ── 🏥 HEALTH ──────────────────────────────────────────────────
        String HEALTH = "🏥 Health";
        MERCHANT_MAP.put("apollo",       HEALTH); MERCHANT_MAP.put("manipal",   HEALTH);
        MERCHANT_MAP.put("fortis",       HEALTH); MERCHANT_MAP.put("max health", HEALTH);
        MERCHANT_MAP.put("aiims",        HEALTH); MERCHANT_MAP.put("nimhans",   HEALTH);
        MERCHANT_MAP.put("lilavati",     HEALTH); MERCHANT_MAP.put("kokilaben", HEALTH);
        MERCHANT_MAP.put("medanta",      HEALTH); MERCHANT_MAP.put("narayana",  HEALTH);
        MERCHANT_MAP.put("aster",        HEALTH); MERCHANT_MAP.put("rainbow",   HEALTH);
        MERCHANT_MAP.put("cloudnine",    HEALTH); MERCHANT_MAP.put("motherhood",HEALTH);
        MERCHANT_MAP.put("lybrate",      HEALTH); MERCHANT_MAP.put("practo",    HEALTH);
        MERCHANT_MAP.put("healthians",   HEALTH); MERCHANT_MAP.put("thyrocare", HEALTH);
        MERCHANT_MAP.put("lal pathlabs", HEALTH); MERCHANT_MAP.put("dr lal",    HEALTH);
        MERCHANT_MAP.put("metropolis",   HEALTH); MERCHANT_MAP.put("srl diag",  HEALTH);

        // ── 💊 MEDICINE ────────────────────────────────────────────────
        String MED = "💊 Medicine";
        MERCHANT_MAP.put("pharmeasy",    MED); MERCHANT_MAP.put("1mg",          MED);
        MERCHANT_MAP.put("netmeds",      MED); MERCHANT_MAP.put("medlife",      MED);
        MERCHANT_MAP.put("medplus",      MED); MERCHANT_MAP.put("apollo pharma",MED);
        MERCHANT_MAP.put("wellness",     MED); MERCHANT_MAP.put("healthkart",   MED);
        MERCHANT_MAP.put("tata health",  MED); MERCHANT_MAP.put("chemist",      MED);
        MERCHANT_MAP.put("pharmacy",     MED); MERCHANT_MAP.put("medical store",MED);
        MERCHANT_MAP.put("guardian",     MED); MERCHANT_MAP.put("frank ross",   MED);

        // ── 📚 EDUCATION ──────────────────────────────────────────────
        String EDU = "📚 Education";
        MERCHANT_MAP.put("byju",         EDU); MERCHANT_MAP.put("unacademy",    EDU);
        MERCHANT_MAP.put("vedantu",      EDU); MERCHANT_MAP.put("testbook",     EDU);
        MERCHANT_MAP.put("doubtnut",     EDU); MERCHANT_MAP.put("toppr",        EDU);
        MERCHANT_MAP.put("meritnation",  EDU); MERCHANT_MAP.put("khan academy", EDU);
        MERCHANT_MAP.put("coursera",     EDU); MERCHANT_MAP.put("udemy",        EDU);
        MERCHANT_MAP.put("upgrad",       EDU); MERCHANT_MAP.put("simplilearn",  EDU);
        MERCHANT_MAP.put("great learning",EDU);MERCHANT_MAP.put("whitehat",     EDU);
        MERCHANT_MAP.put("classplus",    EDU); MERCHANT_MAP.put("extramarks",   EDU);
        MERCHANT_MAP.put("school fee",   EDU); MERCHANT_MAP.put("tuition",      EDU);
        MERCHANT_MAP.put("college fee",  EDU); MERCHANT_MAP.put("exam fee",     EDU);
        MERCHANT_MAP.put("pearson",      EDU); MERCHANT_MAP.put("cambridge",    EDU);
        MERCHANT_MAP.put("british council",EDU);MERCHANT_MAP.put("ielts",       EDU);

        // ── 💪 FITNESS ────────────────────────────────────────────────
        String FIT = "💪 Fitness";
        MERCHANT_MAP.put("cult fit",     FIT); MERCHANT_MAP.put("cure fit",     FIT);
        MERCHANT_MAP.put("fitpass",      FIT); MERCHANT_MAP.put("gold gym",     FIT);
        MERCHANT_MAP.put("anytime fit",  FIT); MERCHANT_MAP.put("planet fitness",FIT);
        MERCHANT_MAP.put("crossfit",     FIT); MERCHANT_MAP.put("yoga",         FIT);
        MERCHANT_MAP.put("zumba",        FIT); MERCHANT_MAP.put("pilates",      FIT);
        MERCHANT_MAP.put("gym",          FIT); MERCHANT_MAP.put("sports",       FIT);
        MERCHANT_MAP.put("swimming",     FIT); MERCHANT_MAP.put("badminton",    FIT);
        MERCHANT_MAP.put("tennis",       FIT); MERCHANT_MAP.put("football",     FIT);
        MERCHANT_MAP.put("cricket academy",FIT);MERCHANT_MAP.put("spa",         FIT);
        MERCHANT_MAP.put("salon",        FIT); MERCHANT_MAP.put("loreal",       FIT);
        MERCHANT_MAP.put("wella",        FIT); MERCHANT_MAP.put("lakme salon",  FIT);
        MERCHANT_MAP.put("naturals salon",FIT);MERCHANT_MAP.put("jawed habib",  FIT);

        // ── 💰 INVESTMENT ─────────────────────────────────────────────
        String INV = "💰 Investment";
        MERCHANT_MAP.put("zerodha",      INV); MERCHANT_MAP.put("groww",        INV);
        MERCHANT_MAP.put("upstox",       INV); MERCHANT_MAP.put("angel broking",INV);
        MERCHANT_MAP.put("5paisa",       INV); MERCHANT_MAP.put("sharekhan",    INV);
        MERCHANT_MAP.put("motilal oswal",INV); MERCHANT_MAP.put("iifl",         INV);
        MERCHANT_MAP.put("hdfc securiti",INV); MERCHANT_MAP.put("icici direct", INV);
        MERCHANT_MAP.put("kotak securit",INV); MERCHANT_MAP.put("sbi cap",      INV);
        MERCHANT_MAP.put("axis direct",  INV); MERCHANT_MAP.put("sbismart",     INV);
        MERCHANT_MAP.put("kuvera",       INV); MERCHANT_MAP.put("scripbox",     INV);
        MERCHANT_MAP.put("paytm money",  INV); MERCHANT_MAP.put("etmoney",      INV);
        MERCHANT_MAP.put("fisdom",       INV); MERCHANT_MAP.put("nps",          INV);
        MERCHANT_MAP.put("lic",          INV); MERCHANT_MAP.put("hdfc life",    INV);
        MERCHANT_MAP.put("bajaj allianz",INV); MERCHANT_MAP.put("max life",     INV);
        MERCHANT_MAP.put("sip",          INV); MERCHANT_MAP.put("mutual fund",  INV);
        MERCHANT_MAP.put("gullak",       INV); MERCHANT_MAP.put("jar app",      INV);
        MERCHANT_MAP.put("smallcase",    INV); MERCHANT_MAP.put("indmoney",     INV);

        // ── 🏠 RENT ───────────────────────────────────────────────────
        String RENT = "🏠 Rent";
        MERCHANT_MAP.put("rent",         RENT); MERCHANT_MAP.put("nobroker",    RENT);
        MERCHANT_MAP.put("housing",      RENT); MERCHANT_MAP.put("magicbricks", RENT);
        MERCHANT_MAP.put("99acres",      RENT); MERCHANT_MAP.put("makaan",      RENT);
        MERCHANT_MAP.put("co-living",    RENT); MERCHANT_MAP.put("coliving",    RENT);
        MERCHANT_MAP.put("stanza",       RENT); MERCHANT_MAP.put("nestaway",    RENT);
        MERCHANT_MAP.put("society main", RENT); MERCHANT_MAP.put("maintenance", RENT);
        MERCHANT_MAP.put("housing socie",RENT);

        // ── 🎁 GIFTS ──────────────────────────────────────────────────
        String GIFT = "🎁 Gifts";
        MERCHANT_MAP.put("ferns n",      GIFT); MERCHANT_MAP.put("fnp",         GIFT);
        MERCHANT_MAP.put("igp",          GIFT); MERCHANT_MAP.put("giftalove",   GIFT);
        MERCHANT_MAP.put("floweraura",   GIFT); MERCHANT_MAP.put("interflora",  GIFT);
        MERCHANT_MAP.put("archies",      GIFT); MERCHANT_MAP.put("hallmark",    GIFT);
        MERCHANT_MAP.put("gift",         GIFT); MERCHANT_MAP.put("flowers",     GIFT);
        MERCHANT_MAP.put("bouquet",      GIFT);
    }

    // ────────────────────────────────────────────────────────────────
    // MERCHANT LEARNING (user overrides)
    // ────────────────────────────────────────────────────────────────
    private static final String PREFS_NAME  = "merchant_learning";
    private static final String ALIAS_PREFS = "merchant_aliases";
    private static SharedPreferences learningPrefs;
    private static SharedPreferences aliasPrefs;

    public static void init(Context ctx) {
        learningPrefs = ctx.getSharedPreferences(PREFS_NAME,  Context.MODE_PRIVATE);
        aliasPrefs    = ctx.getSharedPreferences(ALIAS_PREFS, Context.MODE_PRIVATE);
    }

    public static void learnMerchant(String merchant, String category) {
        if (learningPrefs == null || merchant == null || category == null) return;
        learningPrefs.edit().putString(merchant.toLowerCase().trim(), category).apply();
    }

    private static String getLearnedCategory(String merchant) {
        if (learningPrefs == null || merchant == null) return null;
        return learningPrefs.getString(merchant.toLowerCase().trim(), null);
    }

    public static void learnMerchantAlias(String rawName, String displayName) {
        if (aliasPrefs == null || rawName == null || displayName == null) return;
        if (rawName.trim().equalsIgnoreCase(displayName.trim())) return;
        aliasPrefs.edit().putString(rawName.toLowerCase().trim(), displayName.trim()).apply();
    }

    public static String resolveMerchantAlias(String rawName) {
        if (aliasPrefs == null || rawName == null) return rawName;
        String alias = aliasPrefs.getString(rawName.toLowerCase().trim(), null);
        return alias != null ? alias : rawName;
    }

    // ────────────────────────────────────────────────────────────────
    // UPI MERCHANT CLEANER
    // ────────────────────────────────────────────────────────────────
    public static String resolveUpiMerchant(String merchant) {
        if (merchant == null) return "Unknown";
        String m = merchant.toLowerCase();
        m = m.replaceAll("paytmqr", "paytm");
        m = m.replaceAll("@.*", "");
        if (m.contains("amazon"))   return "Amazon";
        if (m.contains("flipkart")) return "Flipkart";
        if (m.contains("swiggy"))   return "Swiggy";
        if (m.contains("zomato"))   return "Zomato";
        if (m.contains("uber"))     return "Uber";
        if (m.contains("ola"))      return "Ola";
        if (m.contains("paytm"))    return "Paytm";
        if (m.contains("phonepe"))  return "PhonePe";
        if (m.contains("gpay"))     return "Google Pay";
        if (m.contains("bigbasket"))return "BigBasket";
        if (m.contains("blinkit"))  return "Blinkit";
        if (m.contains("jiomart"))  return "JioMart";
        if (m.contains("dmart"))    return "DMart";
        if (m.contains("myntra"))   return "Myntra";
        if (m.contains("zerodha"))  return "Zerodha";
        if (m.contains("groww"))    return "Groww";
        if (m.contains("irctc"))    return "IRCTC";
        return merchant;
    }

    // ────────────────────────────────────────────────────────────────
    // AUTO CATEGORY (used in Add Expense screen as you type merchant)
    // Checks learned overrides first, then static map, then NLP.
    // Never returns Income — income is detected by SMS parser only.
    // ────────────────────────────────────────────────────────────────
    public static String autoCategory(String merchant) {
        if (merchant == null) return "💼 Others";
        String m = merchant.toLowerCase().trim();

        // 1. User-learned overrides (highest priority)
        String learned = getLearnedCategory(m);
        if (learned != null && !isIncome(learned)) return learned;

        // 2. Static merchant map
        for (Map.Entry<String, String> e : MERCHANT_MAP.entrySet()) {
            if (m.contains(e.getKey())) return e.getValue();
        }

        // 3. NLP token fallback
        String nlp = NlpCategorizer.predictWithSource(merchant);
        if (nlp != null) return nlp;

        return "💼 Others";
    }

    // ────────────────────────────────────────────────────────────────
    // CLASSIFY — main engine called during SMS parsing
    // Income is detected here and maps to the unified Income category.
    // ────────────────────────────────────────────────────────────────
    public static String classify(String merchant, String smsBody) {
        if (merchant == null) merchant = "";
        merchant = resolveUpiMerchant(merchant).toLowerCase();
        String body = smsBody != null ? smsBody.toLowerCase() : "";

        // 1. Income detection — all income signals map to single Income category
        if (body.contains("salary")     || body.contains("credited by employer") ||
            body.contains("cashback")   || body.contains("refund")               ||
            body.contains("interest credit") || body.contains("dividend"))
            return INCOME;

        // 2. User-learned merchant overrides
        String learned = getLearnedCategory(merchant);
        if (learned != null) return learned;

        // 3. Merchant map — longest/most-specific match wins
        String bestMatch = null; int bestLen = 0;
        for (Map.Entry<String, String> e : MERCHANT_MAP.entrySet()) {
            String key = e.getKey();
            if (merchant.contains(key) && key.length() > bestLen) {
                bestMatch = e.getValue(); bestLen = key.length();
            }
        }
        if (bestMatch != null) return bestMatch;

        // 4. SMS body keyword fallback
        for (Map.Entry<String, String> e : MERCHANT_MAP.entrySet()) {
            if (body.contains(e.getKey())) return e.getValue();
        }

        // 5. NLP token matching
        String nlp = NlpCategorizer.predictWithSource(merchant);
        if (nlp != null) return nlp;

        return "💼 Others";
    }

    public static CategoryInfo getInfo(String category) {
        CategoryInfo info = CATEGORIES.get(category);
        return info != null ? info : CATEGORIES.get("💼 Others");
    }
}
