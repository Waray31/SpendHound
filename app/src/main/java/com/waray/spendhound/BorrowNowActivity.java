package com.waray.spendhound;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.waray.spendhound.ui.borrow.BorrowFragment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class BorrowNowActivity extends AppCompatActivity {
    public List<String> usernames;
    private Spinner borrowSpinner;
    private TextView date, borrower;
    public String currentNickname, lender, currentDate, status, borrowedAmountSTR, borrowerID, lenderID;
    private Integer borrowedAmount = 0;
    private ProgressBar progressBar;
    private Button borrowBtn;
    private DatabaseReference usersRef;


    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_borrow_now);
        borrowSpinner = findViewById(R.id.borrowee);
        date = findViewById(R.id.date);
        borrower = findViewById(R.id.borrower);
        progressBar = findViewById(R.id.progressBar);
        borrowBtn = findViewById(R.id.borrowBtn);
        status = "For Lender Approval";

        setDate();
        getUsers();
        borrowBtnClicked();
        exitEditText();
        loadNickname();

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    private void loadNickname() {
        String currentUserID = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
        DatabaseReference usersRef = DeclareDatabase.getDatabaseReference().child(currentUserID);
        usersRef.child("username").addListenerForSingleValueEvent(new ValueEventListener() {
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    // Get the username from the dataSnapshot and assign it to usernamePost
                    currentNickname = dataSnapshot.getValue(String.class);
                    Log.d("FirebaseDatabase", "Nickname loaded: " + currentNickname);

                    // Update the TextView with the loaded nickname
                    borrower.setText(currentNickname);
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

    public void getUsers() {
        DatabaseReference databaseReference = DeclareDatabase.getDatabaseReference();
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                usernames = new ArrayList<>();
                usernames.add("Select a lender:"); // Add the default value
                for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                    String username = userSnapshot.child("username").getValue(String.class);
                    if (username != null && !username.equals(currentNickname)) {
                        usernames.add(username);
                    }
                }
                SpinnerItem adapter = new SpinnerItem(BorrowNowActivity.this, usernames);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                borrowSpinner.setAdapter(adapter);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // Handle database read error
                String errorMessage = "Database read error occurred: " + databaseError.getMessage();
                Log.e("FirebaseDatabase", errorMessage);

                // You can also display a Toast message to inform the user
                Toast.makeText(getApplicationContext(), errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setDate() {
        // Get the current date and time
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM-dd-yyyy", Locale.getDefault());
        currentDate = dateFormat.format(calendar.getTime());
        date.setText(currentDate);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // Handle the "Up" button press (e.g., navigate back to BorrowFragment)
                finish(); // Finish the activity to return to the previous fragment
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void addBorrowTransaction() {

        // Get the current date and time
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM-yyyy", Locale.getDefault());
        SimpleDateFormat dayFormat = new SimpleDateFormat("dd", Locale.getDefault());

        String currentMonthYear = dateFormat.format(calendar.getTime());
        String currentDay = dayFormat.format(calendar.getTime());
        long timestamp = System.currentTimeMillis();

        // New path structure: borrows/{month}/{day}/{borrowId}
        DatabaseReference databaseReference = DeclareDatabase.getDBRefBorrows();
        DatabaseReference monthYearRef = databaseReference.child(currentMonthYear);
        DatabaseReference dayRef = monthYearRef.child(currentDay);

        // Generate unique borrowId using push()
        String borrowId = dayRef.push().getKey();
        if (borrowId == null) {
            Toast.makeText(BorrowNowActivity.this, "Failed to generate borrow ID", Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);
            return;
        }

        DatabaseReference borrowRef = dayRef.child(borrowId);

        // Get the current authenticated user
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            if (lender != null) {
                String getBorrowerID = currentUser.getUid();
                usersRef = FirebaseDatabase.getInstance().getReference("users");
                borrowerID = getBorrowerID;

                getUserIDByName(lender, new UserIDCallback() {
                    @Override
                    public void onUserIDRetrieved(String getLenderID) {
                        lenderID = getLenderID;

                        // Create transaction with new structure including borrowId, borrowerName, and timestamp
                        BorrowNowTransaction borrowNowTransaction = new BorrowNowTransaction(
                                borrowId,
                                borrowerID,
                                lenderID,
                                currentNickname, // borrowerName for display
                                currentDate,
                                lender, // lenderName for display
                                borrowedAmountSTR,
                                status,
                                timestamp
                        );

                        borrowRef.setValue(borrowNowTransaction).addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void unused) {
                                // Update userBorrows index for borrower
                                BalanceHelper.addBorrowerEntry(borrowerID, borrowId, null);

                                // Update userBorrows index for lender
                                BalanceHelper.addLenderEntry(lenderID, borrowId, null);

                                // Update borrower's debt (they owe money)
                                int amount = Integer.parseInt(borrowedAmountSTR);
                                BalanceHelper.updateDebt(borrowerID, amount, null);
                                BalanceHelper.updateTotalBorrowed(borrowerID, amount, null);

                                // Update lender's owed (they are owed money)
                                BalanceHelper.updateOwed(lenderID, amount, null);
                                BalanceHelper.updateTotalLent(lenderID, amount, null);

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
    }

    private void borrowBtnClicked() {
        borrowBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText borrowEditText = findViewById(R.id.borrowEditText);
                String borrowedAmountStr = borrowEditText.getText().toString();
                if (!borrowedAmountStr.isEmpty()) {
                    borrowedAmount = Integer.parseInt(borrowedAmountStr);
                    borrowedAmountSTR = String.valueOf(borrowedAmount);
                    progressBar.setVisibility(View.VISIBLE);
                    lender = borrowSpinner.getSelectedItem().toString();

                    if ("Select a lender:".equals(lender) || borrowedAmount == 0) {
                        Toast.makeText(BorrowNowActivity.this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
                        progressBar.setVisibility(View.GONE);
                    } else {
                        addBorrowTransaction();
                        progressBar.setVisibility(View.GONE);
                    }
                } else {
                    borrowedAmount = 0;
                    borrowedAmountSTR = String.valueOf(borrowedAmount);
                }
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    public void exitEditText(){
        final EditText borrowEditText = findViewById(R.id.borrowEditText);
        borrowEditText.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Consume the touch event on the EditText to prevent it from being intercepted
                v.performClick();
                return false;
            }
        });

        // Add an OnTouchListener to the root layout (or any other layout that covers the whole screen)
        View rootView = findViewById(android.R.id.content);
        rootView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Hide the keyboard when the user touches outside the EditText
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
                        lenderID = userSnapshot.getKey();
                        callback.onUserIDRetrieved(lenderID);
                        break;
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
        imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
    }

}


