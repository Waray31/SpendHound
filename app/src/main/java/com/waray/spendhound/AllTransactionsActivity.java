package com.waray.spendhound;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class AllTransactionsActivity extends AppCompatActivity implements RecentTransactionAdapter.OnTransactionClickListener {

    private RecyclerView recyclerView;
    private RecentTransactionAdapter adapter;
    private ArrayList<RecentTransaction> transactionList;
    private Spinner monthSpinner;
    private TextView currentMonthTextView;
    private TextView transactionCountTextView;
    private ProgressBar loadingProgressBar;
    private LinearLayout emptyStateLayout;
    private FirebaseAuth mAuth;
    private String currentNickname = "";
    private List<String> availableMonths;
    private String selectedMonth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_transactions);

        mAuth = DeclareDatabase.getAuth();
        transactionList = new ArrayList<>();
        availableMonths = new ArrayList<>();

        initViews();
        setupBackButton();
        getCurrentNickname();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.allTransactionsRecyclerView);
        monthSpinner = findViewById(R.id.monthSpinner);
        currentMonthTextView = findViewById(R.id.currentMonthTextView);
        transactionCountTextView = findViewById(R.id.transactionCountTextView);
        loadingProgressBar = findViewById(R.id.loadingProgressBar);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);

        adapter = new RecentTransactionAdapter(transactionList, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void setupBackButton() {
        ImageButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());
    }

    private void getCurrentNickname() {
        String userId = Objects.requireNonNull(mAuth.getCurrentUser()).getUid();
        DatabaseReference userRef = DeclareDatabase.getDatabaseReference().child(userId);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    currentNickname = snapshot.child("username").getValue(String.class);
                    if (currentNickname == null) {
                        currentNickname = "";
                    }
                }
                loadAvailableMonths();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("AllTransactions", "Error getting nickname: " + error.getMessage());
                loadAvailableMonths();
            }
        });
    }

    private void loadAvailableMonths() {
        loadingProgressBar.setVisibility(View.VISIBLE);

        DatabaseReference transRef = DeclareDatabase.getDBRefTransaction();
        transRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Set<String> uniqueMonths = new HashSet<>();

                for (DataSnapshot monthSnapshot : dataSnapshot.getChildren()) {
                    String monthYear = monthSnapshot.getKey();
                    if (monthYear != null && !monthYear.isEmpty()) {
                        // Check if there are transactions for this month
                        for (DataSnapshot daySnapshot : monthSnapshot.getChildren()) {
                            for (DataSnapshot timeSnapshot : daySnapshot.getChildren()) {
                                Transaction transaction = timeSnapshot.getValue(Transaction.class);
                                if (transaction != null && isUserInvolved(transaction, currentNickname)) {
                                    uniqueMonths.add(monthYear);
                                    break;
                                }
                            }
                            if (uniqueMonths.contains(monthYear)) break;
                        }
                    }
                }

                availableMonths = new ArrayList<>(uniqueMonths);
                // Sort months in descending order (most recent first)
                Collections.sort(availableMonths, (m1, m2) -> {
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat("MMMM-yyyy", Locale.getDefault());
                        return sdf.parse(m2).compareTo(sdf.parse(m1));
                    } catch (Exception e) {
                        return m2.compareTo(m1);
                    }
                });

                setupMonthSpinner();
                loadingProgressBar.setVisibility(View.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("AllTransactions", "Error loading months: " + error.getMessage());
                loadingProgressBar.setVisibility(View.GONE);
            }
        });
    }

    private void setupMonthSpinner() {
        if (availableMonths.isEmpty()) {
            // Add current month if no months available
            Calendar calendar = Calendar.getInstance();
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM-yyyy", Locale.getDefault());
            availableMonths.add(dateFormat.format(calendar.getTime()));
        }

        SpinnerItemMonths spinnerAdapter = new SpinnerItemMonths(this, availableMonths);
        monthSpinner.setAdapter(spinnerAdapter);

        // Find current month in the list or default to first
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM-yyyy", Locale.getDefault());
        String currentMonth = dateFormat.format(calendar.getTime());

        int defaultPosition = 0;
        for (int i = 0; i < availableMonths.size(); i++) {
            if (availableMonths.get(i).equals(currentMonth)) {
                defaultPosition = i;
                break;
            }
        }

        monthSpinner.setSelection(defaultPosition);
        selectedMonth = availableMonths.get(defaultPosition);
        updateMonthDisplay(selectedMonth);
        fetchTransactionsForMonth(selectedMonth);

        monthSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String month = availableMonths.get(position);
                if (!month.equals(selectedMonth)) {
                    selectedMonth = month;
                    updateMonthDisplay(selectedMonth);
                    fetchTransactionsForMonth(selectedMonth);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
    }

    private void updateMonthDisplay(String monthYear) {
        // Convert "February-2026" to "February 2026"
        String displayMonth = monthYear.replace("-", " ");
        currentMonthTextView.setText(displayMonth);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void fetchTransactionsForMonth(String monthYear) {
        loadingProgressBar.setVisibility(View.VISIBLE);
        emptyStateLayout.setVisibility(View.GONE);
        transactionList.clear();
        adapter.notifyDataSetChanged();

        DatabaseReference monthRef = DeclareDatabase.getDBRefTransaction().child(monthYear);

        monthRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot daySnapshot : dataSnapshot.getChildren()) {
                    String day = daySnapshot.getKey();

                    for (DataSnapshot timeSnapshot : daySnapshot.getChildren()) {
                        Transaction transaction = timeSnapshot.getValue(Transaction.class);
                        String timeKey = timeSnapshot.getKey();

                        if (transaction != null && isUserInvolved(transaction, currentNickname)) {
                            String[] parts = monthYear.split("-");
                            String month = parts[0];
                            String year = parts.length > 1 ? parts[1] : "";
                            String displayDate = month + " - " + day;

                            // Create full date with year for details dialog
                            String fullDateWithYear = month + " " + day + ", " + year;

                            // Create sortDateTime for proper sorting
                            String sortDateTime = year + "-" + month + "-" + day + " " + timeKey;

                            String transactionType = transaction.getTransactionType();
                            String details = transaction.getMultilineStr();
                            double paymentAmount = transaction.getPaymentAmount();
                            String paymentAmountStr = String.format(Locale.getDefault(), "₱ %.2f", paymentAmount);
                            int iconResource = getIconForTransactionType(transactionType);

                            // Get payors list - prefer display names, fallback to UIDs/usernames
                            java.util.List<String> payorsList = transaction.getPayorsDisplayNames();
                            if (payorsList == null || payorsList.isEmpty()) {
                                payorsList = transaction.getPayorsList();
                            }
                            java.util.List<String> payorUids = transaction.getPayorsList();
                            java.util.List<Double> amountsPaidList = transaction.getAmountsPaidList();
                            double totalIndividualPayment = transaction.getTotalIndividualPayment();

                            // Get creator name - prefer display name, fallback to usernamePost
                            String createdBy = transaction.getPosterDisplayName();
                            if (createdBy == null || createdBy.isEmpty()) {
                                createdBy = transaction.getUsernamePost();
                            }

                            // Get creator UID for profile image
                            String createdByUid = transaction.getUsernamePost();

                            RecentTransaction recentTrans = new RecentTransaction(
                                    displayDate,
                                    transactionType,
                                    details,
                                    paymentAmountStr,
                                    iconResource,
                                    sortDateTime,
                                    payorsList,
                                    payorUids,
                                    amountsPaidList,
                                    totalIndividualPayment,
                                    fullDateWithYear,
                                    createdBy,
                                    createdByUid,
                                    monthYear,
                                    day,
                                    timeKey
                            );
                            transactionList.add(recentTrans);
                        }
                    }
                }

                // Sort transactions in descending order by date and time
                Collections.sort(transactionList, (t1, t2) -> {
                    String dateTime1 = t1.getSortDateTime();
                    String dateTime2 = t2.getSortDateTime();
                    if (dateTime1 != null && dateTime2 != null) {
                        return dateTime2.compareTo(dateTime1);
                    }
                    return 0;
                });

                adapter.notifyDataSetChanged();
                loadingProgressBar.setVisibility(View.GONE);

                // Update transaction count
                int count = transactionList.size();
                transactionCountTextView.setText(count + (count == 1 ? " transaction" : " transactions"));

                // Show empty state if no transactions
                if (transactionList.isEmpty()) {
                    emptyStateLayout.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    emptyStateLayout.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("AllTransactions", "Error loading transactions: " + error.getMessage());
                loadingProgressBar.setVisibility(View.GONE);
            }
        });
    }

    private int getIconForTransactionType(String transactionType) {
        if ("Electricity".equals(transactionType)) {
            return R.drawable.lightning_bolt;
        } else if ("Water".equals(transactionType)) {
            return R.drawable.faucet;
        } else if ("Rent".equals(transactionType)) {
            return R.drawable.house;
        } else if ("Internet".equals(transactionType)) {
            return R.drawable.internet;
        } else if ("Online Shopping".equals(transactionType)) {
            return R.drawable.online_shopping;
        } else if ("Travel".equals(transactionType)) {
            return R.drawable.travel;
        } else if ("Groceries".equals(transactionType)) {
            return R.drawable.groceries;
        } else if ("Foods".equals(transactionType)) {
            return R.drawable.hamburger;
        } else if ("House Necessity".equals(transactionType)) {
            return R.drawable.necessities;
        } else if ("Transportation".equals(transactionType)) {
            return R.drawable.vehicles;
        } else {
            return R.drawable.others;
        }
    }

    private boolean isUserInvolved(Transaction transaction, String usernameOrUid) {
        if (transaction == null || usernameOrUid == null || usernameOrUid.isEmpty()) {
            return false;
        }

        // Get current user's UID for comparison
        String currentUid = Objects.requireNonNull(mAuth.getCurrentUser()).getUid();

        // First try UID-based comparison (new data format)
        if (transaction.isUserInvolvedByUid(currentUid)) {
            return true;
        }

        // Fall back to username-based comparison (legacy data format)
        if (usernameOrUid.equals(transaction.getUsernamePost())) {
            return true;
        }

        if (usernameOrUid.equals(transaction.getPosterDisplayName())) {
            return true;
        }

        java.util.List<String> payorsList = transaction.getPayorsList();
        if (payorsList != null && payorsList.contains(usernameOrUid)) {
            return true;
        }

        java.util.List<String> payorsDisplayNames = transaction.getPayorsDisplayNames();
        if (payorsDisplayNames != null && payorsDisplayNames.contains(usernameOrUid)) {
            return true;
        }

        return false;
    }

    @Override
    public void onTransactionClick(RecentTransaction transaction) {
        showTransactionDetailsDialog(transaction);
    }

    private void showTransactionDetailsDialog(RecentTransaction transaction) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_transaction_details, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Find views in dialog
        ImageView iconImageView = dialogView.findViewById(R.id.dialogIconImageView);
        TextView transactionType = dialogView.findViewById(R.id.dialogTransactionType);
        TextView dateTextView = dialogView.findViewById(R.id.dialogDate);
        TextView amountTextView = dialogView.findViewById(R.id.dialogAmount);
        ImageView creatorProfileImage = dialogView.findViewById(R.id.dialogCreatorProfileImage);
        TextView createdByNameTV = dialogView.findViewById(R.id.dialogCreatedByName);
        TextView detailsTextView = dialogView.findViewById(R.id.dialogDetails);
        LinearLayout payorsContainer = dialogView.findViewById(R.id.payorsContainer);
        Button closeButton = dialogView.findViewById(R.id.dialogCloseButton);
        View editHeaderSpacer = dialogView.findViewById(R.id.editHeaderSpacer);

        // Populate dialog with transaction data
        iconImageView.setImageResource(transaction.getIconResource());
        transactionType.setText(transaction.getMostRecentTransactionType());

        // Use full date with year if available
        String dateToDisplay = transaction.getFullDateWithYear();
        if (dateToDisplay == null || dateToDisplay.isEmpty()) {
            dateToDisplay = transaction.getMostRecentDate();
        }
        dateTextView.setText(dateToDisplay);

        amountTextView.setText(transaction.getMostRecentPaymentAmountStr());

        // Populate Created By section
        String createdBy = transaction.getCreatedBy();
        if (createdBy != null && !createdBy.isEmpty()) {
            createdByNameTV.setText(createdBy);
        } else {
            createdByNameTV.setText("Unknown");
        }

        // Load creator profile image
        String createdByUid = transaction.getCreatedByUid();
        String currentUid = mAuth.getCurrentUser().getUid();
        boolean isCreator = createdByUid != null && createdByUid.equals(currentUid);
        
        if (isCreator) {
            editHeaderSpacer.setVisibility(View.VISIBLE);
        }

        if (createdByUid != null && !createdByUid.isEmpty()) {
            com.google.firebase.storage.StorageReference storageRef =
                com.google.firebase.storage.FirebaseStorage.getInstance()
                    .getReference("profile_images").child(createdByUid);
            storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                com.bumptech.glide.Glide.with(this)
                    .load(uri)
                    .placeholder(R.drawable.placeholder_profile_image)
                    .circleCrop()
                    .into(creatorProfileImage);
            }).addOnFailureListener(e -> {
                creatorProfileImage.setImageResource(R.drawable.placeholder_profile_image);
            });
        } else {
            creatorProfileImage.setImageResource(R.drawable.placeholder_profile_image);
        }

        // Populate payors section
        java.util.List<String> payorsList = transaction.getPayorsList();
        java.util.List<String> payorUids = transaction.getPayorUids();
        java.util.List<Double> amountsPaidList = transaction.getAmountsPaidList();
        double individualPayment = transaction.getTotalIndividualPayment();

        if (payorsList != null && !payorsList.isEmpty()) {
            for (int i = 0; i < payorsList.size(); i++) {
                final int index = i;
                String payorName = payorsList.get(i);
                final double amountPaid = (amountsPaidList != null && i < amountsPaidList.size()) ? amountsPaidList.get(i) : 0;

                View rowView = LayoutInflater.from(this).inflate(R.layout.transaction_payor_table_row, payorsContainer, false);
                ImageView payorImage = rowView.findViewById(R.id.payorImage);
                TextView payorNameTV = rowView.findViewById(R.id.payorName);
                TextView payorPaidAmountTV = rowView.findViewById(R.id.payorPaidAmount);
                TextView payorDueAmountTV = rowView.findViewById(R.id.payorDueAmount);
                TextView payorStatusTV = rowView.findViewById(R.id.payorStatus);
                ImageView editBtn = rowView.findViewById(R.id.editPayorAmountBtn);

                payorNameTV.setText(payorName);
                payorPaidAmountTV.setText(String.format(Locale.getDefault(), "₱ %.2f", amountPaid));
                payorDueAmountTV.setText(String.format(Locale.getDefault(), "₱ %.2f", individualPayment));

                if (Math.abs(amountPaid - individualPayment) < 0.01) {
                    payorStatusTV.setVisibility(View.VISIBLE);
                } else {
                    payorStatusTV.setVisibility(View.INVISIBLE);
                }

                if (isCreator) {
                    editBtn.setVisibility(View.VISIBLE);
                    editBtn.setOnClickListener(v -> {
                        showEditAmountDialog(transaction, index, amountPaid);
                        dialog.dismiss();
                    });
                }

                // Load payor image
                if (payorUids != null && i < payorUids.size()) {
                    String payorUid = payorUids.get(i);
                    com.google.firebase.storage.StorageReference pStorageRef =
                        com.google.firebase.storage.FirebaseStorage.getInstance()
                            .getReference("profile_images").child(payorUid);
                    pStorageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        com.bumptech.glide.Glide.with(this)
                            .load(uri)
                            .placeholder(R.drawable.placeholder_profile_image)
                            .circleCrop()
                            .into(payorImage);
                    }).addOnFailureListener(e -> {
                        payorImage.setImageResource(R.drawable.placeholder_profile_image);
                    });
                }

                payorsContainer.addView(rowView);
            }
        } else {
            TextView noPayorsTV = new TextView(this);
            noPayorsTV.setText("No payors information");
            noPayorsTV.setTextColor(getResources().getColor(R.color.grey, null));
            noPayorsTV.setTextSize(14);
            payorsContainer.addView(noPayorsTV);
        }

        String details = transaction.getMostRecentDetails();
        if (details != null && !details.isEmpty() && !details.equals("See Details >")) {
            detailsTextView.setText(details);
        } else {
            detailsTextView.setText("No additional details");
        }

        closeButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showEditAmountDialog(RecentTransaction transaction, int index, double currentAmount) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Amount Paid");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.valueOf(currentAmount));
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newAmountStr = input.getText().toString();
            if (!newAmountStr.isEmpty()) {
                double newAmount = Double.parseDouble(newAmountStr);
                updatePayerAmountInDatabase(transaction, index, newAmount);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void updatePayerAmountInDatabase(RecentTransaction transaction, int index, double newAmount) {
        String monthYear = transaction.getMonthYear();
        String day = transaction.getDay();
        String timeKey = transaction.getTimeKey();

        if (monthYear == null || day == null || timeKey == null) {
            Toast.makeText(this, "Error: Could not find transaction reference", Toast.LENGTH_SHORT).show();
            return;
        }

        DatabaseReference ref = DeclareDatabase.getDBRefTransaction()
                .child(monthYear)
                .child(day)
                .child(timeKey)
                .child("amountsPaidList")
                .child(String.valueOf(index));

        ref.setValue(newAmount).addOnSuccessListener(aVoid -> {
            Toast.makeText(this, "Amount updated successfully", Toast.LENGTH_SHORT).show();
            fetchTransactionsForMonth(selectedMonth); // Refresh the list
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to update amount", Toast.LENGTH_SHORT).show();
        });
    }
}
