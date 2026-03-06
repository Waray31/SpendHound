package com.waray.spendhound;

import android.app.AlertDialog;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;

import java.util.ArrayList;
import java.util.List;

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
        holder.amountTextView.setText(transaction.getMostRecentPaymentAmountStr());
        holder.iconImageView.setImageResource(transaction.getIconResource());

        // Handle expansion
        boolean isExpanded = transaction.isExpanded();
        holder.expandableLayout.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        holder.detailsTextView.setText(isExpanded ? "Hide Details <" : "See Details >");

        if (isExpanded) {
            // Created By Section
            holder.createdByTextView.setText(transaction.getCreatedBy() != null ? transaction.getCreatedBy() : "Unknown");

            // Payors Section
            List<String> payorsUids = transaction.getPayorUids();
            List<String> payorsNames = transaction.getPayorsList();
            List<Double> amountsPaid = transaction.getAmountsPaidList();
            double individualPayment = transaction.getTotalIndividualPayment();

            String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            boolean isCreator = transaction.getCreatedByUid() != null && transaction.getCreatedByUid().equals(currentUid);

            if (payorsUids != null) {
                PayorAdapter payorAdapter = new PayorAdapter(payorsUids, payorsNames, amountsPaid, individualPayment, (index, paid) -> {
                    if (isCreator) {
                        showEditAmountDialog(holder.itemView.getContext(), transaction, index, paid, position);
                    }
                });
                holder.payorsRecyclerView.setLayoutManager(new LinearLayoutManager(holder.itemView.getContext(), LinearLayoutManager.HORIZONTAL, false));
                holder.payorsRecyclerView.setAdapter(payorAdapter);
            }

            // Details Section
            String details = transaction.getMostRecentDetails();
            if (details != null && !details.isEmpty() && !details.equals("See Details >")) {
                holder.fullDetailsTextView.setText(details);
            } else {
                holder.fullDetailsTextView.setText("No additional details");
            }
        }

        // Set click listener to toggle expansion
        holder.itemView.setOnClickListener(v -> {
            transaction.setExpanded(!transaction.isExpanded());
            notifyItemChanged(position);
            if (clickListener != null) {
                clickListener.onTransactionClick(transaction);
            }
        });
    }

    private void showEditAmountDialog(android.content.Context context, RecentTransaction transaction, int index, double currentAmount, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Edit Amount Paid");

        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.valueOf(currentAmount));
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newAmountStr = input.getText().toString();
            if (!newAmountStr.isEmpty()) {
                double newAmount = Double.parseDouble(newAmountStr);
                updatePayerAmountInDatabase(context, transaction, index, newAmount, position);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void updatePayerAmountInDatabase(android.content.Context context, RecentTransaction transaction, int index, double newAmount, int position) {
        String monthYear = transaction.getMonthYear();
        String day = transaction.getDay();
        String timeKey = transaction.getTimeKey();

        if (monthYear == null || day == null || timeKey == null) {
            Toast.makeText(context, "Error: Could not find transaction reference", Toast.LENGTH_SHORT).show();
            return;
        }

        DatabaseReference ref = DeclareDatabase.getDBRefTransaction()
                .child(monthYear)
                .child(day)
                .child(timeKey)
                .child("amountsPaidList")
                .child(String.valueOf(index));

        ref.setValue(newAmount).addOnSuccessListener(aVoid -> {
            Toast.makeText(context, "Amount updated successfully", Toast.LENGTH_SHORT).show();
            // Update local data and refresh
            if (transaction.getAmountsPaidList() != null) {
                if (index < transaction.getAmountsPaidList().size()) {
                    transaction.getAmountsPaidList().set(index, newAmount);
                } else {
                    while (transaction.getAmountsPaidList().size() < index) {
                        transaction.getAmountsPaidList().add(0.0);
                    }
                    transaction.getAmountsPaidList().add(newAmount);
                }
                notifyItemChanged(position);
            }
        }).addOnFailureListener(e -> Toast.makeText(context, "Failed to update amount", Toast.LENGTH_SHORT).show());
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
        
        public View expandableLayout;
        public TextView createdByTextView;
        public RecyclerView payorsRecyclerView;
        public TextView fullDetailsTextView;

        public ViewHolder(View itemView) {
            super(itemView);
            dateTextView = itemView.findViewById(R.id.dateTextView);
            typeTextView = itemView.findViewById(R.id.transactionTypeTextView);
            detailsTextView = itemView.findViewById(R.id.detailsTextView);
            amountTextView = itemView.findViewById(R.id.paymentAmountTextView);
            iconImageView = itemView.findViewById(R.id.iconImageView);
            
            expandableLayout = itemView.findViewById(R.id.expandable_layout);
            createdByTextView = itemView.findViewById(R.id.createdByTextView);
            payorsRecyclerView = itemView.findViewById(R.id.payorsRecyclerView);
            fullDetailsTextView = itemView.findViewById(R.id.fullDetailsTextView);
        }
    }
}
