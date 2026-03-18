package com.spendtracker.pro;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsParser {

    private static final Pattern AMOUNT_PATTERN =
            Pattern.compile("(?i)(?:rs|inr|₹)\\.?\\s?([0-9,]+(?:\\.[0-9]{1,2})?)");

    private static final Pattern DEBIT_PATTERN =
            Pattern.compile("(?i)(debited|spent|paid|purchase|txn|deducted|withdrawn)");

    private static final Pattern CREDIT_PATTERN =
            Pattern.compile("(?i)(credited|received|refund|cashback)");

    // ── Phrases that indicate this is NOT a real debit transaction ──────────
    // These SMS types contain amounts but are NOT spend transactions:
    // price alerts, OTPs, balance enquiries, statements, market rates, etc.
    private static final String[] NON_TRANSACTION_PHRASES = {
        // Market / commodity price alerts
        "as on ", "mcx ", "mcx ltd", "nse ", "bse ", "sensex", "nifty",
        "gold rate", "silver rate", "commodity", "market price",
        // OTP / verification
        "otp", "one time password", "verification code", "do not share",
        // Balance / statement
        "available balance", "avl bal", "avbl bal", "closing balance",
        "min due", "minimum due", "outstanding", "due date", "statement",
        "your limit", "credit limit",
        // Promotional / offers
        "congratulations", "you have won", "offer expires", "discount",
        "cashback offer", "get flat", "upto",
        // Loan / insurance alerts (not spend)
        "emi due", "loan amount", "insurance premium due",
        // SIM / account alerts
        "sim swap", "registered mobile", "kyc", "your account is",
        // Missed call / service alerts
        "missed call", "alert service",
    };

    // ── Sender IDs that never send spend transactions ────────────────────────
    private static final String[] NON_BANK_SENDERS = {
        "mcxltd", "mcx-ltd", "jm-mcx", "nse", "bse", "cdsl", "nsdl",
        "sebi", "irda", "irdai", "mutual", "zerodha-alert",
    };

    /**
     * Returns true if the SMS is a real spend/debit transaction.
     * Returns false for price alerts, OTPs, balance checks, promos, etc.
     */
    public static boolean isTransactionSms(String body, String sender) {
        if (body == null || body.length() < 10) return false;

        String lower = body.toLowerCase();
        String senderLower = sender != null ? sender.toLowerCase() : "";

        // Reject known non-bank senders immediately
        for (String nonBank : NON_BANK_SENDERS) {
            if (senderLower.contains(nonBank)) return false;
        }

        // Reject if any non-transaction phrase is found
        for (String phrase : NON_TRANSACTION_PHRASES) {
            if (lower.contains(phrase)) return false;
        }

        // Must contain an amount
        if (!AMOUNT_PATTERN.matcher(body).find()) return false;

        // Must have a debit keyword (credit-only messages like "credited" without
        // debit keywords are income, not spend — handled separately in classifier)
        boolean hasDebit  = DEBIT_PATTERN.matcher(body).find();
        boolean hasCredit = CREDIT_PATTERN.matcher(body).find();

        // Pure credit with no debit = income, skip
        if (!hasDebit && hasCredit) return false;

        // No transaction keyword at all = probably informational
        if (!hasDebit && !hasCredit) return false;

        return true;
    }

    public static SmsTransaction parse(String body, String sender) {
        if (body == null || body.length() < 5) return null;

        // ── Reject non-transaction SMS before doing anything else ──────────
        if (!isTransactionSms(body, sender)) return null;

        Matcher amountMatcher = AMOUNT_PATTERN.matcher(body);
        if (!amountMatcher.find()) return null;

        double amount;
        try {
            amount = Double.parseDouble(amountMatcher.group(1).replace(",", ""));
        } catch (Exception e) {
            return null;
        }

        if (amount <= 0) return null;

        SmsTransaction t = new SmsTransaction();
        t.amount = amount;

        // Extract merchant
        String merchant = "";
        try {
            merchant = MerchantExtractor.extract(body);
        } catch (Exception ignored) {}

        if (merchant == null || merchant.isEmpty()) {
            merchant = sender != null ? sender : "Unknown";
        }

        t.merchant = merchant;

        // ── FIX: use classify() not getInfo() to resolve category by merchant ──
        t.category = CategoryEngine.classify(merchant, body);

        t.paymentMethod  = detectPaymentMethod(body);
        t.paymentDetail  = sender;
        t.isSelfTransfer = isSelfTransfer(body);

        return t;
    }

    private static String detectPaymentMethod(String sms) {
        if (sms == null) return "BANK";
        String s = sms.toLowerCase();
        if (s.contains("upi"))     return "UPI";
        if (s.contains("card"))    return "CARD";
        if (s.contains("atm"))     return "ATM";
        if (s.contains("netbank")) return "NETBANKING";
        if (s.contains("wallet"))  return "WALLET";
        return "BANK";
    }

    private static boolean isSelfTransfer(String sms) {
        if (sms == null) return false;
        String s = sms.toLowerCase();
        return s.contains("self") || s.contains("own account")
                || s.contains("transfer to your");
    }
}
