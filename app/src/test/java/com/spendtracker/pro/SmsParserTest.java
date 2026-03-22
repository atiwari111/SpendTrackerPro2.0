package com.spendtracker.pro;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for {@link SmsParser}.
 *
 * Covers:
 *  - Transaction detection (isTransactionSms)
 *  - Credit vs debit classification (isCreditTransaction)
 *  - Amount parsing via parse()
 *  - Non-transaction SMS rejection (OTPs, balance alerts, promos, stock alerts)
 *  - Self-transfer detection
 */
public class SmsParserTest {

    // ── isTransactionSms ─────────────────────────────────────────

    @Test
    public void detectsHdfcDebitSms() {
        String body = "Your A/c XX1234 is debited for Rs.1500.00 on 20-Mar-26. "
                    + "Info: UPI/SWIGGY. Avl Bal: Rs.8500.00.";
        assertTrue(SmsParser.isTransactionSms(body, "HDFC-BNK"));
    }

    @Test
    public void detectsSbiCreditSms() {
        String body = "INR 25000.00 credited to your SBI a/c XX5678 "
                    + "by IMPS from EMPLOYER on 01-Mar-26. Ref 123456.";
        assertTrue(SmsParser.isTransactionSms(body, "SBI-SMS"));
    }

    @Test
    public void detectsUpiPaymentSms() {
        String body = "Rs.499 debited from your account via UPI. "
                    + "Paid to NETFLIX. UPI Ref: 999888777.";
        assertTrue(SmsParser.isTransactionSms(body, "ICICI-BNK"));
    }

    @Test
    public void rejectsOtpSms() {
        String body = "Your OTP is 482910. Do not share this one time password with anyone. "
                    + "Valid for Rs.500 transaction.";
        assertFalse("OTP SMS must be rejected", SmsParser.isTransactionSms(body, "VM-HDFCBK"));
    }

    @Test
    public void rejectsAvailableBalanceSms() {
        String body = "Your avl bal is Rs.12340.50 as on 20-Mar-26. -SBI";
        assertFalse("Balance-only SMS must be rejected", SmsParser.isTransactionSms(body, "SBI-SMS"));
    }

    @Test
    public void rejectsStockMarketAlertSms() {
        String body = "SENSEX at 75000. NIFTY 22800. Gold rate Rs.6200/gm. -MCX LTD";
        assertFalse("Stock/MCX alert must be rejected", SmsParser.isTransactionSms(body, "JM-MCX"));
    }

    @Test
    public void rejectsPromoSmsWithUrl() {
        String body = "Get flat Rs.200 cashback on your next order. http://bit.ly/abc123";
        assertFalse("Promo SMS with URL must be rejected", SmsParser.isTransactionSms(body, "AD-PAYTM"));
    }

    @Test
    public void rejectsScheduledDebitNotice() {
        String body = "Rs.1200 will be debited from your account on 25-Mar for EMI.";
        assertFalse("Future/scheduled debit notice must be rejected",
                SmsParser.isTransactionSms(body, "HDFC-BNK"));
    }

    @Test
    public void rejectsMinDueSms() {
        String body = "Min due Rs.500 for your credit card XX9999. Pay by 30-Mar.";
        assertFalse("Min-due SMS must be rejected", SmsParser.isTransactionSms(body, "AXIS-BNK"));
    }

    @Test
    public void rejectsSmsWithNoAmount() {
        String body = "Your account has been debited. Please check your statement.";
        assertFalse("SMS without amount must be rejected", SmsParser.isTransactionSms(body, "GENERIC"));
    }

    @Test
    public void rejectsShortBody() {
        assertFalse(SmsParser.isTransactionSms("Rs.100", "HDFC"));
    }

    @Test
    public void rejectsNullBody() {
        assertFalse(SmsParser.isTransactionSms(null, "HDFC"));
    }

    // ── isCreditTransaction ──────────────────────────────────────

    @Test
    public void classifiesCreditAsCredit() {
        String body = "Rs.50000.00 credited to your account. Salary for March-26.";
        assertTrue(SmsParser.isCreditTransaction(body));
    }

    @Test
    public void classifiesDebitAsNotCredit() {
        String body = "Rs.1200 debited from your a/c for AMAZON order.";
        assertFalse(SmsParser.isCreditTransaction(body));
    }

    @Test
    public void classifiesMixedCreditDebitAsNotCredit() {
        // "reversed" is a credit word but "debited" is also present — should not be pure credit
        String body = "Rs.200 refund reversed. Earlier Rs.500 was debited from your account.";
        assertFalse(SmsParser.isCreditTransaction(body));
    }

    @Test
    public void classifiesCashbackAsCredit() {
        String body = "Cashback of Rs.50 credited to your wallet.";
        assertTrue(SmsParser.isCreditTransaction(body));
    }

    // ── parse() ───────────────────────────────────────────────────

    @Test
    public void parsesAmountCorrectly() {
        String body = "INR 1,23,456.78 debited from your SBI a/c. Merchant: FLIPKART.";
        SmsTransaction tx = SmsParser.parse(body, "SBI-SMS");
        assertNotNull(tx);
        assertEquals(123456.78, tx.amount, 0.001);
    }

    @Test
    public void parsesDebitAsNotCredit() {
        String body = "Rs.999 debited. Paid to SPOTIFY via UPI. Ref 111222.";
        SmsTransaction tx = SmsParser.parse(body, "ICICI-BNK");
        assertNotNull(tx);
        assertFalse(tx.isCredit);
    }

    @Test
    public void parsesCreditAsCredit() {
        String body = "Rs.75000 credited to your account by NEFT. Salary.";
        SmsTransaction tx = SmsParser.parse(body, "HDFC-BNK");
        assertNotNull(tx);
        assertTrue(tx.isCredit);
    }

    @Test
    public void returnsNullForNonTransactionSms() {
        String body = "Your OTP is 123456. Do not share this one time password.";
        assertNull(SmsParser.parse(body, "VM-HDFCBK"));
    }

    @Test
    public void detectsSelfTransfer() {
        String body = "Rs.5000 debited from your account. Transfer to your own account XX9999.";
        SmsTransaction tx = SmsParser.parse(body, "KOTAK-BNK");
        assertNotNull(tx);
        assertTrue(tx.isSelfTransfer);
    }

    @Test
    public void detectsUpiPaymentMethod() {
        String body = "Rs.250 debited via UPI. Merchant: ZOMATO. Ref 998877.";
        SmsTransaction tx = SmsParser.parse(body, "HDFC-BNK");
        assertNotNull(tx);
        assertEquals("UPI", tx.paymentMethod);
    }

    @Test
    public void impsBeatsUpiInPaymentMethodDetection() {
        // IMPS should win over UPI when both keywords appear — IMPS is more specific
        String body = "Rs.10000 debited via IMPS/UPI. Ref 123.";
        SmsTransaction tx = SmsParser.parse(body, "SBI-SMS");
        assertNotNull(tx);
        assertEquals("BANK", tx.paymentMethod); // NEFT/IMPS/RTGS resolves to BANK
    }
}
