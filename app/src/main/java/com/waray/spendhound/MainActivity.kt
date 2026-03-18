package com.waray.spendhound;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SnapHelper;

import com.google.android.material.behavior.HideBottomViewOnScrollBehavior;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.waray.spendhound.databinding.ActivityMainBinding;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
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
    public int owedNum, debtNum;
    private ArrayList<RecentTransaction> recentTransactionList = new ArrayList<>();
    public ArrayList<BorrowTransaction> debtList = new ArrayList<>();
    public ArrayList<OwedTransaction> owedList = new ArrayList<>();
    private RecentTransactionAdapter recentTransactionAdapter;

    // FAB Menu fields
    private FloatingActionButton fabMain;
    private View fabMenuOverlay;
    private LinearLayout containerBorrow, containerAddTransaction, containerAddGroup;
    private boolean isFabMenuOpen = false;
    private String selectedLenderName = "";

    public interface OwedNumCallback {
        void onOwedNumReceived(int owedNum);
    }
    public interface DebtNumCallback {
        void onDebtNumReceived(int debtNum);
    }
    public interface CurrentNicknameCallback {
        void onCurrentNicknameReceived(String CurrentNickname);
    }

    public boolean isUserInvolved(Transaction transaction, String usernameOrUid) {
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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        progressBar = findViewById(R.id.progressBar);
        progressBar.setVisibility(View.VISIBLE);
        mAuth = DeclareDatabase.getAuth();
        UserHelper.preloadAllUsers();

        String currentUserId = Objects.requireNonNull(mAuth.getCurrentUser()).getUid();
        BalanceHelper.ensureBalancesExist(currentUserId, null);
        BalanceHelper.ensureUserBorrowsExist(currentUserId, null);

        getCurrentNickname(nickname -> {});

        navView = findViewById(R.id.navView);
        RecyclerView recyclerView = findViewById(R.id.transactionListRecycler);
        recentTransactionList = new ArrayList<>();
        recentTransactionAdapter = new RecentTransactionAdapter(recentTransactionList, this::onTransactionTap);
        if (recyclerView != null) {
            recyclerView.setAdapter(recentTransactionAdapter);
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
        }

        fabMain = findViewById(R.id.fab_main);
        fabMenuOverlay = findViewById(R.id.fab_menu_overlay);
        containerBorrow = findViewById(R.id.container_borrow);
        containerAddTransaction = findViewById(R.id.container_add_transaction);
        containerAddGroup = findViewById(R.id.container_add_group);

        if (fabMain != null) fabMain.setOnClickListener(v -> toggleFabMenu());
        if (fabMenuOverlay != null) fabMenuOverlay.setOnClickListener(v -> collapseFabMenu());
        
        findViewById(R.id.fab_borrow).setOnClickListener(v -> { collapseFabMenu(); showBorrowNowDialog(); });
        findViewById(R.id.fab_add_transaction).setOnClickListener(v -> { collapseFabMenu(); startActivity(new Intent(MainActivity.this, AddTransactionActivity.class)); });
        findViewById(R.id.fab_add_group).setOnClickListener(v -> { collapseFabMenu(); showCreateGroupDialog(); });

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_activity_main);
        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(navView, navController);
            navView.setOnItemSelectedListener(item -> {
                if (isFabMenuOpen) collapseFabMenu();
                return NavigationUI.onNavDestinationSelected(item, navController);
            });

            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                int id = destination.getId();
                boolean hideOnScroll = (id != R.id.navigation_borrow && id != R.id.navigation_profile);
                updateBottomViewBehavior(hideOnScroll);
            });
        }
        progressBar.setVisibility(View.GONE);
    }

    public void onTransactionTap(RecentTransaction transaction) {
        if (!transaction.isExpanded()) {
            unhideNavigation();
        }
    }

    public void unhideNavigation() {
        if (navView != null) {
            ViewGroup.LayoutParams layoutParams = navView.getLayoutParams();
            if (layoutParams instanceof CoordinatorLayout.LayoutParams) {
                CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) layoutParams;
                if (params.getBehavior() instanceof HideBottomViewOnScrollBehavior) {
                    HideBottomViewOnScrollBehavior<BottomNavigationView> behavior = (HideBottomViewOnScrollBehavior<BottomNavigationView>) params.getBehavior();
                    behavior.slideUp(navView);
                }
            }
        }
        
        View fabMainLayout = findViewById(R.id.fab_main_layout);
        if (fabMainLayout != null) {
            ViewGroup.LayoutParams layoutParams = fabMainLayout.getLayoutParams();
            if (layoutParams instanceof CoordinatorLayout.LayoutParams) {
                CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) layoutParams;
                if (params.getBehavior() instanceof HideBottomViewOnScrollBehavior) {
                    HideBottomViewOnScrollBehavior<View> behavior = (HideBottomViewOnScrollBehavior<View>) params.getBehavior();
                    behavior.slideUp(fabMainLayout);
                }
            }
        }
        
        if (fabMain != null) {
            ViewGroup.LayoutParams layoutParams = fabMain.getLayoutParams();
            if (layoutParams instanceof CoordinatorLayout.LayoutParams) {
                CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) layoutParams;
                if (params.getBehavior() instanceof HideBottomViewOnScrollBehavior) {
                    HideBottomViewOnScrollBehavior<FloatingActionButton> behavior = (HideBottomViewOnScrollBehavior<FloatingActionButton>) params.getBehavior();
                    behavior.slideUp(fabMain);
                }
            }
        }
    }

    private void updateBottomViewBehavior(boolean hideOnScroll) {
        View fabMainLayout = findViewById(R.id.fab_main_layout);
        updateViewBehavior(navView, hideOnScroll);
        updateViewBehavior(fabMain, hideOnScroll);
        updateViewBehavior(fabMainLayout, hideOnScroll);
    }

    private void updateViewBehavior(View view, boolean hideOnScroll) {
        if (view == null) return;
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams instanceof CoordinatorLayout.LayoutParams) {
            CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) layoutParams;
            if (hideOnScroll) {
                if (!(params.getBehavior() instanceof HideBottomViewOnScrollBehavior)) {
                    params.setBehavior(new HideBottomViewOnScrollBehavior<>());
                    view.setTranslationY(0f);
                    view.setLayoutParams(params);
                }
            } else {
                if (params.getBehavior() != null) {
                    params.setBehavior(null);
                    view.setTranslationY(0f);
                    view.setLayoutParams(params);
                }
            }
        }
    }

    private void toggleFabMenu() {
        if (isFabMenuOpen) collapseFabMenu();
        else expandFabMenu();
    }

    private void expandFabMenu() {
        if (isFabMenuOpen) return;
        isFabMenuOpen = true;
        
        fabMenuOverlay.setVisibility(View.VISIBLE);
        fabMenuOverlay.animate().cancel();
        fabMenuOverlay.animate().alpha(1f).setDuration(300).setListener(null).start();

        fabMain.animate().cancel();
        fabMain.animate().rotation(45f).setDuration(300).setListener(null).start();

        float radius = 300f; // Increased radius to accommodate labels
        
        setupExpandAnimation(containerBorrow, (float) (radius * Math.cos(Math.toRadians(210))), (float) (radius * Math.sin(Math.toRadians(210))));
        setupExpandAnimation(containerAddTransaction, 0f, -radius);
        setupExpandAnimation(containerAddGroup, (float) (radius * Math.cos(Math.toRadians(-30))), (float) (radius * Math.sin(Math.toRadians(-30))));
    }

    private void setupExpandAnimation(View view, float tx, float ty) {
        view.setVisibility(View.VISIBLE);
        view.setAlpha(0f);
        view.setTranslationX(0f);
        view.setTranslationY(0f);
        view.animate().cancel();
        view.animate()
                .translationX(tx)
                .translationY(ty)
                .alpha(1f)
                .setDuration(300)
                .setListener(null)
                .start();
    }

    private void collapseFabMenu() {
        if (!isFabMenuOpen) return;
        isFabMenuOpen = false;
        
        fabMenuOverlay.animate().cancel();
        fabMenuOverlay.animate().alpha(0f).setDuration(300).setListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) { fabMenuOverlay.setVisibility(View.GONE); }
        }).start();

        fabMain.animate().cancel();
        fabMain.animate().rotation(0f).setDuration(300).setListener(null).start();

        setupCollapseAnimation(containerBorrow);
        setupCollapseAnimation(containerAddTransaction);
        setupCollapseAnimation(containerAddGroup);
    }

    private void setupCollapseAnimation(View view) {
        view.animate().cancel();
        view.animate()
                .translationX(0f)
                .translationY(0f)
                .alpha(0f)
                .setDuration(300)
                .setListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) { view.setVisibility(View.GONE); }
                }).start();
    }

    private void showBorrowNowDialog() {
        Dialog dialog = new Dialog(this);
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

        // Show progress bar initially while loading lenders and their images
        if (dialogProgressBar != null) {
            dialogProgressBar.setVisibility(View.VISIBLE);
        }

        Calendar calendar = Calendar.getInstance();
        dateTV.setText(new SimpleDateFormat("MMMM-dd-yyyy", Locale.getDefault()).format(calendar.getTime()));
        borrowerTV.setText(currentNickname);
        setupLenderRecyclerView(lenderRecyclerView, dialogProgressBar);
        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        borrowBtn.setOnClickListener(v -> {
            String amountStr = amountEditText.getText().toString().trim();
            if (amountStr.isEmpty() || selectedLenderName.isEmpty()) { Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show(); return; }
            try {
                int amount = Integer.parseInt(amountStr);
                if (amount <= 0) { Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show(); return; }
                borrowBtn.setEnabled(false); cancelBtn.setEnabled(false);
                if (dialogProgressBar != null) dialogProgressBar.setVisibility(View.VISIBLE);
                addBorrowTransaction(selectedLenderName, String.valueOf(amount), dateTV.getText().toString(), dialog, dialogProgressBar, borrowBtn, cancelBtn);
            } catch (NumberFormatException e) { Toast.makeText(this, "Invalid amount format", Toast.LENGTH_SHORT).show(); }
        });
        dialog.show();
    }

    private void setupLenderRecyclerView(RecyclerView recyclerView, View dialogProgressBar) {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        recyclerView.setLayoutManager(layoutManager);
        List<User> lenders = new ArrayList<>();
        LenderAdapter adapter = new LenderAdapter(lenders);
        recyclerView.setAdapter(adapter);
        SnapHelper snapHelper = new LinearSnapHelper(); snapHelper.attachToRecyclerView(recyclerView);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) { super.onScrolled(recyclerView, dx, dy); updateLayoutEffect(recyclerView); }
            @Override public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    View centerView = snapHelper.findSnapView(layoutManager);
                    if (centerView != null) {
                        int pos = layoutManager.getPosition(centerView);
                        User selectedLender = adapter.getLenderAt(pos);
                        if (selectedLender != null) selectedLenderName = selectedLender.getUsername();
                    }
                }
            }
        });
        loadLenders(adapter, lenders, recyclerView, dialogProgressBar);
    }

    private void updateLayoutEffect(RecyclerView recyclerView) {
        float midpoint = recyclerView.getWidth() / 2f;
        float d0 = 0f, d1 = 0.9f * midpoint, s0 = 1.6f, s1 = 1.0f, a0 = 1.0f, a1 = 0.5f;
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            if (recyclerView.getLayoutManager() != null) {
                float childMidpoint = (recyclerView.getLayoutManager().getDecoratedRight(child) + recyclerView.getLayoutManager().getDecoratedLeft(child)) / 2f;
                float d = Math.min(d1, Math.abs(midpoint - childMidpoint));
                float scale = s0 + (s1 - s0) * (d - d0) / (d1 - d0);
                float alpha = a0 + (a1 - a0) * (d - d0) / (d1 - d0);
                child.setScaleX(scale); child.setScaleY(scale); child.setAlpha(alpha);
            }
        }
    }

    private void loadLenders(LenderAdapter adapter, List<User> lenders, RecyclerView recyclerView, View dialogProgressBar) {
        DeclareDatabase.getDatabaseReference().addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                lenders.clear(); lenders.add(new User("", "", "", "", new UserBalance())); lenders.add(new User("", "", "", "", new UserBalance()));
                for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                    User user = userSnapshot.getValue(User.class);
                    if (user != null && user.getUsername() != null && !user.getUsername().equals(currentNickname)) {
                        user.setUid(userSnapshot.getKey());
                        lenders.add(user);
                    }
                }
                lenders.add(new User("", "", "", "", new UserBalance())); lenders.add(new User("", "", "", "", new UserBalance()));
                adapter.notifyDataSetChanged();

                // Preload images before hiding progress bar
                adapter.preloadAllImages(MainActivity.this, () -> {
                    runOnUiThread(() -> {
                        if (dialogProgressBar != null) {
                            dialogProgressBar.setVisibility(View.GONE);
                        }
                        if (lenders.size() > 2) {
                            recyclerView.scrollToPosition(2);
                            recyclerView.post(() -> {
                                User firstUser = adapter.getLenderAt(2);
                                if (firstUser != null) selectedLenderName = firstUser.getUsername();
                                updateLayoutEffect(recyclerView);
                            });
                        }
                    });
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError databaseError) { 
                Log.e("MainActivity", "Database error: " + databaseError.getMessage()); 
                if (dialogProgressBar != null) dialogProgressBar.setVisibility(View.GONE);
            }
        });
    }

    private void addBorrowTransaction(String lender, String borrowedAmountStr, String currentDate, Dialog dialog, View dialogProgressBar, Button borrowBtn, Button cancelBtn) {
        Calendar calendar = Calendar.getInstance();
        String currentMonthYear = new SimpleDateFormat("MMMM-yyyy", Locale.getDefault()).format(calendar.getTime());
        String currentDay = new SimpleDateFormat("dd", Locale.getDefault()).format(calendar.getTime());
        long timestamp = System.currentTimeMillis();
        DatabaseReference dayRef = DeclareDatabase.getDBRefBorrows().child(currentMonthYear).child(currentDay);
        String borrowId = dayRef.push().getKey();
        if (borrowId == null) { Toast.makeText(this, "Failed to generate borrow ID", Toast.LENGTH_SHORT).show(); if (dialogProgressBar != null) dialogProgressBar.setVisibility(View.GONE); borrowBtn.setEnabled(true); cancelBtn.setEnabled(true); return; }
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            String borrowerID = currentUser.getUid();
            getUserIDByName(lender, lenderID -> {
                if (lenderID == null) { runOnUiThread(() -> { Toast.makeText(this, "Failed to find lender", Toast.LENGTH_SHORT).show(); if (dialogProgressBar != null) dialogProgressBar.setVisibility(View.GONE); borrowBtn.setEnabled(true); cancelBtn.setEnabled(true); }); return; }
                BorrowNowTransaction borrowNowTransaction = new BorrowNowTransaction(borrowId, borrowerID, lenderID, currentNickname, currentDate, lender, borrowedAmountStr, "For Lender Approval", timestamp);
                dayRef.child(borrowId).setValue(borrowNowTransaction).addOnSuccessListener(unused -> {
                    BalanceHelper.addBorrowerEntry(borrowerID, borrowId, null); BalanceHelper.addLenderEntry(lenderID, borrowId, null);
                    int amount = Integer.parseInt(borrowedAmountStr); BalanceHelper.updateTotaldebt(borrowerID, amount, null); BalanceHelper.updateTotalreceivable(lenderID, amount, null);
                    runOnUiThread(() -> { Toast.makeText(this, "Borrowed successfully", Toast.LENGTH_SHORT).show(); dialog.dismiss(); });
                }).addOnFailureListener(e -> { runOnUiThread(() -> { Toast.makeText(this, "Failed to Borrow", Toast.LENGTH_SHORT).show(); if (dialogProgressBar != null) dialogProgressBar.setVisibility(View.GONE); borrowBtn.setEnabled(true); cancelBtn.setEnabled(true); }); });
            });
        }
    }

    private void getUserIDByName(String name, UserIDCallback callback) {
        FirebaseDatabase.getInstance().getReference("users").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                    if (name.equals(userSnapshot.child("username").getValue(String.class))) { callback.onUserIDRetrieved(userSnapshot.getKey()); return; }
                }
                callback.onUserIDRetrieved(null);
            }
            @Override public void onCancelled(@NonNull DatabaseError databaseError) { callback.onUserIDRetrieved(null); }
        });
    }

    private interface UserIDCallback { void onUserIDRetrieved(String userID); }

    private void showCreateGroupDialog() {
        DeclareDatabase.getDatabaseReference().addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                List<String> usernamesList = new ArrayList<>(); List<String> userIdsList = new ArrayList<>();
                String currentUserId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
                for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                    String username = userSnapshot.child("username").getValue(String.class); String uid = userSnapshot.getKey();
                    if (username != null && uid != null && !uid.equals(currentUserId)) { usernamesList.add(username); userIdsList.add(uid); }
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                View dialogView = LayoutInflater.from(MainActivity.this).inflate(R.layout.dialog_create_group, null);
                builder.setView(dialogView); AlertDialog dialog = builder.create();
                EditText groupNameEditText = dialogView.findViewById(R.id.groupNameEditText);
                LinearLayout usersCheckboxContainer = dialogView.findViewById(R.id.usersCheckboxContainer);
                Button cancelBtn = dialogView.findViewById(R.id.cancelGroupBtn); Button createBtn = dialogView.findViewById(R.id.createGroupBtn);
                List<CheckBox> checkBoxes = new ArrayList<>();
                for (String username : usernamesList) {
                    CheckBox checkBox = new CheckBox(MainActivity.this); checkBox.setText(username); checkBox.setTextColor(getResources().getColor(R.color.darkBlue));
                    checkBox.setPadding(8, 8, 8, 8); checkBoxes.add(checkBox); usersCheckboxContainer.addView(checkBox);
                }
                cancelBtn.setOnClickListener(v -> dialog.dismiss());
                createBtn.setOnClickListener(v -> {
                    String groupName = groupNameEditText.getText().toString().trim();
                    if (groupName.isEmpty()) { Toast.makeText(MainActivity.this, "Please enter a group name", Toast.LENGTH_SHORT).show(); return; }
                    List<String> selectedMemberUids = new ArrayList<>(); List<String> selectedMemberDisplayNames = new ArrayList<>();
                    selectedMemberUids.add(currentUserId); selectedMemberDisplayNames.add(currentNickname.isEmpty() ? "Me" : currentNickname);
                    for (int i = 0; i < checkBoxes.size(); i++) { if (checkBoxes.get(i).isChecked()) { selectedMemberUids.add(userIdsList.get(i)); selectedMemberDisplayNames.add(usernamesList.get(i)); } }
                    if (selectedMemberUids.size() <= 1) { Toast.makeText(MainActivity.this, "Please select at least one member", Toast.LENGTH_SHORT).show(); return; }
                    saveGroupToDatabase(groupName, selectedMemberUids, selectedMemberDisplayNames, currentUserId); dialog.dismiss();
                });
                dialog.show();
            }
            @Override public void onCancelled(@NonNull DatabaseError databaseError) { Toast.makeText(MainActivity.this, "Failed to load users", Toast.LENGTH_SHORT).show(); }
        });
    }

    private void saveGroupToDatabase(String groupName, List<String> memberUids, List<String> memberDisplayNames, String currentUserId) {
        DatabaseReference groupsRef = DeclareDatabase.getDBRefGroups().child(currentUserId);
        String groupId = groupsRef.push().getKey();
        if (groupId != null) {
            PayerGroup newGroup = new PayerGroup(groupId, groupName, memberUids, currentUserId, memberDisplayNames);
            groupsRef.child(groupId).setValue(newGroup).addOnSuccessListener(aVoid -> Toast.makeText(MainActivity.this, "Group created successfully", Toast.LENGTH_SHORT).show());
        }
    }

    public void getTotalMonthSpends(Runnable callback) {
        if (currentNickname == null || currentNickname.isEmpty()) getCurrentNickname(nickname -> fetchTotalMonthSpends(nickname, callback));
        else fetchTotalMonthSpends(currentNickname, callback);
    }

    private void fetchTotalMonthSpends(String username, Runnable callback) {
        DatabaseReference monthYearRef = DeclareDatabase.getDBRefTransaction().child(new SimpleDateFormat("MMMM-yyyy", Locale.getDefault()).format(Calendar.getInstance().getTime()));
        totalMonthSpends = 0;
        monthYearRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot daySnapshot : dataSnapshot.getChildren()) { for (DataSnapshot timeSnapshot : daySnapshot.getChildren()) {
                    Transaction transaction = timeSnapshot.getValue(Transaction.class);
                    if (transaction != null && isUserInvolved(transaction, username)) totalMonthSpends += transaction.getPaymentAmount();
                } }
                TextView tv = findViewById(R.id.totalMonthSpends);
                if (tv != null) tv.setText(CurrencyUtils.formatAmountWithCurrency(totalMonthSpends));
                if (callback != null) callback.run();
            }
            @Override public void onCancelled(@NonNull DatabaseError databaseError) { if (callback != null) callback.run(); }
        });
    }

    @SuppressLint("DefaultLocale")
    public void getEverydaySpends(Runnable callback) {
        if (currentNickname == null || currentNickname.isEmpty()) getCurrentNickname(nickname -> fetchEverydaySpends(nickname, callback));
        else fetchEverydaySpends(currentNickname, callback);
    }

    @SuppressLint("DefaultLocale")
    private void fetchEverydaySpends(String username, Runnable callback) {
        Calendar calendar = Calendar.getInstance(); calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0);
        AtomicInteger daysFetched = new AtomicInteger(0);
        for (int i = 0; i < 7; i++) {
            String currentMonthYear = new SimpleDateFormat("MMMM-yyyy", Locale.getDefault()).format(calendar.getTime());
            String currentDay = new SimpleDateFormat("dd", Locale.getDefault()).format(calendar.getTime());
            final int dayIndex = i;
            DeclareDatabase.getDBRefTransaction().child(currentMonthYear).child(currentDay).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    double ds = 0; for (DataSnapshot ts : dataSnapshot.getChildren()) { Transaction t = ts.getValue(Transaction.class); if (t != null && isUserInvolved(t, username)) ds += t.getPaymentAmount(); }
                    setViewHeightForDay(dayIndex, ds); if (daysFetched.incrementAndGet() == 7 && callback != null) callback.run();
                }
                @Override public void onCancelled(@NonNull DatabaseError databaseError) { if (daysFetched.incrementAndGet() == 7 && callback != null) callback.run(); }
            });
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
    }

    public void getEverydaySpendsForWeek(Calendar weekStart, Runnable callback) {
        if (currentNickname == null || currentNickname.isEmpty()) getCurrentNickname(nickname -> fetchEverydaySpendsForWeek(weekStart, nickname, callback));
        else fetchEverydaySpendsForWeek(weekStart, currentNickname, callback);
    }

    private void fetchEverydaySpendsForWeek(Calendar weekStart, String username, Runnable callback) {
        Calendar calendar = (Calendar) weekStart.clone();
        AtomicInteger daysFetched = new AtomicInteger(0);
        for (int i = 0; i < 7; i++) {
            String cmy = new SimpleDateFormat("MMMM-yyyy", Locale.getDefault()).format(calendar.getTime());
            String cd = new SimpleDateFormat("dd", Locale.getDefault()).format(calendar.getTime());
            final int dayIndex = i;
            DeclareDatabase.getDBRefTransaction().child(cmy).child(cd).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    double ds = 0; for (DataSnapshot ts : dataSnapshot.getChildren()) { Transaction t = ts.getValue(Transaction.class); if (t != null && isUserInvolved(t, username)) ds += t.getPaymentAmount(); }
                    setViewHeightForDay(dayIndex, ds); if (daysFetched.incrementAndGet() == 7 && callback != null) callback.run();
                }
                @Override public void onCancelled(@NonNull DatabaseError de) { if (daysFetched.incrementAndGet() == 7 && callback != null) callback.run(); }
            });
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
    }

    public void setViewHeightForDay(int day, double dailySpends) {
        String dsString = CurrencyUtils.formatAmount(dailySpends);
        int[] ids = {R.id.totalday7, R.id.totalday6, R.id.totalday5, R.id.totalday4, R.id.totalday3, R.id.totalday2, R.id.totalday1};
        int[] barIds = {R.id.day7_bar, R.id.day6_bar, R.id.day5_bar, R.id.day4_bar, R.id.day3_bar, R.id.day2_bar, R.id.day1_bar};
        
        TextView tv = findViewById(ids[day]); if (tv != null) tv.setText(dsString);
        int h = (dailySpends >= 1000) ? 300 : (dailySpends <= 50) ? 17 : (int) (dailySpends / 3);
        View v = findViewById(barIds[day]); if (v != null) { ViewGroup.LayoutParams lp = v.getLayoutParams(); lp.height = h; v.setLayoutParams(lp); }
    }

    public void getRecentTransaction(Runnable callback) {
        if (currentNickname == null || currentNickname.isEmpty()) getCurrentNickname(nickname -> fetchRecentTransactions(nickname, callback));
        else fetchRecentTransactions(currentNickname, callback);
    }

    private void fetchRecentTransactions(String username, Runnable callback) {
        recentTransactionList.clear();
        Calendar calendar = Calendar.getInstance();
        AtomicInteger daysFetched = new AtomicInteger(0);
        for (int i = 0; i < 7; i++) {
            String cmy = new SimpleDateFormat("MMMM-yyyy", Locale.getDefault()).format(calendar.getTime());
            String cd = new SimpleDateFormat("dd", Locale.getDefault()).format(calendar.getTime());
            final String fmy = cmy, fd = cd;
            DeclareDatabase.getDBRefTransaction().child(cmy).child(cd).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot ds) {
                    for (DataSnapshot ts : ds.getChildren()) {
                        Transaction t = ts.getValue(Transaction.class);
                        if (t != null && isUserInvolved(t, username)) {
                            String tk = ts.getKey(); String[] p = fmy.split("-");
                            recentTransactionList.add(new RecentTransaction(p[0] + " - " + fd, t.getTransactionType(), t.getMultilineStr(), CurrencyUtils.formatAmountWithCurrency(t.getPaymentAmount()), getTransactionIcon(t.getTransactionType()), p[1] + "-" + p[0] + "-" + fd + " " + tk, t.getPayorsDisplayNames() != null ? t.getPayorsDisplayNames() : t.getPayorsList(), t.getPayorsList(), t.getAmountsPaidList(), t.getTotalIndividualPayment(), null, t.getPosterDisplayName() != null ? t.getPosterDisplayName() : t.getUsernamePost(), t.getUsernamePost(), fmy, fd, tk));
                        }
                    }
                    if (daysFetched.incrementAndGet() == 7) {
                        recentTransactionList.sort((t1, t2) -> (t1.getSortDateTime() != null && t2.getSortDateTime() != null) ? t2.getSortDateTime().compareTo(t1.getSortDateTime()) : 0);
                        RecyclerView rv = findViewById(R.id.transactionListRecycler); if (rv != null) { 
                            recentTransactionAdapter = new RecentTransactionAdapter(recentTransactionList, MainActivity.this::onTransactionTap); 
                            rv.setAdapter(recentTransactionAdapter); 
                            rv.setLayoutManager(new LinearLayoutManager(MainActivity.this)); 
                            // Preload images for all transactions in the list
                            recentTransactionAdapter.preloadAllImages(MainActivity.this);
                        }
                        if (callback != null) callback.run();
                    }
                }
                @Override public void onCancelled(@NonNull DatabaseError de) { if (daysFetched.incrementAndGet() == 7 && callback != null) callback.run(); }
            });
            calendar.add(Calendar.DAY_OF_YEAR, -1);
        }
    }

    private int getTransactionIcon(String type) {
        if ("Electricity".equals(type)) return R.drawable.lightning_bolt; if ("Water".equals(type)) return R.drawable.faucet; if ("Rent".equals(type)) return R.drawable.house; if ("Internet".equals(type)) return R.drawable.internet; if ("Online Shopping".equals(type)) return R.drawable.online_shopping; if ("Travel".equals(type)) return R.drawable.travel; if ("Groceries".equals(type)) return R.drawable.groceries; if ("Foods".equals(type)) return R.drawable.hamburger; if ("House Necessity".equals(type)) return R.drawable.necessities; if ("Transportation".equals(type)) return R.drawable.vehicles; return R.drawable.others;
    }

    public void getDebtList(String selectedStatus, DebtNumCallback callback, DebtTransactionAdapter.OnBorrowerActionListener actionListener) {
        debtList.clear(); String currentUserId = Objects.requireNonNull(mAuth.getCurrentUser()).getUid();
        DeclareDatabase.getDBRefBorrows().addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                for (DataSnapshot ms : ds.getChildren()) { String my = ms.getKey(); for (DataSnapshot days : ms.getChildren()) { String d = days.getKey(); for (DataSnapshot bs : days.getChildren()) { BorrowNowTransaction bnt = bs.getValue(BorrowNowTransaction.class); if (bnt != null && Objects.equals(bnt.getBorrowerID(), currentUserId)) { if (!Objects.equals(bnt.getStatus(), "Removed") && !Objects.equals(bnt.getStatus(), "Payment Denied") && shouldIncludeForDebtStatus(bnt.getStatus(), selectedStatus)) addDebtTransactionFromBorrowNow(bnt, my, d, bs.getKey()); } } } }
                debtList.sort((o1, o2) -> { try { SimpleDateFormat f = new SimpleDateFormat("MMM-dd-yyyy", Locale.ENGLISH); return f.parse(o2.getDate()).compareTo(f.parse(o1.getDate())); } catch (Exception e) { return 0; } });
                RecyclerView rv = findViewById(R.id.debtRecyclerList); if (rv != null) { rv.setAdapter(new DebtTransactionAdapter(debtList, actionListener != null ? actionListener : new DebtTransactionAdapter.OnBorrowerActionListener() { @Override public void onPayClicked(BorrowTransaction t, int p) {} @Override public void onRemoveClicked(BorrowTransaction t, int p) {} @Override public void onTryAgainClicked(BorrowTransaction t, int p) {} })); rv.setLayoutManager(new LinearLayoutManager(MainActivity.this)); }
                debtNum = debtList.size(); callback.onDebtNumReceived(debtNum);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
    }

    private void addDebtTransactionFromBorrowNow(BorrowNowTransaction borrowNowTransaction, String monthYear, String day, String borrowId) {
        String date = changeFormatDate(borrowNowTransaction.getDate()); String psd = (borrowNowTransaction.getPaymentSentDate() > 0) ? new SimpleDateFormat("MMM-dd-yyyy", Locale.ENGLISH).format(new Date(borrowNowTransaction.getPaymentSentDate())) : null;
        BorrowTransaction bt = new BorrowTransaction(date, borrowNowTransaction.getLender(), borrowNowTransaction.getBorrowedAmountStr(), borrowNowTransaction.getStatus()); bt.setPaymentSentDate(psd); bt.setBorrowId(borrowId); bt.setMonthYear(monthYear); bt.setDay(day); debtList.add(bt);
    }

    public void getDebtListMonthly(String selectedMonth, String selectedStatus, DebtNumCallback callback, DebtTransactionAdapter.OnBorrowerActionListener actionListener) {
        debtList.clear(); if (selectedMonth == null || selectedMonth.equals("All")) return; String uid = Objects.requireNonNull(mAuth.getCurrentUser()).getUid();
        DeclareDatabase.getDBRefBorrows().child(selectedMonth).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                for (DataSnapshot dayS : ds.getChildren()) { String d = dayS.getKey(); for (DataSnapshot bs : dayS.getChildren()) { BorrowNowTransaction bnt = bs.getValue(BorrowNowTransaction.class); if (bnt != null && Objects.equals(bnt.getBorrowerID(), uid) && !Objects.equals(bnt.getStatus(), "Removed") && !Objects.equals(bnt.getStatus(), "Payment Denied") && shouldIncludeForDebtStatus(bnt.getStatus(), selectedStatus)) addDebtTransactionFromBorrowNow(bnt, selectedMonth, d, bs.getKey()); } }
                debtList.sort((o1, o2) -> { try { SimpleDateFormat f = new SimpleDateFormat("MMM-dd-yyyy", Locale.ENGLISH); return f.parse(o2.getDate()).compareTo(f.parse(o1.getDate())); } catch (Exception e) { return 0; } });
                RecyclerView rv = findViewById(R.id.debtRecyclerList); if (rv != null) { rv.setAdapter(new DebtTransactionAdapter(debtList, actionListener != null ? actionListener : new DebtTransactionAdapter.OnBorrowerActionListener() { @Override public void onPayClicked(BorrowTransaction t, int p) {} @Override public void onRemoveClicked(BorrowTransaction t, int p) {} @Override public void onTryAgainClicked(BorrowTransaction t, int p) {} })); rv.setLayoutManager(new LinearLayoutManager(MainActivity.this)); }
                debtNum = debtList.size(); callback.onDebtNumReceived(debtNum);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
    }

    public void getOwedList(String selectedStatus, OwedNumCallback callback, OwedTransactionAdapter.OnLenderActionListener actionListener) {
        owedList.clear(); String uid = Objects.requireNonNull(mAuth.getCurrentUser()).getUid();
        DeclareDatabase.getDBRefBorrows().addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                for (DataSnapshot ms : ds.getChildren()) { String my = ms.getKey(); for (DataSnapshot days : ms.getChildren()) { String d = days.getKey(); for (DataSnapshot bs : days.getChildren()) { BorrowNowTransaction bnt = bs.getValue(BorrowNowTransaction.class); if (bnt != null && Objects.equals(bnt.getLenderID(), uid) && !Objects.equals(bnt.getStatus(), "Declined") && !Objects.equals(bnt.getStatus(), "Payment Denied") && !Objects.equals(bnt.getStatus(), "Removed") && shouldIncludeForStatus(bnt.getStatus(), selectedStatus)) addOwedTransactionFromBorrowNow(bnt, my, d, bs.getKey()); } } }
                owedList.sort((o1, o2) -> { try { SimpleDateFormat f = new SimpleDateFormat("MMM-dd-yyyy", Locale.ENGLISH); return f.parse(o2.getDate()).compareTo(f.parse(o1.getDate())); } catch (Exception e) { return 0; } });
                RecyclerView rv = findViewById(R.id.owedRecyclerList); if (rv != null) { rv.setAdapter(new OwedTransactionAdapter(owedList, actionListener != null ? actionListener : new OwedTransactionAdapter.OnLenderActionListener() { @Override public void onNotYetClicked(OwedTransaction t, int p) {} @Override public void onReceivedClicked(OwedTransaction t, int p) {} @Override public void onDeclineClicked(OwedTransaction t, int p) {} @Override public void onApprovedClicked(OwedTransaction t, int p) {} })); rv.setLayoutManager(new LinearLayoutManager(MainActivity.this)); }
                owedNum = owedList.size(); callback.onOwedNumReceived(owedNum);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
    }

    public void getCurrentNickname(CurrentNicknameCallback callback) {
        String uid = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
        DeclareDatabase.getDatabaseReference().child(uid).child("username").addListenerForSingleValueEvent(new ValueEventListener() {
            public void onDataChange(@NonNull DataSnapshot ds) { currentNickname = ds.exists() ? ds.getValue(String.class) : ""; callback.onCurrentNicknameReceived(currentNickname); }
            @Override public void onCancelled(@NonNull DatabaseError e) { callback.onCurrentNicknameReceived(""); }
        });
    }

    public String changeFormatDate(String date) { try { Date d = new SimpleDateFormat("MMMM-dd-yyyy", Locale.ENGLISH).parse(date); return new SimpleDateFormat("MMM-dd-yyyy", Locale.getDefault()).format(d); } catch (Exception e) { return date; } }

    private boolean shouldIncludeForStatus(String s, String ss) { if ("All".equals(ss)) return true; if ("Pending".equals(ss)) return "Pending Payment".equals(s) || "For Lender Approval".equals(s); return Objects.equals(s, ss); }
    private boolean shouldIncludeForDebtStatus(String s, String ss) { if ("All".equals(ss)) return true; if ("Pending".equals(ss)) return "Pending Payment".equals(s) || "For Lender Approval".equals(s) || "Declined".equals(s); return Objects.equals(s, ss); }

    private void addOwedTransactionFromBorrowNow(BorrowNowTransaction bnt, String my, String d, String bid) {
        String date = changeFormatDate(bnt.getDate()); String psd = (bnt.getPaymentSentDate() > 0) ? new SimpleDateFormat("MMM-dd-yyyy", Locale.ENGLISH).format(new Date(bnt.getPaymentSentDate())) : null;
        owedList.add(new OwedTransaction(date, bnt.getBorrowerName() != null ? bnt.getBorrowerName() : "Unknown", bnt.getBorrowedAmountStr(), bnt.getStatus(), psd, bid, my, d));
    }

    public void getOwedListMonthly(String sm, String ss, OwedNumCallback callback, OwedTransactionAdapter.OnLenderActionListener actionListener) {
        owedList.clear(); if (sm == null || sm.equals("All")) return; String uid = Objects.requireNonNull(mAuth.getCurrentUser()).getUid();
        DeclareDatabase.getDBRefBorrows().child(sm).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                for (DataSnapshot dayS : ds.getChildren()) { String d = dayS.getKey(); for (DataSnapshot bs : dayS.getChildren()) { BorrowNowTransaction bnt = bs.getValue(BorrowNowTransaction.class); if (bnt != null && Objects.equals(bnt.getLenderID(), uid) && !Objects.equals(bnt.getStatus(), "Declined") && !Objects.equals(bnt.getStatus(), "Payment Denied") && !Objects.equals(bnt.getStatus(), "Removed") && shouldIncludeForStatus(bnt.getStatus(), ss)) addOwedTransactionFromBorrowNow(bnt, sm, d, bs.getKey()); } }
                owedList.sort((o1, o2) -> { try { SimpleDateFormat f = new SimpleDateFormat("MMM-dd-yyyy", Locale.ENGLISH); return f.parse(o2.getDate()).compareTo(f.parse(o1.getDate())); } catch (Exception e) { return 0; } });
                RecyclerView rv = findViewById(R.id.owedRecyclerList); if (rv != null) { rv.setAdapter(new OwedTransactionAdapter(owedList, actionListener != null ? actionListener : new OwedTransactionAdapter.OnLenderActionListener() { @Override public void onNotYetClicked(OwedTransaction t, int p) {} @Override public void onReceivedClicked(OwedTransaction t, int p) {} @Override public void onDeclineClicked(OwedTransaction t, int p) {} @Override public void onApprovedClicked(OwedTransaction t, int p) {} })); rv.setLayoutManager(new LinearLayoutManager(MainActivity.this)); }
                owedNum = owedList.size(); callback.onOwedNumReceived(owedNum);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) { }
        });
    }
}
