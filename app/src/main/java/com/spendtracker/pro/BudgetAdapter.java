package com.spendtracker.pro;

import android.graphics.Color;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.*;
import java.util.*;

public class BudgetAdapter extends RecyclerView.Adapter<BudgetAdapter.VH> {
    private List<Budget> list = new ArrayList<>();
    private final OnBudgetClick listener;
    public interface OnBudgetClick { void onClick(Budget b); }

    public BudgetAdapter(OnBudgetClick l) { this.listener = l; }

    public void setBudgets(List<Budget> l) {
        this.list = l != null ? l : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_budget, p, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int i) {
        Budget b = list.get(i);
        h.tvIcon.setText(b.icon != null ? b.icon : "💰");
        h.tvCategory.setText(b.category);
        h.tvStatus.setText(b.getStatusEmoji());

        double remaining = b.getRemaining();
        double pct = b.limitAmount > 0 ? Math.min((b.usedAmount / b.limitAmount) * 100, 100) : 0;

        h.tvSpent.setText(String.format("Spent: ₹%.0f", b.usedAmount));
        h.tvLimit.setText(String.format("Budget: ₹%.0f", b.limitAmount));

        if (remaining >= 0) {
            h.tvRemaining.setText(String.format("₹%.0f left", remaining));
            h.tvRemaining.setTextColor(Color.parseColor("#10B981")); // green
        } else {
            h.tvRemaining.setText(String.format("₹%.0f over!", Math.abs(remaining)));
            h.tvRemaining.setTextColor(Color.parseColor("#EF4444")); // red
        }

        h.progressBar.setProgress((int) pct);
        int barColor;
        if (pct >= 100) barColor = Color.parseColor("#EF4444");
        else if (pct >= 80) barColor = Color.parseColor("#F59E0B");
        else if (pct >= 50) barColor = Color.parseColor("#FBBF24");
        else barColor = Color.parseColor("#10B981");
        h.progressBar.getProgressDrawable().setColorFilter(barColor, android.graphics.PorterDuff.Mode.SRC_IN);

        h.tvPercent.setText(String.format("%.0f%%", pct));
        h.tvPercent.setTextColor(barColor);

        h.itemView.setOnClickListener(v -> listener.onClick(b));
    }

    @Override public int getItemCount() { return list.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvIcon, tvCategory, tvStatus, tvSpent, tvLimit, tvRemaining, tvPercent;
        ProgressBar progressBar;
        VH(View v) {
            super(v);
            tvIcon      = v.findViewById(R.id.tvIcon);
            tvCategory  = v.findViewById(R.id.tvCategory);
            tvStatus    = v.findViewById(R.id.tvStatus);
            tvSpent     = v.findViewById(R.id.tvSpent);
            tvLimit     = v.findViewById(R.id.tvLimit);
            tvRemaining = v.findViewById(R.id.tvRemaining);
            tvPercent   = v.findViewById(R.id.tvPercent);
            progressBar = v.findViewById(R.id.progressBar);
        }
    }
}
