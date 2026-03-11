package com.waray.spendhound;

import android.app.AlertDialog;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
            holder.loadingOverlay.setVisibility(View.VISIBLE);

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
                PayorAdapter payorAdapter = new PayorAdapter(payorsUids, payorsNames, amountsPaid, individualPayment, new PayorAdapter.OnPayorClickListener() {
                    @Override
                    public void onPayorClick(int index, double paid) {
                        // Regular click behavior if needed
                    }

                    @Override
                    public void onPartialClick(int index, double currentPaid) {
                        showEditAmountDialog(holder.itemView.getContext(), (newAmount) -> {
                            ((PayorAdapter)holder.payorsRecyclerView.getAdapter()).updatePartialAmount(index, newAmount);
                        }, currentPaid);
                    }
                });
                
                // Set loading listener
                payorAdapter.setOnLoadingCompleteListener(() -> holder.loadingOverlay.setVisibility(View.GONE));
                
                // Set data changed listener to enable/disable save button
                payorAdapter.setOnDataChangedListener(hasChanges -> {
                    holder.saveTransactionBtn.setEnabled(hasChanges);
                    holder.saveTransactionBtn.setAlpha(hasChanges ? 1.0f : 0.5f);
                });

                holder.payorsRecyclerView.setLayoutManager(new LinearLayoutManager(holder.itemView.getContext(), LinearLayoutManager.HORIZONTAL, false));
                holder.payorsRecyclerView.setAdapter(payorAdapter);

                // Setup Edit/Save/Cancel buttons
                if (isCreator) {
                    holder.editTransactionBtn.setVisibility(View.VISIBLE);
                    holder.editTransactionBtn.setOnClickListener(v -> {
                        holder.loadingOverlay.setVisibility(View.VISIBLE);
                        payorAdapter.setEditMode(true);
                        holder.editTransactionBtn.setVisibility(View.GONE);
                        holder.saveTransactionBtn.setVisibility(View.VISIBLE);
                        holder.cancelTransactionBtn.setVisibility(View.VISIBLE);
                        // Initial state: no changes yet
                        holder.saveTransactionBtn.setEnabled(false);
                        holder.saveTransactionBtn.setAlpha(0.5f);
                    });

                    holder.cancelTransactionBtn.setOnClickListener(v -> {
                        holder.loadingOverlay.setVisibility(View.VISIBLE);
                        payorAdapter.setEditMode(false);
                        holder.editTransactionBtn.setVisibility(View.VISIBLE);
                        holder.saveTransactionBtn.setVisibility(View.GONE);
                        holder.cancelTransactionBtn.setVisibility(View.GONE);
                    });

                    holder.saveTransactionBtn.setOnClickListener(v -> {
                        holder.loadingOverlay.setVisibility(View.VISIBLE);
                        List<Double> updatedAmounts = payorAdapter.getAmountsPaid();
                        saveTransactionChanges(holder.itemView.getContext(), transaction, updatedAmounts, holder.getAdapterPosition(), () -> {
                            payorAdapter.saveChanges();
                            holder.editTransactionBtn.setVisibility(View.VISIBLE);
                            holder.saveTransactionBtn.setVisibility(View.GONE);
                            holder.cancelTransactionBtn.setVisibility(View.GONE);
                        }, () -> holder.loadingOverlay.setVisibility(View.GONE));
                    });
                } else {
                    holder.editTransactionBtn.setVisibility(View.GONE);
                }
            } else {
                holder.loadingOverlay.setVisibility(View.GONE);
                holder.editTransactionBtn.setVisibility(View.GONE);
            }

            // Details Section
            String details = transaction.getMostRecentDetails();
            if (details != null && !details.isEmpty() && !details.equals("See Details >")) {
                holder.fullDetailsTextView.setText(details);
            } else {
                holder.fullDetailsTextView.setText("No additional details");
            }
        } else {
            holder.loadingOverlay.setVisibility(View.GONE);
        }

        // Set click listener to toggle expansion
        holder.mainContent.setOnClickListener(v -> {
            transaction.setExpanded(!transaction.isExpanded());
            notifyItemChanged(holder.getAdapterPosition());
            if (clickListener != null) {
                clickListener.onTransactionClick(transaction);
            }
        });
    }

    private void showEditAmountDialog(android.content.Context context, OnAmountEnteredListener listener, double currentAmount) {
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
                listener.onAmountEntered(newAmount);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private interface OnAmountEnteredListener {
        void onAmountEntered(double amount);
    }

    private void saveTransactionChanges(android.content.Context context, RecentTransaction transaction, List<Double> updatedAmounts, int position, Runnable onSuccess, Runnable onComplete) {
        String monthYear = transaction.getMonthYear();
        String day = transaction.getDay();
        String timeKey = transaction.getTimeKey();

        if (monthYear == null || day == null || timeKey == null) {
            Toast.makeText(context, "Error: Could not find transaction reference", Toast.LENGTH_SHORT).show();
            onComplete.run();
            return;
        }

        DatabaseReference ref = DeclareDatabase.getDBRefTransaction()
                .child(monthYear)
                .child(day)
                .child(timeKey)
                .child("amountsPaidList");

        ref.setValue(updatedAmounts).addOnSuccessListener(aVoid -> {
            Toast.makeText(context, "Transaction updated successfully", Toast.LENGTH_SHORT).show();
            transaction.setAmountsPaidList(new ArrayList<>(updatedAmounts));
            onSuccess.run();
            notifyItemChanged(position);
            // The loadingOverlay will be hidden by the payorAdapter's LoadingCompleteListener
            // after payorAdapter.saveChanges() triggers notifyDataSetChanged()
        }).addOnFailureListener(e -> {
            Toast.makeText(context, "Failed to update transaction", Toast.LENGTH_SHORT).show();
            onComplete.run();
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
        
        public View mainContent;
        public View expandableLayout;
        public TextView createdByTextView;
        public RecyclerView payorsRecyclerView;
        public TextView fullDetailsTextView;
        public View loadingOverlay;
        public Button editTransactionBtn, saveTransactionBtn, cancelTransactionBtn;

        public ViewHolder(View itemView) {
            super(itemView);
            dateTextView = itemView.findViewById(R.id.dateTextView);
            typeTextView = itemView.findViewById(R.id.transactionTypeTextView);
            detailsTextView = itemView.findViewById(R.id.detailsTextView);
            amountTextView = itemView.findViewById(R.id.paymentAmountTextView);
            iconImageView = itemView.findViewById(R.id.iconImageView);
            
            mainContent = itemView.findViewById(R.id.main_content);
            expandableLayout = itemView.findViewById(R.id.expandable_layout);
            createdByTextView = itemView.findViewById(R.id.createdByTextView);
            payorsRecyclerView = itemView.findViewById(R.id.payorsRecyclerView);
            fullDetailsTextView = itemView.findViewById(R.id.fullDetailsTextView);
            loadingOverlay = itemView.findViewById(R.id.loadingOverlay_transaction);
            editTransactionBtn = itemView.findViewById(R.id.editTransaction_btn);
            saveTransactionBtn = itemView.findViewById(R.id.saveTransaction_btn);
            cancelTransactionBtn = itemView.findViewById(R.id.cancelTransaction_btn);
        }
    }
}
