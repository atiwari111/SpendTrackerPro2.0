package com.spendtracker.pro;

import android.graphics.Color;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.*;
import java.util.*;

public class BankAccountAdapter extends RecyclerView.Adapter<BankAccountAdapter.VH> {

    public interface OnAccountClick {
        void onClick(BankAccount account);
    }

    private List<BankAccount> list = new ArrayList<>();
    private final OnAccountClick listener;

    public BankAccountAdapter(OnAccountClick listener) {
        this.listener = listener;
    }

    public void setAccounts(List<BankAccount> accounts) {
        this.list = accounts != null ? accounts : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_bank_account, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        BankAccount acc = list.get(pos);

        // Card color
        int color = acc.cardColor != 0 ? acc.cardColor : Color.parseColor("#1A3A8F");
        h.cardRoot.setCardBackgroundColor(color);

        // Bank name & account number
        h.tvBankName.setText(acc.bankName != null ? acc.bankName + " Bank" : "Bank");
        h.tvAccountNo.setText(acc.getMaskedAccount());

        // Account type badge
        if (h.tvAccountType != null && acc.accountType != null) {
            h.tvAccountType.setText(acc.accountType);
        }

        // Balance
        h.tvBalance.setText(String.format("₹%,.2f", acc.balance));

        // Balance color: red if negative
        int balColor = acc.balance < 0
                ? Color.parseColor("#EF4444")
                : ContextCompat.getColor(h.itemView.getContext(), R.color.text_primary);
        h.tvBalance.setTextColor(balColor);

        // Bank logo emoji / initials
        if (h.tvBankLogo != null) {
            h.tvBankLogo.setText(getBankInitials(acc.bankName));
        }

        h.itemView.setOnClickListener(v -> listener.onClick(acc));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    /** Returns 2-letter initials for use in the bank logo circle. */
    private String getBankInitials(String bankName) {
        if (bankName == null || bankName.isEmpty()) return "BK";
        String upper = bankName.toUpperCase().trim();
        if (upper.startsWith("SBI"))   return "SBI";
        if (upper.startsWith("HDFC"))  return "HDF";
        if (upper.startsWith("ICICI")) return "ICI";
        if (upper.startsWith("AXIS"))  return "AXS";
        if (upper.startsWith("PNB"))   return "PNB";
        if (upper.startsWith("KOTAK")) return "KOT";
        if (upper.startsWith("YES"))   return "YES";
        if (upper.startsWith("BOI"))   return "BOI";
        if (upper.length() >= 2) return upper.substring(0, 2);
        return upper;
    }

    static class VH extends RecyclerView.ViewHolder {
        androidx.cardview.widget.CardView cardRoot;
        TextView tvBankName, tvAccountNo, tvBalance, tvAccountType, tvBankLogo;

        VH(View v) {
            super(v);
            cardRoot      = v.findViewById(R.id.cardRoot);
            tvBankName    = v.findViewById(R.id.tvBankName);
            tvAccountNo   = v.findViewById(R.id.tvAccountNo);
            tvBalance     = v.findViewById(R.id.tvBalance);
            tvAccountType = v.findViewById(R.id.tvAccountType);
            tvBankLogo    = v.findViewById(R.id.tvBankLogo);
        }
    }
}
