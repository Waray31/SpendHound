package com.waray.spendhound;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class DebtTransactionAdapter extends RecyclerView.Adapter<DebtTransactionAdapter.ViewHolder> {
    private final ArrayList<BorrowTransaction> borrowTransactionList;
    private ArrayList<Integer> checkedPositions;
    private OnItemClickListener clickListener;

    public interface OnItemClickListener {
        void onItemClick(BorrowTransaction transaction, int position);
    }

    public DebtTransactionAdapter(ArrayList<BorrowTransaction> borrowTransactionList) {
        this.borrowTransactionList = borrowTransactionList;
        checkedPositions = new ArrayList<>();
    }

    public DebtTransactionAdapter(ArrayList<BorrowTransaction> borrowTransactionList, OnItemClickListener clickListener) {
        this.borrowTransactionList = borrowTransactionList;
        this.clickListener = clickListener;
        checkedPositions = new ArrayList<>();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.clickListener = listener;
    }
    // Add this method to retrieve a BorrowTransaction by its position
    public BorrowTransaction getBorrowTransaction(int position) {
        return borrowTransactionList.get(position);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.debt_row_layout, parent, false);
        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, @SuppressLint("RecyclerView") int position) {
        BorrowTransaction transaction = borrowTransactionList.get(position);

        // Bind data to the ViewHolder's views
        holder.debtDateTV.setText(transaction.getDate());
        holder.debtBorroweeTV.setText(transaction.getBorrowee());
        holder.debtAmountBorrowedTV.setText(transaction.getBorrowedAmountStr());
        holder.debtStatusTV.setText(transaction.getStatus());

        // Set status color based on status value
        String status = transaction.getStatus();
        int statusColor;
        boolean isPendingStatus = false;
        if ("Paid".equalsIgnoreCase(status)) {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.green);
        } else if ("Pending".equalsIgnoreCase(status)) {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.yellow);
        } else if ("Paid Partially".equalsIgnoreCase(status)) {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.yellow);
        } else if ("Payment Pending".equalsIgnoreCase(status)) {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.orange);
            isPendingStatus = true;
        } else if ("For Lender Approval".equalsIgnoreCase(status)) {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.blue);
            isPendingStatus = true;
        } else {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.red);
        }
        holder.debtStatusTV.setTextColor(statusColor);

        // Set background tint for pending items
        if (isPendingStatus) {
            holder.itemView.setBackgroundResource(R.drawable.pending_item_background);
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
        return borrowTransactionList.size();
    }

    public ArrayList<Integer> getCheckedPositions() {
        return checkedPositions;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public TextView debtDateTV;
        public TextView debtBorroweeTV;
        public TextView debtAmountBorrowedTV;
        public TextView debtStatusTV;

        public ViewHolder(View itemView) {
            super(itemView);
            debtDateTV = itemView.findViewById(R.id.debtDateTV);
            debtBorroweeTV = itemView.findViewById(R.id.debtBorroweeTV);
            debtAmountBorrowedTV = itemView.findViewById(R.id.debtAmountBorrowedTV);
            debtStatusTV = itemView.findViewById(R.id.debtStatusTV);
        }
    }
}
