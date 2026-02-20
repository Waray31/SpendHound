package com.waray.spendhound;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.waray.spendhound.databinding.ActivityMainBinding;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {

    public BottomNavigationView navView;
    public FirebaseAuth mAuth;
    public int totalMonthSpends;
    private ProgressBar progressBar;
    public String currentNickname = "";
    public int dailySpend, owedNum, debtNum;
    private ArrayList<RecentTransaction> recentTransactionList = new ArrayList<>();
    public ArrayList<BorrowTransaction> debtList = new ArrayList<>();
    public ArrayList<OwedTransaction> owedList = new ArrayList<>();
    private RecentTransactionAdapter recentTransactionAdapter;

    public interface OwedNumCallback {
        void onOwedNumReceived(int owedNum);
    }
    public interface DebtNumCallback {
        void onDebtNumReceived(int debtNum);
    }
    public interface CurrentNicknameCallback {
        void onCurrentNicknameReceived(String CurrentNickname);
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        progressBar = findViewById(R.id.progressBar);
        progressBar.setVisibility(View.VISIBLE);

        mAuth = DeclareDatabase.getAuth();
        getCurrentNickname(new CurrentNicknameCallback() {
            @Override
            public void onCurrentNicknameReceived(String CurrentNickname) {

            }
        });

        navView = findViewById(R.id.navView);

        // Setup Recent Transactions RecyclerView
        RecyclerView recyclerView = findViewById(R.id.transactionListRecycler);
        recentTransactionList = new ArrayList<>();
        recentTransactionAdapter = new RecentTransactionAdapter(recentTransactionList);
        recyclerView.setAdapter(recentTransactionAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_borrow, R.id.navigation_profile)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupWithNavController(navView, navController);


        progressBar.setVisibility(View.GONE);
    }

    public void getTotalMonthSpends() {
        // Create a reference to the "transactions" node
        DatabaseReference databaseReference = DeclareDatabase.getDBRefTransaction();

        // Get the current month in the format "MMMM-yyyy" (e.g., "September-2023")
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM-yyyy", Locale.getDefault());
        String currentMonthYear = dateFormat.format(calendar.getTime());

        // Create a child with the format "YYYY-MM" (year-month)
        DatabaseReference monthYearRef = databaseReference.child(currentMonthYear);

        totalMonthSpends = 0; // Initialize the totalMonthSpends

        // Add a listener to retrieve data for the entire month
        monthYearRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot daySnapshot : dataSnapshot.getChildren()) {
                    for (DataSnapshot timeSnapshot : daySnapshot.getChildren()) {
                        Transaction transaction = timeSnapshot.getValue(Transaction.class);
                        if (transaction != null) {
                            // Retrieve the paymentAmount from the transaction
                            int paymentAmount = transaction.getPaymentAmount();

                            // Add the paymentAmount to totalMonthSpends
                            totalMonthSpends += paymentAmount;
                        }
                    }
                }

                // Now, totalMonthSpends contains the sum of all paymentAmounts in the current month
                // You can use it as needed, for example, update a TextView with this value
                String totalMonthSpendsString = String.valueOf(totalMonthSpends);
                TextView totalMonthSpendsTextView = findViewById(R.id.totalMonthSpends);
                totalMonthSpendsTextView.setText("₱ " + totalMonthSpendsString + ".00");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // Handle database read error
                String errorMessage = "Database read error occurred: " + databaseError.getMessage();
                Log.e("FirebaseDatabase", errorMessage);
            }
        });
    }

    @SuppressLint("DefaultLocale")
    public void getEverydaySpends() {
        int[] dailySpends = new int[7];
        Calendar calendar = Calendar.getInstance();
        // Set to beginning of current week (Sunday)
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM-yyyy", Locale.getDefault());
        SimpleDateFormat dayFormat = new SimpleDateFormat("dd", Locale.getDefault());

        // Iterate from Sunday (i=0) to Saturday (i=6)
        for (int i = 0; i < 7; i++) {
            String currentMonthYear = monthFormat.format(calendar.getTime());
            String currentDay = dayFormat.format(calendar.getTime());

            DatabaseReference dayRef = DeclareDatabase.getDBRefTransaction().child(currentMonthYear).child(currentDay);

            // dayIndex: 6=Sunday, 5=Monday, 4=Tuesday, 3=Wednesday, 2=Thursday, 1=Friday, 0=Saturday
            final int dayIndex = 6 - i;
            dayRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    int dailySpend = 0;
                    for (DataSnapshot timeSnapshot : dataSnapshot.getChildren()) {
                        Transaction transaction = timeSnapshot.getValue(Transaction.class);
                        if (transaction != null) {
                            dailySpend += transaction.getPaymentAmount();
                        }
                    }
                    dailySpends[dayIndex] = dailySpend;
                    setViewHeightForDay(dayIndex, dailySpends[dayIndex]);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    // Handle database read error
                    String errorMessage = "Database read error occurred: " + databaseError.getMessage();
                    Log.e("FirebaseDatabase", errorMessage);
                }
            });

            calendar.add(Calendar.DAY_OF_YEAR, 1); // Go to next day for week iteration
        }
    }

    @SuppressLint("DefaultLocale")
    public void getEverydaySpendsForWeek(Calendar weekStart) {
        int[] dailySpends = new int[7];
        Calendar calendar = (Calendar) weekStart.clone();
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM-yyyy", Locale.getDefault());
        SimpleDateFormat dayFormat = new SimpleDateFormat("dd", Locale.getDefault());

        for (int i = 0; i < 7; i++) {
            String currentMonthYear = monthFormat.format(calendar.getTime());
            String currentDay = dayFormat.format(calendar.getTime());

            DatabaseReference dayRef = DeclareDatabase.getDBRefTransaction().child(currentMonthYear).child(currentDay);

            final int dayIndex = 6 - i; // Reverse order to match UI (day7 first, day1 last)
            dayRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    int dailySpend = 0;
                    for (DataSnapshot timeSnapshot : dataSnapshot.getChildren()) {
                        Transaction transaction = timeSnapshot.getValue(Transaction.class);
                        if (transaction != null) {
                            dailySpend += transaction.getPaymentAmount();
                        }
                    }
                    dailySpends[dayIndex] = dailySpend;
                    setViewHeightForDay(dayIndex, dailySpends[dayIndex]);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    String errorMessage = "Database read error occurred: " + databaseError.getMessage();
                    Log.e("FirebaseDatabase", errorMessage);
                }
            });

            calendar.add(Calendar.DAY_OF_YEAR, 1); // Go to next day for week iteration
        }
    }

    public void setViewHeightForDay(int day, int dailySpends) {
        int[] dailySpendsArray = new int[7];
        dailySpendsArray[day] = dailySpends;
        String dailySpendString = String.valueOf(dailySpendsArray[day]);
        TextView day1SpendTextView = findViewById(R.id.totalday1);
        TextView day2SpendTextView = findViewById(R.id.totalday2);
        TextView day3SpendTextView = findViewById(R.id.totalday3);
        TextView day4SpendTextView = findViewById(R.id.totalday4);
        TextView day5SpendTextView = findViewById(R.id.totalday5);
        TextView day6SpendTextView = findViewById(R.id.totalday6);
        TextView day7SpendTextView = findViewById(R.id.totalday7);
        int viewId = 0;

        // Mapping: day7=Sunday(index 6), day6=Monday(5), day5=Tuesday(4),
        // day4=Wednesday(3), day3=Thursday(2), day2=Friday(1), day1=Saturday(0)
        switch (day) {
            case 6: // Sunday
                day7SpendTextView.setText(dailySpendString);
                viewId = R.id.day7_bar;
                break;
            case 5: // Monday
                day6SpendTextView.setText(dailySpendString);
                viewId = R.id.day6_bar;
                break;
            case 4: // Tuesday
                day5SpendTextView.setText(dailySpendString);
                viewId = R.id.day5_bar;
                break;
            case 3: // Wednesday
                day4SpendTextView.setText(dailySpendString);
                viewId = R.id.day4_bar;
                break;
            case 2: // Thursday
                day3SpendTextView.setText(dailySpendString);
                viewId = R.id.day3_bar;
                break;
            case 1: // Friday
                day2SpendTextView.setText(dailySpendString);
                viewId = R.id.day2_bar;
                break;
            case 0: // Saturday
                day1SpendTextView.setText(dailySpendString);
                viewId = R.id.day1_bar;
                break;
        }
        // Calculate the desired height based on the daily spend
        int desiredHeightInPixels;
        if (dailySpends >= 1000) {
            desiredHeightInPixels = 300;
        } else if (dailySpends <= 50) {
            desiredHeightInPixels = 17;
        } else {
            desiredHeightInPixels = dailySpends / 3;
        }

        if (viewId != 0) {
            View view = findViewById(viewId);
            ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
            layoutParams.height = desiredHeightInPixels;
            view.setLayoutParams(layoutParams);
        } else {
            showToast("No viewID");
        }
    }

    public void getRecentTransaction() {
        Log.d("RecentTransaction", "getRecentTransaction() called");
        Log.d("RecentTransaction", "recentTransactionList size before clear: " + recentTransactionList.size());
        Log.d("RecentTransaction", "recentTransactionAdapter is null: " + (recentTransactionAdapter == null));

        recentTransactionList.clear();

        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM-yyyy", Locale.getDefault());
        SimpleDateFormat dayFormat = new SimpleDateFormat("dd", Locale.getDefault());

        int daysToFetch = 7;
        AtomicInteger daysFetched = new AtomicInteger(0);

        Log.d("RecentTransaction", "Starting to fetch " + daysToFetch + " days of transactions");

        for (int i = 0; i < daysToFetch; i++) {

            String currentMonthYear = monthFormat.format(calendar.getTime());
            String currentDay = dayFormat.format(calendar.getTime());

            Log.d("RecentTransaction", "Fetching day " + i + ": " + currentMonthYear + "/" + currentDay);

            DatabaseReference dayRef = DeclareDatabase.getDBRefTransaction().child(currentMonthYear).child(currentDay);

            String finalCurrentMonthYear = currentMonthYear;
            String finalCurrentDay = currentDay;
            dayRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    Log.d("RecentTransaction", "onDataChange for " + finalCurrentMonthYear + "/" + finalCurrentDay + ", exists: " + dataSnapshot.exists() + ", children count: " + dataSnapshot.getChildrenCount());
                    for (DataSnapshot timeSnapshot : dataSnapshot.getChildren()) {
                        Transaction transaction = timeSnapshot.getValue(Transaction.class);
                        if (transaction != null) {
                            String timeKey = timeSnapshot.getKey(); // Time in HH:mm:ss format
                            String[] parts = finalCurrentMonthYear.split("-");
                            String finalCurrentMonth = parts[0];
                            String finalCurrentYear = parts[1];
                            String mostRecentDate = finalCurrentMonth + " - " + finalCurrentDay;

                            // Create sortDateTime for proper sorting (format: yyyy-MM-dd HH:mm:ss)
                            String sortDateTime = finalCurrentYear + "-" + finalCurrentMonth + "-" + finalCurrentDay + " " + timeKey;

                            String mostRecentTransactionType = transaction.getTransactionType();
                            String mostRecentDetails = transaction.getMultilineStr();
                            int mostRecentPaymentAmount = transaction.getPaymentAmount();
                            String mostRecentPaymentAmountStr = "₱ " + mostRecentPaymentAmount;
                            int iconResource;

                            if ("Electricity".equals(mostRecentTransactionType)) {
                                iconResource = R.drawable.lightning_bolt;
                            } else if ("Water".equals(mostRecentTransactionType)) {
                                iconResource = R.drawable.faucet;
                            } else if ("Rent".equals(mostRecentTransactionType)) {
                                iconResource = R.drawable.house;
                            }else if ("Internet".equals(mostRecentTransactionType)) {
                                iconResource = R.drawable.internet;
                            }else if ("Online Shopping".equals(mostRecentTransactionType)) {
                                iconResource = R.drawable.online_shopping;
                            }else if ("Travel".equals(mostRecentTransactionType)) {
                                iconResource = R.drawable.travel;
                            } else if ("Groceries".equals(mostRecentTransactionType)) {
                                iconResource = R.drawable.groceries;
                            } else if ("Foods".equals(mostRecentTransactionType)) {
                                iconResource = R.drawable.hamburger;
                            } else if ("House Necessity".equals(mostRecentTransactionType)) {
                                iconResource = R.drawable.necessities;
                            } else if ("Transportation".equals(mostRecentTransactionType)) {
                                iconResource = R.drawable.vehicles;
                            } else {
                                iconResource = R.drawable.others;
                            }
                            RecentTransaction recentTrans = new RecentTransaction(
                                    mostRecentDate,
                                    mostRecentTransactionType,
                                    mostRecentDetails,
                                    mostRecentPaymentAmountStr,
                                    iconResource,
                                    sortDateTime
                            );
                            recentTransactionList.add(recentTrans);
                        }
                    }

                    if (daysFetched.incrementAndGet() == daysToFetch) {
                        Log.d("RecentTransaction", "All days fetched. Total transactions: " + recentTransactionList.size());

                        // Sort transactions in descending order by date and time
                        Collections.sort(recentTransactionList, (t1, t2) -> {
                            String dateTime1 = t1.getSortDateTime();
                            String dateTime2 = t2.getSortDateTime();
                            if (dateTime1 != null && dateTime2 != null) {
                                return dateTime2.compareTo(dateTime1); // Descending order
                            }
                            return 0;
                        });

                        // Always re-attach adapter to ensure it's the current one
                        RecyclerView recyclerView = findViewById(R.id.transactionListRecycler);
                        if (recyclerView != null) {
                            recentTransactionAdapter = new RecentTransactionAdapter(recentTransactionList);
                            recyclerView.setAdapter(recentTransactionAdapter);
                            recyclerView.setLayoutManager(new LinearLayoutManager(MainActivity.this));
                            Log.d("RecentTransaction", "Adapter re-attached. List size: " + recentTransactionList.size());
                        } else {
                            Log.e("RecentTransaction", "RecyclerView is NULL!");
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.e("FirebaseDatabase", "Database read error occurred: " + databaseError.getMessage());
                    if (daysFetched.incrementAndGet() == daysToFetch) {
                        if (recentTransactionAdapter != null) {
                            recentTransactionAdapter.notifyDataSetChanged();
                        }
                    }
                }
            });
            calendar.add(Calendar.DAY_OF_YEAR, -1);
        }
    }

    public void getDebtList(String selectedStatus, DebtNumCallback callback) {
        debtList.clear();

        DatabaseReference databaseReference = DeclareDatabase.getDBRefBorrows();
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {

            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot monthSnapshot : dataSnapshot.getChildren()) {
                    for (DataSnapshot daySnapshot : monthSnapshot.getChildren()) {
                        for (DataSnapshot currentUserRef : daySnapshot.getChildren()) {
                            String currentUserStr = currentUserRef.getKey();
                            if (Objects.equals(currentUserStr, currentNickname)) {
                                for (DataSnapshot timeSnapshot : currentUserRef.getChildren()) {
                                    BorrowTransaction borrowTransaction = timeSnapshot.getValue(BorrowTransaction.class);
                                    if (borrowTransaction != null) {
                                        String status = borrowTransaction.getStatus();
                                        if (!Objects.equals(status, "Pending Approval") && !Objects.equals(status, "Declined")
                                                && !Objects.equals(status, "Payment Pending") && !Objects.equals(status, "Payment Denied")) {
                                            if (Objects.equals("All", selectedStatus)) {
                                                addDebtTransactionToList(borrowTransaction);
                                            } else if (Objects.equals(status, selectedStatus)) {
                                                addDebtTransactionToList(borrowTransaction);
                                            }
                                        }
                                    }

                                    // Sort debtList by date in descending order
                                    Collections.sort(debtList, new Comparator<BorrowTransaction>() {
                                        SimpleDateFormat format = new SimpleDateFormat("MMM-dd-yyyy", Locale.ENGLISH);

                                        @Override
                                        public int compare(BorrowTransaction o1, BorrowTransaction o2) {
                                            try {
                                                Date date1 = format.parse(o1.getDate());
                                                Date date2 = format.parse(o2.getDate());
                                                return date2.compareTo(date1); // For descending order
                                            } catch (ParseException e) {
                                                throw new RuntimeException(e);
                                            }
                                        }
                                    });

                                    // To Show Debt List without Checkbox
                                    RecyclerView recyclerView = findViewById(R.id.debtRecyclerList);
                                    RecyclerView.Adapter<DebtTransactionAdapter.ViewHolder> adapter = new DebtTransactionAdapter(debtList);
                                    recyclerView.setAdapter(adapter);
                                    adapter.notifyDataSetChanged();
                                    RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(MainActivity.this);
                                    recyclerView.setLayoutManager(layoutManager);

                                    // To Show Debt List with Checkbox
                                    RecyclerView recyclerViewCheckBox = findViewById(R.id.debtCheckboxRecyclerList);
                                    RecyclerView.Adapter<BorrowTransactionAdapter.ViewHolder> adapterCheckbox = new BorrowTransactionAdapter(debtList);
                                    recyclerViewCheckBox.setAdapter(adapterCheckbox);
                                    adapterCheckbox.notifyDataSetChanged();
                                    RecyclerView.LayoutManager layoutManagerCheckbox = new LinearLayoutManager(MainActivity.this);
                                    recyclerViewCheckBox.setLayoutManager(layoutManagerCheckbox);
                                }
                            }
                        }
                    }
                }

                debtNum = debtList.size();
                callback.onDebtNumReceived(debtNum);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // Handle error
            }
        });
    }

    public void getDebtListMonthly(String selectedMonth, String selectedStatus, DebtNumCallback callback) {
        debtList.clear();

        DatabaseReference databaseReference = DeclareDatabase.getDBRefBorrows();

        if (selectedMonth != null && !selectedMonth.equals("All")) {
            DatabaseReference monthRef = databaseReference.child(selectedMonth);
            monthRef.addListenerForSingleValueEvent(new ValueEventListener() {

                @SuppressLint("NotifyDataSetChanged")
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    for (DataSnapshot daySnapshot : dataSnapshot.getChildren()) {
                        for (DataSnapshot currentUserRef : daySnapshot.getChildren()) {
                            String currentUserStr = currentUserRef.getKey();
                            if (Objects.equals(currentUserStr, currentNickname)) {
                                for (DataSnapshot timeSnapshot : currentUserRef.getChildren()) {
                                    BorrowTransaction borrowTransaction = timeSnapshot.getValue(BorrowTransaction.class);
                                    if (borrowTransaction != null) {
                                        String status = borrowTransaction.getStatus();
                                        if (!Objects.equals(status, "Pending Approval") && !Objects.equals(status, "Declined")
                                                && !Objects.equals(status, "Payment Pending") && !Objects.equals(status, "Payment Denied")) {
                                            if (Objects.equals("All", selectedStatus)) {
                                                addDebtTransactionToList(borrowTransaction);
                                            } else if (Objects.equals(status, selectedStatus)) {
                                                addDebtTransactionToList(borrowTransaction);
                                            }
                                        }
                                    }else {
                                        showToast("Borrow Transaction has no data");
                                    }
                                    Collections.sort(debtList, new Comparator<BorrowTransaction>() {
                                        SimpleDateFormat format = new SimpleDateFormat("MMM-dd-yyyy", Locale.ENGLISH);

                                        @Override
                                        public int compare(BorrowTransaction o1, BorrowTransaction o2) {
                                            try {
                                                Date date1 = format.parse(o1.getDate());
                                                Date date2 = format.parse(o2.getDate());
                                                return date2.compareTo(date1); // For descending order
                                            } catch (ParseException e) {
                                                throw new RuntimeException(e);
                                            }
                                        }
                                    });

                                    // To Show Debt List without Checkbox
                                    RecyclerView recyclerView = findViewById(R.id.debtRecyclerList);
                                    RecyclerView.Adapter<DebtTransactionAdapter.ViewHolder> adapter = new DebtTransactionAdapter(debtList);
                                    recyclerView.setAdapter(adapter);
                                    adapter.notifyDataSetChanged();
                                    RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(MainActivity.this);
                                    recyclerView.setLayoutManager(layoutManager);

                                    // To Show Debt List with Checkbox
                                    RecyclerView recyclerViewCheckBox = findViewById(R.id.debtCheckboxRecyclerList);
                                    RecyclerView.Adapter<BorrowTransactionAdapter.ViewHolder> adapterCheckbox = new BorrowTransactionAdapter(debtList);
                                    recyclerViewCheckBox.setAdapter(adapterCheckbox);
                                    adapterCheckbox.notifyDataSetChanged();
                                    RecyclerView.LayoutManager layoutManagerCheckbox = new LinearLayoutManager(MainActivity.this);
                                    recyclerViewCheckBox.setLayoutManager(layoutManagerCheckbox);
                                }
                            }
                        }
                    }

                    debtNum = debtList.size();
                    callback.onDebtNumReceived(debtNum);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    // Handle database read error
                    String errorMessage = "Database read error occurred: " + databaseError.getMessage();
                    Log.e("FirebaseDatabase", errorMessage);
                }
            });
        } else {
            debtList.clear();
        }
    }

    private void addDebtTransactionToList(BorrowTransaction borrowTransaction) {
        String date = borrowTransaction.getDate();
        String borrowee = borrowTransaction.getBorrowee();
        String borrowedAmount = String.valueOf(borrowTransaction.getBorrowedAmountStr());

        date = changeFormatDate(date);

        BorrowTransaction borrowTrans = new BorrowTransaction(
                date,
                borrowee,
                borrowedAmount,
                borrowTransaction.getStatus()
        );
        debtList.add(borrowTrans);
    }

    public void getOwedList(String selectedStatus, OwedNumCallback callback) {
        owedList.clear();

        DatabaseReference databaseReference = DeclareDatabase.getDBRefBorrows();
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {

            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot monthSnapshot : dataSnapshot.getChildren()) {
                    for (DataSnapshot daySnapshot : monthSnapshot.getChildren()) {
                        for (DataSnapshot currentUserRef : daySnapshot.getChildren()) {
                            String currentUserStr = currentUserRef.getKey();
                            if (!Objects.equals(currentUserStr, currentNickname)) {
                                for (DataSnapshot timeSnapshot : currentUserRef.getChildren()) {
                                    BorrowTransaction borrowTransaction = timeSnapshot.getValue(BorrowTransaction.class);
                                    if (borrowTransaction != null) {
                                        String borrower = borrowTransaction.getBorrowee();
                                        String status = borrowTransaction.getStatus();
                                        if (!Objects.equals(status, "Pending Approval") && !Objects.equals(status, "Declined")
                                                && !Objects.equals(status, "Payment Pending") && !Objects.equals(status, "Payment Denied")){
                                            if (Objects.equals(borrower, currentNickname) && Objects.equals("All", selectedStatus)) {
                                                addOwedTransactionToList(borrowTransaction, currentUserStr);
                                            } else if (Objects.equals(borrower, currentNickname) && Objects.equals(status, selectedStatus)) {
                                                addOwedTransactionToList(borrowTransaction, currentUserStr);
                                            }
                                        }

                                    } else {
                                        showToast("No data");
                                    }

                                    // Sort owedList by date in descending order
                                    Collections.sort(owedList, new Comparator<OwedTransaction>() {
                                        SimpleDateFormat format = new SimpleDateFormat("MMM-dd-yyyy", Locale.ENGLISH);

                                        @Override
                                        public int compare(OwedTransaction o1, OwedTransaction o2) {
                                            try {
                                                Date date1 = format.parse(o1.getDate());
                                                Date date2 = format.parse(o2.getDate());
                                                return date2.compareTo(date1); // For descending order
                                            } catch (ParseException e) {
                                                throw new RuntimeException(e);
                                            }
                                        }
                                    });

                                    RecyclerView recyclerView = findViewById(R.id.owedRecyclerList);
                                    RecyclerView.Adapter<OwedTransactionAdapter.ViewHolder> adapter = new OwedTransactionAdapter(owedList);
                                    recyclerView.setAdapter(adapter);
                                    adapter.notifyDataSetChanged();

                                    // Set the RecyclerView.LayoutManager
                                    RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(MainActivity.this);
                                    recyclerView.setLayoutManager(layoutManager);
                                }
                            }
                        }
                    }
                }
                owedNum = owedList.size();
                callback.onOwedNumReceived(owedNum);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // Handle database read error
                String errorMessage = "Database read error occurred: " + databaseError.getMessage();
                Log.e("FirebaseDatabase", errorMessage);
            }
        });
    }

    public void getOwedListMonthly(String selectedMonth, String selectedStatus, OwedNumCallback callback) {
        owedList.clear();

        DatabaseReference databaseReference = DeclareDatabase.getDBRefBorrows();

        if (selectedMonth != null && !selectedMonth.equals("All")) {
            DatabaseReference monthRef = databaseReference.child(selectedMonth);
            monthRef.addListenerForSingleValueEvent(new ValueEventListener() {

                @SuppressLint("NotifyDataSetChanged")
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    for (DataSnapshot daySnapshot : dataSnapshot.getChildren()) {
                        for (DataSnapshot currentUserRef : daySnapshot.getChildren()) {
                            String currentUserStr = currentUserRef.getKey();
                            if (!Objects.equals(currentUserStr, currentNickname)) {
                                for (DataSnapshot timeSnapshot : currentUserRef.getChildren()) {
                                    BorrowTransaction borrowTransaction = timeSnapshot.getValue(BorrowTransaction.class);
                                    if (borrowTransaction != null) {
                                        String borrower = borrowTransaction.getBorrowee();
                                        String status = borrowTransaction.getStatus();
                                        if (!Objects.equals(status, "Pending Approval") && !Objects.equals(status, "Declined")
                                                && !Objects.equals(status, "Payment Pending") && !Objects.equals(status, "Payment Denied")) {
                                            if (Objects.equals(borrower, currentNickname) && Objects.equals("All", selectedStatus)) {
                                                addOwedTransactionToList(borrowTransaction, currentUserStr);
                                            } else if (Objects.equals(borrower, currentNickname) && Objects.equals(status, selectedStatus)) {
                                                addOwedTransactionToList(borrowTransaction, currentUserStr);
                                            }
                                        }
                                    } else {
                                        showToast("Borrow Transaction has no data");
                                    }

                                    // Sort owedList by date in descending order
                                    Collections.sort(owedList, new Comparator<OwedTransaction>() {
                                        SimpleDateFormat format = new SimpleDateFormat("MMM-dd-yyyy", Locale.ENGLISH);

                                        @Override
                                        public int compare(OwedTransaction o1, OwedTransaction o2) {
                                            try {
                                                Date date1 = format.parse(o1.getDate());
                                                Date date2 = format.parse(o2.getDate());
                                                return date2.compareTo(date1); // For descending order
                                            } catch (ParseException e) {
                                                throw new RuntimeException(e);
                                            }
                                        }
                                    });

                                    RecyclerView recyclerView = findViewById(R.id.owedRecyclerList);
                                    RecyclerView.Adapter<OwedTransactionAdapter.ViewHolder> adapter = new OwedTransactionAdapter(owedList);
                                    recyclerView.setAdapter(adapter);
                                    adapter.notifyDataSetChanged();

                                    // Set the RecyclerView.LayoutManager
                                    RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(MainActivity.this);
                                    recyclerView.setLayoutManager(layoutManager);
                                }
                            }
                        }
                    }
                    owedNum = owedList.size();
                    callback.onOwedNumReceived(owedNum);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    // Handle database read error
                    String errorMessage = "Database read error occurred: " + databaseError.getMessage();
                    Log.e("FirebaseDatabase", errorMessage);
                }
            });
        } else {
            owedList.clear();
        }

    }

    private void addOwedTransactionToList(BorrowTransaction borrowTransaction, String currentUserStr) {
        String date = borrowTransaction.getDate();
        String borrowedAmount = String.valueOf(borrowTransaction.getBorrowedAmountStr());
        String borrower = currentUserStr;
        date = changeFormatDate(date);

        OwedTransaction owedTrans = new OwedTransaction(
                date,
                borrower,
                borrowedAmount,
                borrowTransaction.getStatus()
        );
        owedList.add(owedTrans);
    }

    public void getCurrentNickname(CurrentNicknameCallback callback) {
        String currentUserID = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
        DatabaseReference usersRef = DeclareDatabase.getDatabaseReference().child(currentUserID);
        usersRef.child("username").addListenerForSingleValueEvent(new ValueEventListener() {
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    // Get the username from the dataSnapshot and assign it to usernamePost
                    currentNickname = dataSnapshot.getValue(String.class);
                    callback.onCurrentNicknameReceived(currentNickname);
                    Log.d("FirebaseDatabase", "Nickname loaded: " + currentNickname);
                } else {
                    Log.d("FirebaseDatabase", "Nickname not found in database.");
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle database read error
                String errorMessage = "Database read error occurred: " + databaseError.getMessage();
                Log.e("FirebaseDatabase", errorMessage);
            }
        });
    }

    public String changeFormatDate(String date) {
        SimpleDateFormat originalFormat = new SimpleDateFormat("MMMM-dd-yyyy", Locale.ENGLISH); // Assuming "MMMM" for full month name
        Date newDate = null;

        try {
            newDate = originalFormat.parse(date);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        SimpleDateFormat newFormat = new SimpleDateFormat("MMM-dd-yyyy");
        return newFormat.format(newDate);
    }


    public void showToast(String message) {
        Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
    }

}