package com.spendtracker.pro;

public class MerchantExtractor {

    public static String extract(String sms) {

        if (sms == null) return "";

        String lower = sms.toLowerCase();

        int index = lower.indexOf(" at ");

        if (index != -1) {

            int start = index + 4;

            int end = Math.min(start + 20, sms.length());

            return sms.substring(start, end).split(" ")[0];
        }

        return "";
    }
}
