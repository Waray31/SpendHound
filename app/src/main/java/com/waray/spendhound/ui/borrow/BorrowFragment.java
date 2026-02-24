package com.waray.spendhound.ui.borrow;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.waray.spendhound.BorrowNowActivity;
import com.waray.spendhound.BorrowNowTransaction;
import com.waray.spendhound.BorrowTransaction;
import com.waray.spendhound.BorrowTransactionAdapter;
import com.waray.spendhound.CheckedTransactionsAdapter;
import com.waray.spendhound.DeclareDatabase;
import com.waray.spendhound.MainActivity;
import com.waray.spendhound.PendingStatusActivity;
import com.waray.spendhound.R;
import com.waray.spendhound.SpinnerItem;
import com.waray.spendhound.SpinnerItemMonths;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class BorrowFragment extends Fragment {

    // UI Components - Spinners
    private Spinner monthYearSpinner, statusSpinner;

    // UI Components - Buttons
    private Button borrowNowBtn, payNowBtn, pendingStatusBtn, debtSelectBtn, debtCancelPayBtn;

    // UI Components - TextViews
    public TextView owedTV, debtTV;

    // UI Components - Empty State Views
    private View noOwedTextView, noDebtTextView;

    // UI Components - Layouts
    private LinearLayout debtButtons, selectAllLayout;

    // UI Components - RecyclerViews
    private RecyclerView debtRecyclerList, debtCheckboxRecyclerList, owedRecyclerList;

    // UI Components - CheckBox
    private CheckBox payAllCheckBox;

    // UI Components - ProgressBar for loading state
    private View loadingOverlay;

    // Data
    public List<String> debtSortedMonths, owedSortedMonths;
    public String selectedMonth, selectedStatus;
    private boolean owedDebtClicked;
    public String currentNickname = "";

    // State tracking for better UX
    private boolean isLoading = false;
    private boolean isPaymentMode = false;
    private int payableDebtsCount = 0;

    @SuppressLint("MissingInflatedId")
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_borrow, container, false);
        monthYearSpinner = view.findViewById(R.id.monthYearSpinner);
        statusSpinner = view.findViewById(R.id.statusSpinner);
        borrowNowBtn = view.findViewById(R.id.borrowNowBtn);
        payNowBtn = view.findViewById(R.id.payNowBtn);
        owedTV = view.findViewById(R.id.owedTV);
        debtTV = view.findViewById(R.id.debtTV);
        debtButtons = view.findViewById(R.id.debtButtons);
        owedRecyclerList = view.findViewById(R.id.owedRecyclerList);
        noOwedTextView = view.findViewById(R.id.noOwedTextView);
        noDebtTextView = view.findViewById(R.id.noDebtTextView);
        debtRecyclerList = view.findViewById(R.id.debtRecyclerList);
        debtCheckboxRecyclerList = view.findViewById(R.id.debtCheckboxRecyclerList);
        payNowBtn = view.findViewById(R.id.payNowBtn);
        selectAllLayout = view.findViewById(R.id.selectAllLayout);
        payAllCheckBox = view.findViewById(R.id.payAllCheckBox);
        pendingStatusBtn = view.findViewById(R.id.pendingStatusBtn);
        debtSelectBtn = view.findViewById(R.id.debtSelectBtn);
        debtCancelPayBtn = view.findViewById(R.id.debtCancelPayBtn);
        loadingOverlay = view.findViewById(R.id.loadingOverlay);
        owedDebtClicked = true;

        getCurrentNickname();
        setupViews();
        setupSpinners();
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
        debtCheckboxRecyclerList.setLayoutManager(new LinearLayoutManager(getContext()));
        selectAllLayout.setVisibility(View.GONE);
        OwedMonthlyFilterList();
    }

    private void setupSpinners() {
        BorrowStatusItems();
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

        statusSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                selectedStatus = (String) parentView.getItemAtPosition(position);
                applyFilters();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });
    }

    private void setupClickListeners() {
        borrowNowBtn.setOnClickListener(v -> showBorrowNowDialog());

        owedTV.setOnClickListener(v -> {
            if (!isPaymentMode) {
                handleOwedClick();
            }
        });
        debtTV.setOnClickListener(v -> {
            if (!isPaymentMode) {
                handleDebtClick();
            }
        });
        payNowBtn.setOnClickListener(v -> handlePayNowClick());
        debtCancelPayBtn.setOnClickListener(v -> handleCancelPayClick());
        debtSelectBtn.setOnClickListener(v -> handleDebtSelectClick());
        pendingStatusBtn.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), PendingStatusActivity.class);
            startActivity(intent);
        });

        payAllCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            BorrowTransactionAdapter adapter = (BorrowTransactionAdapter) debtCheckboxRecyclerList.getAdapter();
            if (adapter != null) {
                if (isChecked) {
                    adapter.selectAll();
                } else {
                    adapter.deselectAll();
                }
            }
        });
    }

    private void applyFilters() {
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity == null) return;

        showLoading();

        if (owedDebtClicked) {
            if (Objects.equals(selectedMonth, "All")) {
                mainActivity.getOwedList(selectedStatus, this::OwedSize);
            } else {
                mainActivity.getOwedListMonthly(selectedMonth, selectedStatus, this::OwedSize);
            }
        } else {
            if (Objects.equals(selectedMonth, "All")) {
                mainActivity.getDebtList(selectedStatus, this::DebtSize);
            } else {
                mainActivity.getDebtListMonthly(selectedMonth, selectedStatus, this::DebtSize);
            }
        }
    }

    private void handleOwedClick() {
        setTabColors(owedTV, debtTV);
        debtButtons.setVisibility(View.GONE);
        owedRecyclerList.setVisibility(View.VISIBLE);
        debtRecyclerList.setVisibility(View.GONE);
        debtCheckboxRecyclerList.setVisibility(View.GONE);
        selectAllLayout.setVisibility(View.GONE);
        pendingStatusBtn.setVisibility(View.VISIBLE);

        owedDebtClicked = true;
        resetSpinners();
        OwedMonthlyFilterList();
    }

    private void handleDebtClick() {
        setTabColors(debtTV, owedTV);
        debtButtons.setVisibility(View.VISIBLE);
        owedRecyclerList.setVisibility(View.GONE);
        debtRecyclerList.setVisibility(View.VISIBLE);
        debtCheckboxRecyclerList.setVisibility(View.GONE);
        selectAllLayout.setVisibility(View.GONE);
        pendingStatusBtn.setVisibility(View.GONE);
        debtSelectBtn.setVisibility(View.GONE);
        debtCancelPayBtn.setVisibility(View.GONE);
        payNowBtn.setVisibility(View.VISIBLE);
        borrowNowBtn.setVisibility(View.VISIBLE);

        owedDebtClicked = false;
        resetSpinners();
        DebtMonthlyFilterList();
    }

    private void handlePayNowClick() {
        isPaymentMode = true;

        // Disable tab switching during payment mode
        owedTV.setEnabled(false);
        owedTV.setAlpha(0.5f);

        selectAllLayout.setVisibility(View.VISIBLE);
        debtSelectBtn.setVisibility(View.VISIBLE);
        debtCancelPayBtn.setVisibility(View.VISIBLE);
        payNowBtn.setVisibility(View.GONE);
        debtRecyclerList.setVisibility(View.GONE);
        noDebtTextView.setVisibility(View.GONE);
        debtCheckboxRecyclerList.setVisibility(View.VISIBLE);
        borrowNowBtn.setVisibility(View.GONE);
        CheckboxStatus();
    }

    private void handleCancelPayClick() {
        isPaymentMode = false;

        // Re-enable tab switching
        owedTV.setEnabled(true);
        owedTV.setAlpha(1.0f);

        selectAllLayout.setVisibility(View.GONE);
        debtSelectBtn.setVisibility(View.GONE);
        debtCancelPayBtn.setVisibility(View.GONE);
        payNowBtn.setVisibility(View.VISIBLE);
        debtRecyclerList.setVisibility(View.VISIBLE);
        debtCheckboxRecyclerList.setVisibility(View.GONE);
        borrowNowBtn.setVisibility(View.VISIBLE);

        // Restore the original status spinner items
        BorrowStatusItems();
    }

    private void handleDebtSelectClick() {
        BorrowTransactionAdapter adapter = (BorrowTransactionAdapter) debtCheckboxRecyclerList.getAdapter();
        if (adapter != null) {
            ArrayList<Integer> checkedPositions = adapter.getCheckedPositions();
            if (!checkedPositions.isEmpty()) {
                ArrayList<BorrowTransaction> checkedTransactions = new ArrayList<>();
                for (int position : checkedPositions) {
                    checkedTransactions.add(adapter.getBorrowTransaction(position));
                }
                showCheckedTransactionsDialog(checkedTransactions);
            } else {
                Toast.makeText(getActivity(), R.string.toast_no_debt_selected, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setTabColors(TextView activeTab, TextView inactiveTab) {
        activeTab.setBackgroundResource(R.drawable.top_round_border);
        inactiveTab.setBackgroundResource(R.drawable.button_background_invisible);
        activeTab.setTextColor(ContextCompat.getColor(getContext(), R.color.darkBlue));
        inactiveTab.setTextColor(ContextCompat.getColor(getContext(), R.color.whitest));
    }

    private void resetSpinners() {
        monthYearSpinner.setSelection(0);
        statusSpinner.setSelection(0);
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

    private void BorrowStatusItems() {
        String[] transactionTypes = getResources().getStringArray(R.array.borrowStatus_String);
        statusSpinner.setBackgroundResource(R.drawable.transparent_background);

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(), R.layout.spinner_item_status, R.id.status, Arrays.asList(transactionTypes)) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = view.findViewById(R.id.status);
                textView.setText(transactionTypes[position]);
                return view;
            }

            @Override
            public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = LayoutInflater.from(getContext()).inflate(R.layout.spinner_item_status, parent, false);
                TextView textView = view.findViewById(R.id.status);
                textView.setText(transactionTypes[position]);
                return view;
            }
        };
        statusSpinner.setAdapter(adapter);
    }

    private void CheckboxStatus() {
        String[] transactionTypes = getResources().getStringArray(R.array.checkboxStatus_String);
        statusSpinner.setBackgroundResource(R.drawable.transparent_background);

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(), R.layout.spinner_item_status, R.id.status, Arrays.asList(transactionTypes)) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = view.findViewById(R.id.status);
                textView.setText(transactionTypes[position]);
                return view;
            }

            @Override
            public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = LayoutInflater.from(getContext()).inflate(R.layout.spinner_item_status, parent, false);
                TextView textView = view.findViewById(R.id.status);
                textView.setText(transactionTypes[position]);
                return view;
            }
        };
        statusSpinner.setAdapter(adapter);
    }

    public void OwedSize(int owedNum) {
        hideLoading();
        noOwedTextView.setVisibility(owedNum == 0 ? View.VISIBLE : View.GONE);
        owedRecyclerList.setVisibility(owedNum == 0 ? View.GONE : View.VISIBLE);
        noDebtTextView.setVisibility(View.GONE);
    }

    public void DebtSize(int debtNum) {
        hideLoading();

        // Only show debtRecyclerList when not in payment mode
        if (!isPaymentMode) {
            noDebtTextView.setVisibility(debtNum == 0 ? View.VISIBLE : View.GONE);
            debtRecyclerList.setVisibility(debtNum == 0 ? View.GONE : View.VISIBLE);
        } else {
            // In payment mode, keep debtRecyclerList hidden
            noDebtTextView.setVisibility(View.GONE);
            debtRecyclerList.setVisibility(View.GONE);
        }
        noOwedTextView.setVisibility(View.GONE);

        // Update payable debts count and button state
        payableDebtsCount = debtNum;
        updatePayNowButtonState();
    }

    /**
     * Update the Pay Now button state based on whether there are payable debts
     */
    private void updatePayNowButtonState() {
        if (payableDebtsCount > 0) {
            payNowBtn.setEnabled(true);
            payNowBtn.setAlpha(1.0f);
        } else {
            payNowBtn.setEnabled(false);
            payNowBtn.setAlpha(0.5f);
        }
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

    private void showCheckedTransactionsDialog(ArrayList<BorrowTransaction> checkedTransactions) {
        Dialog dialog = new Dialog(getContext());
        dialog.setContentView(R.layout.dialog_topaychecked_transaction);
        dialog.setCancelable(false); // Prevent accidental dismissal

        RecyclerView recyclerView = dialog.findViewById(R.id.checkedTransactionsRecycler);
        CheckedTransactionsAdapter adapter = new CheckedTransactionsAdapter(checkedTransactions);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        // Calculate total amount with proper formatting
        double totalAmount = 0;
        for (BorrowTransaction transaction : checkedTransactions) {
            try {
                String amountStr = transaction.getBorrowedAmountStr().replace("₱", "").replace(",", "").trim();
                totalAmount += Double.parseDouble(amountStr);
            } catch (NumberFormatException e) {
                Log.e("BorrowFragment", "Error parsing amount: " + transaction.getBorrowedAmountStr());
            }
        }

        TextView totalAmountTextView = dialog.findViewById(R.id.totalAmountTextView);
        DecimalFormat df = new DecimalFormat("#,##0.00");
        totalAmountTextView.setText("₱ " + df.format(totalAmount));

        Button closeButton = dialog.findViewById(R.id.closeButton);
        Button payNowConfirmBtn = dialog.findViewById(R.id.payNowConfirmBtn);

        closeButton.setOnClickListener(v -> dialog.dismiss());

        // Handle Pay Now confirmation
        payNowConfirmBtn.setOnClickListener(v -> {
            payNowConfirmBtn.setEnabled(false);
            closeButton.setEnabled(false);
            payNowConfirmBtn.setText(R.string.btn_processing);

            processPayments(checkedTransactions, dialog);
        });

        dialog.show();
    }

    /**
     * Process payments for selected debt transactions
     * Updates Firebase database to mark debts as "Pending Payment"
     */
    private void processPayments(ArrayList<BorrowTransaction> transactions, Dialog dialog) {
        if (transactions.isEmpty()) {
            dialog.dismiss();
            return;
        }

        // Save current spinner positions before processing
        final int savedMonthPosition = monthYearSpinner.getSelectedItemPosition();
        final int savedStatusPosition = statusSpinner.getSelectedItemPosition();

        DatabaseReference borrowsRef = DeclareDatabase.getDBRefBorrows();
        String currentUserId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
        final int[] processedCount = {0};
        final int totalCount = transactions.size();

        for (BorrowTransaction transaction : transactions) {
            // Find and update the transaction in Firebase
            borrowsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    boolean found = false;

                    for (DataSnapshot monthSnapshot : dataSnapshot.getChildren()) {
                        if (found) break;
                        for (DataSnapshot daySnapshot : monthSnapshot.getChildren()) {
                            if (found) break;
                            for (DataSnapshot borrowSnapshot : daySnapshot.getChildren()) {
                                if (found) break;

                                // Try new structure first: borrows/{month}/{day}/{borrowId}
                                BorrowNowTransaction borrowNowTransaction = borrowSnapshot.getValue(BorrowNowTransaction.class);

                                if (borrowNowTransaction != null && borrowNowTransaction.getBorrowerID() != null) {
                                    // New UID-based structure
                                    if (Objects.equals(borrowNowTransaction.getBorrowerID(), currentUserId) &&
                                        Objects.equals(borrowNowTransaction.getDate(), transaction.getDate()) &&
                                        Objects.equals(borrowNowTransaction.getBorrowedAmountStr(), transaction.getBorrowedAmountStr())) {

                                        // Check lender matches (by name or display name)
                                        String transactionBorrowee = transaction.getBorrowee();
                                        if (Objects.equals(borrowNowTransaction.getLender(), transactionBorrowee) ||
                                            Objects.equals(borrowNowTransaction.getLenderID(), transactionBorrowee)) {

                                            // Update status to "Pending Payment"
                                            borrowSnapshot.getRef().child("status").setValue("Pending Payment");
                                            found = true;
                                        }
                                    }
                                } else {
                                    // Legacy structure: borrows/{month}/{day}/{username}/{time}
                                    if (Objects.equals(borrowSnapshot.getKey(), currentNickname)) {
                                        for (DataSnapshot transSnapshot : borrowSnapshot.getChildren()) {
                                            try {
                                                BorrowTransaction dbTransaction = transSnapshot.getValue(BorrowTransaction.class);
                                                if (dbTransaction != null &&
                                                    Objects.equals(dbTransaction.getDate(), transaction.getDate()) &&
                                                    Objects.equals(dbTransaction.getBorrowee(), transaction.getBorrowee()) &&
                                                    Objects.equals(dbTransaction.getBorrowedAmountStr(), transaction.getBorrowedAmountStr())) {

                                                    // Update status to "Pending Payment"
                                                    transSnapshot.getRef().child("status").setValue("Pending Payment");
                                                    found = true;
                                                    break;
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

                    processedCount[0]++;
                    if (processedCount[0] >= totalCount) {
                        // All transactions processed
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                dialog.dismiss();
                                showToast(getString(R.string.toast_payment_sent));

                                // Reset the view state
                                handleCancelPayClick();

                                // Restore spinner positions after resetting
                                if (savedMonthPosition < monthYearSpinner.getCount()) {
                                    monthYearSpinner.setSelection(savedMonthPosition);
                                }
                                if (savedStatusPosition < statusSpinner.getCount()) {
                                    statusSpinner.setSelection(savedStatusPosition);
                                }

                                // Refresh the debt list
                                applyFilters();
                            });
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.e("BorrowFragment", "Payment processing error: " + databaseError.getMessage());
                    processedCount[0]++;
                    if (processedCount[0] >= totalCount) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                dialog.dismiss();
                                showToast(getString(R.string.toast_payment_failed));
                            });
                        }
                    }
                }
            });
        }
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

    @Override
    public void onResume() {
        super.onResume();
        // Refresh data when returning to this fragment
        applyFilters();
    }
}

