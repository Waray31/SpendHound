package com.waray.spendhound;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;

public class DebtTransactionAdapter extends RecyclerView.Adapter<DebtTransactionAdapter.ViewHolder> {
    private final ArrayList<BorrowTransaction> borrowTransactionList;
    private ArrayList<Integer> checkedPositions;
    private OnItemClickListener clickListener;
    private OnBorrowerActionListener borrowerActionListener;

    public interface OnItemClickListener {
        void onItemClick(BorrowTransaction transaction, int position);
    }

    public interface OnBorrowerActionListener {
        void onPayClicked(BorrowTransaction transaction, int position);
        void onRemoveClicked(BorrowTransaction transaction, int position);
        void onTryAgainClicked(BorrowTransaction transaction, int position);
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

    public DebtTransactionAdapter(ArrayList<BorrowTransaction> borrowTransactionList, OnBorrowerActionListener borrowerActionListener) {
        this.borrowTransactionList = borrowTransactionList;
        this.borrowerActionListener = borrowerActionListener;
        checkedPositions = new ArrayList<>();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnBorrowerActionListener(OnBorrowerActionListener listener) {
        this.borrowerActionListener = listener;
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

        // Hide all action layouts by default
        holder.unpaidActionsLayout.setVisibility(View.GONE);
        holder.declinedActionsLayout.setVisibility(View.GONE);
        holder.paymentSentDateTV.setVisibility(View.GONE);

        // Set status color based on status value
        String status = transaction.getStatus();
        int statusColor;
        boolean isPendingStatus = false;
        boolean isPendingPayment = false;
        boolean isPaid = false;
        boolean isUnpaid = false;
        boolean isDeclined = false;
        if ("Paid".equalsIgnoreCase(status)) {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.green);
            isPaid = true;
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
        } else if ("Declined".equalsIgnoreCase(status)) {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.red);
            isDeclined = true;
        } else if ("Unpaid".equalsIgnoreCase(status)) {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.red);
            isUnpaid = true;
        } else {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.red);
        }
        holder.debtStatusTV.setTextColor(statusColor);

        // Show action buttons based on status
        if (isUnpaid) {
            holder.unpaidActionsLayout.setVisibility(View.VISIBLE);
        } else if (isDeclined) {
            holder.declinedActionsLayout.setVisibility(View.VISIBLE);
        }

        // Show payment sent date for Paid status
        if (isPaid && transaction.getPaymentSentDate() != null && !transaction.getPaymentSentDate().isEmpty()) {
            holder.paymentSentDateTV.setVisibility(View.VISIBLE);
            holder.paymentSentDateTV.setText("Sent: " + transaction.getPaymentSentDate());
        }

        // Cast to MaterialCardView for elevation and background
        MaterialCardView cardView = (MaterialCardView) holder.itemView;
        float density = holder.itemView.getContext().getResources().getDisplayMetrics().density;

        // Set background tint and elevation based on status
        if (isPaid) {
            cardView.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.paid_bg));
            cardView.setCardElevation(4 * density);
        } else if (isUnpaid) {
            cardView.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.unpaid_bg));
            cardView.setCardElevation(4 * density);
        } else if (isPendingPayment) {
            cardView.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.pending_payment_bg));
            cardView.setCardElevation(4 * density);
        } else if (isPendingStatus) {
            cardView.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.pending_approval_bg));
            cardView.setCardElevation(4 * density);
        } else if (isDeclined) {
            cardView.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.unpaid_bg));
            cardView.setCardElevation(4 * density);
        } else {
            cardView.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.whitest));
            cardView.setCardElevation(4 * density);
        }

        // Set click listeners for action buttons
        holder.payBtn.setOnClickListener(v -> {
            if (borrowerActionListener != null) {
                borrowerActionListener.onPayClicked(transaction, holder.getAdapterPosition());
            }
        });

        holder.removeBtn.setOnClickListener(v -> {
            if (borrowerActionListener != null) {
                borrowerActionListener.onRemoveClicked(transaction, holder.getAdapterPosition());
            }
        });

        holder.tryAgainBtn.setOnClickListener(v -> {
            if (borrowerActionListener != null) {
                borrowerActionListener.onTryAgainClicked(transaction, holder.getAdapterPosition());
            }
        });

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
        public TextView paymentSentDateTV;
        public LinearLayout unpaidActionsLayout;
        public LinearLayout declinedActionsLayout;
        public TextView payBtn;
        public TextView removeBtn;
        public TextView tryAgainBtn;

        public ViewHolder(View itemView) {
            super(itemView);
            debtDateTV = itemView.findViewById(R.id.debtDateTV);
            debtBorroweeTV = itemView.findViewById(R.id.debtBorroweeTV);
            debtAmountBorrowedTV = itemView.findViewById(R.id.debtAmountBorrowedTV);
            debtStatusTV = itemView.findViewById(R.id.debtStatusTV);
            paymentSentDateTV = itemView.findViewById(R.id.debtPaymentSentDateTV);
            unpaidActionsLayout = itemView.findViewById(R.id.unpaidActionsLayout);
            declinedActionsLayout = itemView.findViewById(R.id.declinedActionsLayout);
            payBtn = itemView.findViewById(R.id.payBtn);
            removeBtn = itemView.findViewById(R.id.removeBtn);
            tryAgainBtn = itemView.findViewById(R.id.tryAgainBtn);
        }
    }
}
