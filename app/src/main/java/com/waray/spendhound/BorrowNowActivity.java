package com.waray.spendhound;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SnapHelper;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class BorrowNowActivity extends AppCompatActivity {
    private RecyclerView lenderRecyclerView;
    private TextView date, borrower;
    public String currentNickname, lender, currentDate, status, borrowedAmountSTR, borrowerID, lenderID;
    private Integer borrowedAmount = 0;
    private ProgressBar progressBar;
    private Button borrowBtn;
    private DatabaseReference usersRef;
    private LenderAdapter adapter;
    private List<User> lenders;


    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_borrow_now);
        lenderRecyclerView = findViewById(R.id.lenderRecyclerView);
        date = findViewById(R.id.dialogBorrowDate);
        borrower = findViewById(R.id.dialogBorrower);
        progressBar = findViewById(R.id.progressBar);
        borrowBtn = findViewById(R.id.dialogBorrowBtn);
        status = "For Lender Approval";
        usersRef = FirebaseDatabase.getInstance().getReference("users");

        setDate();
        setupLenderRecyclerView();
        borrowBtnClicked();
        exitEditText();
        loadNickname();

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    private void setupLenderRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        lenderRecyclerView.setLayoutManager(layoutManager);
        
        lenders = new ArrayList<>();
        adapter = new LenderAdapter(lenders);
        lenderRecyclerView.setAdapter(adapter);

        SnapHelper snapHelper = new LinearSnapHelper();
        snapHelper.attachToRecyclerView(lenderRecyclerView);

        lenderRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
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
                            lender = selectedLender.getUsername();
                        }
                    }
                }
            }
        });

        getUsers();
    }

    private void updateLayoutEffect(RecyclerView recyclerView) {
        float midpoint = recyclerView.getWidth() / 2f;
        float d0 = 0f;
        float d1 = 0.9f * midpoint;
        float s0 = 1.6f; // Increased selected scale from 1.3f to 1.6f
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

    private void loadNickname() {
        String currentUserID = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
        DatabaseReference usersRef = DeclareDatabase.getDatabaseReference().child(currentUserID);
        usersRef.child("username").addListenerForSingleValueEvent(new ValueEventListener() {
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    currentNickname = dataSnapshot.getValue(String.class);
                    borrower.setText(currentNickname);
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("FirebaseDatabase", "Database read error: " + databaseError.getMessage());
            }
        });
    }

    public void getUsers() {
        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                lenders.clear();
                // Padding for snapping (2 items on each side to center the actual users)
                lenders.add(new User("", "", "", "", new UserBalance()));
                lenders.add(new User("", "", "", "", new UserBalance()));

                for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                    User user = userSnapshot.getValue(User.class);
                    if (user != null && user.getUsername() != null && !user.getUsername().equals(currentNickname)) {
                        lenders.add(user);
                    }
                }

                lenders.add(new User("", "", "", "", new UserBalance()));
                lenders.add(new User("", "", "", "", new UserBalance()));

                adapter.notifyDataSetChanged();
                
                // Initial selection and layout update
                if (lenders.size() > 2) {
                    lenderRecyclerView.scrollToPosition(2);
                    lenderRecyclerView.post(() -> {
                        User firstUser = adapter.getLenderAt(2);
                        if (firstUser != null) {
                            lender = firstUser.getUsername();
                        }
                        updateLayoutEffect(lenderRecyclerView);
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("FirebaseDatabase", "Database read error: " + databaseError.getMessage());
                Toast.makeText(getApplicationContext(), "Failed to load users", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setDate() {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM-dd-yyyy", Locale.getDefault());
        currentDate = dateFormat.format(calendar.getTime());
        date.setText(currentDate);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void addBorrowTransaction() {
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
            Toast.makeText(BorrowNowActivity.this, "Failed to generate borrow ID", Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);
            return;
        }

        DatabaseReference borrowRef = dayRef.child(borrowId);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && lender != null && !lender.isEmpty()) {
            borrowerID = currentUser.getUid();

            getUserIDByName(lender, new UserIDCallback() {
                @Override
                public void onUserIDRetrieved(String getLenderID) {
                    lenderID = getLenderID;

                    BorrowNowTransaction borrowNowTransaction = new BorrowNowTransaction(
                            borrowId,
                            borrowerID,
                            lenderID,
                            currentNickname,
                            currentDate,
                            lender,
                            borrowedAmountSTR,
                            status,
                            timestamp
                    );

                    borrowRef.setValue(borrowNowTransaction).addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void unused) {
                            BalanceHelper.addBorrowerEntry(borrowerID, borrowId, null);
                            BalanceHelper.addLenderEntry(lenderID, borrowId, null);

                            int amount = Integer.parseInt(borrowedAmountSTR);
                            // Update with new balance field methods
                            BalanceHelper.updateTotaldebt(borrowerID, amount, null);
                            BalanceHelper.updateTotalreceivable(lenderID, amount, null);

                            Toast.makeText(BorrowNowActivity.this, "Borrowed successfully", Toast.LENGTH_SHORT).show();
                            progressBar.setVisibility(View.GONE);
                            finish();
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(BorrowNowActivity.this, "Failed to Borrow", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        }
    }

    private void borrowBtnClicked() {
        borrowBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText borrowEditText = findViewById(R.id.dialogBorrowEditText);
                String borrowedAmountStr = borrowEditText.getText().toString();
                if (!borrowedAmountStr.isEmpty() && lender != null && !lender.isEmpty()) {
                    borrowedAmount = Integer.parseInt(borrowedAmountStr);
                    borrowedAmountSTR = String.valueOf(borrowedAmount);
                    
                    if (borrowedAmount == 0) {
                        Toast.makeText(BorrowNowActivity.this, "Please enter a valid amount", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    progressBar.setVisibility(View.VISIBLE);
                    addBorrowTransaction();
                } else {
                    Toast.makeText(BorrowNowActivity.this, "Please select a lender and enter amount", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    public void exitEditText(){
        final EditText borrowEditText = findViewById(R.id.dialogBorrowEditText);
        borrowEditText.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                v.performClick();
                return false;
            }
        });

        View rootView = findViewById(android.R.id.content);
        rootView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                hideKeyboard(borrowEditText);
                return false;
            }
        });
    }

    public void getUserIDByName(String name, UserIDCallback callback) {
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
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("FirebaseDatabase", "Database error: " + databaseError.getMessage());
            }
        });
    }

    public interface UserIDCallback {
        void onUserIDRetrieved(String getLenderID);
    }

    private void hideKeyboard(EditText editText) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
        }
    }
}
