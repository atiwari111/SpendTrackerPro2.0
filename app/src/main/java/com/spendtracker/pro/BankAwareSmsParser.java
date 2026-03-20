package com.spendtracker.pro;

import java.util.regex.*;

/**
 * BankAwareSmsParser — bank-specific SMS parser with confidence scoring.
 *
 * Confidence scoring (inspired by RegexOptimizer pattern):
 *   Each Pattern is paired with a confidence weight (0.0–1.0).
 *   ALL patterns for the detected bank are tried; the highest-confidence
 *   match wins — not the first match. This eliminates wrong merchant
 *   extractions caused by a weaker pattern matching before a stronger one.
 *
 * Bank coverage: HDFC, SBI, ICICI, AXIS, KOTAK, PNB, YES, IDFC, BOB, CANARA.
 */
public class BankAwareSmsParser {

    public static class ParseResult {
        public final double  amount;
        public final String  merchant;
        public final String  paymentMethod;
        public final String  paymentDetail;
        public final String  category;
        public final String  bankName;
        public final boolean isUpi;
        public final double  confidence;

        ParseResult(double amount, String merchant, String paymentMethod,
                    String paymentDetail, String category, String bankName,
                    boolean isUpi, double confidence) {
            this.amount        = amount;
            this.merchant      = merchant != null ? merchant : "Unknown";
            this.paymentMethod = paymentMethod != null ? paymentMethod : "BANK";
            this.paymentDetail = paymentDetail != null ? paymentDetail : "";
            this.category      = category != null ? category : "Others";
            this.bankName      = bankName != null ? bankName : "";
            this.isUpi         = isUpi;
            this.confidence    = confidence;
        }
    }

    private static class WeightedPattern {
        final Pattern pattern;
        final double  confidence;
        WeightedPattern(String regex, double confidence) {
            this.pattern    = safeCompile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            this.confidence = confidence;
        }
    }

    // ── HDFC ──────────────────────────────────────────────────────
    private static final WeightedPattern[] HDFC = {
        new WeightedPattern(
            "(?i)Txn\\s+(?:Rs\\.?|INR|\\u20b9)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)[\\s\\S]{0,120}?At\\s+([A-Za-z0-9@&'./\\-]{2,50})",
            0.95),
        new WeightedPattern(
            "(?:Rs\\.?|INR|\\u20b9)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:has been\\s*)?debited.*?to\\s+([A-Za-z0-9&'./\\-\\s]{2,40}?)\\s*(?:via|Ref|Avbl|$)",
            0.90),
        new WeightedPattern(
            "(?:Rs\\.?|INR|\\u20b9)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*debited.*?Info:\\s*([A-Za-z0-9&'./\\-\\s]{2,40})",
            0.90),
    };

    // ── SBI ───────────────────────────────────────────────────────
    private static final WeightedPattern[] SBI = {
        new WeightedPattern(
            "(?:IMPS|UPI)[/\\s].*?(?:Rs\\.?|INR|\\u20b9)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*debited.*?to\\s+([A-Za-z0-9@&'./\\-\\s]{2,50}?)(?:\\s*\\(|\\s+Ref|\\.|,|$)",
            0.92),
        new WeightedPattern(
            "(?:INR|Rs\\.?|\\u20b9)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:spent|debited).*?(?:at|to|merchant:?)\\s+([A-Za-z0-9&'./\\-\\s]{2,40}?)(?:\\s+on|\\s+via|\\s*Ref|\\s*Avl|\\.|,|$)",
            0.87),
    };

    // ── ICICI ─────────────────────────────────────────────────────
    private static final WeightedPattern[] ICICI = {
        new WeightedPattern(
            "UPI\\s+txn\\s+of\\s+(?:Rs\\.?|INR|\\u20b9)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s+to\\s+([A-Za-z0-9&'./\\-\\s]{2,40}?)\\s+Ref",
            0.95),
        new WeightedPattern(
            "(?:Rs\\.?|INR|\\u20b9)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*debited.*?Info[:\\s]+([A-Za-z0-9&'./\\-\\s]{2,40}?)(?:\\.|Avail|$)",
            0.88),
    };

    // ── AXIS ──────────────────────────────────────────────────────
    private static final WeightedPattern[] AXIS = {
        new WeightedPattern(
            "(?:INR|Rs\\.?|\\u20b9)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:debited|spent).*?(?:towards|at|to)\\s+([A-Za-z0-9&'./\\-\\s]{2,40}?)\\s+on",
            0.90),
        new WeightedPattern(
            "(?:Rs\\.?|INR|\\u20b9)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*debited.*?(?:to|at)\\s+([A-Za-z0-9&'./\\-\\s]{2,40}?)(?:\\s+Ref|\\s+via|\\.|$)",
            0.82),
    };

    // ── KOTAK ─────────────────────────────────────────────────────
    private static final WeightedPattern[] KOTAK = {
        new WeightedPattern(
            "(?:Rs\\.?|INR|\\u20b9)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*debited.*?to\\s+([A-Za-z0-9&'./\\-\\s]{2,40}?)(?:\\s+via|\\s+Ref|\\.|$)",
            0.88),
    };

    // ── PNB ───────────────────────────────────────────────────────
    private static final WeightedPattern[] PNB = {
        new WeightedPattern(
            "(?:INR|Rs\\.?|\\u20b9)\\s*([0-9,]+(?:\\.[0-9]{1,2})?).*?(?:to|ben)\\s+([A-Za-z][A-Za-z0-9&'.\\-\\s]{1,35}?)(?:\\s+(?:Ref|A/c|UPI)|\\.|,|$)",
            0.88),
        new WeightedPattern(
            "(?:INR|Rs\\.?|\\u20b9)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s+(?:Dt|on)\\s+[0-9\\-/]+",
            0.60),
    };

    // ── YES Bank ──────────────────────────────────────────────────
    private static final WeightedPattern[] YES = {
        new WeightedPattern(
            "(?:INR|Rs\\.?|\\u20b9)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:debited|spent|paid).*?using\\s+card.*?(?:at|to)\\s+([A-Za-z0-9&'./\\-\\s]{2,40}?)(?:\\s+Ref|\\.|,|$)",
            0.88),
        new WeightedPattern(
            "(?:INR|Rs\\.?|\\u20b9)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:debited|spent|paid).*?(?:to|at)\\s+([A-Za-z0-9&'./\\-\\s]{2,40}?)(?:\\s+via|\\s+Ref|\\s+on|\\.|,|$)",
            0.82),
    };

    // ── IDFC First ────────────────────────────────────────────────
    private static final WeightedPattern[] IDFC = {
        new WeightedPattern(
            "(?:INR|Rs\\.?|\\u20b9)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:debited|spent).*?(?:at|to)\\s+([A-Za-z0-9&'./\\-\\s]{2,40}?)(?:\\s+Ref|\\s+on|\\.|,|$)",
            0.85),
    };

    // ── Bank of Baroda (BOB) ──────────────────────────────────────
    private static final WeightedPattern[] BOB = {
        new WeightedPattern(
            "(?:INR|Rs\\.?|\\u20b9)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:debited|spent|paid).*?(?:to|at)\\s+([A-Za-z0-9&'./\\-\\s]{2,40}?)(?:\\s+Ref|\\s+via|\\.|,|$)",
            0.85),
        new WeightedPattern(
            "(?:INR|Rs\\.?|\\u20b9)\\s*([0-9,]+(?:\\.[0-9]{1,2})?).*?(?:to|ben)\\s+([A-Za-z][A-Za-z0-9&'.\\-\\s]{1,35}?)(?:\\s+Ref|\\.|,|$)",
            0.78),
    };

    // ── Canara Bank ───────────────────────────────────────────────
    private static final WeightedPattern[] CANARA = {
        new WeightedPattern(
            "(?:INR|Rs\\.?|\\u20b9)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:debited|dr).*?(?:to|at|towards)\\s+([A-Za-z0-9&'./\\-\\s]{2,40}?)(?:\\s+Ref|\\s+via|\\.|,|$)",
            0.85),
        new WeightedPattern(
            "(?:INR|Rs\\.?|\\u20b9)\\s*([0-9,]+(?:\\.[0-9]{1,2})?).*?(?:to|ben)\\s+([A-Za-z][A-Za-z0-9&'.\\-\\s]{1,35}?)(?:\\s+Ref|\\.|,|$)",
            0.75),
    };

    // ── Generic fallback ──────────────────────────────────────────
    private static final WeightedPattern[] GENERIC = {
        new WeightedPattern(
            "(?:payment|txn|transaction|transfer).*?(?:Rs\\.?|INR|\\u20b9)\\s*([0-9,]+(?:\\.[0-9]{1,2})?).*?to\\s+([A-Za-z0-9@&'./\\-\\s]{2,50}?)(?:\\s+Ref|\\s+on|\\.|,|$)",
            0.65),
        new WeightedPattern(
            "(?:Rs\\.?|INR|\\u20b9)\\s*([0-9,]+(?:\\.[0-9]{1,2})?).*?(?:to|at)\\s+([A-Za-z][A-Za-z0-9&'./\\-\\s]{2,35}?)(?:\\s+(?:via|Ref|on)|\\.|,|$)",
            0.55),
    };

    // ═══════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════

    public static ParseResult parse(String body, String sender) {
        if (body == null || body.isEmpty()) return null;
        if (!SmsParser.isTransactionSms(body, sender)) return null;

        try {
            BankDetector.BankInfo bankInfo = BankDetector.detect(sender, body);
            String bank = (bankInfo != null && bankInfo.name != null) ? bankInfo.name : "";

            WeightedPattern[] patterns;
            switch (bank) {
                case "HDFC":   patterns = HDFC;   break;
                case "SBI":    patterns = SBI;    break;
                case "ICICI":  patterns = ICICI;  break;
                case "AXIS":   patterns = AXIS;   break;
                case "KOTAK":  patterns = KOTAK;  break;
                case "PNB":    patterns = PNB;    break;
                case "YES":    patterns = YES;    break;
                case "IDFC":   patterns = IDFC;   break;
                case "BOB":    patterns = BOB;    break;
                case "CANARA": patterns = CANARA; break;
                default:       patterns = GENERIC; break;
            }

            // Try bank-specific patterns first, then generic as extra fallback
            AmountMerchant best = bestMatch(body, patterns);
            if ((best == null || best.amount <= 0) && patterns != GENERIC) {
                best = bestMatch(body, GENERIC);
            }

            // Last resort: delegate to SmsParser generic logic
            if (best == null || best.amount <= 0) {
                SmsTransaction generic = SmsParser.parse(body, sender);
                if (generic == null) return null;
                return new ParseResult(generic.amount, generic.merchant,
                        generic.paymentMethod,
                        buildPaymentDetail(bank, generic.paymentDetail),
                        generic.category, bank, isUpi(body), 0.40);
            }

            String merchant = best.merchant;
            // P2: resolve user-corrected merchant alias before classifying
            merchant = CategoryEngine.resolveMerchantAlias(merchant);
            String category = SmsParser.isCreditTransaction(body)
                    ? SmsParser.classifyCreditCategory(body, merchant)
                    : CategoryEngine.classify(merchant, body);
            if (category == null) category = "Others";

            String paymentMethod = detectPaymentMethod(body);
            String paymentDetail = buildPaymentDetail(bank, buildDetailFromBody(body, bank));
            String upiId         = UpiDetector.detectUpiId(body);
            if (upiId != null && !upiId.isEmpty()) paymentDetail += " \u00b7 " + upiId;

            return new ParseResult(best.amount, merchant, paymentMethod,
                    paymentDetail, category, bank, isUpi(body), best.confidence);

        } catch (Exception e) {
            android.util.Log.e("BankAwareSmsParser", "Parse error: " + e.getMessage());
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // CONFIDENCE-SCORED MATCHING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Tries ALL weighted patterns; returns the highest-confidence match.
     * This is the key difference from the old tryPatterns() — first-match
     * wins was causing weaker patterns to override better ones.
     */
    private static AmountMerchant bestMatch(String body, WeightedPattern[] patterns) {
        AmountMerchant best = null;
        for (WeightedPattern wp : patterns) {
            if (wp == null || wp.pattern == null) continue;
            try {
                Matcher m = wp.pattern.matcher(body);
                if (!m.find()) continue;

                double amount;
                try {
                    amount = Double.parseDouble(m.group(1).replace(",", ""));
                } catch (NumberFormatException nfe) { continue; }
                if (amount <= 0 || amount >= 10_000_000) continue;

                String merchant = "";
                try {
                    if (m.groupCount() >= 2 && m.group(2) != null) {
                        merchant = m.group(2).trim();
                    }
                } catch (IndexOutOfBoundsException ignored) {}

                merchant = cleanMerchant(merchant);
                // Small bonus for a non-empty merchant name
                double score = wp.confidence + (merchant.length() >= 2 ? 0.05 : 0.0);

                if (best == null || score > best.confidence) {
                    best = new AmountMerchant(amount,
                            merchant.length() >= 2 ? titleCase(merchant) : "",
                            score);
                }
            } catch (Exception e) {
                android.util.Log.e("BankAwareSmsParser", "Pattern error: " + e.getMessage());
            }
        }
        return best;
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════

    private static Pattern safeCompile(String regex, int flags) {
        try {
            return Pattern.compile(regex, flags);
        } catch (PatternSyntaxException e) {
            android.util.Log.e("BankAwareSmsParser", "Bad regex: " + e.getMessage());
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
        if (lower.contains("neft") || lower.contains("imps") || lower.contains("rtgs")) return "BANK";
        if (lower.contains("credit card") || lower.contains("credit a/c"))  return "CREDIT_CARD";
        if (lower.contains("debit card")  || lower.contains("debit a/c"))   return "DEBIT_CARD";
        if (lower.contains("upi"))                                           return "UPI";
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

    // ── Fix 2.5: balance & account extraction ─────────────────────

    private static final Pattern BAL_PATTERN = Pattern.compile(
        // Standard: "Bal INR 3454.36" / "Balance: Rs. 5204"
        // Fix 2.4: also captures "Avail Bal in A/c xxx253: Rs. 5204.87 CR"
        "(?i)(?:Avail(?:able)?\\s+)?Bal(?:ance)?[^0-9]{0,30}?(?:INR|Rs\\.?|\\u20b9)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)");

    private static final Pattern ACCT_LAST4_PATTERN = Pattern.compile(
        // Fix 2.4: support 3-digit suffixes too (e.g. SBI "xxx253") in addition to standard 4-digit
        "(?i)A/c\\s+[Xx*0-9]{0,8}([0-9]{3,4})");

    public static double extractBalance(String body) {
        if (body == null) return -1;
        Matcher m = BAL_PATTERN.matcher(body);
        if (m.find()) {
            try { return Double.parseDouble(m.group(1).replace(",", "")); }
            catch (Exception ignored) {}
        }
        return -1;
    }

    public static String extractAccountLast4(String body) {
        if (body == null) return null;
        Matcher m = ACCT_LAST4_PATTERN.matcher(body);
        return m.find() ? m.group(1) : null;
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
        final double confidence;
        AmountMerchant(double amount, String merchant, double confidence) {
            this.amount     = amount;
            this.merchant   = merchant;
            this.confidence = confidence;
        }
    }
}
