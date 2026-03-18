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

public class BorrowerListTransactionAdapter extends RecyclerView.Adapter<BorrowerListTransactionAdapter.ViewHolder> {
    private List<BorrowerListTransaction> transactionList;
    private final List<String[]> pathList;
    private Context context;
    private OnTransactionStatusUpdatedListener statusUpdatedListener;

    public interface OnTransactionStatusUpdatedListener {
        void onTransactionStatusUpdated();
    }

    public BorrowerListTransactionAdapter(Context context, List<BorrowerListTransaction> transactionList, List<String[]> pathList, PendingStatusActivity statusUpdatedListener, Button acceptAllBorrowerBtn, Button declineAllBorrowerBtn) {
        this.context = context;
        this.transactionList = transactionList;
        this.pathList = pathList;
        this.statusUpdatedListener = statusUpdatedListener;

        acceptAllBorrowerBtn.setOnClickListener(v -> handleAllTransactions("Accept"));
        declineAllBorrowerBtn.setOnClickListener(v -> handleAllTransactions("Decline"));
    }

    public BorrowerListTransaction getBorrowerListTransaction(int position) {
        return transactionList.get(position);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(context).inflate(R.layout.borrower_row_layout, parent, false);
        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BorrowerListTransaction transaction = transactionList.get(position);
        holder.hoursAgoTV.setText(transaction.getDate());
        holder.borrowerNameTV.setText(transaction.getBorrowee());
        holder.amountBorrowedTV.setText(CurrencyUtils.formatAmountWithCurrency(transaction.getBorrowedAmountStr()));

        // Set a placeholder image and a tag for the ImageView
        holder.borrowerImg.setImageResource(R.drawable.placeholder_profile_image);
        holder.borrowerImg.setTag(transaction.getBorrowee());

        // Load the profile image asynchronously
        setProfileImage(holder.borrowerImg, transaction.getBorrowee());

        holder.acceptBorrowerBtn.setOnClickListener(v -> showConfirmationDialog("Accept", transaction, pathList.get(position), position));
        holder.declineBorrowerBtn.setOnClickListener(v -> showConfirmationDialog("Decline", transaction, pathList.get(position), position));
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
            if ("Accept".equalsIgnoreCase(action)) {
                updateTransactionStatus(transaction, path, position, "Unpaid");
            } else if ("Decline".equalsIgnoreCase(action)) {
                updateTransactionStatus(transaction, path, position, "Declined");
            }
            dialog.dismiss();
        });

        closeButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void updateTransactionStatus(BorrowerListTransaction transaction, String[] path, int position, String status) {
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("borrows")
                .child(path[0]).child(path[1]).child(path[2]).child(path[3]);

        transaction.setStatus(status);
        userRef.child("status").setValue(status)
                .addOnSuccessListener(aVoid -> {
                    transactionList.set(position, transaction);
                    notifyDataSetChanged();
                    if (statusUpdatedListener != null) {
                        statusUpdatedListener.onTransactionStatusUpdated();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(context, "Failed to update status: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView borrowerImg;
        public TextView hoursAgoTV;
        public TextView borrowerNameTV;
        public TextView amountBorrowedTV;
        public Button acceptBorrowerBtn,declineBorrowerBtn, acceptAllBorrowerBtn, declineAllBorrowerBtn ;

        public ViewHolder(View itemView) {
            super(itemView);
            borrowerImg = itemView.findViewById(R.id.borrowerImg);
            hoursAgoTV = itemView.findViewById(R.id.hoursAgoTV);
            borrowerNameTV = itemView.findViewById(R.id.borrowerNameTV);
            amountBorrowedTV = itemView.findViewById(R.id.amountBorrowedTV);
            acceptBorrowerBtn = itemView.findViewById(R.id.acceptBorrowerBtn);
            declineBorrowerBtn = itemView.findViewById(R.id.declineBorrowerBtn);
            acceptAllBorrowerBtn = itemView.findViewById(R.id.acceptAllBorrowerBtn);
            declineAllBorrowerBtn = itemView.findViewById(R.id.declineAllBorrowerBtn);
        }
    }

    private void handleAllTransactions(String action) {
        showAllConfirmationDialog(action, transactionList.get(0), pathList.get(0), 0, true);
    }

    private void showAllConfirmationDialog(String action, BorrowerListTransaction transaction, String[] path, int position, boolean isAll) {
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
            if (isAll) {
                updateAllTransactionStatus(action.equals("Accept") ? "Unpaid" : "Declined");
            } else {
                if ("Accept".equalsIgnoreCase(action)) {
                    updateTransactionStatus(transaction, path, position, "Unpaid");
                } else if ("Decline".equalsIgnoreCase(action)) {
                    updateTransactionStatus(transaction, path, position, "Declined");
                }
            }
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

    /*public void AcceptDeclineAllBtnClicked(){
        acceptAllBorrowerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                allTV.setVisibility(View.VISIBLE);
            }
        });
        declineAllBorrowerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                allTV.setVisibility(View.VISIBLE);
            }
        });
    }*/

    private void setProfileImage(ImageView imageView, String borrowerNameTV) {
        if (imageView == null || borrowerNameTV == null) {
            Log.e("BorrowerListTransactionAdapter", "ImageView or borrowerNameTV is null.");
            return;
        }

        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");
        Query query = usersRef.orderByChild("username").equalTo(borrowerNameTV);

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
                                if (borrowerNameTV.equals(imageView.getTag()) && context != null) {
                                    Glide.with(context).load(uri).placeholder(R.drawable.placeholder_profile_image).into(imageView);
                                }
                            }).addOnFailureListener(e -> {
                                Log.e("FirebaseStorage", "Failed to get download URL: " + e.getMessage());
                                if (borrowerNameTV.equals(imageView.getTag())) {
                                    imageView.setImageResource(R.drawable.placeholder_profile_image); // default image
                                }
                            });
                        }
                    }
                } else {
                    if (borrowerNameTV.equals(imageView.getTag())) {
                        imageView.setImageResource(R.drawable.placeholder_profile_image); // default image
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("FirebaseDatabase", "Profile image query cancelled: " + databaseError.getMessage());
                if (borrowerNameTV.equals(imageView.getTag())) {
                    imageView.setImageResource(R.drawable.placeholder_profile_image); // default image
                }
            }
        });
    }
}
