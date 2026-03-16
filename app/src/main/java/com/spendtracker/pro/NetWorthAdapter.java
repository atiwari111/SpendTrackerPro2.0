package com.spendtracker.pro;

import android.graphics.Color;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.*;
import java.util.*;

public class NetWorthAdapter extends RecyclerView.Adapter<NetWorthAdapter.VH> {
    public interface OnClick { void onClick(NetWorthItem i); }
    private List<NetWorthItem> list = new ArrayList<>();
    private final OnClick click;
    public NetWorthAdapter(OnClick c) { this.click = c; }
    public void setItems(List<NetWorthItem> l) { this.list = l != null ? l : new ArrayList<>(); notifyDataSetChanged(); }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_networth, p, false));
    }

    @Override public void onBindViewHolder(@NonNull VH h, int i) {
        NetWorthItem item = list.get(i);
        h.tvIcon.setText(item.icon != null ? item.icon : (item.type.equals("ASSET") ? "💰" : "💳"));
        h.tvName.setText(item.name);
        h.tvType.setText(item.type);
        h.tvAmount.setText(String.format("₹%.0f", item.amount));
        h.tvAmount.setTextColor(item.type.equals("ASSET") ? Color.parseColor("#10B981") : Color.parseColor("#EF4444"));
        h.tvType.setTextColor(item.type.equals("ASSET") ? Color.parseColor("#10B981") : Color.parseColor("#EF4444"));
        h.itemView.setOnClickListener(v -> click.onClick(item));
    }

    @Override public int getItemCount() { return list.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvIcon, tvName, tvType, tvAmount;
        VH(View v) { super(v); tvIcon = v.findViewById(R.id.tvIcon); tvName = v.findViewById(R.id.tvName); tvType = v.findViewById(R.id.tvType); tvAmount = v.findViewById(R.id.tvAmount); }
    }
}
