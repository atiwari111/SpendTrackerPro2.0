package com.spendtracker.pro;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsParser {

    private static final Pattern AMOUNT_PATTERN =
            Pattern.compile("(?i)(?:rs|inr|₹)\\.?\\s?(\\d+(?:\\.\\d{1,2})?)");

    private static final Pattern DEBIT_PATTERN =
            Pattern.compile("(?i)(debited|spent|paid|purchase|txn)");

    private static final Pattern CREDIT_PATTERN =
            Pattern.compile("(?i)(credited|received)");

    public static SmsTransaction parse(String body, String sender) {

        if (body == null || body.length() < 5) return null;

        Matcher amountMatcher = AMOUNT_PATTERN.matcher(body);

        if (!amountMatcher.find()) return null;

        double amount;

        try {
            amount = Double.parseDouble(amountMatcher.group(1));
        } catch (Exception e) {
            return null;
        }

        boolean isDebit = DEBIT_PATTERN.matcher(body).find();
        boolean isCredit = CREDIT_PATTERN.matcher(body).find();

        if (!isDebit && isCredit) return null;

        SmsTransaction t = new SmsTransaction();
        t.amount = amount;

        String merchant = "";

        try {
            merchant = MerchantExtractor.extract(body);
        } catch (Exception ignored) {}

        if (merchant == null || merchant.isEmpty()) {
            merchant = sender != null ? sender : "Unknown";
        }

        t.merchant = merchant;

        try {
            t.category = CategoryEngine.getInfo(merchant).name;
        } catch (Exception e) {
            t.category = "Others";
        }

        t.paymentMethod = detectPaymentMethod(body);
        t.paymentDetail = sender;
        t.isSelfTransfer = isSelfTransfer(body);

        return t;
    }

    private static String detectPaymentMethod(String sms) {

        if (sms == null) return "BANK";

        String s = sms.toLowerCase();

        if (s.contains("upi")) return "UPI";
        if (s.contains("card")) return "CARD";
        if (s.contains("atm")) return "ATM";
        if (s.contains("netbank")) return "NETBANKING";
        if (s.contains("wallet")) return "WALLET";

        return "BANK";
    }

    private static boolean isSelfTransfer(String sms) {

        if (sms == null) return false;

        String s = sms.toLowerCase();

        return s.contains("self")
                || s.contains("own account")
                || s.contains("transfer to your");
    }
}
