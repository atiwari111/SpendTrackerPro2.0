package com.spendtracker.pro;

import java.util.*;

/**
 * NlpCategorizer v1.6
 *
 * Word-level NLP fallback categorizer. Activates when CategoryEngine
 * returns "💼 Others" — handles unknown merchants by decomposing the
 * merchant name into tokens and matching them against semantic word groups.
 *
 * Example:
 *   "Blue Tokai Coffee Roasters" → tokens: [blue, tokai, coffee, roasters]
 *   "coffee" → matched → 🍔 Food
 *
 * This handles new/unknown merchants that haven't been added to the dataset yet.
 */
public class NlpCategorizer {

    // Each entry: { category, word1, word2, ... }
    private static final String[][] WORD_GROUPS = {
        // Food
        { "🍔 Food",
          "coffee", "cafe", "restaurant", "kitchen", "food", "eatery", "dining",
          "pizza", "burger", "biryani", "chinese", "italian", "dhaba", "canteen",
          "bakery", "sweets", "mithai", "chaat", "snacks", "grill", "bbq",
          "bistro", "brasserie", "diner", "mess", "tiffin", "takeaway",
          "chai", "tea house", "patisserie", "cakery", "juice", "smoothie",
          "noodles", "sushi", "thai", "mexican", "steakhouse", "seafood",
          "roasters", "brewer", "brew", "roastery" },

        // Groceries
        { "🛒 Groceries",
          "grocery", "mart", "bazaar", "bazar", "market", "fresh",
          "dairy", "organic", "farm", "vegetables", "provisions",
          "kirana", "supershop", "hypermarket", "wholesale", "bulk",
          "milk", "eggs", "produce", "pantry" },

        // Shopping
        { "🛍️ Shopping",
          "store", "shop", "retail", "boutique", "fashion", "wear",
          "clothing", "apparel", "garments", "textiles", "accessories",
          "jewels", "jewellery", "jewelry", "optical", "eyewear",
          "footwear", "shoes", "sneakers", "electronics", "digital",
          "gadgets", "appliances", "furniture", "decor", "interiors",
          "hardware", "tools", "stationery", "gifting", "cosmetics",
          "beauty", "skincare", "haircare", "perfume", "watch",
          "bags", "luggage", "leather" },

        // Transport
        { "🚗 Transport",
          "cab", "taxi", "rides", "commute", "auto", "shuttle",
          "carpool", "bike", "scooter", "metro", "transit", "bus",
          "ferry", "rickshaw", "chauffeur", "driver", "parking",
          "toll", "valet" },

        // Fuel
        { "⛽ Fuel",
          "petrol", "diesel", "fuel", "cng", "lng", "ev",
          "charging", "refuel", "pump", "petroleum", "gas station",
          "filling station", "energy" },

        // Travel
        { "✈️ Travel",
          "travel", "tours", "tourism", "holidays", "vacation",
          "flight", "airline", "airways", "air", "hotel", "inn",
          "resort", "lodge", "hostel", "guesthouse", "homestay",
          "railway", "trains", "bus travels", "yatra", "pilgrimage",
          "expedition", "adventure", "trek", "safari", "cruise",
          "visa", "passport" },

        // Bills
        { "🔌 Bills",
          "electricity", "power", "utility", "telecom", "mobile",
          "broadband", "internet", "cable", "dth", "recharge",
          "water", "gas", "municipal", "corporation", "postpaid",
          "prepaid", "landline", "wifi", "fiber" },

        // Entertainment
        { "🎬 Entertainment",
          "entertainment", "cinema", "theater", "theatre", "movie",
          "multiplex", "gaming", "games", "play", "stream",
          "subscription", "music", "concert", "live", "event",
          "comedy", "show", "sports", "arcade", "amusement",
          "recreation", "fun", "virtual" },

        // Health
        { "🏥 Health",
          "hospital", "clinic", "health", "care", "medical",
          "doctor", "physician", "surgeon", "dental", "dentist",
          "eye care", "vision", "ortho", "diagnostics",
          "pathology", "lab", "scan", "radiology", "nursing",
          "maternity", "pediatric", "cardiology", "neuro",
          "dermatology", "wellness", "ayurveda", "homeopathy" },

        // Medicine
        { "💊 Medicine",
          "pharmacy", "chemist", "drug", "pharma", "medicine",
          "medical store", "dispensary", "tablets", "prescriptions",
          "therapeutics", "surgical", "medico" },

        // Education
        { "📚 Education",
          "school", "college", "university", "institute", "academy",
          "coaching", "tuition", "learning", "education", "study",
          "tutorial", "training", "course", "exam", "certification",
          "library", "books", "publication", "publishing",
          "skill", "knowledge", "edtech" },

        // Fitness
        { "💪 Fitness",
          "gym", "fitness", "yoga", "zumba", "pilates", "crossfit",
          "workout", "sports", "athletics", "martial arts",
          "swimming", "dance", "aerobics", "health club",
          "salon", "spa", "grooming", "barber", "parlour",
          "wellness center", "physio", "rehabilitation" },

        // Investment
        { "💰 Investment",
          "invest", "investment", "trading", "stocks", "equity",
          "mutual fund", "mf", "sip", "insurance", "life",
          "pension", "retirement", "portfolio", "wealth",
          "finance", "financial", "capital", "asset",
          "securities", "brokerage", "demat" },

        // Rent
        { "🏠 Rent",
          "rent", "rental", "lease", "property", "housing",
          "realty", "real estate", "flat", "apartment",
          "pg", "co-living", "coliving", "society",
          "maintenance", "hoa" },

        // Gifts
        { "🎁 Gifts",
          "gift", "gifting", "flowers", "florist", "bouquet",
          "greet", "celebration", "party", "occasion",
          "anniversary", "birthday", "wedding", "hamper",
          "chocolate", "cake", "sweets box", "memento",
          "trophy", "souvenir" },
    };

    /**
     * Predict category from merchant name using word-token matching.
     * Returns "💼 Others" if no match found.
     *
     * Call this ONLY as a fallback when CategoryEngine.classify() returns Others.
     */
    public static String predict(String merchant) {
        if (merchant == null || merchant.trim().isEmpty()) return "💼 Others";

        String m = merchant.toLowerCase().trim();

        // Try full string first, then token by token
        for (String[] group : WORD_GROUPS) {
            String category = group[0];
            for (int i = 1; i < group.length; i++) {
                if (m.contains(group[i])) return category;
            }
        }

        return "💼 Others";
    }

    /**
     * Returns a confidence label based on how the match was made.
     * Useful for debugging / showing "Auto-detected" badge in UI.
     */
    public static String predictWithSource(String merchant) {
        String result = predict(merchant);
        return result.equals("💼 Others") ? null : result;
    }
}
