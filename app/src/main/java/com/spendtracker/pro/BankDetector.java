package com.spendtracker.pro;

/**
 * BankDetector v1.6
 *
 * Banks use registered sender IDs (short codes) in India — these are far more
 * reliable than scanning the SMS body for bank names.
 *
 * Sender ID format: VM-HDFCBK, AM-SBIINB, etc.
 * We strip the prefix and match the core code.
 *
 * Also includes a body-scan fallback for cases where sender is unavailable
 * (e.g. long number senders on older devices).
 */
public class BankDetector {

    public static class BankInfo {
        public final String name;   // e.g. "HDFC"
        public final String fullName; // e.g. "HDFC Bank"

        BankInfo(String name, String fullName) {
            this.name = name;
            this.fullName = fullName;
        }
    }

    // ── Sender ID → Bank mapping ──────────────────────────────────
    // Each entry: { senderCode, bankShortName, bankFullName }
    private static final String[][] SENDER_MAP = {
        // HDFC Bank
        { "HDFCBK",  "HDFC",    "HDFC Bank" },
        { "HDFCBN",  "HDFC",    "HDFC Bank" },
        { "HDFCCC",  "HDFC",    "HDFC Bank" }, // credit card
        // SBI
        { "SBIINB",  "SBI",     "State Bank of India" },
        { "SBIPSG",  "SBI",     "State Bank of India" },
        { "SBICRD",  "SBI",     "State Bank of India" },
        { "SBIBNK",  "SBI",     "State Bank of India" },
        // ICICI Bank
        { "ICICIB",  "ICICI",   "ICICI Bank" },
        { "ICICIC",  "ICICI",   "ICICI Bank" }, // credit card
        { "ICICIT",  "ICICI",   "ICICI Bank" },
        // Axis Bank
        { "AXISBK",  "AXIS",    "Axis Bank" },
        { "AXISBN",  "AXIS",    "Axis Bank" },
        { "AXISCC",  "AXIS",    "Axis Bank" },
        // Kotak
        { "KOTAKB",  "KOTAK",   "Kotak Mahindra Bank" },
        { "KOTAKM",  "KOTAK",   "Kotak Mahindra Bank" },
        // Yes Bank
        { "YESBKA",  "YES",     "Yes Bank" },
        { "YESBNK",  "YES",     "Yes Bank" },
        // PNB
        { "PNBSMS",  "PNB",     "Punjab National Bank" },
        { "PNBINB",  "PNB",     "Punjab National Bank" },
        // Bank of Baroda
        { "BOBIMT",  "BOB",     "Bank of Baroda" },
        { "BOBSMS",  "BOB",     "Bank of Baroda" },
        // Canara Bank
        { "CNRBNK",  "CANARA",  "Canara Bank" },
        // Union Bank
        { "UNIONB",  "UNION",   "Union Bank of India" },
        // IndusInd
        { "INDBNK",  "INDUSIND","IndusInd Bank" },
        { "INDUSL",  "INDUSIND","IndusInd Bank" },
        // Federal Bank
        { "FEDBKA",  "FEDERAL", "Federal Bank" },
        { "FEDBNK",  "FEDERAL", "Federal Bank" },
        // RBL
        { "RBLBNK",  "RBL",     "RBL Bank" },
        // IDFC First
        { "IDFCFB",  "IDFC",    "IDFC First Bank" },
        // AU Small Finance
        { "AUSFBL",  "AU",      "AU Small Finance Bank" },
        // Bandhan
        { "BANDHN",  "BANDHAN", "Bandhan Bank" },
        // Paytm Payments Bank
        { "PYTMBN",  "PAYTM",   "Paytm Payments Bank" },
        // Airtel Payments Bank
        { "AIRBPB",  "AIRTEL",  "Airtel Payments Bank" },
        // Fi Money (Federal Bank backend)
        { "FIMONB",  "FI",      "Fi Money" },
        // Jupiter (Federal Bank backend)
        { "JUPBNK",  "JUPITER", "Jupiter" },
    };

    // ── Body keyword fallback ─────────────────────────────────────
    private static final String[][] BODY_MAP = {
        { "hdfc",          "HDFC",     "HDFC Bank" },
        { "sbi",           "SBI",      "State Bank of India" },
        { "icici",         "ICICI",    "ICICI Bank" },
        { "axis bank",     "AXIS",     "Axis Bank" },
        { "kotak",         "KOTAK",    "Kotak Mahindra Bank" },
        { "yes bank",      "YES",      "Yes Bank" },
        { "pnb",           "PNB",      "Punjab National Bank" },
        { "bank of baroda","BOB",      "Bank of Baroda" },
        { "canara",        "CANARA",   "Canara Bank" },
        { "union bank",    "UNION",    "Union Bank of India" },
        { "indusind",      "INDUSIND", "IndusInd Bank" },
        { "federal bank",  "FEDERAL",  "Federal Bank" },
        { "rbl",           "RBL",      "RBL Bank" },
        { "idfc",          "IDFC",     "IDFC First Bank" },
        { "au bank",       "AU",       "AU Small Finance Bank" },
        { "bandhan",       "BANDHAN",  "Bandhan Bank" },
        { "paytm bank",    "PAYTM",    "Paytm Payments Bank" },
        { "airtel payment","AIRTEL",   "Airtel Payments Bank" },
    };

    /**
     * Primary method — detect bank from sender ID (most reliable).
     * Falls back to body scan if sender unavailable.
     *
     * @param sender  SMS originating address (e.g. "VM-HDFCBK", "AM-SBIINB")
     * @param body    SMS body text (used only as fallback)
     * @return BankInfo with name="" if bank could not be detected
     */
    public static BankInfo detect(String sender, String body) {
        // 1. Sender ID detection (strip prefix: VM-, AM-, JK- etc.)
        if (sender != null && !sender.isEmpty()) {
            String s = sender.toUpperCase().replaceAll("^[A-Z]{2}-", "").trim();
            for (String[] row : SENDER_MAP) {
                if (s.contains(row[0])) {
                    return new BankInfo(row[1], row[2]);
                }
            }
        }

        // 2. Body keyword fallback
        if (body != null && !body.isEmpty()) {
            String b = body.toLowerCase();
            for (String[] row : BODY_MAP) {
                if (b.contains(row[0])) {
                    return new BankInfo(row[1], row[2]);
                }
            }
        }

        return new BankInfo("", "Unknown Bank");
    }

    /** Convenience — just the short bank name, or empty string */
    public static String detectName(String sender, String body) {
        return detect(sender, body).name;
    }
}
