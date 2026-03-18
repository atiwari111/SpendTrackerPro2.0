package com.spendtracker.pro;

import java.util.regex.*;

/**
 * BankAwareSmsParser v1.7
 *
 * Fixes from v1.6:
 * - CRASH FIX: SBI_SPENT and GENERIC_UPI_TO had broken regex: [0-9]{1,2]) 
 *   (mismatched bracket) → PatternSyntaxException crash at class load time
 * - Added top-level try/catch around all Pattern.compile() calls so a bad
 *   regex never crashes the app
 * - Defensive Double.parseDouble with try/catch in tryPatterns()
 * - Null-safe merchant, category, paymentDetail throughout
 */
public class BankAwareSmsParser {

    // ── Result class ─────────────────────────────────────────────
    public static class ParseResult {
        public final double  amount;
        public final String  merchant;
        public final String  paymentMethod;
        public final String  paymentDetail;
        public final String  category;
        public final String  bankName;
        public final boolean isUpi;

        ParseResult(double amount, String merchant, String paymentMethod,
                    String paymentDetail, String category, String bankName, boolean isUpi) {
            this.amount        = amount;
            this.merchant      = merchant != null ? merchant : "Unknown";
            this.paymentMethod = paymentMethod != null ? paymentMethod : "BANK";
            this.paymentDetail = paymentDetail != null ? paymentDetail : "";
            this.category      = category != null ? category : "Others";
            this.bankName      = bankName != null ? bankName : "";
            this.isUpi         = isUpi;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // BANK-SPECIFIC PATTERNS
    // ═══════════════════════════════════════════════════════════════

    // ── HDFC ──────────────────────────────────────────────────────
    private static final Pattern HDFC_DEBIT = safeCompile(
        "(?:Rs\\.?|INR|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:has been\\s*)?debited.*?to\\s+([A-Za-z0-9&'./\\-\\s]{2,40}?)\\s*(?:via|Ref|Avbl|$)",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern HDFC_INFO = safeCompile(
        "(?:Rs\\.?|INR|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*debited.*?Info:([A-Za-z0-9&'./\\-\\s]{2,40})",
        Pattern.CASE_INSENSITIVE);

    // ── SBI ───────────────────────────────────────────────────────
    // FIX: was [0-9]{1,2]) — missing opening bracket, caused PatternSyntaxException
    private static final Pattern SBI_SPENT = safeCompile(
        "(?:INR|Rs\\.?|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:spent|debited).*?(?:at|to|merchant:?)\\s+([A-Za-z0-9&'./\\-\\s]{2,40}?)(?:\\s+on|\\s+via|\\s*Ref|\\s*Avl|\\.|,|$)",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern SBI_UPI = safeCompile(
        "(?:IMPS|UPI)[/\\s].*?(?:Rs\\.?|INR|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*debited.*?to\\s+([A-Za-z0-9@&'./\\-\\s]{2,50}?)(?:\\s*\\(|\\s+Ref|\\.|,|$)",
        Pattern.CASE_INSENSITIVE);

    // ── ICICI ─────────────────────────────────────────────────────
    private static final Pattern ICICI_UPI = safeCompile(
        "UPI\\s+txn\\s+of\\s+(?:Rs\\.?|INR|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s+to\\s+([A-Za-z0-9&'./\\-\\s]{2,40}?)\\s+Ref",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern ICICI_INFO = safeCompile(
        "(?:Rs\\.?|INR|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*debited.*?Info[:\\s]+([A-Za-z0-9&'./\\-\\s]{2,40}?)(?:\\.|Avail|$)",
        Pattern.CASE_INSENSITIVE);

    // ── AXIS ──────────────────────────────────────────────────────
    private static final Pattern AXIS_TOWARDS = safeCompile(
        "(?:INR|Rs\\.?|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:debited|spent).*?(?:towards|at|to)\\s+([A-Za-z0-9&'./\\-\\s]{2,40}?)\\s+on",
        Pattern.CASE_INSENSITIVE);

    // ── KOTAK ─────────────────────────────────────────────────────
    private static final Pattern KOTAK_TO = safeCompile(
        "(?:Rs\\.?|INR|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*debited.*?to\\s+([A-Za-z0-9&'./\\-\\s]{2,40}?)(?:\\s+via|\\s+Ref|\\.|$)",
        Pattern.CASE_INSENSITIVE);

        // ── PNB ──────────────────────────────────────────────────
    // "A/c XXXX debited INR 60.00 Dt 17-03-26 thru UPI:XXXXXXXXXX"
    // Merchant not in body for bare UPI debits — resolved via category engine from sender/UPI info
    private static final Pattern PNB_DEBIT = safeCompile(
        "(?:INR|Rs\.?|\u20b9)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s+(?:Dt|on)\\s+[0-9\\-/]+",
        Pattern.CASE_INSENSITIVE);

    // PNB NEFT/IMPS with beneficiary: "transferred to BENEFICIARY Ref"
    private static final Pattern PNB_NEFT = safeCompile(
        "(?:INR|Rs\.?|\u20b9)\\s*([0-9,]+(?:\\.[0-9]{1,2})?).*?(?:to|ben)\\s+([A-Za-z][A-Za-z0-9&'.\\-\\s]{1,35}?)(?:\\s+(?:Ref|A/c|UPI)|\\.|,|$)",
        Pattern.CASE_INSENSITIVE);

    // ── GENERIC FALLBACK ──────────────────────────────────────────
    // FIX: was [0-9]{1,2]) — same mismatched bracket bug as SBI_SPENT
    private static final Pattern GENERIC_UPI_TO = safeCompile(
        "(?:payment|txn|transaction|transfer).*?(?:Rs\\.?|INR|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?).*?to\\s+([A-Za-z0-9@&'./\\-\\s]{2,50}?)(?:\\s+Ref|\\s+on|\\.|,|$)",
        Pattern.CASE_INSENSITIVE);

    // ═══════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════

    public static ParseResult parse(String body, String sender) {
        if (body == null || body.isEmpty()) return null;

        // Reject non-transaction SMS (price alerts, OTPs, balance checks, promos)
        if (!SmsParser.isTransactionSms(body, sender)) return null;

        try {
            BankDetector.BankInfo bankInfo = BankDetector.detect(sender, body);
            String bank = bankInfo != null ? bankInfo.name : "";
            if (bank == null) bank = "";

            AmountMerchant am = null;
            switch (bank) {
                case "HDFC":  am = tryPatterns(body, HDFC_DEBIT, HDFC_INFO);  break;
                case "SBI":   am = tryPatterns(body, SBI_UPI, SBI_SPENT);     break;
                case "ICICI": am = tryPatterns(body, ICICI_UPI, ICICI_INFO);  break;
                case "AXIS":  am = tryPatterns(body, AXIS_TOWARDS);            break;
                case "KOTAK": am = tryPatterns(body, KOTAK_TO);                break;
                case "PNB":   am = tryPatterns(body, PNB_NEFT, PNB_DEBIT);    break;
                default:      am = tryPatterns(body, GENERIC_UPI_TO);          break;
            }

            // Fall back to generic SmsParser if bank-specific failed
            if (am == null || am.amount <= 0) {
                SmsTransaction generic = SmsParser.parse(body, sender);
                if (generic == null) return null;
                String detail = buildPaymentDetail(bank, generic.paymentDetail);
                return new ParseResult(generic.amount, generic.merchant,
                        generic.paymentMethod, detail, generic.category, bank, isUpi(body));
            }

            // Bank-specific result
            String merchant      = am.merchant;
            String category      = CategoryEngine.classify(merchant, body);
            if (category == null) category = "Others";
            String paymentMethod = detectPaymentMethod(body);
            String paymentDetail = buildPaymentDetail(bank, buildDetailFromBody(body, bank));
            String upiId         = UpiDetector.detectUpiId(body);
            if (upiId != null && !upiId.isEmpty()) paymentDetail += " · " + upiId;

            return new ParseResult(am.amount, merchant, paymentMethod,
                    paymentDetail, category, bank, isUpi(body));

        } catch (Exception e) {
            // Never crash the caller — return null so importer skips this SMS
            android.util.Log.e("BankAwareSmsParser", "Parse error: " + e.getMessage());
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Try each pattern in order, return first successful AmountMerchant.
     * Defensive: skips null patterns (safeCompile returns null on bad regex).
     */
    private static AmountMerchant tryPatterns(String body, Pattern... patterns) {
        for (Pattern p : patterns) {
            if (p == null) continue; // safeCompile returned null — skip
            try {
                Matcher m = p.matcher(body);
                if (m.find()) {
                    // FIX 2: Defensive Double.parseDouble — returns null instead of crashing
                    double amount;
                    try {
                        amount = Double.parseDouble(m.group(1).replace(",", ""));
                    } catch (NumberFormatException nfe) {
                        continue; // bad amount string — try next pattern
                    }
                    if (amount <= 0 || amount >= 10_000_000) continue; // sanity check

                    String merchant = m.group(2) != null ? m.group(2).trim() : "";
                    merchant = cleanMerchant(merchant);
                    if (merchant.length() >= 2) {
                        return new AmountMerchant(amount, titleCase(merchant));
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("BankAwareSmsParser", "Pattern match error: " + e.getMessage());
            }
        }
        return null;
    }

    /**
     * Safely compile a regex pattern.
     * Returns null instead of crashing the app if the pattern is invalid.
     * A null pattern is skipped in tryPatterns().
     */
    private static Pattern safeCompile(String regex, int flags) {
        try {
            return Pattern.compile(regex, flags);
        } catch (PatternSyntaxException e) {
            android.util.Log.e("BankAwareSmsParser", "Bad regex pattern: " + e.getMessage());
            return null;
        }
    }

    private static String cleanMerchant(String s) {
        if (s == null) return "";
        s = s.replaceAll("(?i)\\s+(via|ref|on|avail|avbl|mob|utr)\\b.*$", "").trim();
        s = s.replaceAll("(?i)\\b(your|the|a|an)\\b", "").trim();
        s = s.replaceAll("\\s{2,}", " ").trim();
        return s;
    }

    private static String detectPaymentMethod(String body) {
        if (body == null) return "BANK";
        String lower = body.toLowerCase();
        if (lower.contains("upi"))                                          return "UPI";
        if (lower.contains("credit card") || lower.contains("credit a/c")) return "CREDIT_CARD";
        if (lower.contains("debit card")  || lower.contains("debit a/c"))  return "DEBIT_CARD";
        return "BANK";
    }

    private static String buildDetailFromBody(String body, String bank) {
        String method = detectPaymentMethod(body);
        Matcher card = Pattern.compile("[Xx*]{0,8}([0-9]{4})").matcher(body);
        String last4 = card.find() ? " (xx" + card.group(1) + ")" : "";
        switch (method) {
            case "UPI":         return bank + " UPI";
            case "CREDIT_CARD": return bank + " Credit Card" + last4;
            case "DEBIT_CARD":  return bank + " Debit Card" + last4;
            default:            return bank + " Bank Transfer";
        }
    }

    private static String buildPaymentDetail(String bank, String existing) {
        if (bank == null || bank.isEmpty()) return existing != null ? existing : "";
        if (existing != null && existing.toUpperCase().startsWith(bank)) return existing;
        return bank + " " + (existing != null ? existing : "");
    }

    private static boolean isUpi(String body) {
        return body != null && body.toLowerCase().contains("upi");
    }

    private static String titleCase(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder sb = new StringBuilder();
        for (String w : s.toLowerCase().split("\\s+")) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0)));
                if (w.length() > 1) sb.append(w.substring(1));
                sb.append(" ");
            }
        }
        return sb.toString().trim();
    }

    private static class AmountMerchant {
        final double amount;
        final String merchant;
        AmountMerchant(double amount, String merchant) {
            this.amount   = amount;
            this.merchant = merchant;
        }
    }
}
