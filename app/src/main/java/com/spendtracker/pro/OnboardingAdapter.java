package com.spendtracker.pro;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.Holder> {

    private final int[] layouts = {
            R.layout.page_intro_1,
            R.layout.page_intro_2,
            R.layout.page_intro_3
    };

    @Override
    public int getItemViewType(int position) {
        // Each page gets its own view type so onCreateViewHolder inflates the correct layout
        return position;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(layouts[viewType], parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        // Static pages — nothing to bind dynamically
    }

    @Override
    public int getItemCount() {
        return layouts.length;
    }

    static class Holder extends RecyclerView.ViewHolder {
        Holder(View itemView) {
            super(itemView);
        }
    }
}

