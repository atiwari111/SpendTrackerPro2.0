package com.spendtracker.pro;

import java.util.regex.*;

/**
 * UpiDetector v1.5
 * Extracts UPI IDs from SMS bodies.
 * Handles formats like: rahul@ybl, name@oksbi, merchant@upi, 9876543210@paytm
 * Avoids matching plain email addresses (known non-UPI domains filtered out).
 */
public class UpiDetector {

    // Matches word@handle — UPI handles are short alphanumeric (not full email domains)
    private static final Pattern UPI_PATTERN =
        Pattern.compile("\\b([a-zA-Z0-9._-]{2,50}@[a-zA-Z]{2,20})\\b");

    // Common email domains that are NOT UPI — skip these
    private static final String[] EMAIL_DOMAINS = {
        "gmail", "yahoo", "hotmail", "outlook", "rediffmail",
        "icloud", "live", "msn", "protonmail", "zoho"
    };

    /**
     * Extracts the first UPI ID found in the SMS body.
     * Returns null if none found or if match looks like a regular email.
     */
    public static String detectUpiId(String sms) {
        if (sms == null || sms.isEmpty()) return null;
        Matcher m = UPI_PATTERN.matcher(sms);
        while (m.find()) {
            String match = m.group(1);
            String handle = match.substring(match.indexOf('@') + 1).toLowerCase();
            if (!isEmailDomain(handle)) {
                return match;
            }
        }
        return null;
    }

    /**
     * Returns true if the SMS contains a valid UPI ID.
     */
    public static boolean hasUpiId(String sms) {
        return detectUpiId(sms) != null;
    }

    private static boolean isEmailDomain(String handle) {
        for (String domain : EMAIL_DOMAINS) {
            if (handle.equals(domain)) return true;
        }
        return false;
    }
}
