package com.spendtracker.pro;

import android.content.*;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        SmsMessage[] msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (msgs == null) return;
        for (SmsMessage sms : msgs) {
            String body   = sms.getMessageBody();
            String sender = sms.getOriginatingAddress();
            long   ts     = sms.getTimestampMillis();
            AppExecutors.db().execute(() -> process(ctx, body, sender, ts));
        }
    }

    private void process(Context ctx, String body, String sender, long ts) {
        // ── Pipeline: BankDetector → BankAwareSmsParser ──────────
        // BankAwareSmsParser internally calls BankDetector to pick bank-specific
        // regex patterns (HDFC, SBI, ICICI, Axis, Kotak), then falls back to
        // the generic SmsParser if no bank-specific pattern matches.
        BankAwareSmsParser.ParseResult p = BankAwareSmsParser.parse(body, sender);
        if (p == null) return;

        AppDatabase db = AppDatabase.getInstance(ctx);
        if (db.transactionDao().findBySms(body) != null) return; // dedup

        // ── Save transaction ──────────────────────────────────────
        Transaction tx       = new Transaction();
        tx.merchant          = p.merchant;
        tx.amount            = p.amount;
        tx.paymentMethod     = p.paymentMethod;
        tx.paymentDetail     = p.paymentDetail;
        tx.category          = p.category;
        tx.timestamp         = ts;
        tx.rawSms            = body;
        tx.smsAddress        = sender;
        tx.isManual          = false;
        tx.categoryIcon      = CategoryEngine.getInfo(p.category).icon;
        tx.isCredit          = SmsParser.isCreditTransaction(body);
        // Detect self-transfers (own account moves) — exclude from spending totals
        tx.isSelfTransfer    = isSelfTransfer(body);
        db.transactionDao().insert(tx);

        // ── Budget recalc ─────────────────────────────────────────
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(ts);
        int txnMonth = cal.get(Calendar.MONTH) + 1;
        int txnYear  = cal.get(Calendar.YEAR);

        Calendar monthCal = Calendar.getInstance();
        monthCal.set(Calendar.MONTH, txnMonth - 1);
        monthCal.set(Calendar.YEAR, txnYear);
        monthCal.set(Calendar.DAY_OF_MONTH, 1);
        monthCal.set(Calendar.HOUR_OF_DAY, 0);
        monthCal.set(Calendar.MINUTE, 0);
        monthCal.set(Calendar.SECOND, 0);
        monthCal.set(Calendar.MILLISECOND, 0);
        long mStart = monthCal.getTimeInMillis();
        monthCal.add(Calendar.MONTH, 1);
        long mEnd = monthCal.getTimeInMillis();
        db.budgetDao().recalcAllUsed(txnMonth, txnYear, mStart, mEnd);

        // ── Budget alert ──────────────────────────────────────────
        Budget budget = db.budgetDao().getByCategoryMonthYear(p.category, txnMonth, txnYear);
        if (budget != null && budget.getProgress() >= 0.9f) {
            NotificationHelper.sendBudgetAlert(ctx, p.category,
                    budget.usedAmount, budget.limitAmount, budget.id);
        }

        // ── Anomaly detection ─────────────────────────────────────
        // Get last 20 transactions in the same category for the baseline
        List<Transaction> recentSameCategory = db.transactionDao().getAllSync()
                .stream()
                .filter(t -> p.category.equals(t.category) && !t.isSelfTransfer)
                .sorted((a, b) -> Long.compare(b.timestamp, a.timestamp))
                .limit(20)
                .collect(Collectors.toList());

        List<Double> history = new ArrayList<>();
        for (Transaction t : recentSameCategory) history.add(t.amount);

        String anomalyReason = SpendingAnomalyDetector.getAnomalyReason(p.amount, history);
        if (anomalyReason != null) {
            NotificationHelper.sendAnomalyAlert(ctx, p.merchant, p.amount, p.category, anomalyReason);
        } else {
            NotificationHelper.sendSpendAlert(ctx, p.merchant, p.amount, p.category);
        }
    }

    private boolean isSelfTransfer(String body) {
        if (body == null) return false;
        String b = body.toLowerCase();
        return b.contains("self") || b.contains("own account")
                || b.contains("transfer to your") || b.contains("transfer to self")
                || b.contains("linked account") || b.contains("savings account to")
                || b.contains("current account to");
    }
}
