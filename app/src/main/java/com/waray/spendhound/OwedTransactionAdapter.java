package com.waray.spendhound;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;

public class OwedTransactionAdapter extends RecyclerView.Adapter<OwedTransactionAdapter.ViewHolder> {
    private final ArrayList<OwedTransaction> owedTransactionList;
    private OnItemClickListener clickListener;

    public interface OnItemClickListener {
        void onItemClick(OwedTransaction transaction, int position);
    }

    public OwedTransactionAdapter(ArrayList<OwedTransaction> owedTransactionList) {
        this.owedTransactionList = owedTransactionList;
    }

    public OwedTransactionAdapter(ArrayList<OwedTransaction> owedTransactionList, OnItemClickListener clickListener) {
        this.owedTransactionList = owedTransactionList;
        this.clickListener = clickListener;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.clickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.owed_row_layout, parent, false);
        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        OwedTransaction transaction = owedTransactionList.get(position);

        // Bind data to the ViewHolder's views
        holder.owedDateTV.setText(transaction.getDate());
        holder.owedBorroweeTV.setText(transaction.getBorrower());
        holder.owedAmountBorrowedTV.setText(transaction.getBorrowedAmountStr());
        holder.owedStatusTV.setText(transaction.getStatus());

        // Set status color based on status value
        String status = transaction.getStatus();
        int statusColor;
        boolean isPendingStatus = false;
        boolean isPendingPayment = false;
        if ("Paid".equalsIgnoreCase(status)) {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.green);
        } else if ("Pending".equalsIgnoreCase(status)) {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.yellow);
        } else if ("Paid Partially".equalsIgnoreCase(status)) {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.yellow);
        } else if ("Pending Payment".equalsIgnoreCase(status)) {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.yellow);
            isPendingPayment = true;
        } else if ("For Lender Approval".equalsIgnoreCase(status)) {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.blue);
            isPendingStatus = true;
        } else {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.red);
        }
        holder.owedStatusTV.setTextColor(statusColor);

        // Cast to MaterialCardView for elevation and background
        MaterialCardView cardView = (MaterialCardView) holder.itemView;
        float density = holder.itemView.getContext().getResources().getDisplayMetrics().density;

        // Set background tint and elevation for pending items
        if (isPendingPayment) {
            cardView.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.pending_payment_bg));
            cardView.setCardElevation(4 * density);
        } else if (isPendingStatus) {
            cardView.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.pending_approval_bg));
            cardView.setCardElevation(4 * density);
        } else {
            cardView.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.whitest));
            cardView.setCardElevation(4 * density);
        }

        // Set click listener for pending items to navigate to PendingStatusActivity
        final boolean finalIsPendingStatus = isPendingStatus;
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null && finalIsPendingStatus) {
                clickListener.onItemClick(transaction, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return owedTransactionList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public TextView owedDateTV;
        public TextView owedBorroweeTV;
        public TextView owedAmountBorrowedTV;
        public TextView owedStatusTV;

        public ViewHolder(View itemView) {
            super(itemView);
            owedDateTV = itemView.findViewById(R.id.owedDateTV);
            owedBorroweeTV = itemView.findViewById(R.id.owedBorroweeTV);
            owedAmountBorrowedTV = itemView.findViewById(R.id.owedAmountBorrowedTV);
            owedStatusTV = itemView.findViewById(R.id.owedStatusTV);
        }
    }
}

