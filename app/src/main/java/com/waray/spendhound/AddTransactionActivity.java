package com.waray.spendhound;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class AddTransactionActivity extends AppCompatActivity {

    private LinearLayout container;
    private Button btnAdd;
    private Button addTransactionbtn;
    private Spinner payorSpinner;
    private Spinner transactionTypeSpinner;
    private String transactionType;
    public String paymentAmountStr;
    public String multilineStr;
    private ProgressBar progressBar;
    public List<String> usernames;
    public FirebaseAuth mAuth;
    private List<View> rows;
    public List<String> payorsList;
    public List<Integer> amountsPaidList;
    public Integer totalAmountPaid = 0;
    public Integer paymentAmount;
    private EditText paymentAmountEditText;
    private EditText editTextTextMultiLine;
    private TextView individualPayment;
    private int totalIndividualPayment, totalBalanced, totalUnpaid, totalOwed, totalDept;
    public String usernamePost;
    private ArrayList<RecentTransaction> recentTransactionList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_transcation);

        // Get the Firebase Authentication instance
        mAuth = DeclareDatabase.getAuth();

        // Check the user's authentication state
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            // User is not authenticated, you can redirect them to the login activity
            Intent intent = new Intent(AddTransactionActivity.this, LoginActivity.class);
            startActivity(intent);
            finish(); // Finish this activity to prevent returning to it when pressing back
            return;
        }

        transactionTypeSpinner = findViewById(R.id.transactionType);
        String[] transactionTypes = getResources().getStringArray(R.array.transactionTypes_String);
        SpinnerItem adapter = new SpinnerItem(this, Arrays.asList(transactionTypes));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        transactionTypeSpinner.setAdapter(adapter);

        container = findViewById(R.id.container);
        btnAdd = findViewById(R.id.btnAdd);
        rows = new ArrayList<>();
        progressBar = findViewById(R.id.progressBar);

        fetchUsernamesAndSetupInitialRow();

        btnAdd.setOnClickListener(v -> {
            if (usernames != null && rows.size() < usernames.size() - 1) {
                addRow();
            } else {
                Toast.makeText(AddTransactionActivity.this, "You can't add more payors.", Toast.LENGTH_SHORT).show();
            }
        });

        addTransactionbtn = findViewById(R.id.addTransactionbtn);
        addTransactionbtn.setOnClickListener(v -> addTransaction());

        paymentAmountEditText = findViewById(R.id.paymentAmount);
        individualPayment = findViewById(R.id.individualPayment);
        setupIndividualPaymentCalculator();

        detailsCharacterCount();
        exitEditText();
    }

    private void fetchUsernamesAndSetupInitialRow() {
        DatabaseReference databaseReference = DeclareDatabase.getDatabaseReference();
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                usernames = new ArrayList<>();
                usernames.add("Select a payor:");
                for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                    String username = userSnapshot.child("username").getValue(String.class);
                    if (username != null) {
                        usernames.add(username);
                    }
                }
                addRow(); // Add the initial row after fetching usernames
                btnAdd.setEnabled(true); // Enable the button to add more payors
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("FirebaseDatabase", "Database read error occurred: " + databaseError.getMessage());
                Toast.makeText(getApplicationContext(), "Failed to load users.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void addRow() {
        LayoutInflater inflater = LayoutInflater.from(this);
        View row = inflater.inflate(R.layout.row_layout, container, false);

        payorSpinner = row.findViewById(R.id.payor);
        if (usernames != null && !usernames.isEmpty()) {
            SpinnerItem adapter = new SpinnerItem(AddTransactionActivity.this, usernames);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            payorSpinner.setAdapter(adapter);
        }

        Button btnMinus = row.findViewById(R.id.closeBtn);
        btnMinus.setOnClickListener(v -> removeRow(row));

        Drawable roundedDrawable = getResources().getDrawable(R.drawable.rounded_alternating_row);
        ViewCompat.setBackground(row, roundedDrawable);

        rows.add(row);
        container.addView(row);
    }

    private void removeRow(View row) {
        container.removeView(row);
        rows.remove(row);
    }

    private void addTransaction() {
        progressBar.setVisibility(View.VISIBLE);
        transactionType = transactionTypeSpinner.getSelectedItem().toString();
        paymentAmountEditText = findViewById(R.id.paymentAmount);
        editTextTextMultiLine = findViewById(R.id.editTextTextMultiLine);

        paymentAmountStr = paymentAmountEditText.getText().toString();
        multilineStr = editTextTextMultiLine.getText().toString();

        if (TextUtils.isEmpty(paymentAmountStr)) {
            paymentAmount = 0;
        } else {
            paymentAmount = Integer.parseInt(paymentAmountStr);
        }

        payorsList = new ArrayList<>();
        amountsPaidList = new ArrayList<>();

        if ("Select a transaction:".equals(transactionType) || paymentAmount == 0) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);
            return;
        }

        HashSet<String> uniquePayors = new HashSet<>();
        totalAmountPaid = 0;

        for (View row : rows) {
            Spinner payorSpinner = row.findViewById(R.id.payor);
            EditText amountPaidEditText = row.findViewById(R.id.amountPaid);

            String payor = payorSpinner.getSelectedItem().toString();
            String amountPaidStr = amountPaidEditText.getText().toString().trim();

            if ("Select a payor:".equals(payor) || TextUtils.isEmpty(amountPaidStr)) {
                Toast.makeText(AddTransactionActivity.this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
                progressBar.setVisibility(View.GONE);
                return;
            }

            if (!uniquePayors.add(payor)) {
                Toast.makeText(AddTransactionActivity.this, "Duplicate payor detected: " + payor, Toast.LENGTH_SHORT).show();
                progressBar.setVisibility(View.GONE);
                return;
            }

            try {
                int amountPaid = Integer.parseInt(amountPaidStr);
                payorsList.add(payor);
                amountsPaidList.add(amountPaid);
                totalAmountPaid += amountPaid;
            } catch (NumberFormatException e) {
                Toast.makeText(AddTransactionActivity.this, "Invalid amount format for payor: " + payor, Toast.LENGTH_SHORT).show();
                progressBar.setVisibility(View.GONE);
                return;
            }
        }

        if (!paymentAmount.equals(totalAmountPaid)) {
            Toast.makeText(AddTransactionActivity.this, "Total amount paid does not match the payment amount.", Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);
            return;
        }

        String currentUserID = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference usersRef = DeclareDatabase.getDatabaseReference().child(currentUserID);

        usersRef.child("username").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    usernamePost = dataSnapshot.getValue(String.class);
                    saveTransaction();
                } else {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(AddTransactionActivity.this, "Username not found.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                progressBar.setVisibility(View.GONE);
                Log.e("FirebaseDatabase", "Database read error occurred: " + databaseError.getMessage());
            }
        });
    }

    private void saveTransaction() {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM-yyyy", Locale.getDefault());
        SimpleDateFormat dayFormat = new SimpleDateFormat("dd", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

        String currentMonthYear = dateFormat.format(calendar.getTime());
        String currentDay = dayFormat.format(calendar.getTime());
        String currentTime = timeFormat.format(calendar.getTime());

        DatabaseReference timestampRef = DeclareDatabase.getDBRefTransaction()
                .child(currentMonthYear)
                .child(currentDay)
                .child(currentTime);

        Transaction transaction = new Transaction(transactionType, paymentAmount, multilineStr, payorsList, amountsPaidList, usernamePost, totalIndividualPayment);

        timestampRef.setValue(transaction)
                .addOnSuccessListener(aVoid -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(AddTransactionActivity.this, "Transaction added successfully", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(AddTransactionActivity.this, MainActivity.class);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(AddTransactionActivity.this, "Failed to add transaction", Toast.LENGTH_SHORT).show();
                });
    }


    private void setupIndividualPaymentCalculator() {
        paymentAmountEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                calculateAndDisplayIndividualPayment();
            }
        });
    }

    private void calculateAndDisplayIndividualPayment() {
        String amountStr = paymentAmountEditText.getText().toString();
        if (!TextUtils.isEmpty(amountStr) && usernames != null && usernames.size() > 1) {
            try {
                int amount = Integer.parseInt(amountStr);
                int numberOfUsers = usernames.size() - 1; // Exclude "Select a payor:"
                totalIndividualPayment = amount / numberOfUsers;
                individualPayment.setText("₱ " + totalIndividualPayment + ".00");
            } catch (NumberFormatException e) {
                individualPayment.setText("₱ 0.00");
            }
        } else {
            individualPayment.setText("₱ 0.00");
        }
    }

    private void detailsCharacterCount() {
        // Implement character count logic here if needed
    }

    private void exitEditText() {
        // Implement exit edit text logic here if needed
    }
}