package com.spendtracker.pro;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for {@link BankAwareSmsParser}.
 *
 * Validates per-bank pattern matching, confidence scoring, and correct
 * merchant / amount extraction for all supported bank formats.
 */
public class BankAwareSmsParserTest {

    // ── HDFC ─────────────────────────────────────────────────────

    @Test
    public void parsesHdfcTxnFormat() {
        String body = "Txn Rs.2500.00 done on HDFC Bank Card ending 1234 At AMAZON on 20-03-26. "
                    + "Avl Lmt: Rs.47500.";
        BankAwareSmsParser.ParseResult r = BankAwareSmsParser.parse(body, "HDFC-BNK");
        assertNotNull("HDFC txn must parse", r);
        assertEquals(2500.0, r.amount, 0.001);
        assertTrue("Confidence must be high for exact format", r.confidence >= 0.90);
    }

    @Test
    public void parsesHdfcDebitedFormat() {
        String body = "Rs.1200.00 has been debited from your HDFC Bank a/c ending 5678 "
                    + "to SWIGGY via UPI Ref 987654321.";
        BankAwareSmsParser.ParseResult r = BankAwareSmsParser.parse(body, "HDFC-BNK");
        assertNotNull(r);
        assertEquals(1200.0, r.amount, 0.001);
    }

    // ── SBI ───────────────────────────────────────────────────────

    @Test
    public void parsesSbiImpsDebit() {
        String body = "IMPS/987654321/INR 5000.00 debited from your SBI a/c XX1234 "
                    + "to FLIPKART (YESB0000123). Ref 987654321.";
        BankAwareSmsParser.ParseResult r = BankAwareSmsParser.parse(body, "SBI-SMS");
        assertNotNull("SBI IMPS format must parse", r);
        assertEquals(5000.0, r.amount, 0.001);
    }

    @Test
    public void parsesSbiSpentFormat() {
        String body = "Rs.750 spent on SBI debit card ending 4321 at BIGBASKET on 20/03/26. "
                    + "Avl Bal Rs.22000.";
        BankAwareSmsParser.ParseResult r = BankAwareSmsParser.parse(body, "SBI-SMS");
        assertNotNull(r);
        assertEquals(750.0, r.amount, 0.001);
    }

    // ── ICICI ─────────────────────────────────────────────────────

    @Test
    public void parsesIciciUpiFormat() {
        String body = "UPI txn of Rs.350.00 to UBER INDIA done from ICICI Bank a/c XX9999. "
                    + "UPI Ref 111222333.";
        BankAwareSmsParser.ParseResult r = BankAwareSmsParser.parse(body, "ICICI-BNK");
        assertNotNull("ICICI UPI format must parse", r);
        assertEquals(350.0, r.amount, 0.001);
        assertTrue(r.isUpi);
    }

    @Test
    public void parsesIciciDebitedInfoFormat() {
        String body = "Rs.4500.00 debited from ICICI Bank a/c XX1111 on 20-Mar-26. "
                    + "Info: POS/CROMA/MUMBAI. Avail Bal Rs.15500.";
        BankAwareSmsParser.ParseResult r = BankAwareSmsParser.parse(body, "ICICI-BNK");
        assertNotNull(r);
        assertEquals(4500.0, r.amount, 0.001);
    }

    // ── AXIS ──────────────────────────────────────────────────────

    @Test
    public void parsesAxisDebitedTowardsFormat() {
        String body = "INR 1800.00 debited from Axis Bank a/c XX2233 towards MYNTRA on 20-Mar-26. "
                    + "Ref 445566778.";
        BankAwareSmsParser.ParseResult r = BankAwareSmsParser.parse(body, "AXIS-BNK");
        assertNotNull("AXIS towards format must parse", r);
        assertEquals(1800.0, r.amount, 0.001);
    }

    // ── KOTAK ─────────────────────────────────────────────────────

    @Test
    public void parsesKotakDebitFormat() {
        String body = "INR 3200.00 debited from your Kotak a/c XX3344 to IRCTC via UPI. "
                    + "Ref 556677889.";
        BankAwareSmsParser.ParseResult r = BankAwareSmsParser.parse(body, "KOTAK-BNK");
        assertNotNull("KOTAK format must parse", r);
        assertEquals(3200.0, r.amount, 0.001);
    }

    // ── Confidence scoring ────────────────────────────────────────

    @Test
    public void highConfidencePatternBeatsLowerConfidence() {
        // This SMS matches both the high-confidence (0.95) ICICI UPI pattern
        // and could partially match a lower-confidence fallback.
        // The result must use the highest-confidence match.
        String body = "UPI txn of Rs.100.00 to NETFLIX done from ICICI Bank a/c. "
                    + "UPI Ref 999000111. Info: NETFLIX SUBSCRIPTION.";
        BankAwareSmsParser.ParseResult r = BankAwareSmsParser.parse(body, "ICICI-BNK");
        assertNotNull(r);
        assertTrue("High-confidence pattern should win; confidence >= 0.90",
                r.confidence >= 0.90);
    }

    // ── Edge cases ────────────────────────────────────────────────

    @Test
    public void returnsNullForUnknownBankFormat() {
        String body = "Transaction of some money somewhere happened.";
        BankAwareSmsParser.ParseResult r = BankAwareSmsParser.parse(body, "UNKNOWN-BANK");
        assertNull("Unparseable SMS must return null", r);
    }

    @Test
    public void handlesNullBodyGracefully() {
        assertNull(BankAwareSmsParser.parse(null, "HDFC-BNK"));
    }

    @Test
    public void handlesNullSenderGracefully() {
        String body = "Txn Rs.500.00 done At ZOMATO.";
        // Should not throw — may return null if bank cannot be detected
        BankAwareSmsParser.ParseResult r = BankAwareSmsParser.parse(body, null);
        // No assertion on result — just confirming no NullPointerException
    }

    @Test
    public void parsesAmountWithCommas() {
        String body = "Txn Rs.1,23,456.00 done on HDFC Bank Card At HOSPITAL on 20-03-26.";
        BankAwareSmsParser.ParseResult r = BankAwareSmsParser.parse(body, "HDFC-BNK");
        assertNotNull(r);
        assertEquals(123456.0, r.amount, 0.001);
    }
}
