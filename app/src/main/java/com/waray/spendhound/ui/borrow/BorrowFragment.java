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
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SnapHelper;

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
import com.waray.spendhound.LenderAdapter;
import com.waray.spendhound.MainActivity;
import com.waray.spendhound.OwedTransaction;
import com.waray.spendhound.OwedTransactionAdapter;
import com.waray.spendhound.R;
import com.waray.spendhound.SpinnerItemMonths;
import com.waray.spendhound.User;
import com.waray.spendhound.UserBalance;

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

    private View globalLoadingOverlay;

    private String selectedLenderName = "";

    @SuppressLint("MissingInflatedId")
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_borrow, container, false);
        monthYearSpinner = view.findViewById(R.id.monthYearSpinner);
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

        globalLoadingOverlay = getActivity().findViewById(R.id.loadingOverlay);

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
                            BorrowNowTransaction borrowNowTransaction = borrowSnapshot.getValue(BorrowNowTransaction.class);

                            if (borrowNowTransaction != null && borrowNowTransaction.getBorrowerID() != null) {
                                if (Objects.equals(borrowNowTransaction.getBorrowerID(), currentUserId)) {
                                    debtUniqueMonthYear.add(monthYear);
                                }
                            } else {
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
                            BorrowNowTransaction borrowNowTransaction = borrowSnapshot.getValue(BorrowNowTransaction.class);

                            if (borrowNowTransaction != null && borrowNowTransaction.getLenderID() != null) {
                                if (Objects.equals(borrowNowTransaction.getLenderID(), currentUserId)) {
                                    owedUniqueMonthYear.add(monthYear);
                                }
                            } else {
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

    public void updateTransactionStatus(String borrowId, String monthYear, String day, String newStatus, Runnable onSuccess) {
        DatabaseReference borrowRef = DeclareDatabase.getDBRefBorrows()
                .child(monthYear)
                .child(day)
                .child(borrowId);

        if ("Paid".equals(newStatus)) {
            borrowRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    BorrowNowTransaction borrow = dataSnapshot.getValue(BorrowNowTransaction.class);
                    if (borrow != null && !("Paid".equals(borrow.getStatus()))) {
                        try {
                            int amount = Integer.parseInt(borrow.getBorrowedAmountStr());
                            String borrowerID = borrow.getBorrowerID();
                            String lenderID = borrow.getLenderID();

                            if (borrowerID != null) {
                                BalanceHelper.updateTotaldebt(borrowerID, -amount, null);
                            }
                            if (lenderID != null) {
                                BalanceHelper.updateTotalreceivable(lenderID, -amount, null);
                            }
                        } catch (NumberFormatException e) {
                            Log.e("BorrowFragment", "Error parsing borrow amount: " + e.getMessage());
                        }
                    }

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

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e("BorrowFragment", "Failed to read borrow data: " + error.getMessage());
                    showToast(getString(R.string.toast_status_update_failed));
                }
            });
        } else {
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
    }

    public void updateTransactionStatusWithPaymentDate(String borrowId, String monthYear, String day, String newStatus, Runnable onSuccess) {
        DatabaseReference borrowRef = DeclareDatabase.getDBRefBorrows()
                .child(monthYear)
                .child(day)
                .child(borrowId);

        long paymentSentDate = System.currentTimeMillis();

        if ("Paid".equals(newStatus)) {
            borrowRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    BorrowNowTransaction borrow = dataSnapshot.getValue(BorrowNowTransaction.class);
                    if (borrow != null && !("Paid".equals(borrow.getStatus()))) {
                        try {
                            int amount = Integer.parseInt(borrow.getBorrowedAmountStr());
                            String borrowerID = borrow.getBorrowerID();
                            String lenderID = borrow.getLenderID();

                            if (borrowerID != null) {
                                BalanceHelper.updateTotaldebt(borrowerID, -amount, null);
                            }
                            if (lenderID != null) {
                                BalanceHelper.updateTotalreceivable(lenderID, -amount, null);
                            }
                        } catch (NumberFormatException e) {
                            Log.e("BorrowFragment", "Error parsing borrow amount: " + e.getMessage());
                        }
                    }

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

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e("BorrowFragment", "Failed to read borrow data: " + error.getMessage());
                    showToast(getString(R.string.toast_status_update_failed));
                }
            });
        } else {
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
    }

    private void showBorrowNowDialog() {
        Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_borrow_now);
        dialog.setCancelable(false);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.WHITE));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView dateTV = dialog.findViewById(R.id.dialogBorrowDate);
        TextView borrowerTV = dialog.findViewById(R.id.dialogBorrower);
        RecyclerView lenderRecyclerView = dialog.findViewById(R.id.lenderRecyclerView);
        EditText amountEditText = dialog.findViewById(R.id.dialogBorrowEditText);
        Button cancelBtn = dialog.findViewById(R.id.dialogCancelBtn);
        Button borrowBtn = dialog.findViewById(R.id.dialogBorrowBtn);
        View dialogProgressBar = dialog.findViewById(R.id.dialogProgressBar);

        dialogProgressBar.setVisibility(View.VISIBLE);

        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM-dd-yyyy", Locale.getDefault());
        String currentDate = dateFormat.format(calendar.getTime());
        dateTV.setText(currentDate);

        borrowerTV.setText(currentNickname);

        setupLenderRecyclerView(lenderRecyclerView, dialogProgressBar);

        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        borrowBtn.setOnClickListener(v -> {
            String amountStr = amountEditText.getText().toString().trim();
            
            if (amountStr.isEmpty() || selectedLenderName.isEmpty()) {
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

            borrowBtn.setEnabled(false);
            cancelBtn.setEnabled(false);
            dialogProgressBar.setVisibility(View.VISIBLE);
            showGlobalLoading();

            addBorrowTransaction(selectedLenderName, String.valueOf(amount), currentDate, dialog, dialogProgressBar, borrowBtn, cancelBtn);
        });

        dialog.show();
    }

    private void setupLenderRecyclerView(RecyclerView recyclerView, View dialogProgressBar) {
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false);
        recyclerView.setLayoutManager(layoutManager);
        
        List<User> lenders = new ArrayList<>();
        LenderAdapter adapter = new LenderAdapter(lenders);
        recyclerView.setAdapter(adapter);

        SnapHelper snapHelper = new LinearSnapHelper();
        snapHelper.attachToRecyclerView(recyclerView);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                updateLayoutEffect(recyclerView);
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    View centerView = snapHelper.findSnapView(layoutManager);
                    if (centerView != null) {
                        int pos = layoutManager.getPosition(centerView);
                        User selectedLender = adapter.getLenderAt(pos);
                        if (selectedLender != null) {
                            selectedLenderName = selectedLender.getUsername();
                        }
                    }
                }
            }
        });

        loadLenders(adapter, lenders, recyclerView, dialogProgressBar);
    }

    private void updateLayoutEffect(RecyclerView recyclerView) {
        float midpoint = recyclerView.getWidth() / 2f;
        float d0 = 0f;
        float d1 = 0.9f * midpoint;
        float s0 = 1.6f;
        float s1 = 1.0f;
        float a0 = 1.0f;
        float a1 = 0.5f;

        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            float childMidpoint = (recyclerView.getLayoutManager().getDecoratedRight(child) + recyclerView.getLayoutManager().getDecoratedLeft(child)) / 2f;
            float d = Math.min(d1, Math.abs(midpoint - childMidpoint));
            float scale = s0 + (s1 - s0) * (d - d0) / (d1 - d0);
            float alpha = a0 + (a1 - a0) * (d - d0) / (d1 - d0);
            child.setScaleX(scale);
            child.setScaleY(scale);
            child.setAlpha(alpha);
        }
    }

    private void loadLenders(LenderAdapter adapter, List<User> lenders, RecyclerView recyclerView, View dialogProgressBar) {
        DatabaseReference databaseReference = DeclareDatabase.getDatabaseReference();
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                lenders.clear();
                lenders.add(new User("", "", "", "", new UserBalance()));
                lenders.add(new User("", "", "", "", new UserBalance()));

                for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                    User user = userSnapshot.getValue(User.class);
                    if (user != null && user.getUsername() != null && !user.getUsername().equals(currentNickname)) {
                        user.setUid(userSnapshot.getKey());
                        lenders.add(user);
                    }
                }

                lenders.add(new User("", "", "", "", new UserBalance()));
                lenders.add(new User("", "", "", "", new UserBalance()));

                adapter.notifyDataSetChanged();
                
                adapter.preloadAllImages(getContext(), () -> {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            dialogProgressBar.setVisibility(View.GONE);
                            if (lenders.size() > 2) {
                                recyclerView.scrollToPosition(2);
                                recyclerView.post(() -> {
                                    User firstUser = adapter.getLenderAt(2);
                                    if (firstUser != null) {
                                        selectedLenderName = firstUser.getUsername();
                                    }
                                    updateLayoutEffect(recyclerView);
                                });
                            }
                        });
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("BorrowFragment", "Database error: " + databaseError.getMessage());
                dialogProgressBar.setVisibility(View.GONE);
            }
        });
    }

    private void addBorrowTransaction(String lender, String borrowedAmountStr, String currentDate,
            Dialog dialog, View dialogProgressBar, Button borrowBtn, Button cancelBtn) {

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
            dialogProgressBar.setVisibility(View.GONE);
            borrowBtn.setEnabled(true);
            cancelBtn.setEnabled(true);
            hideGlobalLoading();
            return;
        }

        DatabaseReference borrowRef = dayRef.child(borrowId);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            String borrowerID = currentUser.getUid();

            getUserIDByName(lender, lenderID -> {
                if (lenderID == null) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            showToast(getString(R.string.toast_borrow_failed));
                            dialogProgressBar.setVisibility(View.GONE);
                            borrowBtn.setEnabled(true);
                            cancelBtn.setEnabled(true);
                            hideGlobalLoading();
                        });
                    }
                    return;
                }

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
                    BalanceHelper.addBorrowerEntry(borrowerID, borrowId, null);
                    BalanceHelper.addLenderEntry(lenderID, borrowId, null);

                    int amount = Integer.parseInt(borrowedAmountStr);
                    BalanceHelper.updateTotaldebt(borrowerID, amount, null);
                    BalanceHelper.updateTotalreceivable(lenderID, amount, null);

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
                            dialogProgressBar.setVisibility(View.GONE);
                            borrowBtn.setEnabled(true);
                            cancelBtn.setEnabled(true);
                            hideGlobalLoading();
                        });
                    }
                });
            });
        }
    }

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

    private interface UserIDCallback {
        void onUserIDRetrieved(String userID);
    }


    public void showToast(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    private void showLoading() {
        isLoading = true;
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.VISIBLE);
        }
    }

    private void hideLoading() {
        isLoading = false;
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.GONE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        applyFilters();
    }

    private void showGlobalLoading() {
        if (globalLoadingOverlay != null) {
            globalLoadingOverlay.setVisibility(View.VISIBLE);
        }
    }

    private void hideGlobalLoading() {
        if (globalLoadingOverlay != null) {
            globalLoadingOverlay.setVisibility(View.GONE);
        }
    }
}
