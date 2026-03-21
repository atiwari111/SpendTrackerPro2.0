package com.spendtracker.pro;

import android.view.*;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class RecurringAdapter extends RecyclerView.Adapter<RecurringAdapter.VH> {
    public interface OnClick { void onClick(RecurringTransaction r); }
    private List<RecurringTransaction> list = new ArrayList<>();
    private final OnClick click;
    private final SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH);
    public RecurringAdapter(OnClick c) { this.click = c; }
    public void setItems(List<RecurringTransaction> l) { this.list = l != null ? l : new ArrayList<>(); notifyDataSetChanged(); }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_recurring, p, false));
    }

    @Override public void onBindViewHolder(@NonNull VH h, int i) {
        RecurringTransaction r = list.get(i);
        h.tvIcon.setText(r.icon != null ? r.icon : "🔄");
        h.tvName.setText(r.name);
        h.tvAmount.setText(String.format(Locale.getDefault(), "₹%.0f", r.amount));
        h.tvFreq.setText(r.frequency);
        h.tvDue.setText("Due: " + sdf.format(new Date(r.nextDueDate)));
        long daysLeft = (r.nextDueDate - System.currentTimeMillis()) / 86400000L;
        h.tvDaysLeft.setText(daysLeft <= 0 ? "⚠ Due now!" : daysLeft == 1 ? "Tomorrow" : "in " + daysLeft + " days");
        h.tvDaysLeft.setTextColor(daysLeft <= 3 ? 0xFFEF4444 : 0xFF7C3AED);
        h.itemView.setOnClickListener(v -> click.onClick(r));
    }

    @Override public int getItemCount() { return list.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvIcon, tvName, tvAmount, tvFreq, tvDue, tvDaysLeft;
        VH(View v) { super(v); tvIcon = v.findViewById(R.id.tvIcon); tvName = v.findViewById(R.id.tvName); tvAmount = v.findViewById(R.id.tvAmount); tvFreq = v.findViewById(R.id.tvFreq); tvDue = v.findViewById(R.id.tvDue); tvDaysLeft = v.findViewById(R.id.tvDaysLeft); }
    }
}
