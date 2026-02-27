package com.waray.spendhound;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
        // Ensure we have the current user's nickname before fetching data
        if (currentNickname == null || currentNickname.isEmpty()) {
            getCurrentNickname(new CurrentNicknameCallback() {
                @Override
                public void onCurrentNicknameReceived(String nickname) {
                    fetchTotalMonthSpends(nickname);
                }
            });
        } else {
            fetchTotalMonthSpends(currentNickname);
        }
    }

    private void fetchTotalMonthSpends(String username) {
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
        // Ensure we have the current user's nickname before fetching data
        if (currentNickname == null || currentNickname.isEmpty()) {
            getCurrentNickname(new CurrentNicknameCallback() {
                @Override
                public void onCurrentNicknameReceived(String nickname) {
                    fetchEverydaySpends(nickname);
                }
            });
        } else {
            fetchEverydaySpends(currentNickname);
        }
    }

    @SuppressLint("DefaultLocale")
    private void fetchEverydaySpends(String username) {
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
                        if (transaction != null && isUserInvolved(transaction, username)) {
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
        // Ensure we have the current user's nickname before fetching data
        if (currentNickname == null || currentNickname.isEmpty()) {
            getCurrentNickname(new CurrentNicknameCallback() {
                @Override
                public void onCurrentNicknameReceived(String nickname) {
                    fetchEverydaySpendsForWeek(weekStart, nickname);
                }
            });
        } else {
            fetchEverydaySpendsForWeek(weekStart, currentNickname);
        }
    }

    @SuppressLint("DefaultLocale")
    private void fetchEverydaySpendsForWeek(Calendar weekStart, String username) {
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
                        if (transaction != null && isUserInvolved(transaction, username)) {
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

        // Ensure we have the current user's nickname before fetching data
        if (currentNickname == null || currentNickname.isEmpty()) {
            getCurrentNickname(new CurrentNicknameCallback() {
                @Override
                public void onCurrentNicknameReceived(String nickname) {
                    fetchRecentTransactions(nickname);
                }
            });
        } else {
            fetchRecentTransactions(currentNickname);
        }
    }

    private void fetchRecentTransactions(String username) {
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

                            // Get payors list - prefer display names, fallback to UIDs/usernames
                            java.util.List<String> payorsList = transaction.getPayorsDisplayNames();
                            if (payorsList == null || payorsList.isEmpty()) {
                                payorsList = transaction.getPayorsList();
                            }
                            java.util.List<Integer> amountsPaidList = transaction.getAmountsPaidList();

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
                                    amountsPaidList,
                                    fullDateWithYear,
                                    createdBy,
                                    createdByUid
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
        java.util.List<Integer> amountsPaidList = transaction.getAmountsPaidList();

        if (payorsList != null && !payorsList.isEmpty()) {
            for (int i = 0; i < payorsList.size(); i++) {
                String payorName = payorsList.get(i);
                String amountStr = "₱ 0";
                if (amountsPaidList != null && i < amountsPaidList.size()) {
                    amountStr = "₱ " + amountsPaidList.get(i);
                }

                // Create a row for each payor
                android.widget.LinearLayout payorRow = new android.widget.LinearLayout(this);
                payorRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                payorRow.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ));
                if (i > 0) {
                    payorRow.setPadding(0, 8, 0, 0);
                }

                TextView payorNameTV = new TextView(this);
                payorNameTV.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                ));
                payorNameTV.setText(payorName);
                payorNameTV.setTextColor(getResources().getColor(R.color.black, null));
                payorNameTV.setTextSize(14);

                TextView payorAmountTV = new TextView(this);
                payorAmountTV.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ));
                payorAmountTV.setText(amountStr);
                payorAmountTV.setTextColor(getResources().getColor(R.color.darkBlue, null));
                payorAmountTV.setTextSize(14);

                payorRow.addView(payorNameTV);
                payorRow.addView(payorAmountTV);
                payorsContainer.addView(payorRow);
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

    private String getCurrentUTCDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

}
