package com.spendtracker.pro;

import android.graphics.Color;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class BillAdapter extends RecyclerView.Adapter<BillAdapter.VH> {

    public interface OnBillClick { void onClick(Bill bill); }

    private final List<Bill> list;
    private final OnBillClick click;
    private final SimpleDateFormat dateFmt =
            new SimpleDateFormat("dd MMM", Locale.ROOT);

    public BillAdapter(List<Bill> list, OnBillClick click) {
        this.list  = list != null ? list : new ArrayList<>();
        this.click = click;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_bill, p, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int i) {
        Bill b = list.get(i);
        h.tvIcon.setText(b.icon != null ? b.icon : "📋");
        h.tvName.setText(b.name);
        h.tvAmount.setText(String.format(Locale.getDefault(), "₹%.0f", b.amount));
        h.tvStatus.setText(b.getStatusLabel());
        h.tvDueDate.setText(b.dueDate > 0 ? "Due: " + dateFmt.format(new Date(b.dueDate)) : "");

        // Colour status
        if (b.isOverdue()) {
            h.tvStatus.setTextColor(Color.parseColor("#EF4444"));
            h.tvAmount.setTextColor(Color.parseColor("#EF4444"));
        } else if (b.isPaid()) {
            h.tvStatus.setTextColor(Color.parseColor("#10B981"));
            h.tvAmount.setTextColor(Color.parseColor("#10B981"));
        } else {
            int days = b.daysUntilDue();
            int col = days <= 3 ? Color.parseColor("#F59E0B") : Color.parseColor("#A78BFA");
            h.tvStatus.setTextColor(col);
            h.tvAmount.setTextColor(Color.parseColor("#F1F5F9"));
        }

        h.itemView.setOnClickListener(v -> click.onClick(b));
    }

    @Override public int getItemCount() { return list.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvIcon, tvName, tvAmount, tvStatus, tvDueDate;
        VH(View v) {
            super(v);
            tvIcon    = v.findViewById(R.id.tvIcon);
            tvName    = v.findViewById(R.id.tvName);
            tvAmount  = v.findViewById(R.id.tvAmount);
            tvStatus  = v.findViewById(R.id.tvStatus);
            tvDueDate = v.findViewById(R.id.tvDueDate);
        }
    }
}
