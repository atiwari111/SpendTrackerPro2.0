package com.spendtracker.pro;

import java.util.*;

/**
 * NlpCategorizer v1.7
 *
 * Word-level NLP fallback categorizer.
 * Changes vs v1.6:
 *  - Fix 2.41: Added "💄 Beauty & Salon" group
 *  - Fix 2.42: Renamed "🍔 Food" → "🍽️ Cafe & Food Delivery"
 *  - salon/spa/beauty words moved from Fitness/Shopping → Beauty & Salon
 *  - beauty/skincare/haircare words moved from Shopping → Beauty & Salon
 */
public class NlpCategorizer {

    private static final String[][] WORD_GROUPS = {

        // Cafe & Food Delivery (Fix 2.42)
        { "🍽️ Cafe & Food Delivery",
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

        // Shopping (beauty/salon words removed — now in Beauty & Salon)
        { "🛍️ Shopping",
          "store", "shop", "retail", "boutique", "fashion", "wear",
          "clothing", "apparel", "garments", "textiles", "accessories",
          "jewels", "jewellery", "jewelry", "optical", "eyewear",
          "footwear", "shoes", "sneakers", "electronics", "digital",
          "gadgets", "appliances", "furniture", "decor", "interiors",
          "hardware", "tools", "stationery", "gifting",
          "perfume", "watch", "bags", "luggage", "leather" },

        // Beauty & Salon (Fix 2.41 — new category)
        { "💄 Beauty & Salon",
          "salon", "spa", "beauty", "skincare", "haircare", "grooming",
          "barber", "parlour", "parlor", "makeup", "nail", "nails",
          "waxing", "threading", "facial", "manicure", "pedicure",
          "bridal", "mehndi", "cosmetics", "loreal", "wella",
          "lakme", "naturals salon", "jawed habib", "toni guy",
          "toni&guy", "yves rocher", "the body shop", "kiehl",
          "forest essentials", "biotique", "himalaya", "plum",
          "mcaffeine", "minimalist", "dot and key", "sugar cosmetics" },

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

        // Fitness (salon/spa/beauty moved out to Beauty & Salon)
        { "💪 Fitness",
          "gym", "fitness", "yoga", "zumba", "pilates", "crossfit",
          "workout", "athletics", "martial arts",
          "swimming", "dance", "aerobics", "health club",
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

    public static String predict(String merchant) {
        if (merchant == null || merchant.trim().isEmpty()) return "💼 Others";
        String m = merchant.toLowerCase().trim();
        for (String[] group : WORD_GROUPS) {
            String category = group[0];
            for (int i = 1; i < group.length; i++) {
                if (m.contains(group[i])) return category;
            }
        }
        return "💼 Others";
    }

    public static String predictWithSource(String merchant) {
        String result = predict(merchant);
        return result.equals("💼 Others") ? null : result;
    }
}
