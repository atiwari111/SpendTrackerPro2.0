package com.spendtracker.pro;

public class SmsTransaction {
    public double amount;
    public String merchant;
    public String category;
    public String paymentMethod;
    public String paymentDetail;
    public boolean isSelfTransfer;
    /** true = credited (salary/refund/cashback/dividend), false = debited (spend) */
    public boolean isCredit;

    public SmsTransaction() {}
}
