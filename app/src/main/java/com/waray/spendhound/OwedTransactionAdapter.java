package com.waray.spendhound;

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

public class OwedTransactionAdapter extends RecyclerView.Adapter<OwedTransactionAdapter.ViewHolder> {
    private final ArrayList<OwedTransaction> owedTransactionList;
    private OnItemClickListener clickListener;
    private OnLenderActionListener lenderActionListener;

    public interface OnItemClickListener {
        void onItemClick(OwedTransaction transaction, int position);
    }

    public interface OnLenderActionListener {
        void onNotYetClicked(OwedTransaction transaction, int position);
        void onReceivedClicked(OwedTransaction transaction, int position);
        void onDeclineClicked(OwedTransaction transaction, int position);
        void onApprovedClicked(OwedTransaction transaction, int position);
    }

    public OwedTransactionAdapter(ArrayList<OwedTransaction> owedTransactionList) {
        this.owedTransactionList = owedTransactionList;
    }

    public OwedTransactionAdapter(ArrayList<OwedTransaction> owedTransactionList, OnItemClickListener clickListener) {
        this.owedTransactionList = owedTransactionList;
        this.clickListener = clickListener;
    }

    public OwedTransactionAdapter(ArrayList<OwedTransaction> owedTransactionList, OnLenderActionListener lenderActionListener) {
        this.owedTransactionList = owedTransactionList;
        this.lenderActionListener = lenderActionListener;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnLenderActionListener(OnLenderActionListener listener) {
        this.lenderActionListener = listener;
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

        // Hide all action layouts by default
        holder.pendingPaymentActionsLayout.setVisibility(View.GONE);
        holder.lenderApprovalActionsLayout.setVisibility(View.GONE);
        holder.paymentSentDateTV.setVisibility(View.GONE);

        // Set status color based on status value
        String status = transaction.getStatus();
        int statusColor;
        boolean isPendingStatus = false;
        boolean isPendingPayment = false;
        boolean isPaid = false;
        boolean isUnpaid = false;
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
        } else if ("Unpaid".equalsIgnoreCase(status)) {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.red);
            isUnpaid = true;
        } else {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.red);
        }
        holder.owedStatusTV.setTextColor(statusColor);

        // Show action buttons based on status
        if (isPendingPayment) {
            holder.pendingPaymentActionsLayout.setVisibility(View.VISIBLE);
        } else if (isPendingStatus) {
            holder.lenderApprovalActionsLayout.setVisibility(View.VISIBLE);
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
        } else {
            cardView.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.whitest));
            cardView.setCardElevation(4 * density);
        }

        // Set click listeners for action buttons
        holder.notYetBtn.setOnClickListener(v -> {
            if (lenderActionListener != null) {
                lenderActionListener.onNotYetClicked(transaction, holder.getAdapterPosition());
            }
        });

        holder.receivedBtn.setOnClickListener(v -> {
            if (lenderActionListener != null) {
                lenderActionListener.onReceivedClicked(transaction, holder.getAdapterPosition());
            }
        });

        holder.declineBtn.setOnClickListener(v -> {
            if (lenderActionListener != null) {
                lenderActionListener.onDeclineClicked(transaction, holder.getAdapterPosition());
            }
        });

        holder.approvedBtn.setOnClickListener(v -> {
            if (lenderActionListener != null) {
                lenderActionListener.onApprovedClicked(transaction, holder.getAdapterPosition());
            }
        });

        // Set click listener for item
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
        public TextView paymentSentDateTV;
        public LinearLayout pendingPaymentActionsLayout;
        public LinearLayout lenderApprovalActionsLayout;
        public TextView notYetBtn;
        public TextView receivedBtn;
        public TextView declineBtn;
        public TextView approvedBtn;

        public ViewHolder(View itemView) {
            super(itemView);
            owedDateTV = itemView.findViewById(R.id.owedDateTV);
            owedBorroweeTV = itemView.findViewById(R.id.owedBorroweeTV);
            owedAmountBorrowedTV = itemView.findViewById(R.id.owedAmountBorrowedTV);
            owedStatusTV = itemView.findViewById(R.id.owedStatusTV);
            paymentSentDateTV = itemView.findViewById(R.id.owedPaymentSentDateTV);
            pendingPaymentActionsLayout = itemView.findViewById(R.id.pendingPaymentActionsLayout);
            lenderApprovalActionsLayout = itemView.findViewById(R.id.lenderApprovalActionsLayout);
            notYetBtn = itemView.findViewById(R.id.notYetBtn);
            receivedBtn = itemView.findViewById(R.id.receivedBtn);
            declineBtn = itemView.findViewById(R.id.declineBtn);
            approvedBtn = itemView.findViewById(R.id.approvedBtn);
        }
    }
}

