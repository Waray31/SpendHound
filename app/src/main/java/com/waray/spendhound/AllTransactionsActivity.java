package com.waray.spendhound;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
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

public class AllTransactionsActivity extends AppCompatActivity {

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

    // Status Tabs
    private TextView allTabTV, paidTabTV, unpaidTabTV, pendingTabTV;
    private String selectedStatusTab = "All";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_transactions);

        mAuth = DeclareDatabase.getAuth();
        transactionList = new ArrayList<>();
        availableMonths = new ArrayList<>();

        initViews();
        getCurrentNickname();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.allTransactionsRecyclerView);
        monthSpinner = findViewById(R.id.monthSpinner);
        currentMonthTextView = findViewById(R.id.currentMonthTextView);
        transactionCountTextView = findViewById(R.id.transactionCountTextView);
        loadingProgressBar = findViewById(R.id.loadingProgressBar);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);

        // Status Tabs
        allTabTV = findViewById(R.id.allTabTV);
        paidTabTV = findViewById(R.id.paidTabTV);
        unpaidTabTV = findViewById(R.id.unpaidTabTV);
        pendingTabTV = findViewById(R.id.pendingTabTV);

        setupStatusTabs();

        adapter = new RecentTransactionAdapter(transactionList, this::onTransactionTap);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void setupStatusTabs() {
        setStatusTabSelected(allTabTV);

        allTabTV.setOnClickListener(v -> {
            selectedStatusTab = "All";
            setStatusTabSelected(allTabTV);
            refreshTransactions();
        });

        paidTabTV.setOnClickListener(v -> {
            selectedStatusTab = "Paid";
            setStatusTabSelected(paidTabTV);
            refreshTransactions();
        });

        unpaidTabTV.setOnClickListener(v -> {
            selectedStatusTab = "Unpaid";
            setStatusTabSelected(unpaidTabTV);
            refreshTransactions();
        });

        pendingTabTV.setOnClickListener(v -> {
            selectedStatusTab = "Pending";
            setStatusTabSelected(pendingTabTV);
            refreshTransactions();
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

    private void refreshTransactions() {
        if (selectedMonth != null) {
            fetchTransactionsForMonth(selectedMonth);
        }
    }

    private void onTransactionTap(RecentTransaction transaction) {
        // Handle tap if needed
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
            Calendar calendar = Calendar.getInstance();
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM-yyyy", Locale.getDefault());
            availableMonths.add(dateFormat.format(calendar.getTime()));
        }

        SpinnerItemMonths spinnerAdapter = new SpinnerItemMonths(this, availableMonths);
        monthSpinner.setAdapter(spinnerAdapter);

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
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void updateMonthDisplay(String monthYear) {
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
                            
                            // Apply Status Filter
                            if (!matchesStatusFilter(transaction, selectedStatusTab)) {
                                continue;
                            }

                            String[] parts = monthYear.split("-");
                            String month = parts[0];
                            String year = parts.length > 1 ? parts[1] : "";
                            String displayDate = month + " - " + day;
                            String fullDateWithYear = month + " " + day + ", " + year;
                            String sortDateTime = year + "-" + month + "-" + day + " " + timeKey;

                            String transactionType = transaction.getTransactionType();
                            String details = transaction.getMultilineStr();
                            double paymentAmount = transaction.getPaymentAmount();
                            String paymentAmountStr = CurrencyUtils.formatAmountWithCurrency(paymentAmount);
                            int iconResource = getIconForTransactionType(transactionType);

                            java.util.List<String> payorsList = transaction.getPayorsDisplayNames();
                            if (payorsList == null || payorsList.isEmpty()) {
                                payorsList = transaction.getPayorsList();
                            }
                            java.util.List<String> payorUids = transaction.getPayorsList();
                            java.util.List<Double> amountsPaidList = transaction.getAmountsPaidList();
                            double totalIndividualPayment = transaction.getTotalIndividualPayment();

                            String createdBy = transaction.getPosterDisplayName();
                            if (createdBy == null || createdBy.isEmpty()) {
                                createdBy = transaction.getUsernamePost();
                            }
                            String createdByUid = transaction.getUsernamePost();

                            RecentTransaction recentTrans = new RecentTransaction(
                                    displayDate, transactionType, details, paymentAmountStr,
                                    iconResource, sortDateTime, payorsList, payorUids,
                                    amountsPaidList, totalIndividualPayment, fullDateWithYear,
                                    createdBy, createdByUid, monthYear, day, timeKey
                            );
                            transactionList.add(recentTrans);
                        }
                    }
                }

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

                int count = transactionList.size();
                transactionCountTextView.setText(count + (count == 1 ? " transaction" : " transactions"));

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

    private boolean matchesStatusFilter(Transaction transaction, String statusFilter) {
        if ("All".equalsIgnoreCase(statusFilter)) return true;

        List<Double> paidAmounts = transaction.getAmountsPaidList();
        double totalToPay = transaction.getTotalIndividualPayment();

        if (paidAmounts == null || paidAmounts.isEmpty()) {
            return "Unpaid".equalsIgnoreCase(statusFilter);
        }

        boolean allPaid = true;
        boolean allUnpaid = true;

        for (Double paid : paidAmounts) {
            if (paid < totalToPay) {
                allPaid = false;
            }
            if (paid > 0) {
                allUnpaid = false;
            }
        }

        String status;
        if (allPaid) {
            status = "Paid";
        } else if (allUnpaid) {
            status = "Unpaid";
        } else {
            status = "Pending";
        }

        return status.equalsIgnoreCase(statusFilter);
    }

    private int getIconForTransactionType(String transactionType) {
        if ("Electricity".equals(transactionType)) return R.drawable.lightning_bolt;
        if ("Water".equals(transactionType)) return R.drawable.faucet;
        if ("Rent".equals(transactionType)) return R.drawable.house;
        if ("Internet".equals(transactionType)) return R.drawable.internet;
        if ("Online Shopping".equals(transactionType)) return R.drawable.online_shopping;
        if ("Travel".equals(transactionType)) return R.drawable.travel;
        if ("Groceries".equals(transactionType)) return R.drawable.groceries;
        if ("Foods".equals(transactionType)) return R.drawable.hamburger;
        if ("House Necessity".equals(transactionType)) return R.drawable.necessities;
        if ("Transportation".equals(transactionType)) return R.drawable.vehicles;
        return R.drawable.others;
    }

    private boolean isUserInvolved(Transaction transaction, String usernameOrUid) {
        if (transaction == null || usernameOrUid == null || usernameOrUid.isEmpty()) return false;
        String currentUid = Objects.requireNonNull(mAuth.getCurrentUser()).getUid();
        if (transaction.isUserInvolvedByUid(currentUid)) return true;
        if (usernameOrUid.equals(transaction.getUsernamePost())) return true;
        if (usernameOrUid.equals(transaction.getPosterDisplayName())) return true;
        java.util.List<String> payorsList = transaction.getPayorsList();
        if (payorsList != null && payorsList.contains(usernameOrUid)) return true;
        java.util.List<String> payorsDisplayNames = transaction.getPayorsDisplayNames();
        if (payorsDisplayNames != null && payorsDisplayNames.contains(usernameOrUid)) return true;
        return false;
    }
}
