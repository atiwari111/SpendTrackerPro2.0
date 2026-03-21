package com.spendtracker.pro;

import java.util.*;
import java.util.regex.*;

/**
 * BillSmsDetector — identifies bill/payment-due SMS and extracts structured data.
 *
 * Handles:
 *  - Electricity bills ("Your BSES bill of Rs.X is due on DD-MM")
 *  - Mobile/broadband recharges ("Your Airtel bill Rs.X due on...")
 *  - Subscription reminders ("Netflix subscription Rs.X due on...")
 *  - Credit card bills ("Min due Rs.X, Total due Rs.X by DD-MM")
 *  - Insurance premiums ("LIC premium Rs.X due on DD-MMM")
 *  - Paid confirmation ("Payment of Rs.X received for your Airtel bill")
 */
public class BillSmsDetector {

    public static class BillSmsResult {
        public final String  name;
        public final String  category;
        public final String  icon;
        public final double  amount;
        public final long    dueDate;    // 0 if not found
        public final String  status;     // PENDING or PAID
        public final boolean isRecurring;
        public final String  merchantId;

        BillSmsResult(String name, String category, String icon,
                      double amount, long dueDate, String status, boolean isRecurring) {
            this.name       = name;
            this.category   = category;
            this.icon       = icon;
            this.amount     = amount;
            this.dueDate    = dueDate;
            this.status     = status;
            this.isRecurring = isRecurring;
            this.merchantId = name != null ? name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","") : "";
        }
    }

    // ── Keywords that indicate a bill due notice ─────────────────────────────
    private static final String[] BILL_DUE_KEYWORDS = {
        "bill due", "bill amount", "payment due", "due on", "due date",
        "please pay", "last date", "outstanding amount",
        "recharge due", "subscription due", "premium due",
        "emi", "payable by", "pay before",
        // Fix 2.7: HDFC/bank credit card bill SMS keywords
        "amount due", "total due", "min.due", "pay instantly",
        "credit card statement", "statement:", "pay by",
        // Fix 2.8: BOBCARD / generic card bill SMS patterns
        "bill of rs", "bill of inr", "bill of ₹",
        "credit card bill", "card bill",
    };

    // ── Keywords for paid confirmation ────────────────────────────────────────
    private static final String[] BILL_PAID_KEYWORDS = {
        "payment received", "payment successful", "bill paid", "payment confirmed",
        "thank you for payment", "transaction successful for bill",
        "recharge successful", "subscription renewed",
    };


    // ── Promotional / marketing SMS — must be rejected BEFORE bill detection ─
    // These phrases appear in welcome, offer, and marketing messages that
    // happen to mention "credit card", "bill", or an amount — but are NOT dues.
    private static final String[] PROMO_REJECT_PHRASES = {
        "hope you love",        // BOBCARD / welcome SMS
        "congratulations",      // card approval / offer SMS
        "you have been approved",
        "your application",
        "welcome to",           // welcome onboarding SMS
        "thank you for choosing",
        "enjoy your",           // "enjoy your new card"
        "exclusive offer",
        "special offer",
        "limited offer",
        "cashback offer",
        "reward points",
        "you have earned",
        "upgrade your",
        "pre-approved",
        "pre approved",
        "apply now",
        "click here",
        "download app",
        "install app",
        "refer a friend",
        "as per rbi",           // RBI promotional disclaimers
        "to unsubscribe",
        "to stop sms",
        "is now active",        // card activation message
        "has been activated",
        "card is active",
        "card activated",
        "card delivered",
        "to know your",
        "for more details",
        "visit our website",
        "call us at",
        "for assistance",
        "dial 1800",
        "toll free",
    };

    private static final Pattern AMOUNT_PAT = Pattern.compile(
            "(?i)(?:rs\\.?|inr|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)");

    // Fix 2.7: prefer "Total due" amount over first-found amount in CC statements
    private static final Pattern TOTAL_DUE_PAT = Pattern.compile(
            "(?i)total\\s+due[:\\s]*(?:Rs\\.?|INR|₹)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)");
    private static final Pattern AMOUNT_DUE_PAT = Pattern.compile(
            "(?i)amount\\s+due[:\\s]*(?:Rs\\.?|INR|₹)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)");
    private static final Pattern CARD_LAST4_PAT = Pattern.compile(
            "(?i)(?:credit\\s*card|card)\\s*(?:xx|x{1,4})?\\s*([0-9]{4})");
    // Fix 2.8: extract card brand name appearing BEFORE "credit card" in the SMS
    // e.g. "BOBCARD One Credit Card bill" → captures "BOBCARD One"
    private static final Pattern CARD_BRAND_PAT = Pattern.compile(
            "(?i)([A-Za-z][A-Za-z0-9\\s\\-]{2,40}?)\\s+(?:credit\\s+card|cc)\\b");

    // Matches: "due on 25-03", "due by 25 Mar", "due date: 25/03/2026"
    private static final Pattern DUE_DATE_PAT = Pattern.compile(
            "(?i)(?:due\\s+(?:on|by|date[:\\s]?)\\s*|last\\s+date[:\\s]*|pay\\s+(?:by|before)[:\\s]*)([0-3]?[0-9][\\s\\-/](?:(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*|[01]?[0-9])[\\s\\-/]?(?:2?0?[2-9][0-9])?)",
            Pattern.CASE_INSENSITIVE);

    // ── Known biller patterns ─────────────────────────────────────────────────
    private static final String[][] BILLERS = {
        // { keyword, name, category, icon, isRecurring }
        { "airtel",      "Airtel",         "🔌 Bills", "📱", "true" },
        { "jio",         "Jio",            "🔌 Bills", "📱", "true" },
        { "vodafone",    "Vodafone",       "🔌 Bills", "📱", "true" },
        { "bsnl",        "BSNL",           "🔌 Bills", "📱", "true" },
        { "act fibernet","ACT Fibernet",   "🔌 Bills", "🌐", "true" },
        { "hathway",     "Hathway",        "🔌 Bills", "🌐", "true" },
        { "bses",        "BSES Electricity","🔌 Bills","⚡", "true" },
        { "tata power",  "Tata Power",     "🔌 Bills", "⚡", "true" },
        { "electricity", "Electricity",    "🔌 Bills", "⚡", "true" },
        { "bescom",      "BESCOM",         "🔌 Bills", "⚡", "true" },
        { "ndmc",        "NDMC",           "🔌 Bills", "⚡", "true" },
        { "netflix",     "Netflix",        "🎬 Entertainment","🎬","true" },
        { "hotstar",     "Disney Hotstar", "🎬 Entertainment","🎬","true" },
        { "spotify",     "Spotify",        "🎬 Entertainment","🎵","true" },
        { "amazon prime","Amazon Prime",   "🎬 Entertainment","📦","true" },
        { "lic",         "LIC",            "💰 Investment","🛡️","true" },
        { "insurance",   "Insurance",      "💰 Investment","🛡️","true" },
        { "credit card", "Credit Card",    "🔌 Bills","💳","true" },
        { "indane",      "Indane Gas",     "🔌 Bills","🔥","true" },
        { "hp gas",      "HP Gas",         "🔌 Bills","🔥","true" },
        { "maintenance", "Society Maintenance","🏠 Rent","🏢","true" },
    };

    /**
     * Detects if SMS is a bill-related message and extracts bill info.
     * Returns null if this SMS is not a bill notification.
     */
    public static BillSmsResult detect(String body, String sender) {
        if (body == null || body.length() < 10) return null;
        String lower = body.toLowerCase(Locale.ROOT);

        // Reject promotional / marketing messages before any other check
        if (containsAny(lower, PROMO_REJECT_PHRASES)) return null;

        // Check if paid or pending
        boolean isPaid    = containsAny(lower, BILL_PAID_KEYWORDS);
        boolean isBillDue = containsAny(lower, BILL_DUE_KEYWORDS);

        if (!isPaid && !isBillDue) return null;

        // Extract amount — prefer "Total due" for CC statements
        double amount;
        try {
            Matcher totalM = TOTAL_DUE_PAT.matcher(body);
            if (totalM.find()) {
                amount = Double.parseDouble(totalM.group(1).replace(",", ""));
            } else {
                Matcher dueM = AMOUNT_DUE_PAT.matcher(body);
                if (dueM.find()) {
                    amount = Double.parseDouble(dueM.group(1).replace(",", ""));
                } else {
                Matcher amtM = AMOUNT_PAT.matcher(body);
                if (!amtM.find()) return null;
                amount = Double.parseDouble(amtM.group(1).replace(",", ""));
                }
            }
        } catch (NumberFormatException e) {
            android.util.Log.w("BillSmsDetector", "Amount parse failed in SMS: " + e.getMessage());
            return null;
        }
        if (amount <= 0) return null;

        // Identify biller
        String name = null, category = "🔌 Bills", icon = "📋";
        boolean isRecurring = true;
        for (String[] biller : BILLERS) {
            if (lower.contains(biller[0])) {
                name       = biller[1];
                category   = biller[2];
                icon       = biller[3];
                isRecurring = "true".equals(biller[4]);
                break;
            }
        }
        if (name == null) {
            // Try to extract from sender
            name = extractBillerFromSender(sender);
            if (name == null) name = "Bill Payment";
        }

        // Fix 2.8: Enrich credit-card bill name with brand name or last4.
        // Priority: (1) card brand before "credit card" keyword, (2) known bank, (3) last4.
        if (lower.contains("credit card") || lower.contains("card bill")) {
            // Try to extract brand/card name from text before "credit card"
            Matcher brandM = CARD_BRAND_PAT.matcher(body);
            String cardBrand = "";
            if (brandM.find()) {
                cardBrand = brandM.group(1).trim()
                        .replaceAll("(?i)^(your|the|a|an)\\s+", "").trim();
                // Discard noise-only results
                if (cardBrand.equalsIgnoreCase("your") || cardBrand.equalsIgnoreCase("the")
                        || cardBrand.length() < 2) {
                    cardBrand = "";
                }
            }
            Matcher card4 = CARD_LAST4_PAT.matcher(body);
            String last4 = card4.find() ? card4.group(1) : "";

            if (!cardBrand.isEmpty()) {
                // e.g. "BOBCARD One Credit Card Bill"
                name = cardBrand + " Credit Card Bill";
            } else if (lower.contains("hdfc")) {
                name = last4.isEmpty() ? "HDFC Credit Card Bill" : "HDFC Credit Card " + last4 + " Bill";
            } else if (lower.contains("sbi")) {
                name = last4.isEmpty() ? "SBI Credit Card Bill" : "SBI Credit Card " + last4 + " Bill";
            } else if (lower.contains("icici")) {
                name = last4.isEmpty() ? "ICICI Credit Card Bill" : "ICICI Credit Card " + last4 + " Bill";
            } else if (lower.contains("axis")) {
                name = last4.isEmpty() ? "Axis Credit Card Bill" : "Axis Credit Card " + last4 + " Bill";
            } else if (!last4.isEmpty()) {
                name = "Credit Card " + last4 + " Bill";
            } else {
                name = "Credit Card Bill";
            }
        }

        // Extract due date
        long dueDate = 0;
        if (!isPaid) {
            Matcher dateM = DUE_DATE_PAT.matcher(body);
            if (dateM.find()) {
                dueDate = parseDate(dateM.group(1));
            }
            // If no explicit date, assume due in 7 days
            if (dueDate <= 0) {
                dueDate = System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000);
            }
        }

        String status = isPaid ? "PAID" : "PENDING";
        return new BillSmsResult(name, category, icon, amount, dueDate, status, isRecurring);
    }

    private static String extractBillerFromSender(String sender) {
        if (sender == null) return null;
        String s = sender.toLowerCase(Locale.ROOT).replaceAll("^[a-z]{2}-", "");
        if (s.contains("airtel")) return "Airtel";
        if (s.contains("jio"))    return "Jio";
        if (s.contains("bses"))   return "BSES";
        if (s.contains("tata"))   return "Tata";
        return null;
    }

    private static boolean containsAny(String text, String[] keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private static long parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return 0;
        try {
            // Normalize separators
            String clean = dateStr.trim().replaceAll("[\\s/]", "-");
            // Try dd-MMM-yy, dd-MM-yy, dd-MM-yyyy
            String[] formats = { "dd-MMM-yy", "dd-MMM-yyyy", "dd-MM-yy", "dd-MM-yyyy" };
            Calendar cal = Calendar.getInstance();
            for (String fmt : formats) {
                try {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(fmt, Locale.ENGLISH);
                    sdf.setLenient(false);
                    java.util.Date d = sdf.parse(clean);
                    if (d != null) return d.getTime();
                } catch (java.text.ParseException ignored) {
                    // Expected — try next format
                }
            }
            // If just dd-MM, use current year
            if (clean.matches("\\d{1,2}-\\d{2}")) {
                clean = clean + "-" + cal.get(Calendar.YEAR);
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH);
                java.util.Date d = sdf.parse(clean);
                if (d != null) {
                    // If date is in past, it's next month
                    if (d.getTime() < System.currentTimeMillis()) {
                        cal.setTime(d);
                        cal.add(Calendar.MONTH, 1);
                        return cal.getTimeInMillis();
                    }
                    return d.getTime();
                }
            }
        } catch (Exception e) {
            android.util.Log.w("BillSmsDetector", "parseDate failed for '" + dateStr + "': " + e.getMessage());
        }
        return 0;
    }
}
