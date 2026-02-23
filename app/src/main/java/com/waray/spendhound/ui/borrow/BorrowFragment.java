package com.waray.spendhound.ui.borrow;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.waray.spendhound.BorrowNowActivity;
import com.waray.spendhound.BorrowTransaction;
import com.waray.spendhound.BorrowTransactionAdapter;
import com.waray.spendhound.CheckedTransactionsAdapter;
import com.waray.spendhound.DeclareDatabase;
import com.waray.spendhound.MainActivity;
import com.waray.spendhound.PendingStatusActivity;
import com.waray.spendhound.R;
import com.waray.spendhound.SpinnerItemMonths;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
        borrowNowBtn.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), BorrowNowActivity.class);
            startActivity(intent);
        });

        owedTV.setOnClickListener(v -> handleOwedClick());
        debtTV.setOnClickListener(v -> handleDebtClick());
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
        selectAllLayout.setVisibility(View.VISIBLE);
        debtSelectBtn.setVisibility(View.VISIBLE);
        debtCancelPayBtn.setVisibility(View.VISIBLE);
        payNowBtn.setVisibility(View.GONE);
        debtRecyclerList.setVisibility(View.GONE);
        debtCheckboxRecyclerList.setVisibility(View.VISIBLE);
        borrowNowBtn.setVisibility(View.GONE);
        CheckboxStatus();
    }

    private void handleCancelPayClick() {
        selectAllLayout.setVisibility(View.GONE);
        debtSelectBtn.setVisibility(View.GONE);
        debtCancelPayBtn.setVisibility(View.GONE);
        payNowBtn.setVisibility(View.VISIBLE);
        debtRecyclerList.setVisibility(View.VISIBLE);
        debtCheckboxRecyclerList.setVisibility(View.GONE);
        borrowNowBtn.setVisibility(View.VISIBLE);
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
        transRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                debtUniqueMonthYear.add("All");
                for (DataSnapshot monthSnapshot : dataSnapshot.getChildren()) {
                    String monthYear = monthSnapshot.getKey();
                    for (DataSnapshot daySnapshot : monthSnapshot.getChildren()) {
                        for (DataSnapshot currentUserRef : daySnapshot.getChildren()) {
                            if (Objects.equals(currentUserRef.getKey(), currentNickname)) {
                                debtUniqueMonthYear.add(monthYear);
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
        transRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                owedUniqueMonthYear.add("All");
                for (DataSnapshot monthSnapshot : dataSnapshot.getChildren()) {
                    String monthYear = monthSnapshot.getKey();
                    for (DataSnapshot daySnapshot : monthSnapshot.getChildren()) {
                        for (DataSnapshot currentUserRef : daySnapshot.getChildren()) {
                            if (!Objects.equals(currentUserRef.getKey(), currentNickname)) {
                                for (DataSnapshot timeSnapshot : currentUserRef.getChildren()) {
                                    BorrowTransaction borrowTransaction = timeSnapshot.getValue(BorrowTransaction.class);
                                    if (borrowTransaction != null && Objects.equals(borrowTransaction.getBorrowee(), currentNickname)) {
                                        owedUniqueMonthYear.add(monthYear);
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
        noDebtTextView.setVisibility(debtNum == 0 ? View.VISIBLE : View.GONE);
        debtRecyclerList.setVisibility(debtNum == 0 ? View.GONE : View.VISIBLE);
        noOwedTextView.setVisibility(View.GONE);
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
     * Updates Firebase database to mark debts as "Pending" (awaiting lender confirmation)
     */
    private void processPayments(ArrayList<BorrowTransaction> transactions, Dialog dialog) {
        if (transactions.isEmpty()) {
            dialog.dismiss();
            return;
        }

        DatabaseReference borrowsRef = DeclareDatabase.getDBRefBorrows();
        final int[] processedCount = {0};
        final int totalCount = transactions.size();

        for (BorrowTransaction transaction : transactions) {
            // Find and update the transaction in Firebase
            borrowsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    for (DataSnapshot monthSnapshot : dataSnapshot.getChildren()) {
                        for (DataSnapshot daySnapshot : monthSnapshot.getChildren()) {
                            for (DataSnapshot userSnapshot : daySnapshot.getChildren()) {
                                if (Objects.equals(userSnapshot.getKey(), currentNickname)) {
                                    for (DataSnapshot transSnapshot : userSnapshot.getChildren()) {
                                        BorrowTransaction dbTransaction = transSnapshot.getValue(BorrowTransaction.class);
                                        if (dbTransaction != null &&
                                            Objects.equals(dbTransaction.getDate(), transaction.getDate()) &&
                                            Objects.equals(dbTransaction.getBorrowee(), transaction.getBorrowee()) &&
                                            Objects.equals(dbTransaction.getBorrowedAmountStr(), transaction.getBorrowedAmountStr())) {

                                            // Update status to "Pending" (awaiting lender confirmation)
                                            transSnapshot.getRef().child("status").setValue("Pending");
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

