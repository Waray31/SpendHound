package com.waray.spendhound.ui.borrow;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.waray.spendhound.BalanceHelper;
import com.waray.spendhound.BorrowNowTransaction;
import com.waray.spendhound.BorrowTransaction;
import com.waray.spendhound.DeclareDatabase;
import com.waray.spendhound.DebtTransactionAdapter;
import com.waray.spendhound.MainActivity;
import com.waray.spendhound.OwedTransaction;
import com.waray.spendhound.OwedTransactionAdapter;
import com.waray.spendhound.R;
import com.waray.spendhound.SpinnerItem;
import com.waray.spendhound.SpinnerItemMonths;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class BorrowFragment extends Fragment {

    // UI Components - Spinners
    private Spinner monthYearSpinner;

    // UI Components - Buttons
    private Button borrowNowBtn;

    // UI Components - TextViews
    public TextView owedTV, debtTV;

    // UI Components - Status Tabs
    private TextView allTabTV, paidTabTV, unpaidTabTV, pendingTabTV;

    // UI Components - Empty State Views
    private View noOwedTextView, noDebtTextView;

    // UI Components - RecyclerViews
    private RecyclerView debtRecyclerList, owedRecyclerList;

    // UI Components - ProgressBar for loading state
    private View loadingOverlay;

    // Data
    public List<String> debtSortedMonths, owedSortedMonths;
    public String selectedMonth;
    private String selectedStatusTab = "All";
    private boolean owedDebtClicked;
    public String currentNickname = "";

    // State tracking for better UX
    private boolean isLoading = false;

    @SuppressLint("MissingInflatedId")
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_borrow, container, false);
        monthYearSpinner = view.findViewById(R.id.monthYearSpinner);
        borrowNowBtn = view.findViewById(R.id.borrowNowBtn);
        owedTV = view.findViewById(R.id.owedTV);
        debtTV = view.findViewById(R.id.debtTV);
        owedRecyclerList = view.findViewById(R.id.owedRecyclerList);
        noOwedTextView = view.findViewById(R.id.noOwedTextView);
        noDebtTextView = view.findViewById(R.id.noDebtTextView);
        debtRecyclerList = view.findViewById(R.id.debtRecyclerList);
        loadingOverlay = view.findViewById(R.id.loadingOverlay);

        // Status tabs
        allTabTV = view.findViewById(R.id.allTabTV);
        paidTabTV = view.findViewById(R.id.paidTabTV);
        unpaidTabTV = view.findViewById(R.id.unpaidTabTV);
        pendingTabTV = view.findViewById(R.id.pendingTabTV);

        owedDebtClicked = true;

        getCurrentNickname();
        setupViews();
        setupSpinners();
        setupStatusTabs();
        setupClickListeners();

        // Get the hosting Activity and remove the ActionBar
        AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity != null && activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().hide();
        }
        return view;
    }

    private void setupViews() {
        owedRecyclerList.setLayoutManager(new LinearLayoutManager(getContext()));
        debtRecyclerList.setLayoutManager(new LinearLayoutManager(getContext()));
        OwedMonthlyFilterList();
    }

    private void setupSpinners() {
        monthYearSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                selectedMonth = (String) parentView.getItemAtPosition(position);
                applyFilters();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });
    }

    private void setupStatusTabs() {
        setStatusTabSelected(allTabTV);

        allTabTV.setOnClickListener(v -> {
            selectedStatusTab = "All";
            setStatusTabSelected(allTabTV);
            applyFilters();
        });

        paidTabTV.setOnClickListener(v -> {
            selectedStatusTab = "Paid";
            setStatusTabSelected(paidTabTV);
            applyFilters();
        });

        unpaidTabTV.setOnClickListener(v -> {
            selectedStatusTab = "Unpaid";
            setStatusTabSelected(unpaidTabTV);
            applyFilters();
        });

        pendingTabTV.setOnClickListener(v -> {
            selectedStatusTab = "Pending";
            setStatusTabSelected(pendingTabTV);
            applyFilters();
        });
    }

    private void setStatusTabSelected(TextView selectedTab) {
        // Reset all tabs
        allTabTV.setBackgroundResource(0);
        paidTabTV.setBackgroundResource(0);
        unpaidTabTV.setBackgroundResource(0);
        pendingTabTV.setBackgroundResource(0);

        // Set selected tab background
        selectedTab.setBackgroundResource(R.drawable.bg_status_tab_selected);
    }

    private void setupClickListeners() {
        borrowNowBtn.setOnClickListener(v -> showBorrowNowDialog());

        owedTV.setOnClickListener(v -> handleOwedClick());
        debtTV.setOnClickListener(v -> handleDebtClick());
    }

    private void applyFilters() {
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity == null) return;

        showLoading();

        if (owedDebtClicked) {
            if (Objects.equals(selectedMonth, "All")) {
                mainActivity.getOwedList(selectedStatusTab, this::OwedSize, getLenderActionListener());
            } else {
                mainActivity.getOwedListMonthly(selectedMonth, selectedStatusTab, this::OwedSize, getLenderActionListener());
            }
        } else {
            if (Objects.equals(selectedMonth, "All")) {
                mainActivity.getDebtList(selectedStatusTab, this::DebtSize, getBorrowerActionListener());
            } else {
                mainActivity.getDebtListMonthly(selectedMonth, selectedStatusTab, this::DebtSize, getBorrowerActionListener());
            }
        }
    }

    /**
     * Create lender action listener for owed transactions
     */
    private OwedTransactionAdapter.OnLenderActionListener getLenderActionListener() {
        return new OwedTransactionAdapter.OnLenderActionListener() {
            @Override
            public void onNotYetClicked(OwedTransaction transaction, int position) {
                showConfirmationDialog(
                        getString(R.string.confirm_not_yet_title),
                        getString(R.string.confirm_not_yet_message),
                        R.color.grey,
                        () -> updateTransactionStatus(
                                transaction.getBorrowId(),
                                transaction.getMonthYear(),
                                transaction.getDay(),
                                "Unpaid",
                                null
                        )
                );
            }

            @Override
            public void onReceivedClicked(OwedTransaction transaction, int position) {
                showConfirmationDialog(
                        getString(R.string.confirm_received_title),
                        getString(R.string.confirm_received_message),
                        R.color.green,
                        () -> updateTransactionStatus(
                                transaction.getBorrowId(),
                                transaction.getMonthYear(),
                                transaction.getDay(),
                                "Paid",
                                null
                        )
                );
            }

            @Override
            public void onDeclineClicked(OwedTransaction transaction, int position) {
                showConfirmationDialog(
                        getString(R.string.confirm_decline_title),
                        getString(R.string.confirm_decline_message),
                        R.color.red,
                        () -> updateTransactionStatus(
                                transaction.getBorrowId(),
                                transaction.getMonthYear(),
                                transaction.getDay(),
                                "Declined",
                                null
                        )
                );
            }

            @Override
            public void onApprovedClicked(OwedTransaction transaction, int position) {
                showConfirmationDialog(
                        getString(R.string.confirm_approve_title),
                        getString(R.string.confirm_approve_message),
                        R.color.green,
                        () -> updateTransactionStatus(
                                transaction.getBorrowId(),
                                transaction.getMonthYear(),
                                transaction.getDay(),
                                "Unpaid",
                                null
                        )
                );
            }
        };
    }

    /**
     * Create borrower action listener for debt transactions
     */
    private DebtTransactionAdapter.OnBorrowerActionListener getBorrowerActionListener() {
        return new DebtTransactionAdapter.OnBorrowerActionListener() {
            @Override
            public void onPayClicked(BorrowTransaction transaction, int position) {
                showConfirmationDialog(
                        getString(R.string.confirm_pay_title),
                        getString(R.string.confirm_pay_message),
                        R.color.green,
                        () -> updateTransactionStatusWithPaymentDate(
                                transaction.getBorrowId(),
                                transaction.getMonthYear(),
                                transaction.getDay(),
                                "Pending Payment",
                                null
                        )
                );
            }

            @Override
            public void onRemoveClicked(BorrowTransaction transaction, int position) {
                showConfirmationDialog(
                        getString(R.string.confirm_remove_title),
                        getString(R.string.confirm_remove_message),
                        R.color.red,
                        () -> updateTransactionStatus(
                                transaction.getBorrowId(),
                                transaction.getMonthYear(),
                                transaction.getDay(),
                                "Removed",
                                null
                        )
                );
            }

            @Override
            public void onTryAgainClicked(BorrowTransaction transaction, int position) {
                showConfirmationDialog(
                        getString(R.string.confirm_try_again_title),
                        getString(R.string.confirm_try_again_message),
                        R.color.green,
                        () -> updateTransactionStatus(
                                transaction.getBorrowId(),
                                transaction.getMonthYear(),
                                transaction.getDay(),
                                "For Lender Approval",
                                null
                        )
                );
            }
        };
    }

    private void handleOwedClick() {
        setTabColors(owedTV, debtTV);
        owedRecyclerList.setVisibility(View.VISIBLE);
        debtRecyclerList.setVisibility(View.GONE);

        owedDebtClicked = true;
        resetSpinners();
        OwedMonthlyFilterList();
    }

    private void handleDebtClick() {
        setTabColors(debtTV, owedTV);
        owedRecyclerList.setVisibility(View.GONE);
        debtRecyclerList.setVisibility(View.VISIBLE);

        owedDebtClicked = false;
        resetSpinners();
        DebtMonthlyFilterList();
    }

    private void setTabColors(TextView activeTab, TextView inactiveTab) {
        activeTab.setBackgroundResource(R.drawable.top_round_border);
        inactiveTab.setBackgroundResource(R.drawable.button_background_invisible);
        activeTab.setTextColor(ContextCompat.getColor(getContext(), R.color.darkBlue));
        inactiveTab.setTextColor(ContextCompat.getColor(getContext(), R.color.whitest));
    }

    private void resetSpinners() {
        monthYearSpinner.setSelection(0);
    }


    public void DebtMonthlyFilterList() {
        DatabaseReference transRef = DeclareDatabase.getDBRefBorrows();
        Set<String> debtUniqueMonthYear = new HashSet<>();
        String currentUserId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();

        transRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                debtUniqueMonthYear.add("All");
                for (DataSnapshot monthSnapshot : dataSnapshot.getChildren()) {
                    String monthYear = monthSnapshot.getKey();
                    for (DataSnapshot daySnapshot : monthSnapshot.getChildren()) {
                        for (DataSnapshot borrowSnapshot : daySnapshot.getChildren()) {
                            // Try new structure first: borrows/{month}/{day}/{borrowId}
                            BorrowNowTransaction borrowNowTransaction = borrowSnapshot.getValue(BorrowNowTransaction.class);

                            if (borrowNowTransaction != null && borrowNowTransaction.getBorrowerID() != null) {
                                // New UID-based structure - check if current user is the borrower
                                if (Objects.equals(borrowNowTransaction.getBorrowerID(), currentUserId)) {
                                    debtUniqueMonthYear.add(monthYear);
                                }
                            } else {
                                // Legacy structure: borrows/{month}/{day}/{username}/{time}
                                if (Objects.equals(borrowSnapshot.getKey(), currentNickname)) {
                                    debtUniqueMonthYear.add(monthYear);
                                }
                            }
                        }
                    }
                }

                debtSortedMonths = new ArrayList<>(debtUniqueMonthYear);
                Collections.sort(debtSortedMonths);
                updateSpinnerAdapter(debtSortedMonths);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("FirebaseDatabase", "Database read error occurred: " + databaseError.getMessage());
            }
        });
    }

    public void OwedMonthlyFilterList() {
        DatabaseReference transRef = DeclareDatabase.getDBRefBorrows();
        Set<String> owedUniqueMonthYear = new HashSet<>();
        String currentUserId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();

        transRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                owedUniqueMonthYear.add("All");
                for (DataSnapshot monthSnapshot : dataSnapshot.getChildren()) {
                    String monthYear = monthSnapshot.getKey();
                    for (DataSnapshot daySnapshot : monthSnapshot.getChildren()) {
                        for (DataSnapshot borrowSnapshot : daySnapshot.getChildren()) {
                            // Try new structure first: borrows/{month}/{day}/{borrowId}
                            BorrowNowTransaction borrowNowTransaction = borrowSnapshot.getValue(BorrowNowTransaction.class);

                            if (borrowNowTransaction != null && borrowNowTransaction.getLenderID() != null) {
                                // New UID-based structure - check if current user is the lender
                                if (Objects.equals(borrowNowTransaction.getLenderID(), currentUserId)) {
                                    owedUniqueMonthYear.add(monthYear);
                                }
                            } else {
                                // Legacy structure: borrows/{month}/{day}/{username}/{time}
                                if (!Objects.equals(borrowSnapshot.getKey(), currentNickname)) {
                                    for (DataSnapshot timeSnapshot : borrowSnapshot.getChildren()) {
                                        try {
                                            BorrowTransaction borrowTransaction = timeSnapshot.getValue(BorrowTransaction.class);
                                            if (borrowTransaction != null && Objects.equals(borrowTransaction.getBorrowee(), currentNickname)) {
                                                owedUniqueMonthYear.add(monthYear);
                                            }
                                        } catch (Exception e) {
                                            Log.e("BorrowFragment", "Error parsing legacy transaction: " + e.getMessage());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                owedSortedMonths = new ArrayList<>(owedUniqueMonthYear);
                Collections.sort(owedSortedMonths);
                updateSpinnerAdapter(owedSortedMonths);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("FirebaseDatabase", "Database read error occurred: " + databaseError.getMessage());
            }
        });
    }

    private void updateSpinnerAdapter(List<String> months) {
        monthYearSpinner.setBackgroundResource(R.drawable.transparent_background);
        SpinnerItemMonths adapter = new SpinnerItemMonths(getActivity(), months);
        monthYearSpinner.setAdapter(adapter);
    }

    public void getCurrentNickname() {
        String currentUserID = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
        DatabaseReference usersRef = DeclareDatabase.getDatabaseReference().child(currentUserID);
        usersRef.child("username").addListenerForSingleValueEvent(new ValueEventListener() {
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    currentNickname = dataSnapshot.getValue(String.class);
                } else {
                    Log.d("FirebaseDatabase", "Nickname not found in database.");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("FirebaseDatabase", "Database read error occurred: " + databaseError.getMessage());
            }
        });
    }

    public void OwedSize(int owedNum) {
        hideLoading();
        noOwedTextView.setVisibility(owedNum == 0 ? View.VISIBLE : View.GONE);
        owedRecyclerList.setVisibility(owedNum == 0 ? View.GONE : View.VISIBLE);
        noDebtTextView.setVisibility(View.GONE);
    }

    public void DebtSize(int debtNum) {
        hideLoading();
        noDebtTextView.setVisibility(debtNum == 0 ? View.VISIBLE : View.GONE);
        debtRecyclerList.setVisibility(debtNum == 0 ? View.GONE : View.VISIBLE);
        noOwedTextView.setVisibility(View.GONE);
    }

    /**
     * Show confirmation dialog for actions
     */
    public void showConfirmationDialog(String title, String message, int confirmBtnColor, Runnable onConfirm) {
        Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_confirm_action);
        dialog.setCancelable(true);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.WHITE));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView dialogTitle = dialog.findViewById(R.id.dialogTitle);
        TextView dialogMessage = dialog.findViewById(R.id.dialogMessage);
        Button cancelBtn = dialog.findViewById(R.id.dialogCancelBtn);
        Button confirmBtn = dialog.findViewById(R.id.dialogConfirmBtn);

        dialogTitle.setText(title);
        dialogMessage.setText(message);
        confirmBtn.setBackgroundTintList(ContextCompat.getColorStateList(getContext(), confirmBtnColor));

        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        confirmBtn.setOnClickListener(v -> {
            dialog.dismiss();
            onConfirm.run();
        });

        dialog.show();
    }

    /**
     * Update transaction status in Firebase
     */
    public void updateTransactionStatus(String borrowId, String monthYear, String day, String newStatus, Runnable onSuccess) {
        DatabaseReference borrowRef = DeclareDatabase.getDBRefBorrows()
                .child(monthYear)
                .child(day)
                .child(borrowId);

        borrowRef.child("status").setValue(newStatus)
                .addOnSuccessListener(unused -> {
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                    showToast(getString(R.string.toast_status_updated));
                    applyFilters();
                })
                .addOnFailureListener(e -> {
                    Log.e("BorrowFragment", "Failed to update status: " + e.getMessage());
                    showToast(getString(R.string.toast_status_update_failed));
                });
    }

    /**
     * Update transaction status and save payment sent date
     */
    public void updateTransactionStatusWithPaymentDate(String borrowId, String monthYear, String day, String newStatus, Runnable onSuccess) {
        DatabaseReference borrowRef = DeclareDatabase.getDBRefBorrows()
                .child(monthYear)
                .child(day)
                .child(borrowId);

        long paymentSentDate = System.currentTimeMillis();

        borrowRef.child("status").setValue(newStatus);
        borrowRef.child("paymentSentDate").setValue(paymentSentDate)
                .addOnSuccessListener(unused -> {
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                    showToast(getString(R.string.toast_status_updated));
                    applyFilters();
                })
                .addOnFailureListener(e -> {
                    Log.e("BorrowFragment", "Failed to update status: " + e.getMessage());
                    showToast(getString(R.string.toast_status_update_failed));
                });
    }

    /**
     * Show the Borrow Now dialog as a modal
     */
    private void showBorrowNowDialog() {
        Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_borrow_now);
        dialog.setCancelable(false);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.WHITE));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        // Initialize dialog views
        TextView dateTV = dialog.findViewById(R.id.dialogBorrowDate);
        TextView borrowerTV = dialog.findViewById(R.id.dialogBorrower);
        Spinner lenderSpinner = dialog.findViewById(R.id.dialogBorroweeSpinner);
        EditText amountEditText = dialog.findViewById(R.id.dialogBorrowEditText);
        Button cancelBtn = dialog.findViewById(R.id.dialogCancelBtn);
        Button borrowBtn = dialog.findViewById(R.id.dialogBorrowBtn);
        ProgressBar progressBar = dialog.findViewById(R.id.dialogProgressBar);

        // Set current date
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM-dd-yyyy", Locale.getDefault());
        String currentDate = dateFormat.format(calendar.getTime());
        dateTV.setText(currentDate);

        // Set borrower name
        borrowerTV.setText(currentNickname);

        // Load users for lender spinner
        loadUsersForSpinner(lenderSpinner);

        // Cancel button
        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        // Borrow button
        borrowBtn.setOnClickListener(v -> {
            String amountStr = amountEditText.getText().toString().trim();
            String selectedLender = lenderSpinner.getSelectedItem() != null ?
                    lenderSpinner.getSelectedItem().toString() : "";

            if (amountStr.isEmpty() || selectedLender.equals(getString(R.string.dialog_select_lender)) || selectedLender.isEmpty()) {
                showToast(getString(R.string.toast_fill_all_fields));
                return;
            }

            int amount;
            try {
                amount = Integer.parseInt(amountStr);
                if (amount <= 0) {
                    showToast(getString(R.string.toast_fill_all_fields));
                    return;
                }
            } catch (NumberFormatException e) {
                showToast(getString(R.string.toast_fill_all_fields));
                return;
            }

            // Disable buttons and show progress
            borrowBtn.setEnabled(false);
            cancelBtn.setEnabled(false);
            progressBar.setVisibility(View.VISIBLE);

            // Process the borrow transaction
            addBorrowTransaction(selectedLender, String.valueOf(amount), currentDate, dialog, progressBar, borrowBtn, cancelBtn);
        });

        dialog.show();
    }

    /**
     * Load users for the lender spinner in the dialog
     */
    private void loadUsersForSpinner(Spinner spinner) {
        DatabaseReference databaseReference = DeclareDatabase.getDatabaseReference();
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                List<String> usernames = new ArrayList<>();
                usernames.add(getString(R.string.dialog_select_lender));
                for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                    String username = userSnapshot.child("username").getValue(String.class);
                    if (username != null && !username.equals(currentNickname)) {
                        usernames.add(username);
                    }
                }
                if (getActivity() != null) {
                    SpinnerItem adapter = new SpinnerItem(getActivity(), usernames);
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinner.setAdapter(adapter);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("BorrowFragment", "Database error: " + databaseError.getMessage());
                showToast(getString(R.string.toast_borrow_failed));
            }
        });
    }

    /**
     * Add a borrow transaction to Firebase
     */
    private void addBorrowTransaction(String lender, String borrowedAmountStr, String currentDate,
            Dialog dialog, ProgressBar progressBar, Button borrowBtn, Button cancelBtn) {

        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM-yyyy", Locale.getDefault());
        SimpleDateFormat dayFormat = new SimpleDateFormat("dd", Locale.getDefault());

        String currentMonthYear = dateFormat.format(calendar.getTime());
        String currentDay = dayFormat.format(calendar.getTime());
        long timestamp = System.currentTimeMillis();

        DatabaseReference databaseReference = DeclareDatabase.getDBRefBorrows();
        DatabaseReference monthYearRef = databaseReference.child(currentMonthYear);
        DatabaseReference dayRef = monthYearRef.child(currentDay);

        String borrowId = dayRef.push().getKey();
        if (borrowId == null) {
            showToast(getString(R.string.toast_borrow_failed));
            progressBar.setVisibility(View.GONE);
            borrowBtn.setEnabled(true);
            cancelBtn.setEnabled(true);
            return;
        }

        DatabaseReference borrowRef = dayRef.child(borrowId);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            String borrowerID = currentUser.getUid();

            // Get lender's user ID
            getUserIDByName(lender, lenderID -> {
                if (lenderID == null) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            showToast(getString(R.string.toast_borrow_failed));
                            progressBar.setVisibility(View.GONE);
                            borrowBtn.setEnabled(true);
                            cancelBtn.setEnabled(true);
                        });
                    }
                    return;
                }

                // Create transaction with "For Lender Approval" status
                BorrowNowTransaction borrowNowTransaction = new BorrowNowTransaction(
                        borrowId,
                        borrowerID,
                        lenderID,
                        currentNickname,
                        currentDate,
                        lender,
                        borrowedAmountStr,
                        "For Lender Approval",
                        timestamp
                );

                borrowRef.setValue(borrowNowTransaction).addOnSuccessListener(unused -> {
                    // Update userBorrows index for borrower
                    BalanceHelper.addBorrowerEntry(borrowerID, borrowId, null);

                    // Update userBorrows index for lender
                    BalanceHelper.addLenderEntry(lenderID, borrowId, null);

                    // Update borrower's debt
                    int amount = Integer.parseInt(borrowedAmountStr);
                    BalanceHelper.updateDebt(borrowerID, amount, null);
                    BalanceHelper.updateTotalBorrowed(borrowerID, amount, null);

                    // Update lender's owed
                    BalanceHelper.updateOwed(lenderID, amount, null);
                    BalanceHelper.updateTotalLent(lenderID, amount, null);

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            showToast(getString(R.string.toast_borrow_success));
                            dialog.dismiss();
                            applyFilters();
                        });
                    }
                }).addOnFailureListener(e -> {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            showToast(getString(R.string.toast_borrow_failed));
                            progressBar.setVisibility(View.GONE);
                            borrowBtn.setEnabled(true);
                            cancelBtn.setEnabled(true);
                        });
                    }
                });
            });
        }
    }

    /**
     * Get user ID by username
     */
    private void getUserIDByName(String name, UserIDCallback callback) {
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");
        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                    String userName = userSnapshot.child("username").getValue(String.class);
                    if (name.equals(userName)) {
                        callback.onUserIDRetrieved(userSnapshot.getKey());
                        return;
                    }
                }
                callback.onUserIDRetrieved(null);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("BorrowFragment", "Database error: " + databaseError.getMessage());
                callback.onUserIDRetrieved(null);
            }
        });
    }

    /**
     * Callback interface for user ID retrieval
     */
    private interface UserIDCallback {
        void onUserIDRetrieved(String userID);
    }


    public void showToast(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Show loading overlay while data is being fetched
     */
    private void showLoading() {
        isLoading = true;
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Hide loading overlay when data fetch is complete
     */
    private void hideLoading() {
        isLoading = false;
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.GONE);
        }
    }

    /**
     * Convert display date format (MMM-dd-yyyy) to Firebase date format (MMMM-dd-yyyy)
     * @param displayDate Date in MMM-dd-yyyy format (e.g., "Feb-25-2026")
     * @return Date in MMMM-dd-yyyy format (e.g., "February-25-2026")
     */
    private String convertToFirebaseDateFormat(String displayDate) {
        try {
            SimpleDateFormat displayFormat = new SimpleDateFormat("MMM-dd-yyyy", Locale.ENGLISH);
            SimpleDateFormat firebaseFormat = new SimpleDateFormat("MMMM-dd-yyyy", Locale.ENGLISH);
            java.util.Date date = displayFormat.parse(displayDate);
            if (date != null) {
                return firebaseFormat.format(date);
            }
        } catch (Exception e) {
            Log.e("BorrowFragment", "Error converting date format: " + e.getMessage());
        }
        return displayDate; // Return original if conversion fails
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh data when returning to this fragment
        applyFilters();
    }
}

