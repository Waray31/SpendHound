package com.waray.spendhound;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class PendingStatusActivity extends AppCompatActivity implements BorrowerListTransactionAdapter.OnTransactionStatusUpdatedListener, PayerListTransactionAdapter.OnTransactionStatusUpdatedListener {
    private TextView borrowerListTV, payerListTV, allTV;
    private ScrollView borrowerListScrollView, payerListScrollView;
    private boolean borrowerPayerClicked;
    private ImageView backBtn, borrowerImg, payerImg;
    private LinearLayout borrowerListLinearLayout, payerListLinearLayout, borrowerListBtn, payerListBtn;
    public int borrowerNum, payerNum;
    public String currentNickname, currentNickname2;
    private RecyclerView borrowerListRecyclerView, payerListRecyclerView;
    private BorrowerListTransactionAdapter adapter;
    private PayerListTransactionAdapter adapterPayer;
    private List<BorrowerListTransaction> borrowerListTransactions, payerListTransactions;
    private List<String[]> borrowerListPath, payerListPath;
    List<BorrowerListTransaction> transactionList;
    List<String[]> pathList;
    private Context context;
    public Button acceptAllBorrowerBtn, declineAllBorrowerBtn, acceptBorrowerBtn, declineBorrowerBtn, confirmPayerBtn,denyPayerBtn,confirmAllPayerBtn,denyAllPayerBtn;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pending_status);

        context = this;

        borrowerListTV = findViewById(R.id.borrowerListTV);
        payerListTV = findViewById(R.id.payerListTV);
        borrowerListScrollView = findViewById(R.id.borrowerListScrollView);
        payerListScrollView = findViewById(R.id.payerListScrollView);
        backBtn = findViewById(R.id.backBtn);
        borrowerListLinearLayout = findViewById(R.id.borrowerListLinearLayout);
        payerListLinearLayout = findViewById(R.id.payerListLinearLayout);
        borrowerImg = findViewById(R.id.borrowerImg);
        acceptBorrowerBtn = findViewById(R.id.acceptBorrowerBtn);
        declineBorrowerBtn = findViewById(R.id.declineBorrowerBtn);
        acceptAllBorrowerBtn = findViewById(R.id.acceptAllBorrowerBtn);
        declineAllBorrowerBtn = findViewById(R.id.declineAllBorrowerBtn);
        allTV = findViewById(R.id.allTV);
        payerListBtn = findViewById(R.id.payerListBtn);
        borrowerListBtn = findViewById(R.id.borrowerListBtn);
        payerImg = findViewById(R.id.payerImg);
        confirmPayerBtn = findViewById(R.id.confirmPayerBtn);
        denyPayerBtn = findViewById(R.id.denyPayerBtn);
        confirmAllPayerBtn = findViewById(R.id.confirmAllPayerBtn);
        denyAllPayerBtn = findViewById(R.id.denyAllPayerBtn);

        borrowerPayerClicked = true;

        BorrowerListTVClicked();
        PayerListTVClicked();
        BackButtonCLicked();

        MainActivity mainActivity = new MainActivity();
        mainActivity.getCurrentNickname(new MainActivity.CurrentNicknameCallback() {
            @Override
            public void onCurrentNicknameReceived(String currentNickname) {
                currentNickname2 = currentNickname;
            }
        });

        BorrowerList();
        PayerList();
    }

    private void BorrowerListTVClicked() {
        borrowerListTV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                payerListTV.setBackgroundResource(R.drawable.button_background_invisible);
                borrowerListTV.setBackgroundResource(R.drawable.top_round_border);
                payerListTV.setTextColor(ContextCompat.getColor(PendingStatusActivity.this, R.color.whitest));
                borrowerListTV.setTextColor(ContextCompat.getColor(PendingStatusActivity.this, R.color.darkBlue));
                borrowerListScrollView.setVisibility(View.VISIBLE);
                payerListScrollView.setVisibility(View.GONE);
                borrowerListLinearLayout.setVisibility(View.VISIBLE);
                payerListLinearLayout.setVisibility(View.GONE);
                borrowerListBtn.setVisibility(View.VISIBLE);
                payerListBtn.setVisibility(View.GONE);

                borrowerListTV.setEnabled(false);
                payerListTV.setEnabled(true);
                borrowerPayerClicked = true;
            }
        });
    }

    private void PayerListTVClicked() {
        payerListTV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                borrowerListTV.setBackgroundResource(R.drawable.button_background_invisible);
                payerListTV.setBackgroundResource(R.drawable.top_round_border);
                borrowerListTV.setTextColor(ContextCompat.getColor(PendingStatusActivity.this, R.color.whitest));
                payerListTV.setTextColor(ContextCompat.getColor(PendingStatusActivity.this, R.color.darkBlue));
                payerListScrollView.setVisibility(View.VISIBLE);
                borrowerListScrollView.setVisibility(View.GONE);
                borrowerListLinearLayout.setVisibility(View.GONE);
                payerListLinearLayout.setVisibility(View.VISIBLE);
                borrowerListBtn.setVisibility(View.GONE);
                payerListBtn.setVisibility(View.VISIBLE);

                payerListTV.setEnabled(false);
                borrowerListTV.setEnabled(true);
                borrowerPayerClicked = false;
            }
        });
    }

    private void BorrowerList() {
        borrowerListTransactions = new ArrayList<>();
        borrowerListPath = new ArrayList<String[]>();

        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        DatabaseReference databaseReference = DeclareDatabase.getDBRefBorrows();
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            MainActivity mainActivity = new MainActivity();

            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot monthSnapshot : dataSnapshot.getChildren()) {
                    String month = monthSnapshot.getKey();
                    for (DataSnapshot daySnapshot : monthSnapshot.getChildren()) {
                        String day = daySnapshot.getKey();
                        for (DataSnapshot borrowSnapshot : daySnapshot.getChildren()) {
                            // Try to read as new structure first
                            BorrowNowTransaction borrowNowTransaction = borrowSnapshot.getValue(BorrowNowTransaction.class);

                            if (borrowNowTransaction != null && borrowNowTransaction.getLenderID() != null && borrowNowTransaction.getBorrowId() != null) {
                                // New UID-based structure: borrows/{month}/{day}/{borrowId}
                                // Check if current user is the lender (receiving borrow requests)
                                if (Objects.equals(borrowNowTransaction.getLenderID(), currentUserId)) {
                                    String status = borrowNowTransaction.getStatus();
                                    if (Objects.equals(status, "For Lender Approval")) {
                                        String borrowId = borrowNowTransaction.getBorrowId();
                                        String borrowerName = borrowNowTransaction.getBorrowerName();
                                        if (borrowerName == null || borrowerName.isEmpty()) {
                                            borrowerName = "Unknown";
                                        }
                                        String borrowedAmountStr = CurrencyUtils.formatAmountWithCurrency(borrowNowTransaction.getBorrowedAmountStr());

                                        // Calculate time difference using timestamp
                                        long timestamp = borrowNowTransaction.getTimestamp();
                                        String timeDifferenceStr = calculateTimeDifference(timestamp);

                                        BorrowerListTransaction borrowerTrans = new BorrowerListTransaction(
                                                timeDifferenceStr,
                                                borrowerName,
                                                borrowedAmountStr,
                                                status
                                        );
                                        borrowerListTransactions.add(borrowerTrans);

                                        // New path format: month, day, borrowId (no username/time)
                                        borrowerListPath.add(new String[]{month, day, borrowId, ""});
                                    }
                                }
                            } else {
                                // Legacy structure: borrows/{month}/{day}/{username}/{time}
                                String currentUserStr = borrowSnapshot.getKey();
                                if (!Objects.equals(currentUserStr, currentNickname2)) {
                                    for (DataSnapshot timeSnapshot : borrowSnapshot.getChildren()) {
                                        String time = timeSnapshot.getKey();
                                        BorrowerListTransaction borrowerListTransaction = timeSnapshot.getValue(BorrowerListTransaction.class);
                                        if (borrowerListTransaction != null) {
                                            String status = borrowerListTransaction.getStatus();
                                            String borrowee = borrowerListTransaction.getBorrowee();
                                            if (Objects.equals(status, "For Lender Approval") && Objects.equals(borrowee, currentNickname2)) {
                                                borrowee = currentUserStr;
                                                String borrowedAmountStr = CurrencyUtils.formatAmountWithCurrency(borrowerListTransaction.getBorrowedAmountStr());
                                                String date = borrowerListTransaction.getDate();

                                                String formatPattern = "MMMM-dd-yyyy HH:mm:ss";
                                                String timeDifferenceStr = "0s";

                                                try {
                                                    String dateTime = date + " " + time;
                                                    DateFormat dateFormat = new SimpleDateFormat(formatPattern, Locale.ENGLISH);
                                                    Date pastDate = dateFormat.parse(dateTime);
                                                    Date currentDate = new Date();
                                                    long timeDifferenceMillis = currentDate.getTime() - pastDate.getTime();
                                                    long secondsSinceDate = timeDifferenceMillis / 1000;
                                                    timeDifferenceStr = formatTimeDifference(secondsSinceDate);
                                                } catch (ParseException e) {
                                                    e.printStackTrace();
                                                }

                                                BorrowerListTransaction borrowerTrans = new BorrowerListTransaction(
                                                        timeDifferenceStr,
                                                        borrowee,
                                                        borrowedAmountStr,
                                                        status
                                                );
                                                borrowerListTransactions.add(borrowerTrans);
                                                borrowerListPath.add(new String[]{month, day, currentUserStr, time});
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                adapter = new BorrowerListTransactionAdapter(context, borrowerListTransactions, borrowerListPath, PendingStatusActivity.this,  acceptAllBorrowerBtn, declineAllBorrowerBtn);
                borrowerListRecyclerView = findViewById(R.id.borrowerListRecyclerView);
                borrowerListRecyclerView.setAdapter(adapter);
                borrowerListRecyclerView.setLayoutManager(new LinearLayoutManager(PendingStatusActivity.this));
                adapter.notifyDataSetChanged();

                borrowerNum = borrowerListTransactions.size();
                if (borrowerNum < 2){
                    acceptAllBorrowerBtn.setEnabled(false);
                    declineAllBorrowerBtn.setEnabled(false);
                    acceptAllBorrowerBtn.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(PendingStatusActivity.this, R.color.grey)));
                    declineAllBorrowerBtn.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(PendingStatusActivity.this, R.color.grey)));
                } else {
                    acceptAllBorrowerBtn.setEnabled(true);
                    declineAllBorrowerBtn.setEnabled(true);
                    acceptAllBorrowerBtn.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(PendingStatusActivity.this, R.color.yellow)));
                    declineAllBorrowerBtn.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(PendingStatusActivity.this, R.color.red)));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("FirebaseDatabase", "Database read error: " + databaseError.getMessage());
            }
        });
    }

    private String calculateTimeDifference(long timestamp) {
        long currentTime = System.currentTimeMillis();
        long differenceMillis = currentTime - timestamp;
        long secondsSinceDate = differenceMillis / 1000;
        return formatTimeDifference(secondsSinceDate);
    }

    private String formatTimeDifference(long secondsSinceDate) {
        if (secondsSinceDate >= 60 * 60 * 24 * 365) {
            long years = secondsSinceDate / (60 * 60 * 24 * 365);
            return years + "y";
        } else if (secondsSinceDate >= 60 * 60 * 24 * 30) {
            long months = secondsSinceDate / (60 * 60 * 24 * 30);
            return months + "mo";
        } else if (secondsSinceDate >= 60 * 60 * 24) {
            long days = secondsSinceDate / (60 * 60 * 24);
            return days + "d";
        } else if (secondsSinceDate >= 60 * 60) {
            long hours = secondsSinceDate / (60 * 60);
            return hours + "h";
        } else if (secondsSinceDate >= 60) {
            long minutes = secondsSinceDate / 60;
            return minutes + "m";
        } else {
            return secondsSinceDate + "s";
        }
    }

    private void PayerList() {
        payerListTransactions = new ArrayList<>();
        payerListPath = new ArrayList<String[]>();

        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        DatabaseReference databaseReference = DeclareDatabase.getDBRefBorrows();
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            MainActivity mainActivity = new MainActivity();

            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot monthSnapshot : dataSnapshot.getChildren()) {
                    String month = monthSnapshot.getKey();
                    for (DataSnapshot daySnapshot : monthSnapshot.getChildren()) {
                        String day = daySnapshot.getKey();
                        for (DataSnapshot borrowSnapshot : daySnapshot.getChildren()) {
                            // Try to read as new structure first
                            BorrowNowTransaction borrowNowTransaction = borrowSnapshot.getValue(BorrowNowTransaction.class);

                            if (borrowNowTransaction != null && borrowNowTransaction.getLenderID() != null && borrowNowTransaction.getBorrowId() != null) {
                                // New UID-based structure: borrows/{month}/{day}/{borrowId}
                                // Check if current user is the lender (receiving payment confirmations)
                                if (Objects.equals(borrowNowTransaction.getLenderID(), currentUserId)) {
                                    String status = borrowNowTransaction.getStatus();
                                    if (Objects.equals(status, "Payment Pending")) {
                                        String borrowId = borrowNowTransaction.getBorrowId();
                                        String borrowerName = borrowNowTransaction.getBorrowerName();
                                        if (borrowerName == null || borrowerName.isEmpty()) {
                                            borrowerName = "Unknown";
                                        }
                                        String borrowedAmountStr = CurrencyUtils.formatAmountWithCurrency(borrowNowTransaction.getBorrowedAmountStr());

                                        // Calculate time difference using timestamp
                                        long timestamp = borrowNowTransaction.getTimestamp();
                                        String timeDifferenceStr = calculateTimeDifference(timestamp);

                                        BorrowerListTransaction payerTrans = new BorrowerListTransaction(
                                                timeDifferenceStr,
                                                borrowerName,
                                                borrowedAmountStr,
                                                status
                                        );
                                        payerListTransactions.add(payerTrans);

                                        // New path format: month, day, borrowId (no username/time)
                                        payerListPath.add(new String[]{month, day, borrowId, ""});
                                    }
                                }
                            } else {
                                // Legacy structure: borrows/{month}/{day}/{username}/{time}
                                String currentUserStr = borrowSnapshot.getKey();
                                if (!Objects.equals(currentUserStr, currentNickname2)) {
                                    for (DataSnapshot timeSnapshot : borrowSnapshot.getChildren()) {
                                        String time = timeSnapshot.getKey();
                                        BorrowerListTransaction borrowerListTransaction = timeSnapshot.getValue(BorrowerListTransaction.class);
                                        if (borrowerListTransaction != null) {
                                            String status = borrowerListTransaction.getStatus();
                                            String borrowee = borrowerListTransaction.getBorrowee();
                                            if (Objects.equals(status, "Payment Pending") && Objects.equals(borrowee, currentNickname2)) {
                                                borrowee = currentUserStr;
                                                String borrowedAmountStr = CurrencyUtils.formatAmountWithCurrency(borrowerListTransaction.getBorrowedAmountStr());
                                                String date = borrowerListTransaction.getDate();

                                                String formatPattern = "MMMM-dd-yyyy HH:mm:ss";
                                                String timeDifferenceStr = "0s";

                                                try {
                                                    String dateTime = date + " " + time;
                                                    DateFormat dateFormat = new SimpleDateFormat(formatPattern, Locale.ENGLISH);
                                                    Date pastDate = dateFormat.parse(dateTime);
                                                    Date currentDate = new Date();
                                                    long timeDifferenceMillis = currentDate.getTime() - pastDate.getTime();
                                                    long secondsSinceDate = timeDifferenceMillis / 1000;
                                                    timeDifferenceStr = formatTimeDifference(secondsSinceDate);
                                                } catch (ParseException e) {
                                                    e.printStackTrace();
                                                }

                                                BorrowerListTransaction borrowerTrans = new BorrowerListTransaction(
                                                        timeDifferenceStr,
                                                        borrowee,
                                                        borrowedAmountStr,
                                                        status
                                                );
                                                payerListTransactions.add(borrowerTrans);
                                                payerListPath.add(new String[]{month, day, currentUserStr, time});
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                adapterPayer = new PayerListTransactionAdapter(context, payerListTransactions, payerListPath, PendingStatusActivity.this,  confirmAllPayerBtn, denyAllPayerBtn);
                payerListRecyclerView = findViewById(R.id.payerListRecyclerView);
                payerListRecyclerView.setAdapter(adapterPayer);
                payerListRecyclerView.setLayoutManager(new LinearLayoutManager(PendingStatusActivity.this));
                adapterPayer.notifyDataSetChanged();

                payerNum = payerListTransactions.size();
                if (payerNum < 2){
                    confirmAllPayerBtn.setEnabled(false);
                    denyAllPayerBtn.setEnabled(false);
                    confirmAllPayerBtn.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(PendingStatusActivity.this, R.color.grey)));
                    denyAllPayerBtn.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(PendingStatusActivity.this, R.color.grey)));
                } else {
                    confirmAllPayerBtn.setEnabled(true);
                    denyAllPayerBtn.setEnabled(true);
                    confirmAllPayerBtn.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(PendingStatusActivity.this, R.color.yellow)));
                    denyAllPayerBtn.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(PendingStatusActivity.this, R.color.red)));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("FirebaseDatabase", "Database read error: " + databaseError.getMessage());
            }
        });
    }

    private void BackButtonCLicked() {
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    public void onTransactionStatusUpdated() {
        BorrowerList();
    }


    private void AcceptDeclineBtnClicked(){
        acceptBorrowerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                allTV.setVisibility(View.GONE);
            }
        });
        declineBorrowerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                allTV.setVisibility(View.GONE);
            }
        });
    }

    public void showToast(String message) {
        Toast.makeText(PendingStatusActivity.this, message, Toast.LENGTH_SHORT).show();
    }

}
