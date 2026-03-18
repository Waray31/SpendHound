package com.waray.spendhound;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;


public class CheckedTransactionsAdapter extends RecyclerView.Adapter<CheckedTransactionsAdapter.ViewHolder> {
    private final ArrayList<BorrowTransaction> checkedTransactions;

    public CheckedTransactionsAdapter(ArrayList<BorrowTransaction> checkedTransactions) {
        this.checkedTransactions = checkedTransactions;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_checked_transaction, parent, false);
        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BorrowTransaction transaction = checkedTransactions.get(position);
        holder.dateTextView.setText(transaction.getDate());
        holder.borroweeTextView.setText(transaction.getBorrowee());
        holder.amountTextView.setText(transaction.getBorrowedAmountStr());
        holder.statusTextView.setText(transaction.getStatus());
    }

    @Override
    public int getItemCount() {
        return checkedTransactions.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView dateTextView;
        public TextView borroweeTextView;
        public TextView amountTextView;
        public TextView statusTextView;

        public ViewHolder(View itemView) {
            super(itemView);
            dateTextView = itemView.findViewById(R.id.dateTextView);
            borroweeTextView = itemView.findViewById(R.id.borroweeTextView);
            amountTextView = itemView.findViewById(R.id.amountTextView);
            statusTextView = itemView.findViewById(R.id.statusTextView);
        }
    }
}

