package com.waray.spendhound;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper class for managing user balance operations in Firebase
 * Uses transactions for atomic updates to prevent race conditions
 */
public class BalanceHelper {

    private static final String TAG = "BalanceHelper";

    public interface BalanceCallback {
        void onSuccess();
        void onFailure(String error);
    }

    /**
     * Initialize balances node for new users during registration
     */
    public static void initializeBalancesForNewUser(String uid, BalanceCallback callback) {
        DatabaseReference userBalanceRef = DeclareDatabase.getDatabaseReference()
                .child(uid)
                .child("balances");

        UserBalance initialBalance = new UserBalance(0, 0, 0, 0, 0);
        userBalanceRef.setValue(initialBalance)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Balances initialized for user: " + uid);
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to initialize balances: " + e.getMessage());
                    if (callback != null) callback.onFailure(e.getMessage());
                });
    }

    /**
     * Initialize userBorrows node for new users during registration
     */
    public static void initializeUserBorrowsForNewUser(String uid, BalanceCallback callback) {
        DatabaseReference userBorrowsRef = DeclareDatabase.getDBRefUserBorrows().child(uid);

        Map<String, Object> initialBorrows = new HashMap<>();
        initialBorrows.put("asBorrower", new HashMap<String, Boolean>());
        initialBorrows.put("asLender", new HashMap<String, Boolean>());

        userBorrowsRef.setValue(initialBorrows)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "UserBorrows initialized for user: " + uid);
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to initialize userBorrows: " + e.getMessage());
                    if (callback != null) callback.onFailure(e.getMessage());
                });
    }

    /**
     * Check if balances node exists for existing users, create if not (migration safety)
     */
    public static void ensureBalancesExist(String uid, BalanceCallback callback) {
        DatabaseReference userBalanceRef = DeclareDatabase.getDatabaseReference()
                .child(uid)
                .child("balances");

        userBalanceRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    // Balances don't exist, initialize them
                    initializeBalancesForNewUser(uid, callback);
                } else {
                    // Balances already exist
                    if (callback != null) callback.onSuccess();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to check balances: " + error.getMessage());
                if (callback != null) callback.onFailure(error.getMessage());
            }
        });
    }

    /**
     * Check if userBorrows node exists for existing users, create if not (migration safety)
     */
    public static void ensureUserBorrowsExist(String uid, BalanceCallback callback) {
        DatabaseReference userBorrowsRef = DeclareDatabase.getDBRefUserBorrows().child(uid);

        userBorrowsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    initializeUserBorrowsForNewUser(uid, callback);
                } else {
                    if (callback != null) callback.onSuccess();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to check userBorrows: " + error.getMessage());
                if (callback != null) callback.onFailure(error.getMessage());
            }
        });
    }

    /**
     * Update user's totalBillSpent atomically using Firebase transaction
     * totalBillSpent is the sum of paymentAmount in all transactions where user is in payorsList
     */
    public static void updateTotalBillSpent(String uid, int amountChange, BalanceCallback callback) {
        DatabaseReference billSpentRef = DeclareDatabase.getDatabaseReference()
                .child(uid)
                .child("balances")
                .child("totalBillSpent");

        billSpentRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                Integer currentValue = mutableData.getValue(Integer.class);
                if (currentValue == null) {
                    currentValue = 0;
                }
                mutableData.setValue(currentValue + amountChange);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                if (error != null) {
                    Log.e(TAG, "Failed to update totalBillSpent: " + error.getMessage());
                    if (callback != null) callback.onFailure(error.getMessage());
                } else if (committed) {
                    Log.d(TAG, "TotalBillSpent updated for user: " + uid);
                    if (callback != null) callback.onSuccess();
                }
            }
        });
    }

    /**
     * Update user's totalBillPayment atomically using Firebase transaction
     * totalBillPayment is the sum of user's individual amounts from amountsPaidList in all transactions
     */
    public static void updateTotalBillPayment(String uid, int amountChange, BalanceCallback callback) {
        DatabaseReference billPaymentRef = DeclareDatabase.getDatabaseReference()
                .child(uid)
                .child("balances")
                .child("totalBillPayment");

        billPaymentRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                Integer currentValue = mutableData.getValue(Integer.class);
                if (currentValue == null) {
                    currentValue = 0;
                }
                mutableData.setValue(currentValue + amountChange);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                if (error != null) {
                    Log.e(TAG, "Failed to update totalBillPayment: " + error.getMessage());
                    if (callback != null) callback.onFailure(error.getMessage());
                } else if (committed) {
                    Log.d(TAG, "TotalBillPayment updated for user: " + uid);
                    if (callback != null) callback.onSuccess();
                }
            }
        });
    }

    /**
     * Update user's totalIndividualSpent atomically using Firebase transaction
     * totalIndividualSpent is the sum of totalIndividualPayment for each transaction user participated in
     */
    public static void updateTotalIndividualSpent(String uid, int amountChange, BalanceCallback callback) {
        DatabaseReference individualSpentRef = DeclareDatabase.getDatabaseReference()
                .child(uid)
                .child("balances")
                .child("totalIndividualSpent");

        individualSpentRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                Integer currentValue = mutableData.getValue(Integer.class);
                if (currentValue == null) {
                    currentValue = 0;
                }
                mutableData.setValue(currentValue + amountChange);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                if (error != null) {
                    Log.e(TAG, "Failed to update totalIndividualSpent: " + error.getMessage());
                    if (callback != null) callback.onFailure(error.getMessage());
                } else if (committed) {
                    Log.d(TAG, "TotalIndividualSpent updated for user: " + uid);
                    if (callback != null) callback.onSuccess();
                }
            }
        });
    }

    /**
     * Update user's totaldebt atomically using Firebase transaction
     * totaldebt is the sum of borrow amounts where user is borrower with status != "Paid"
     */
    public static void updateTotaldebt(String uid, int amountChange, BalanceCallback callback) {
        DatabaseReference debtRef = DeclareDatabase.getDatabaseReference()
                .child(uid)
                .child("balances")
                .child("totaldebt");

        debtRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                Integer currentValue = mutableData.getValue(Integer.class);
                if (currentValue == null) {
                    currentValue = 0;
                }
                mutableData.setValue(currentValue + amountChange);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                if (error != null) {
                    Log.e(TAG, "Failed to update totaldebt: " + error.getMessage());
                    if (callback != null) callback.onFailure(error.getMessage());
                } else if (committed) {
                    Log.d(TAG, "Totaldebt updated for user: " + uid);
                    if (callback != null) callback.onSuccess();
                }
            }
        });
    }

    /**
     * Update user's totalreceivable atomically using Firebase transaction
     * totalreceivable is the sum of borrow amounts where user is lender with status != "Paid"
     */
    public static void updateTotalreceivable(String uid, int amountChange, BalanceCallback callback) {
        DatabaseReference receivableRef = DeclareDatabase.getDatabaseReference()
                .child(uid)
                .child("balances")
                .child("totalreceivable");

        receivableRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                Integer currentValue = mutableData.getValue(Integer.class);
                if (currentValue == null) {
                    currentValue = 0;
                }
                mutableData.setValue(currentValue + amountChange);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                if (error != null) {
                    Log.e(TAG, "Failed to update totalreceivable: " + error.getMessage());
                    if (callback != null) callback.onFailure(error.getMessage());
                } else if (committed) {
                    Log.d(TAG, "Totalreceivable updated for user: " + uid);
                    if (callback != null) callback.onSuccess();
                }
            }
        });
    }

    /**
     * Add borrow entry to userBorrows index
     */
    public static void addBorrowerEntry(String borrowerUid, String borrowId, BalanceCallback callback) {
        DatabaseReference borrowerRef = DeclareDatabase.getDBRefUserBorrows()
                .child(borrowerUid)
                .child("asBorrower")
                .child(borrowId);

        borrowerRef.setValue(true)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Borrower entry added for: " + borrowerUid);
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to add borrower entry: " + e.getMessage());
                    if (callback != null) callback.onFailure(e.getMessage());
                });
    }

    /**
     * Add lender entry to userBorrows index
     */
    public static void addLenderEntry(String lenderUid, String borrowId, BalanceCallback callback) {
        DatabaseReference lenderRef = DeclareDatabase.getDBRefUserBorrows()
                .child(lenderUid)
                .child("asLender")
                .child(borrowId);

        lenderRef.setValue(true)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Lender entry added for: " + lenderUid);
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to add lender entry: " + e.getMessage());
                    if (callback != null) callback.onFailure(e.getMessage());
                });
    }
}

