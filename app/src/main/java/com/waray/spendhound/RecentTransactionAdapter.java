package com.waray.spendhound;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class RecentTransactionAdapter extends RecyclerView.Adapter<RecentTransactionAdapter.ViewHolder> {
    private ArrayList<RecentTransaction> recentTransactionList;
    private OnTransactionClickListener clickListener;

    public interface OnTransactionClickListener {
        void onTransactionClick(RecentTransaction transaction);
    }

    public RecentTransactionAdapter(ArrayList<RecentTransaction> recentTransactionList) {
        this.recentTransactionList = recentTransactionList;
    }

    public RecentTransactionAdapter(ArrayList<RecentTransaction> recentTransactionList, OnTransactionClickListener clickListener) {
        this.recentTransactionList = recentTransactionList;
        this.clickListener = clickListener;
    }

    public void setOnTransactionClickListener(OnTransactionClickListener listener) {
        this.clickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.fragment_transaction, parent, false);
        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RecentTransaction transaction = recentTransactionList.get(position);

        // Bind data to the ViewHolder's views
        holder.dateTextView.setText(transaction.getMostRecentDate());
        holder.typeTextView.setText(transaction.getMostRecentTransactionType());
        holder.detailsTextView.setText(transaction.getMostRecentDetails());
        holder.amountTextView.setText(transaction.getMostRecentPaymentAmountStr());
        holder.iconImageView.setImageResource(transaction.getIconResource());

        // Set click listener
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onTransactionClick(transaction);
            }
        });
    }

    @Override
    public int getItemCount() {
        return recentTransactionList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public TextView dateTextView;
        public TextView typeTextView;
        public TextView detailsTextView;
        public TextView amountTextView;
        public ImageView iconImageView;

        public ViewHolder(View itemView) {
            super(itemView);
            dateTextView = itemView.findViewById(R.id.dateTextView);
            typeTextView = itemView.findViewById(R.id.transactionTypeTextView);
            detailsTextView = itemView.findViewById(R.id.detailsTextView);
            amountTextView = itemView.findViewById(R.id.paymentAmountTextView);
            iconImageView = itemView.findViewById(R.id.iconImageView);
        }
    }
}
