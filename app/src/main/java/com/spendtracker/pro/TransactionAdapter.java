package com.spendtracker.pro;

import android.content.Intent;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.*;

public class TransactionAdapter
        extends RecyclerView.Adapter<TransactionAdapter.ViewHolder> {

    private List<Transaction> transactions = new ArrayList<>();
    private final boolean dashboard;
    private final SimpleDateFormat dateFmt =
            new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());

    public TransactionAdapter(boolean dashboard) {
        this.dashboard = dashboard;
    }

    public List<Transaction> getTransactions() { return transactions; }

    public void setTransactions(List<Transaction> list) {
        if (list == null) list = new ArrayList<>();
        this.transactions = list;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        if (transactions == null || position >= transactions.size()) return;
        Transaction t = transactions.get(position);

        // Icon — use categoryIcon emoji, fall back to category first char
        String icon = (t.categoryIcon != null && !t.categoryIcon.isEmpty())
                ? t.categoryIcon : "💼";
        h.icon.setText(icon);

        h.merchant.setText(t.merchant != null ? t.merchant : "Unknown");
        h.category.setText(t.category != null ? t.category : "");

        // Payment method — show friendly label
        h.payment.setText(friendlyPayment(t.paymentMethod));

        // Date
        h.date.setText(t.timestamp > 0 ? dateFmt.format(new Date(t.timestamp)) : "");

        // Amount — green for credit/income, red for expense, grey for self-transfer
        String amtPrefix = t.isCredit ? "+ ₹" : "₹";
        h.amount.setText(String.format(amtPrefix + "%.0f", t.amount));
        h.amount.setTextColor(t.isSelfTransfer
                ? 0xFF9CA3AF   // grey  — self-transfer
                : t.isCredit
                ? 0xFF10B981   // green — credit/income
                : 0xFFEF4444); // red   — expense

        // Tap / long-press to edit (full list only, not dashboard)
        if (!dashboard) {
            View.OnClickListener editAction = v -> {
                Intent intent = new Intent(v.getContext(), AddExpenseActivity.class);
                intent.putExtra(AddExpenseActivity.EXTRA_TRANSACTION_ID, t.id);
                v.getContext().startActivity(intent);
            };
            h.itemView.setOnClickListener(editAction);
            h.itemView.setOnLongClickListener(v -> { editAction.onClick(v); return true; });
        } else {
            h.itemView.setOnClickListener(null);
            h.itemView.setOnLongClickListener(null);
        }
    }

    @Override
    public int getItemCount() {
        return transactions == null ? 0 : transactions.size();
    }

    private static String friendlyPayment(String method) {
        if (method == null) return "";
        switch (method) {
            case "UPI":         return "UPI";
            case "CREDIT_CARD": return "Credit Card";
            case "DEBIT_CARD":  return "Debit Card";
            case "CASH":        return "Cash";
            case "BANK":        return "Bank Transfer";
            default:            return method;
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView icon, merchant, category, payment, date, amount;

        ViewHolder(View v) {
            super(v);
            icon     = v.findViewById(R.id.tvIcon);
            merchant = v.findViewById(R.id.tvMerchant);
            category = v.findViewById(R.id.tvCategory);
            payment  = v.findViewById(R.id.tvPayment);
            date     = v.findViewById(R.id.tvDate);
            amount   = v.findViewById(R.id.tvAmount);
        }
    }
}

