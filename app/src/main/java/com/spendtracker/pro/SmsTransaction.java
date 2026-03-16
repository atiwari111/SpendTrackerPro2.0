package com.spendtracker.pro;

public class SmsTransaction {

    // Parsed transaction amount
    public double amount;

    // Merchant extracted from SMS
    public String merchant;

    // Category assigned by CategoryEngine
    public String category;

    // Payment method (UPI / CARD / ATM / etc)
    public String paymentMethod;

    // Additional payment detail (sender or account)
    public String paymentDetail;

    // Used to ignore self transfers
    public boolean isSelfTransfer;

    public SmsTransaction() {
        // Default constructor
    }
}
