package com.waray.spendhound.ui.transactions;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.waray.spendhound.DeclareDatabase;
import com.waray.spendhound.R;
import com.waray.spendhound.RecentTransaction;
import com.waray.spendhound.RecentTransactionAdapter;
import com.waray.spendhound.SpinnerItemMonths;
import com.waray.spendhound.Transaction;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class TransactionsFragment extends Fragment {

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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.activity_all_transactions, container, false);

        mAuth = DeclareDatabase.getAuth();
        transactionList = new ArrayList<>();
        availableMonths = new ArrayList<>();

        initViews(root);
        
        getCurrentNickname();

        return root;
    }

    private void initViews(View root) {
        recyclerView = root.findViewById(R.id.allTransactionsRecyclerView);
        monthSpinner = root.findViewById(R.id.monthSpinner);
        currentMonthTextView = root.findViewById(R.id.currentMonthTextView);
        transactionCountTextView = root.findViewById(R.id.transactionCountTextView);
        loadingProgressBar = root.findViewById(R.id.loadingProgressBar);
        emptyStateLayout = root.findViewById(R.id.emptyStateLayout);

        adapter = new RecentTransactionAdapter(transactionList, null);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
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
                loadAvailableMonths();
            }
        });
    }

    private void loadAvailableMonths() {
        if (loadingProgressBar != null) loadingProgressBar.setVisibility(View.VISIBLE);

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
                if (loadingProgressBar != null) loadingProgressBar.setVisibility(View.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (loadingProgressBar != null) loadingProgressBar.setVisibility(View.GONE);
            }
        });
    }

    private void setupMonthSpinner() {
        if (getContext() == null) return;

        if (availableMonths.isEmpty()) {
            Calendar calendar = Calendar.getInstance();
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM-yyyy", Locale.getDefault());
            availableMonths.add(dateFormat.format(calendar.getTime()));
        }

        SpinnerItemMonths spinnerAdapter = new SpinnerItemMonths(getContext(), availableMonths);
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
        if (loadingProgressBar != null) loadingProgressBar.setVisibility(View.VISIBLE);
        if (emptyStateLayout != null) emptyStateLayout.setVisibility(View.GONE);
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
                            String fullDateWithYear = month + " " + day + ", " + year;
                            String sortDateTime = year + "-" + month + "-" + day + " " + timeKey;

                            String transactionType = transaction.getTransactionType();
                            String details = transaction.getMultilineStr();
                            double paymentAmount = transaction.getPaymentAmount();
                            String paymentAmountStr = String.format(Locale.getDefault(), "₱ %.2f", paymentAmount);
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
                if (loadingProgressBar != null) loadingProgressBar.setVisibility(View.GONE);

                int count = transactionList.size();
                transactionCountTextView.setText(count + (count == 1 ? " transaction" : " transactions"));

                if (transactionList.isEmpty()) {
                    if (emptyStateLayout != null) emptyStateLayout.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    if (emptyStateLayout != null) emptyStateLayout.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (loadingProgressBar != null) loadingProgressBar.setVisibility(View.GONE);
            }
        });
    }

    private int getIconForTransactionType(String transactionType) {
        if ("Electricity".equals(transactionType)) return R.drawable.lightning_bolt;
        else if ("Water".equals(transactionType)) return R.drawable.faucet;
        else if ("Rent".equals(transactionType)) return R.drawable.house;
        else if ("Internet".equals(transactionType)) return R.drawable.internet;
        else if ("Online Shopping".equals(transactionType)) return R.drawable.online_shopping;
        else if ("Travel".equals(transactionType)) return R.drawable.travel;
        else if ("Groceries".equals(transactionType)) return R.drawable.groceries;
        else if ("Foods".equals(transactionType)) return R.drawable.hamburger;
        else if ("House Necessity".equals(transactionType)) return R.drawable.necessities;
        else if ("Transportation".equals(transactionType)) return R.drawable.vehicles;
        else return R.drawable.others;
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
        return payorsDisplayNames != null && payorsDisplayNames.contains(usernameOrUid);
    }
}
