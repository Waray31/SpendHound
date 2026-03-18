package com.waray.spendhound;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.List;

public class PayerListTransactionAdapter extends RecyclerView.Adapter<PayerListTransactionAdapter.ViewHolder> {
    private List<BorrowerListTransaction> transactionList;
    private final List<String[]> pathList;
    private Context context;
    private OnTransactionStatusUpdatedListener statusUpdatedListener;

    public interface OnTransactionStatusUpdatedListener {
        void onTransactionStatusUpdated();
    }

    public PayerListTransactionAdapter(Context context, List<BorrowerListTransaction> transactionList, List<String[]> pathList, OnTransactionStatusUpdatedListener statusUpdatedListener, Button confirmAllPayerBtn, Button denyAllPayerBtn) {
        this.context = context;
        this.transactionList = transactionList;
        this.pathList = pathList;
        this.statusUpdatedListener = statusUpdatedListener;

        confirmAllPayerBtn.setOnClickListener(v -> handleAllTransactions("Confirm"));
        denyAllPayerBtn.setOnClickListener(v -> handleAllTransactions("Deny"));
    }

    public BorrowerListTransaction getBorrowerListTransaction(int position) {
        return transactionList.get(position);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(context).inflate(R.layout.payer_row_layout, parent, false);
        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BorrowerListTransaction transaction = transactionList.get(position);
        holder.hoursAgoTV.setText(transaction.getDate());
        holder.payerNameTV.setText(transaction.getBorrowee());
        holder.amountPaidTV.setText(CurrencyUtils.formatAmountWithCurrency(transaction.getBorrowedAmountStr()));

        // Set a placeholder image and a tag for the ImageView
        holder.payerImg.setImageResource(R.drawable.placeholder_profile_image);
        holder.payerImg.setTag(transaction.getBorrowee());

        // Load the profile image asynchronously
        setProfileImage(holder.payerImg, transaction.getBorrowee());

        holder.confirmPayerBtn.setOnClickListener(v -> showConfirmationDialog("Confirm", transaction, pathList.get(position), position));
        holder.denyPayerBtn.setOnClickListener(v -> showConfirmationDialog("Deny", transaction, pathList.get(position), position));
    }

    @Override
    public int getItemCount() {
        return transactionList.size();
    }

    private void showConfirmationDialog(String action, BorrowerListTransaction transaction, String[] path, int position) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_borrowerlistconfirmation, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(dialogView);

        TextView confirmAction = dialogView.findViewById(R.id.confirmAction);
        Button payNowConfirmBtn = dialogView.findViewById(R.id.payNowConfirmBtn);
        Button closeButton = dialogView.findViewById(R.id.closeButton);

        confirmAction.setText(action);

        AlertDialog dialog = builder.create();

        payNowConfirmBtn.setOnClickListener(v -> {
            if ("Confirm".equalsIgnoreCase(action)) {
                updateTransactionStatus(transaction, path, position, "Paid");
            } else if ("Deny".equalsIgnoreCase(action)) {
                updateTransactionStatus(transaction, path, position, "Payment Denied");
            }
            dialog.dismiss();
        });

        closeButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void updateTransactionStatus(BorrowerListTransaction transaction, String[] path, int position, String status) {
        DatabaseReference borrowRef = FirebaseDatabase.getInstance().getReference("borrows")
                .child(path[0]).child(path[1]).child(path[2]).child(path[3]);

        // If status is being changed to "Paid", decrement balance fields
        if ("Paid".equals(status)) {
            borrowRef.addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                @Override
                public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot dataSnapshot) {
                    BorrowNowTransaction borrow = dataSnapshot.getValue(BorrowNowTransaction.class);
                    if (borrow != null && !("Paid".equals(borrow.getStatus()))) {
                        // Only decrement if it wasn't already paid
                        try {
                            int amount = Integer.parseInt(borrow.getBorrowedAmountStr());
                            String borrowerID = borrow.getBorrowerID();
                            String lenderID = borrow.getLenderID();

                            // Decrement balances (negative amount to subtract)
                            if (borrowerID != null) {
                                BalanceHelper.updateTotaldebt(borrowerID, -amount, null);
                            }
                            if (lenderID != null) {
                                BalanceHelper.updateTotalreceivable(lenderID, -amount, null);
                            }
                        } catch (NumberFormatException e) {
                            Log.e("PayerListTransactionAdapter", "Error parsing borrow amount: " + e.getMessage());
                        }
                    }

                    // Update status
                    transaction.setStatus(status);
                    borrowRef.child("status").setValue(status)
                            .addOnSuccessListener(aVoid -> {
                                transactionList.set(position, transaction);
                                notifyDataSetChanged();
                                if (statusUpdatedListener != null) {
                                    statusUpdatedListener.onTransactionStatusUpdated();
                                }
                            })
                            .addOnFailureListener(e -> Toast.makeText(context, "Failed to update status: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                    Log.e("PayerListTransactionAdapter", "Failed to read borrow data: " + error.getMessage());
                    Toast.makeText(context, "Failed to update status", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            // If not changing to "Paid", just update the status
            transaction.setStatus(status);
            borrowRef.child("status").setValue(status)
                    .addOnSuccessListener(aVoid -> {
                        transactionList.set(position, transaction);
                        notifyDataSetChanged();
                        if (statusUpdatedListener != null) {
                            statusUpdatedListener.onTransactionStatusUpdated();
                        }
                    })
                    .addOnFailureListener(e -> Toast.makeText(context, "Failed to update status: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView payerImg;
        public TextView hoursAgoTV;
        public TextView payerNameTV;
        public TextView amountPaidTV;
        public Button confirmPayerBtn, denyPayerBtn;

        public ViewHolder(View itemView) {
            super(itemView);
            payerImg = itemView.findViewById(R.id.payerImg);
            hoursAgoTV = itemView.findViewById(R.id.hoursAgoTV);
            payerNameTV = itemView.findViewById(R.id.payerNameTV);
            amountPaidTV = itemView.findViewById(R.id.amountPaidTV);
            confirmPayerBtn = itemView.findViewById(R.id.confirmPayerBtn);
            denyPayerBtn = itemView.findViewById(R.id.denyPayerBtn);
        }
    }

    private void handleAllTransactions(String action) {
        showAllConfirmationDialog(action);
    }

    private void showAllConfirmationDialog(String action) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_borrowerlistconfirmation, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(dialogView);

        TextView confirmAction = dialogView.findViewById(R.id.confirmAction);
        Button payNowConfirmBtn = dialogView.findViewById(R.id.payNowConfirmBtn);
        Button closeButton = dialogView.findViewById(R.id.closeButton);

        confirmAction.setText(action);

        AlertDialog dialog = builder.create();

        payNowConfirmBtn.setOnClickListener(v -> {
            updateAllTransactionStatus(action.equals("Confirm") ? "Paid" : "Payment Denied");
            dialog.dismiss();
        });

        closeButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void updateAllTransactionStatus(String status) {
        for (int i = 0; i < transactionList.size(); i++) {
            BorrowerListTransaction transaction = transactionList.get(i);
            String[] path = pathList.get(i);
            final int index = i;
            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("borrows")
                    .child(path[0]).child(path[1]).child(path[2]).child(path[3]);

            transaction.setStatus(status);
            userRef.child("status").setValue(status)
                    .addOnSuccessListener(aVoid -> {
                        transactionList.set(index, transaction);
                        if (index == transactionList.size() - 1) {
                            notifyDataSetChanged();
                            if (statusUpdatedListener != null) {
                                statusUpdatedListener.onTransactionStatusUpdated();
                            }
                        }
                    })
                    .addOnFailureListener(e -> Toast.makeText(context, "Failed to update status: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }

    private void setProfileImage(ImageView imageView, String payerNameTV) {
        if (imageView == null || payerNameTV == null) {
            Log.e("PayerListTransactionAdapter", "ImageView or payerNameTV is null.");
            return;
        }

        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");
        Query query = usersRef.orderByChild("username").equalTo(payerNameTV);

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                        String userId = userSnapshot.getKey(); // Assuming the key is the userId
                        if (userId != null) {
                            StorageReference storageRef = FirebaseStorage.getInstance().getReference("profile_images").child(userId);
                            storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                                // Check if the tag is still valid before loading the image
                                if (payerNameTV.equals(imageView.getTag()) && context != null) {
                                    Glide.with(context).load(uri).placeholder(R.drawable.placeholder_profile_image).into(imageView);
                                }
                            }).addOnFailureListener(e -> {
                                Log.e("FirebaseStorage", "Failed to get download URL: " + e.getMessage());
                                if (payerNameTV.equals(imageView.getTag())) {
                                    imageView.setImageResource(R.drawable.placeholder_profile_image); // default image
                                }
                            });
                        }
                    }
                } else {
                    Log.e("PayerListTransactionAdapter", "No user found with username: " + payerNameTV);
                    if (payerNameTV.equals(imageView.getTag())) {
                        imageView.setImageResource(R.drawable.placeholder_profile_image); // default image
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("FirebaseDatabase", "Profile image query cancelled: " + databaseError.getMessage());
                if (payerNameTV.equals(imageView.getTag())) {
                    imageView.setImageResource(R.drawable.placeholder_profile_image); // default image
                }
            }
        });
    }
}
