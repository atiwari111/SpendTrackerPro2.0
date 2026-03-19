package com.spendtracker.pro;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsParser {

    private static final Pattern AMOUNT_PATTERN =
            Pattern.compile("(?i)(?:rs|inr|₹)\\.?\\s?([0-9,]+(?:\\.[0-9]{1,2})?)");

    private static final Pattern DEBIT_PATTERN =
            Pattern.compile("(?i)(debited|spent|paid|purchase|txn|deducted|withdrawn)");

    private static final Pattern CREDIT_PATTERN =
            Pattern.compile("(?i)(credited|received|refund|cashback|reversed)");

    // NON_TRANSACTION phrases — amounts in these SMS are NOT real transactions
    private static final String[] NON_TRANSACTION_PHRASES = {
        "as on ", "mcx ", "mcx ltd", "nse ", "bse ", "sensex", "nifty",
        "gold rate", "silver rate", "commodity", "market price",
        "otp", "one time password", "verification code", "do not share",
        "avl bal", "avbl bal", "closing balance",
        "min due", "minimum due", "your limit", "credit limit",
        "congratulations", "you have won", "offer expires",
        "get flat", "upto ", "cashback offer",
        "emi due", "loan amount", "insurance premium due",
        "sim swap", "registered mobile", "kyc", "your account is",
        "missed call", "alert service",
        // Fix 2.6: future/scheduled debit notices — money has NOT moved yet
        "will be debited", "will be charged", "would be debited",
        "scheduled debit", "auto-debit scheduled",
        // Fraud / promo SMS — confirmed by dataset analysis (74 false positives eliminated)
        "lottery",          // "You have received lottery Rs X"
        "claim now",        // "Free cashback Rs X claim now http://..."
        "click now",
        "click link",
        "http://",          // Real bank SMS never contain raw URLs
        "https://bit.ly",   // Shortened URLs = promo/phishing
        "free cashback",    // Legitimate cashback SMS say "cashback credited", not "free cashback"
        "you have won",
        "you won",
        "prize",
        "winner",
    };

    private static final String[] NON_BANK_SENDERS = {
        "mcxltd", "mcx-ltd", "jm-mcx", "nse", "bse", "cdsl", "nsdl",
        "sebi", "irda", "irdai", "zerodha-alert",
    };

    /**
     * Returns true if SMS is a real financial transaction (debit OR credit).
     * Rejects price alerts, OTPs, balance checks, promos, etc.
     */
    public static boolean isTransactionSms(String body, String sender) {
        if (body == null || body.length() < 10) return false;

        String lower = body.toLowerCase();
        String senderLower = sender != null ? sender.toLowerCase() : "";

        for (String nonBank : NON_BANK_SENDERS) {
            if (senderLower.contains(nonBank)) return false;
        }
        for (String phrase : NON_TRANSACTION_PHRASES) {
            if (lower.contains(phrase)) return false;
        }
        if (!AMOUNT_PATTERN.matcher(body).find()) return false;

        boolean hasDebit  = DEBIT_PATTERN.matcher(body).find();
        boolean hasCredit = CREDIT_PATTERN.matcher(body).find();

        // Must have at least one transaction keyword
        return hasDebit || hasCredit;
    }

    /** Returns true if this SMS is a credit/income transaction (not a debit). */
    public static boolean isCreditTransaction(String body) {
        if (body == null) return false;
        String lower = body.toLowerCase();
        boolean hasDebit  = DEBIT_PATTERN.matcher(body).find();
        boolean hasCredit = CREDIT_PATTERN.matcher(body).find();
        // Pure credit = credited/received/refund/cashback without any debit keyword
        return hasCredit && !hasDebit;
    }

    public static SmsTransaction parse(String body, String sender) {
        if (body == null || body.length() < 5) return null;
        if (!isTransactionSms(body, sender)) return null;

        Matcher amountMatcher = AMOUNT_PATTERN.matcher(body);
        if (!amountMatcher.find()) return null;

        double amount;
        try {
            amount = Double.parseDouble(amountMatcher.group(1).replace(",", ""));
        } catch (Exception e) { return null; }
        if (amount <= 0) return null;

        SmsTransaction t = new SmsTransaction();
        t.amount = amount;
        t.isCredit = isCreditTransaction(body);

        String merchant = "";
        try { merchant = MerchantExtractor.extract(body); } catch (Exception ignored) {}
        if (merchant == null || merchant.isEmpty()) {
            merchant = sender != null ? sender : "Unknown";
        }
        t.merchant = merchant;

        // Classify with credit awareness
        if (t.isCredit) {
            t.category = classifyCreditCategory(body, merchant);
        } else {
            t.category = CategoryEngine.classify(merchant, body);
        }

        t.paymentMethod  = detectPaymentMethod(body);
        t.paymentDetail  = sender;
        t.isSelfTransfer = isSelfTransfer(body);

        return t;
    }

    /**
     * Classify credit/income transactions into the correct income category.
     */
    public static String classifyCreditCategory(String body, String merchant) {
        String lower = (body != null ? body : "").toLowerCase();
        String m     = (merchant != null ? merchant : "").toLowerCase();

        if (lower.contains("salary") || lower.contains("payroll") ||
            lower.contains("credited by employer"))
            return "💵 Salary";

        if (lower.contains("cashback"))
            return "🎉 Cashback";

        // Fix 2.2: insurance/medical claim credits are refunds
        if (lower.contains("claim") || lower.contains("reimburs"))
            return "↩️ Refund";

        if (lower.contains("refund") || lower.contains("reversed") ||
            lower.contains("return"))
            return "↩️ Refund";

        if (lower.contains("dividend") || lower.contains("interest credit") ||
            lower.contains("mutual fund") || lower.contains("redemption") ||
            m.contains("zerodha") || m.contains("groww") || m.contains("upstox"))
            return "📈 Investment Return";

        // Generic bank transfer from known person = likely personal transfer
        if (lower.contains("credited") && (lower.contains("upi") || lower.contains("imps")))
            return "↩️ Refund"; // Closest — user can re-categorise

        return "↩️ Refund"; // Safe default for any credit
    }

    private static String detectPaymentMethod(String sms) {
        if (sms == null) return "BANK";
        String s = sms.toLowerCase();
        // Fix 2.3: check bank-wire methods before UPI to avoid false UPI matches
        if (s.contains("neft") || s.contains("imps") || s.contains("rtgs")) return "BANK";
        if (s.contains("credit card")) return "CREDIT_CARD";
        if (s.contains("debit card"))  return "DEBIT_CARD";
        if (s.contains("upi"))         return "UPI";
        if (s.contains("card"))        return "CARD";
        if (s.contains("atm"))         return "ATM";
        if (s.contains("netbank"))     return "NETBANKING";
        if (s.contains("wallet"))      return "WALLET";
        return "BANK";
    }

    private static boolean isSelfTransfer(String sms) {
        if (sms == null) return false;
        String s = sms.toLowerCase();
        return s.contains("self") || s.contains("own account")
                || s.contains("transfer to your");
    }
}
