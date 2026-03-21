package com.spendtracker.pro;

import android.graphics.Color;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.*;
import java.util.*;

public class CreditCardAdapter extends RecyclerView.Adapter<CreditCardAdapter.VH> {

    public interface OnCardClick {
        void onClick(CreditCard card);
    }

    private List<CreditCard> list = new ArrayList<>();
    private final OnCardClick listener;

    public CreditCardAdapter(OnCardClick listener) {
        this.listener = listener;
    }

    public void setCards(List<CreditCard> cards) {
        this.list = cards != null ? cards : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_credit_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        CreditCard card = list.get(pos);

        // Card background color
        int color = card.cardColor != 0 ? card.cardColor : Color.parseColor("#1A3A8F");
        h.cardRoot.setCardBackgroundColor(color);

        // Header
        h.tvBankName.setText(card.cardLabel != null ? card.cardLabel : card.bankName);
        h.tvNetwork.setText(card.network != null ? card.network : "CREDIT CARD");

        // Spent amount
        h.tvSpent.setText(String.format(Locale.getDefault(), "₹%.0f", card.currentSpent));

        // Masked card number
        h.tvCardNumber.setText(card.getMaskedNumber());

        // Billing cycle
        if (card.billingDay > 0) {
            h.tvBillingCycle.setText("Billing: " + card.billingDay + "th");
        } else {
            h.tvBillingCycle.setText("Set billing cycle");
        }

        // Statement / utilisation
        if (card.statementAmount > 0) {
            h.tvStatement.setText(String.format("Stmt: ₹%.0f", card.statementAmount));
        } else if (card.creditLimit > 0) {
            int pct = (int)(card.getUtilisation() * 100);
            h.tvStatement.setText(pct + "% used");
        } else {
            h.tvStatement.setText("Not available");
        }

        // Utilisation progress bar
        if (h.progressUtil != null) {
            h.progressUtil.setProgress((int)(card.getUtilisation() * 100));
            // Tint: green < 50%, amber < 80%, red >= 80%
            float util = card.getUtilisation();
            int tint = util < 0.5f ? Color.parseColor("#10B981")
                     : util < 0.8f ? Color.parseColor("#F59E0B")
                     : Color.parseColor("#EF4444");
            h.progressUtil.getProgressDrawable().setTint(tint);
        }

        h.itemView.setOnClickListener(v -> listener.onClick(card));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        androidx.cardview.widget.CardView cardRoot;
        TextView tvBankName, tvNetwork, tvSpent, tvCardNumber, tvBillingCycle, tvStatement;
        ProgressBar progressUtil;

        VH(View v) {
            super(v);
            cardRoot      = v.findViewById(R.id.cardRoot);
            tvBankName    = v.findViewById(R.id.tvBankName);
            tvNetwork     = v.findViewById(R.id.tvNetwork);
            tvSpent       = v.findViewById(R.id.tvSpent);
            tvCardNumber  = v.findViewById(R.id.tvCardNumber);
            tvBillingCycle= v.findViewById(R.id.tvBillingCycle);
            tvStatement   = v.findViewById(R.id.tvStatement);
            progressUtil  = v.findViewById(R.id.progressUtil);
        }
    }
}
