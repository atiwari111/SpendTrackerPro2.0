package com.spendtracker.pro;

import java.util.regex.*;

/**
 * MerchantExtractor — extracts merchant name from SMS body.
 *
 * Handles patterns common in Indian bank SMS:
 *   "debited at MERCHANT"
 *   "paid to MERCHANT"
 *   "transferred to MERCHANT"
 *   "UPI Autopay on MERCHANT"       ← PNB autopay mandates
 *   "for UPI Autopay on MERCHANT"   ← PNB advance notices
 *   "Info: MERCHANT"                ← HDFC/ICICI
 *   "merchant: MERCHANT"
 */
public class MerchantExtractor {

    // Priority-ordered patterns — first match wins
    private static final Pattern[] PATTERNS = {
        // UPI Autopay mandate: "for UPI Autopay on Gullak Money,UMN:..."
        Pattern.compile("(?i)autopay\\s+on\\s+([A-Za-z][A-Za-z0-9&'./\\-\\s]{1,35}?)(?:,|\\.|\\s+UMN|$)"),
        // "paid to MERCHANT" / "transferred to MERCHANT"
        Pattern.compile("(?i)(?:paid|transfer(?:red)?)\\s+to\\s+([A-Za-z][A-Za-z0-9&'./\\-\\s]{1,35}?)(?:\\s+(?:Ref|on|via|UPI)|\\.|,|$)"),
        // "debited at MERCHANT" / "spent at MERCHANT"
        Pattern.compile("(?i)(?:debited|spent)\\s+at\\s+([A-Za-z][A-Za-z0-9&'./\\-\\s]{1,35}?)(?:\\s+(?:Ref|on|via)|\\.|,|$)"),
        // "Info: MERCHANT" (HDFC/ICICI style)
        Pattern.compile("(?i)Info[:\\s]+([A-Za-z][A-Za-z0-9&'./\\-\\s]{1,35}?)(?:\\.|Avail|$)"),
        // "merchant: MERCHANT"
        Pattern.compile("(?i)merchant[:\\s]+([A-Za-z][A-Za-z0-9&'./\\-\\s]{1,35}?)(?:\\s+Ref|\\.|,|$)"),
        // Generic " at MERCHANT" (keep as last resort — prone to false positives)
        Pattern.compile("(?i)\\bat\\s+([A-Za-z][A-Za-z0-9&'./\\-\\s]{1,25}?)(?:\\s+(?:on|Ref|via)|\\.|,|$)"),
    };

    // Words to strip from extracted merchant names
    private static final String[] NOISE_WORDS = {
        "your", "the", "a ", "an ", " via", " ref", " on", " by", " using"
    };

    public static String extract(String sms) {
        if (sms == null || sms.isEmpty()) return "";

        for (Pattern p : PATTERNS) {
            Matcher m = p.matcher(sms);
            if (m.find()) {
                String merchant = m.group(1).trim();
                merchant = clean(merchant);
                if (merchant.length() >= 2) return merchant;
            }
        }
        return "";
    }

    private static String clean(String s) {
        if (s == null) return "";
        // Remove trailing noise
        s = s.replaceAll("(?i)\\s+(via|ref|on|avail|avbl|mob|utr|upi)\\b.*$", "").trim();
        // Remove leading articles
        for (String noise : NOISE_WORDS) {
            if (s.toLowerCase().startsWith(noise.trim())) {
                s = s.substring(noise.trim().length()).trim();
            }
        }
        // Collapse whitespace
        s = s.replaceAll("\\s{2,}", " ").trim();
        return s;
    }
}
