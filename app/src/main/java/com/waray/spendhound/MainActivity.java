package com.waray.spendhound;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {

    public BottomNavigationView navView;
    public FirebaseAuth mAuth;
    public double totalMonthSpends;
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

    /**
     * Helper method to check if the current user is involved in a transaction.
     * Supports both new UID-based data and legacy username-based data.
     * A user is involved if their UID/username is in the payorsList or they created the transaction.
     */
    public boolean isUserInvolved(Transaction transaction, String usernameOrUid) {
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
        // Check if user is the creator of the transaction
        if (usernameOrUid.equals(transaction.getUsernamePost())) {
            return true;
        }

        // Check poster display name (new format)
        if (usernameOrUid.equals(transaction.getPosterDisplayName())) {
            return true;
        }

        // Check if user is in the payors list (legacy: could contain usernames)
        java.util.List<String> payorsList = transaction.getPayorsList();
        if (payorsList != null && payorsList.contains(usernameOrUid)) {
            return true;
        }

        // Check payors display names (new format)
        java.util.List<String> payorsDisplayNames = transaction.getPayorsDisplayNames();
        if (payorsDisplayNames != null && payorsDisplayNames.contains(usernameOrUid)) {
            return true;
        }

        return false;
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        progressBar = findViewById(R.id.progressBar);
        progressBar.setVisibility(View.VISIBLE);

        mAuth = DeclareDatabase.getAuth();

        // Preload user UID-to-username cache for better performance
        UserHelper.preloadAllUsers();

        // Migration safety: Ensure existing users have balances and userBorrows nodes
        String currentUserId = Objects.requireNonNull(mAuth.getCurrentUser()).getUid();
        BalanceHelper.ensureBalancesExist(currentUserId, null);
        BalanceHelper.ensureUserBorrowsExist(currentUserId, null);

        getCurrentNickname(new CurrentNicknameCallback() {
            @Override
            public void onCurrentNicknameReceived(String CurrentNickname) {
                // Nickname is now cached in currentNickname field
            }
        });

        navView = findViewById(R.id.navView);

        // Setup Recent Transactions RecyclerView
        RecyclerView recyclerView = findViewById(R.id.transactionListRecycler);
        recentTransactionList = new ArrayList<>();
        recentTransactionAdapter = new RecentTransactionAdapter(recentTransactionList, this::showTransactionDetailsDialog);
        recyclerView.setAdapter(recentTransactionAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Setup viewAllTransaction click listener
        TextView viewAllTransaction = findViewById(R.id.viewAllTransaction);
        viewAllTransaction.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AllTransactionsActivity.class);
            startActivity(intent);
        });

        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_borrow, R.id.navigation_profile)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupWithNavController(navView, navController);

        /*Button crashButton = new Button(this);
        crashButton.setText("Test Crash");
        crashButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                throw new RuntimeException("Test Crash"); // Force a crash
            }
        });

        addContentView(crashButton, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));*/

        progressBar.setVisibility(View.GONE);
    }

    public void getTotalMonthSpends() {
        getTotalMonthSpends(null);
    }

    public void getTotalMonthSpends(Runnable callback) {
        // Ensure we have the current user's nickname before fetching data
        if (currentNickname == null || currentNickname.isEmpty()) {
            getCurrentNickname(new CurrentNicknameCallback() {
                @Override
                public void onCurrentNicknameReceived(String nickname) {
                    fetchTotalMonthSpends(nickname, callback);
                }
            });
        } else {
            fetchTotalMonthSpends(currentNickname, callback);
        }
    }

    private void fetchTotalMonthSpends(String username, Runnable callback) {
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
                        if (transaction != null && isUserInvolved(transaction, username)) {
                            // Retrieve the paymentAmount from the transaction
                            double paymentAmount = transaction.getPaymentAmount();

                            // Add the paymentAmount to totalMonthSpends
                            totalMonthSpends += paymentAmount;
                        }
                    }
                }

                // Now, totalMonthSpends contains the sum of all paymentAmounts in the current month
                // You can use it as needed, for example, update a TextView with this value
                String totalMonthSpendsString = String.format(Locale.getDefault(), "%.2f", totalMonthSpends);
                TextView totalMonthSpendsTextView = findViewById(R.id.totalMonthSpends);
                if (totalMonthSpendsTextView != null) {
                    totalMonthSpendsTextView.setText("₱ " + totalMonthSpendsString);
                }
                if (callback != null) callback.run();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // Handle database read error
                String errorMessage = "Database read error occurred: " + databaseError.getMessage();
                Log.e("FirebaseDatabase", errorMessage);
                if (callback != null) callback.run();
            }
        });
    }

    @SuppressLint("DefaultLocale")
    public void getEverydaySpends() {
        getEverydaySpends(null);
    }

    @SuppressLint("DefaultLocale")
    public void getEverydaySpends(Runnable callback) {
        // Ensure we have the current user's nickname before fetching data
        if (currentNickname == null || currentNickname.isEmpty()) {
            getCurrentNickname(new CurrentNicknameCallback() {
                @Override
                public void onCurrentNicknameReceived(String nickname) {
                    fetchEverydaySpends(nickname, callback);
                }
            });
        } else {
            fetchEverydaySpends(currentNickname, callback);
        }
    }

    @SuppressLint("DefaultLocale")
    private void fetchEverydaySpends(String username, Runnable callback) {
        double[] dailySpends = new double[7];
        Calendar calendar = Calendar.getInstance();
        // Set to beginning of current week (Sunday)
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM-yyyy", Locale.getDefault());
        SimpleDateFormat dayFormat = new SimpleDateFormat("dd", Locale.getDefault());

        AtomicInteger daysFetched = new AtomicInteger(0);

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
                    double dailySpend = 0;
                    for (DataSnapshot timeSnapshot : dataSnapshot.getChildren()) {
                        Transaction transaction = timeSnapshot.getValue(Transaction.class);
                        if (transaction != null && isUserInvolved(transaction, username)) {
                            dailySpend += transaction.getPaymentAmount();
                        }
                    }
                    dailySpends[dayIndex] = dailySpend;
                    setViewHeightForDay(dayIndex, dailySpends[dayIndex]);
                    if (daysFetched.incrementAndGet() == 7) {
                        if (callback != null) callback.run();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    // Handle database read error
                    String errorMessage = "Database read error occurred: " + databaseError.getMessage();
                    Log.e("FirebaseDatabase", errorMessage);
                    if (daysFetched.incrementAndGet() == 7) {
                        if (callback != null) callback.run();
                    }
                }
            });

            calendar.add(Calendar.DAY_OF_YEAR, 1); // Go to next day for week iteration
        }
    }

    @SuppressLint("DefaultLocale")
    public void getEverydaySpendsForWeek(Calendar weekStart) {
        getEverydaySpendsForWeek(weekStart, null);
    }

    @SuppressLint("DefaultLocale")
    public void getEverydaySpendsForWeek(Calendar weekStart, Runnable callback) {
        // Ensure we have the current user's nickname before fetching data
        if (currentNickname == null || currentNickname.isEmpty()) {
            getCurrentNickname(new CurrentNicknameCallback() {
                @Override
                public void onCurrentNicknameReceived(String nickname) {
                    fetchEverydaySpendsForWeek(weekStart, nickname, callback);
                }
            });
        } else {
            fetchEverydaySpendsForWeek(weekStart, currentNickname, callback);
        }
    }

    @SuppressLint("DefaultLocale")
    private void fetchEverydaySpendsForWeek(Calendar weekStart, String username, Runnable callback) {
        double[] dailySpends = new double[7];
        Calendar calendar = (Calendar) weekStart.clone();
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM-yyyy", Locale.getDefault());
        SimpleDateFormat dayFormat = new SimpleDateFormat("dd", Locale.getDefault());

        AtomicInteger daysFetched = new AtomicInteger(0);

        for (int i = 0; i < 7; i++) {
            String currentMonthYear = monthFormat.format(calendar.getTime());
            String currentDay = dayFormat.format(calendar.getTime());

            DatabaseReference dayRef = DeclareDatabase.getDBRefTransaction().child(currentMonthYear).child(currentDay);

            final int dayIndex = 6 - i; // Reverse order to match UI (day7 first, day1 last)
            dayRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    double dailySpend = 0;
                    for (DataSnapshot timeSnapshot : dataSnapshot.getChildren()) {
                        Transaction transaction = timeSnapshot.getValue(Transaction.class);
                        if (transaction != null && isUserInvolved(transaction, username)) {
                            dailySpend += transaction.getPaymentAmount();
                        }
                    }
                    dailySpends[dayIndex] = dailySpend;
                    setViewHeightForDay(dayIndex, dailySpends[dayIndex]);
                    if (daysFetched.incrementAndGet() == 7) {
                        if (callback != null) callback.run();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    String errorMessage = "Database read error occurred: " + databaseError.getMessage();
                    Log.e("FirebaseDatabase", errorMessage);
                    if (daysFetched.incrementAndGet() == 7) {
                        if (callback != null) callback.run();
                    }
                }
            });

            calendar.add(Calendar.DAY_OF_YEAR, 1); // Go to next day for week iteration
        }
    }

    public void setViewHeightForDay(int day, double dailySpends) {
        double[] dailySpendsArray = new double[7];
        dailySpendsArray[day] = dailySpends;
        String dailySpendString = String.format(Locale.getDefault(), "%.2f", dailySpendsArray[day]);
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
                if (day7SpendTextView != null) day7SpendTextView.setText(dailySpendString);
                viewId = R.id.day7_bar;
                break;
            case 5: // Monday
                if (day6SpendTextView != null) day6SpendTextView.setText(dailySpendString);
                viewId = R.id.day6_bar;
                break;
            case 4: // Tuesday
                if (day5SpendTextView != null) day5SpendTextView.setText(dailySpendString);
                viewId = R.id.day5_bar;
                break;
            case 3: // Wednesday
                if (day4SpendTextView != null) day4SpendTextView.setText(dailySpendString);
                viewId = R.id.day4_bar;
                break;
            case 2: // Thursday
                if (day3SpendTextView != null) day3SpendTextView.setText(dailySpendString);
                viewId = R.id.day3_bar;
                break;
            case 1: // Friday
                if (day2SpendTextView != null) day2SpendTextView.setText(dailySpendString);
                viewId = R.id.day2_bar;
                break;
            case 0: // Saturday
                if (day1SpendTextView != null) day1SpendTextView.setText(dailySpendString);
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
            desiredHeightInPixels = (int) (dailySpends / 3);
        }

        if (viewId != 0) {
            View view = findViewById(viewId);
            if (view != null) {
                ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
                layoutParams.height = desiredHeightInPixels;
                view.setLayoutParams(layoutParams);
            }
        }
    }

    public void getRecentTransaction() {
        getRecentTransaction(null);
    }

    public void getRecentTransaction(Runnable callback) {
        Log.d("RecentTransaction", "getRecentTransaction() called");

        // Ensure we have the current user's nickname before fetching data
        if (currentNickname == null || currentNickname.isEmpty()) {
            getCurrentNickname(new CurrentNicknameCallback() {
                @Override
                public void onCurrentNicknameReceived(String nickname) {
                    fetchRecentTransactions(nickname, callback);
                }
            });
        } else {
            fetchRecentTransactions(currentNickname, callback);
        }
    }

    private void fetchRecentTransactions(String username, Runnable callback) {
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
                        if (transaction != null && isUserInvolved(transaction, username)) {
                            String timeKey = timeSnapshot.getKey(); // Time in HH:mm:ss format
                            String[] parts = finalCurrentMonthYear.split("-");
                            String finalCurrentMonth = parts[0];
                            String finalCurrentYear = parts[1];
                            String mostRecentDate = finalCurrentMonth + " - " + finalCurrentDay;

                            // Create full date with year for details dialog
                            String fullDateWithYear = finalCurrentMonth + " " + finalCurrentDay + ", " + finalCurrentYear;

                            // Create sortDateTime for proper sorting (format: yyyy-MM-dd HH:mm:ss)
                            String sortDateTime = finalCurrentYear + "-" + finalCurrentMonth + "-" + finalCurrentDay + " " + timeKey;

                            String mostRecentTransactionType = transaction.getTransactionType();
                            String mostRecentDetails = transaction.getMultilineStr();
                            double mostRecentPaymentAmount = transaction.getPaymentAmount();
                            String mostRecentPaymentAmountStr = String.format(Locale.getDefault(), "₱ %.2f", mostRecentPaymentAmount);
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
                                    mostRecentDate,
                                    mostRecentTransactionType,
                                    mostRecentDetails,
                                    mostRecentPaymentAmountStr,
                                    iconResource,
                                    sortDateTime,
                                    payorsList,
                                    payorUids,
                                    amountsPaidList,
                                    totalIndividualPayment,
                                    fullDateWithYear,
                                    createdBy,
                                    createdByUid,
                                    finalCurrentMonthYear,
                                    finalCurrentDay,
                                    timeKey
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
                            recentTransactionAdapter = new RecentTransactionAdapter(recentTransactionList, MainActivity.this::showTransactionDetailsDialog);
                            recyclerView.setAdapter(recentTransactionAdapter);
                            recyclerView.setLayoutManager(new LinearLayoutManager(MainActivity.this));
                            Log.d("RecentTransaction", "Adapter re-attached. List size: " + recentTransactionList.size());
                        } else {
                            Log.e("RecentTransaction", "RecyclerView is NULL!");
                        }
                        if (callback != null) callback.run();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.e("FirebaseDatabase", "Database read error occurred: " + databaseError.getMessage());
                    if (daysFetched.incrementAndGet() == daysToFetch) {
                        if (recentTransactionAdapter != null) {
                            recentTransactionAdapter.notifyDataSetChanged();
                        }
                        if (callback != null) callback.run();
                    }
                }
            });
            calendar.add(Calendar.DAY_OF_YEAR, -1);
        }
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
        android.widget.LinearLayout payorsContainer = dialogView.findViewById(R.id.payorsContainer);
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
                double amountPaid = 0;
                if (amountsPaidList != null && i < amountsPaidList.size()) {
                    amountPaid = amountsPaidList.get(i);
                }
                final double finalAmountPaid = amountPaid;

                View rowView = LayoutInflater.from(this).inflate(R.layout.transaction_payor_table_row, payorsContainer, false);
                ImageView payorImage = rowView.findViewById(R.id.payorImage);
                TextView payorNameTV = rowView.findViewById(R.id.payorName);
                TextView payorPaidAmountTV = rowView.findViewById(R.id.payorPaidAmount);
                TextView payorDueAmountTV = rowView.findViewById(R.id.payorDueAmount);
                TextView payorStatusTV = rowView.findViewById(R.id.payorStatus);
                ImageView editBtn = rowView.findViewById(R.id.editPayorAmountBtn);

                payorNameTV.setText(payorName);
                payorPaidAmountTV.setText(String.format(Locale.getDefault(), "₱ %.2f", finalAmountPaid));
                payorDueAmountTV.setText(String.format(Locale.getDefault(), "₱ %.2f", individualPayment));

                if (Math.abs(finalAmountPaid - individualPayment) < 0.01) {
                    payorStatusTV.setVisibility(View.VISIBLE);
                } else {
                    payorStatusTV.setVisibility(View.INVISIBLE);
                }

                if (isCreator) {
                    editBtn.setVisibility(View.VISIBLE);
                    editBtn.setOnClickListener(v -> {
                        showEditAmountDialog(transaction, index, finalAmountPaid);
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
            getRecentTransaction(); // Refresh the list
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to update amount", Toast.LENGTH_SHORT).show();
        });
    }

    // Overload for backward compatibility
    public void getDebtList(String selectedStatus, DebtNumCallback callback) {
        getDebtList(selectedStatus, callback, null);
    }

    public void getDebtList(String selectedStatus, DebtNumCallback callback, DebtTransactionAdapter.OnBorrowerActionListener actionListener) {
        debtList.clear();

        String currentUserId = Objects.requireNonNull(mAuth.getCurrentUser()).getUid();

        DatabaseReference databaseReference = DeclareDatabase.getDBRefBorrows();
        final DebtTransactionAdapter.OnBorrowerActionListener finalActionListener = actionListener;
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {

            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot monthSnapshot : dataSnapshot.getChildren()) {
                    String monthYear = monthSnapshot.getKey();
                    for (DataSnapshot daySnapshot : monthSnapshot.getChildren()) {
                        String day = daySnapshot.getKey();
                        for (DataSnapshot borrowSnapshot : daySnapshot.getChildren()) {
                            // New structure: borrows/{month}/{day}/{borrowId}
                            BorrowNowTransaction borrowNowTransaction = borrowSnapshot.getValue(BorrowNowTransaction.class);

                            if (borrowNowTransaction != null && borrowNowTransaction.getBorrowerID() != null) {
                                // New UID-based structure - check if current user is the borrower
                                if (Objects.equals(borrowNowTransaction.getBorrowerID(), currentUserId)) {
                                    String status = borrowNowTransaction.getStatus();
                                    // Exclude Removed status from all views
                                    if (!Objects.equals(status, "Removed") && !Objects.equals(status, "Payment Denied")) {
                                        if (shouldIncludeForDebtStatus(status, selectedStatus)) {
                                            addDebtTransactionFromBorrowNow(borrowNowTransaction, monthYear, day, borrowSnapshot.getKey());
                                        }
                                    }
                                }
                            } else {
                                // Legacy structure: borrows/{month}/{day}/{username}/{time}
                                String currentUserStr = borrowSnapshot.getKey();
                                if (Objects.equals(currentUserStr, currentNickname)) {
                                    for (DataSnapshot timeSnapshot : borrowSnapshot.getChildren()) {
                                        BorrowTransaction borrowTransaction = timeSnapshot.getValue(BorrowTransaction.class);
                                        if (borrowTransaction != null) {
                                            String status = borrowTransaction.getStatus();
                                            // Exclude Removed status from all views
                                            if (!Objects.equals(status, "Removed") && !Objects.equals(status, "Payment Denied")) {
                                                if (shouldIncludeForDebtStatus(status, selectedStatus)) {
                                                    addDebtTransactionToList(borrowTransaction);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
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
                            return date2.compareTo(date1);
                        } catch (ParseException e) {
                            return 0;
                        }
                    }
                });

                // To Show Debt List without Checkbox
                RecyclerView recyclerView = findViewById(R.id.debtRecyclerList);
                if (recyclerView != null) {
                    DebtTransactionAdapter.OnBorrowerActionListener listener = finalActionListener != null ? finalActionListener : new DebtTransactionAdapter.OnBorrowerActionListener() {
                        @Override
                        public void onPayClicked(BorrowTransaction transaction, int position) {
                        }

                        @Override
                        public void onRemoveClicked(BorrowTransaction transaction, int position) {
                        }

                        @Override
                        public void onTryAgainClicked(BorrowTransaction transaction, int position) {
                        }
                    };
                    DebtTransactionAdapter adapter = new DebtTransactionAdapter(debtList, listener);
                    recyclerView.setAdapter(adapter);
                    adapter.notifyDataSetChanged();
                    RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(MainActivity.this);
                    recyclerView.setLayoutManager(layoutManager);
                }

                debtNum = debtList.size();
                callback.onDebtNumReceived(debtNum);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // Handle error
                Log.e("FirebaseDatabase", "Database read error: " + databaseError.getMessage());
            }
        });
    }

    private void addDebtTransactionFromBorrowNow(BorrowNowTransaction borrowNowTransaction, String monthYear, String day, String borrowId) {
        String date = borrowNowTransaction.getDate();
        String borrowee = borrowNowTransaction.getLender(); // Lender name for display
        String borrowedAmount = borrowNowTransaction.getBorrowedAmountStr();

        date = changeFormatDate(date);

        // Format paymentSentDate if available
        String paymentSentDateStr = null;
        if (borrowNowTransaction.getPaymentSentDate() > 0) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM-dd-yyyy", Locale.ENGLISH);
            paymentSentDateStr = dateFormat.format(new Date(borrowNowTransaction.getPaymentSentDate()));
        }

        BorrowTransaction borrowTrans = new BorrowTransaction(
                date,
                borrowee,
                borrowedAmount,
                borrowNowTransaction.getStatus()
        );
        borrowTrans.setPaymentSentDate(paymentSentDateStr);
        borrowTrans.setBorrowId(borrowId);
        borrowTrans.setMonthYear(monthYear);
        borrowTrans.setDay(day);
        debtList.add(borrowTrans);
    }

    // Overload for backward compatibility
    public void getDebtListMonthly(String selectedMonth, String selectedStatus, DebtNumCallback callback) {
        getDebtListMonthly(selectedMonth, selectedStatus, callback, null);
    }

    public void getDebtListMonthly(String selectedMonth, String selectedStatus, DebtNumCallback callback, DebtTransactionAdapter.OnBorrowerActionListener actionListener) {
        debtList.clear();

        String currentUserId = Objects.requireNonNull(mAuth.getCurrentUser()).getUid();

        DatabaseReference databaseReference = DeclareDatabase.getDBRefBorrows();
        final DebtTransactionAdapter.OnBorrowerActionListener finalActionListener = actionListener;

        if (selectedMonth != null && !selectedMonth.equals("All")) {
            DatabaseReference monthRef = databaseReference.child(selectedMonth);
            monthRef.addListenerForSingleValueEvent(new ValueEventListener() {

                @SuppressLint("NotifyDataSetChanged")
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    for (DataSnapshot daySnapshot : dataSnapshot.getChildren()) {
                        String day = daySnapshot.getKey();
                        for (DataSnapshot borrowSnapshot : daySnapshot.getChildren()) {
                            // New structure: borrows/{month}/{day}/{borrowId}
                            BorrowNowTransaction borrowNowTransaction = borrowSnapshot.getValue(BorrowNowTransaction.class);

                            if (borrowNowTransaction != null && borrowNowTransaction.getBorrowerID() != null) {
                                // New UID-based structure
                                if (Objects.equals(borrowNowTransaction.getBorrowerID(), currentUserId)) {
                                    String status = borrowNowTransaction.getStatus();
                                    // Exclude Removed status from all views
                                    if (!Objects.equals(status, "Removed") && !Objects.equals(status, "Payment Denied")) {
                                        if (shouldIncludeForDebtStatus(status, selectedStatus)) {
                                            addDebtTransactionFromBorrowNow(borrowNowTransaction, selectedMonth, day, borrowSnapshot.getKey());
                                        }
                                    }
                                }
                            } else {
                                // Legacy structure: borrows/{month}/{day}/{username}/{time}
                                String currentUserStr = borrowSnapshot.getKey();
                                if (Objects.equals(currentUserStr, currentNickname)) {
                                    for (DataSnapshot timeSnapshot : borrowSnapshot.getChildren()) {
                                        BorrowTransaction borrowTransaction = timeSnapshot.getValue(BorrowTransaction.class);
                                        if (borrowTransaction != null) {
                                            String status = borrowTransaction.getStatus();
                                            // Exclude Removed status from all views
                                            if (!Objects.equals(status, "Removed") && !Objects.equals(status, "Payment Denied")) {
                                                if (shouldIncludeForDebtStatus(status, selectedStatus)) {
                                                    addDebtTransactionToList(borrowTransaction);
                                                }
                                            }
                                        }
                                    }
                                }
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
                                return date2.compareTo(date1);
                            } catch (ParseException e) {
                                return 0;
                            }
                        }
                    });

                    // To Show Debt List
                    RecyclerView recyclerView = findViewById(R.id.debtRecyclerList);
                    if (recyclerView != null) {
                        DebtTransactionAdapter.OnBorrowerActionListener listener = finalActionListener != null ? finalActionListener : new DebtTransactionAdapter.OnBorrowerActionListener() {
                            @Override
                            public void onPayClicked(BorrowTransaction transaction, int position) {
                            }

                            @Override
                            public void onRemoveClicked(BorrowTransaction transaction, int position) {
                            }

                            @Override
                            public void onTryAgainClicked(BorrowTransaction transaction, int position) {
                            }
                        };
                        DebtTransactionAdapter adapter = new DebtTransactionAdapter(debtList, listener);
                        recyclerView.setAdapter(adapter);
                        adapter.notifyDataSetChanged();
                        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(MainActivity.this);
                        recyclerView.setLayoutManager(layoutManager);
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

    // Overload for backward compatibility
    public void getOwedList(String selectedStatus, OwedNumCallback callback) {
        getOwedList(selectedStatus, callback, null);
    }

    public void getOwedList(String selectedStatus, OwedNumCallback callback, OwedTransactionAdapter.OnLenderActionListener actionListener) {
        owedList.clear();

        String currentUserId = Objects.requireNonNull(mAuth.getCurrentUser()).getUid();

        DatabaseReference databaseReference = DeclareDatabase.getDBRefBorrows();
        final OwedTransactionAdapter.OnLenderActionListener finalActionListener = actionListener;
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {

            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot monthSnapshot : dataSnapshot.getChildren()) {
                    String monthYear = monthSnapshot.getKey();
                    for (DataSnapshot daySnapshot : monthSnapshot.getChildren()) {
                        String day = daySnapshot.getKey();
                        for (DataSnapshot borrowSnapshot : daySnapshot.getChildren()) {
                            // New structure: borrows/{month}/{day}/{borrowId}
                            BorrowNowTransaction borrowNowTransaction = borrowSnapshot.getValue(BorrowNowTransaction.class);

                            if (borrowNowTransaction != null && borrowNowTransaction.getLenderID() != null) {
                                // New UID-based structure - check if current user is the lender
                                if (Objects.equals(borrowNowTransaction.getLenderID(), currentUserId)) {
                                    String status = borrowNowTransaction.getStatus();
                                    // Exclude Declined and Removed statuses for lender view
                                    if (!Objects.equals(status, "Declined") && !Objects.equals(status, "Payment Denied") && !Objects.equals(status, "Removed")) {
                                        if (shouldIncludeForStatus(status, selectedStatus)) {
                                            addOwedTransactionFromBorrowNow(borrowNowTransaction, monthYear, day, borrowSnapshot.getKey());
                                        }
                                    }
                                }
                            } else {
                                // Legacy structure: borrows/{month}/{day}/{username}/{time}
                                String currentUserStr = borrowSnapshot.getKey();
                                if (!Objects.equals(currentUserStr, currentNickname)) {
                                    for (DataSnapshot timeSnapshot : borrowSnapshot.getChildren()) {
                                        BorrowTransaction borrowTransaction = timeSnapshot.getValue(BorrowTransaction.class);
                                        if (borrowTransaction != null) {
                                            String borrower = borrowTransaction.getBorrowee();
                                            String status = borrowTransaction.getStatus();
                                            // Exclude Declined and Removed statuses for lender view
                                            if (!Objects.equals(status, "Declined") && !Objects.equals(status, "Payment Denied") && !Objects.equals(status, "Removed")) {
                                                if (Objects.equals(borrower, currentNickname) && shouldIncludeForStatus(status, selectedStatus)) {
                                                    addOwedTransactionToList(borrowTransaction, currentUserStr);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Sort owedList by date in descending order
                Collections.sort(owedList, new Comparator<OwedTransaction>() {
                    SimpleDateFormat format = new SimpleDateFormat("MMM-dd-yyyy", Locale.ENGLISH);

                    @Override
                    public int compare(OwedTransaction o1, OwedTransaction o2) {
                        try {
                            Date date1 = format.parse(o1.getDate());
                            Date date2 = format.parse(o2.getDate());
                            return date2.compareTo(date1);
                        } catch (ParseException e) {
                            return 0;
                        }
                    }
                });

                RecyclerView recyclerView = findViewById(R.id.owedRecyclerList);
                if (recyclerView != null) {
                    OwedTransactionAdapter.OnLenderActionListener listener = finalActionListener != null ? finalActionListener : new OwedTransactionAdapter.OnLenderActionListener() {
                        @Override
                        public void onNotYetClicked(OwedTransaction transaction, int position) {
                        }

                        @Override
                        public void onReceivedClicked(OwedTransaction transaction, int position) {
                        }

                        @Override
                        public void onDeclineClicked(OwedTransaction transaction, int position) {
                        }

                        @Override
                        public void onApprovedClicked(OwedTransaction transaction, int position) {
                        }
                    };
                    OwedTransactionAdapter adapter = new OwedTransactionAdapter(owedList, listener);
                    recyclerView.setAdapter(adapter);
                    adapter.notifyDataSetChanged();
                    RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(MainActivity.this);
                    recyclerView.setLayoutManager(layoutManager);
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

    /**
     * Check if a status should be included based on the selected filter
     */
    private boolean shouldIncludeForStatus(String status, String selectedStatus) {
        if (Objects.equals("All", selectedStatus)) {
            return true;
        } else if (Objects.equals("Pending", selectedStatus)) {
            // Pending tab includes both "Pending Payment" and "For Lender Approval"
            return Objects.equals(status, "Pending Payment") || Objects.equals(status, "For Lender Approval");
        } else {
            return Objects.equals(status, selectedStatus);
        }
    }

    /**
     * Check if a status should be included for debt list (My Debt tab)
     * Includes Declined status in Pending tab
     */
    private boolean shouldIncludeForDebtStatus(String status, String selectedStatus) {
        if (Objects.equals("All", selectedStatus)) {
            return true;
        } else if (Objects.equals("Pending", selectedStatus)) {
            // Pending tab includes "Pending Payment", "For Lender Approval", and "Declined" for My Debt
            return Objects.equals(status, "Pending Payment") || Objects.equals(status, "For Lender Approval") || Objects.equals(status, "Declined");
        } else {
            return Objects.equals(status, selectedStatus);
        }
    }

    private void addOwedTransactionFromBorrowNow(BorrowNowTransaction borrowNowTransaction, String monthYear, String day, String borrowId) {
        String date = borrowNowTransaction.getDate();
        String borrower = borrowNowTransaction.getBorrowerName();
        if (borrower == null || borrower.isEmpty()) {
            borrower = "Unknown";
        }
        String borrowedAmount = borrowNowTransaction.getBorrowedAmountStr();

        date = changeFormatDate(date);

        // Format paymentSentDate if available
        String paymentSentDateStr = null;
        if (borrowNowTransaction.getPaymentSentDate() > 0) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM-dd-yyyy", Locale.ENGLISH);
            paymentSentDateStr = dateFormat.format(new Date(borrowNowTransaction.getPaymentSentDate()));
        }

        OwedTransaction owedTrans = new OwedTransaction(
                date,
                borrower,
                borrowedAmount,
                borrowNowTransaction.getStatus(),
                paymentSentDateStr,
                borrowId,
                monthYear,
                day
        );
        owedList.add(owedTrans);
    }

    // Overload for backward compatibility
    public void getOwedListMonthly(String selectedMonth, String selectedStatus, OwedNumCallback callback) {
        getOwedListMonthly(selectedMonth, selectedStatus, callback, null);
    }

    public void getOwedListMonthly(String selectedMonth, String selectedStatus, OwedNumCallback callback, OwedTransactionAdapter.OnLenderActionListener actionListener) {
        owedList.clear();

        String currentUserId = Objects.requireNonNull(mAuth.getCurrentUser()).getUid();

        DatabaseReference databaseReference = DeclareDatabase.getDBRefBorrows();
        final OwedTransactionAdapter.OnLenderActionListener finalActionListener = actionListener;

        if (selectedMonth != null && !selectedMonth.equals("All")) {
            DatabaseReference monthRef = databaseReference.child(selectedMonth);
            monthRef.addListenerForSingleValueEvent(new ValueEventListener() {

                @SuppressLint("NotifyDataSetChanged")
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    for (DataSnapshot daySnapshot : dataSnapshot.getChildren()) {
                        String day = daySnapshot.getKey();
                        for (DataSnapshot borrowSnapshot : daySnapshot.getChildren()) {
                            // New structure: borrows/{month}/{day}/{borrowId}
                            BorrowNowTransaction borrowNowTransaction = borrowSnapshot.getValue(BorrowNowTransaction.class);

                            if (borrowNowTransaction != null && borrowNowTransaction.getLenderID() != null) {
                                // New UID-based structure - check if current user is the lender
                                if (Objects.equals(borrowNowTransaction.getLenderID(), currentUserId)) {
                                    String status = borrowNowTransaction.getStatus();
                                    // Exclude Declined and Removed statuses for lender view
                                    if (!Objects.equals(status, "Declined") && !Objects.equals(status, "Payment Denied") && !Objects.equals(status, "Removed")) {
                                        if (shouldIncludeForStatus(status, selectedStatus)) {
                                            addOwedTransactionFromBorrowNow(borrowNowTransaction, selectedMonth, day, borrowSnapshot.getKey());
                                        }
                                    }
                                }
                            } else {
                                // Legacy structure: borrows/{month}/{day}/{username}/{time}
                                String currentUserStr = borrowSnapshot.getKey();
                                if (!Objects.equals(currentUserStr, currentNickname)) {
                                    for (DataSnapshot timeSnapshot : borrowSnapshot.getChildren()) {
                                        BorrowTransaction borrowTransaction = timeSnapshot.getValue(BorrowTransaction.class);
                                        if (borrowTransaction != null) {
                                            String borrower = borrowTransaction.getBorrowee();
                                            String status = borrowTransaction.getStatus();
                                            // Exclude Declined and Removed statuses for lender view
                                            if (!Objects.equals(status, "Declined") && !Objects.equals(status, "Payment Denied") && !Objects.equals(status, "Removed")) {
                                                if (Objects.equals(borrower, currentNickname) && shouldIncludeForStatus(status, selectedStatus)) {
                                                    addOwedTransactionToList(borrowTransaction, currentUserStr);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Sort owedList by date in descending order
                    Collections.sort(owedList, new Comparator<OwedTransaction>() {
                        SimpleDateFormat format = new SimpleDateFormat("MMM-dd-yyyy", Locale.ENGLISH);

                        @Override
                        public int compare(OwedTransaction o1, OwedTransaction o2) {
                            try {
                                Date date1 = format.parse(o1.getDate());
                                Date date2 = format.parse(o2.getDate());
                                return date2.compareTo(date1);
                            } catch (ParseException e) {
                                return 0;
                            }
                        }
                    });

                    RecyclerView recyclerView = findViewById(R.id.owedRecyclerList);
                    if (recyclerView != null) {
                        OwedTransactionAdapter.OnLenderActionListener listener = finalActionListener != null ? finalActionListener : new OwedTransactionAdapter.OnLenderActionListener() {
                            @Override
                            public void onNotYetClicked(OwedTransaction transaction, int position) {
                            }

                            @Override
                            public void onReceivedClicked(OwedTransaction transaction, int position) {
                            }

                            @Override
                            public void onDeclineClicked(OwedTransaction transaction, int position) {
                            }

                            @Override
                            public void onApprovedClicked(OwedTransaction transaction, int position) {
                            }
                        };
                        OwedTransactionAdapter adapter = new OwedTransactionAdapter(owedList, listener);
                        recyclerView.setAdapter(adapter);
                        adapter.notifyDataSetChanged();
                        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(MainActivity.this);
                        recyclerView.setLayoutManager(layoutManager);
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
                    callback.onCurrentNicknameReceived("");
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle database read error
                String errorMessage = "Database read error occurred: " + databaseError.getMessage();
                Log.e("FirebaseDatabase", errorMessage);
                callback.onCurrentNicknameReceived("");
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

    private String getCurrentUTCDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

}
