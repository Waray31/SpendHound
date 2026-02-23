package com.waray.spendhound;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class BorrowTransactionAdapter extends RecyclerView.Adapter<BorrowTransactionAdapter.ViewHolder> {
    private final ArrayList<BorrowTransaction> borrowTransactionList;
    private ArrayList<Integer> checkedPositions;

    public BorrowTransactionAdapter(ArrayList<BorrowTransaction> borrowTransactionList) {
        this.borrowTransactionList = borrowTransactionList;
        checkedPositions = new ArrayList<>();
    }
    // Add this method to retrieve a BorrowTransaction by its position
    public BorrowTransaction getBorrowTransaction(int position) {
        return borrowTransactionList.get(position);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.debt_rowcheckbox_layout, parent, false);
        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, @SuppressLint("RecyclerView") int position) {
        BorrowTransaction transaction = borrowTransactionList.get(position);

        // Bind data to the ViewHolder's views
        holder.cbDebtDateTV.setText(transaction.getDate());
        holder.cbDebtBorroweeTV.setText(transaction.getBorrowee());
        holder.cbDebtAmountBorrowedTV.setText(transaction.getBorrowedAmountStr());
        holder.cbDebtStatusTV.setText(transaction.getStatus());

        // Set status color based on status value
        String status = transaction.getStatus();
        int statusColor;
        if ("Paid".equalsIgnoreCase(status)) {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.green);
        } else if ("Pending".equalsIgnoreCase(status)) {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.yellow);
        } else if ("Paid Partially".equalsIgnoreCase(status)) {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.yellow);
        } else {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.red);
        }
        holder.cbDebtStatusTV.setTextColor(statusColor);

        // Set click listener for payCheckBox
        holder.payCheckBox.setOnCheckedChangeListener(null); // To prevent triggering listener for recycled views
        holder.payCheckBox.setChecked(checkedPositions.contains(position));
        holder.payCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // Add position to checkedPositions
                    if (!checkedPositions.contains(position)) {
                        checkedPositions.add(position);
                    }
                } else {
                    // Remove position from checkedPositions
                    checkedPositions.remove((Integer) position);
                }
            }
        });
    }

    public void selectAll() {
        checkedPositions.clear();
        for (int i = 0; i < borrowTransactionList.size(); i++) {
            checkedPositions.add(i);
        }
        notifyDataSetChanged();
    }

    public void deselectAll() {
        checkedPositions.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return borrowTransactionList.size();
    }

    public ArrayList<Integer> getCheckedPositions() {
        return checkedPositions;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public TextView cbDebtDateTV;
        public TextView cbDebtBorroweeTV;
        public TextView cbDebtAmountBorrowedTV;
        public TextView cbDebtStatusTV;
        public CheckBox payCheckBox;

        public ViewHolder(View itemView) {
            super(itemView);
            cbDebtDateTV = itemView.findViewById(R.id.cbDebtDateTV);
            cbDebtBorroweeTV = itemView.findViewById(R.id.cbDebtBorroweeTV);
            cbDebtAmountBorrowedTV = itemView.findViewById(R.id.cbDebtAmountBorrowedTV);
            cbDebtStatusTV = itemView.findViewById(R.id.cbDebtStatusTV);
            payCheckBox = itemView.findViewById(R.id.payCheckBox);
        }
    }
}
