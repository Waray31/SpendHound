package com.waray.spendhound

import android.util.Log
import com.google.firebase.database.DataSnapshot

/**
 * Helper class for managing user balance operations in Firebase
 * Uses transactions for atomic updates to prevent race conditions
 */
object BalanceHelper {
    private const val TAG = "BalanceHelper"

    /**
     * Initialize balances node for new users during registration
     */
    fun initializeBalancesForNewUser(uid: String?, callback: BalanceCallback?) {
        val userBalanceRef: DatabaseReference = DeclareDatabase.getDatabaseReference()
            .child(uid)
            .child("balances")

        val initialBalance = UserBalance(0.0, 0.0, 0.0, 0.0, 0.0)
        userBalanceRef.setValue(initialBalance)
            .addOnSuccessListener({ aVoid ->
                Log.d(TAG, "Balances initialized for user: " + uid)
                if (callback != null) callback.onSuccess()
            })
            .addOnFailureListener({ e ->
                Log.e(TAG, "Failed to initialize balances: " + e.getMessage())
                if (callback != null) callback.onFailure(e.getMessage())
            })
    }

    /**
     * Initialize userBorrows node for new users during registration
     */
    fun initializeUserBorrowsForNewUser(uid: String?, callback: BalanceCallback?) {
        val userBorrowsRef: DatabaseReference = DeclareDatabase.getDBRefUserBorrows().child(uid)

        val initialBorrows: MutableMap<String?, Any?> = HashMap<String?, Any?>()
        initialBorrows.put("asBorrower", HashMap<String?, Boolean?>())
        initialBorrows.put("asLender", HashMap<String?, Boolean?>())

        userBorrowsRef.setValue(initialBorrows)
            .addOnSuccessListener({ aVoid ->
                Log.d(TAG, "UserBorrows initialized for user: " + uid)
                if (callback != null) callback.onSuccess()
            })
            .addOnFailureListener({ e ->
                Log.e(TAG, "Failed to initialize userBorrows: " + e.getMessage())
                if (callback != null) callback.onFailure(e.getMessage())
            })
    }

    /**
     * Check if balances node exists for existing users, create if not (migration safety)
     */
    fun ensureBalancesExist(uid: String?, callback: BalanceCallback?) {
        val userBalanceRef: DatabaseReference = DeclareDatabase.getDatabaseReference()
            .child(uid)
            .child("balances")

        userBalanceRef.addListenerForSingleValueEvent(object : ValueEventListener() {
            public override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    // Balances don't exist, initialize them
                    initializeBalancesForNewUser(uid, callback)
                } else {
                    // Balances already exist
                    if (callback != null) callback.onSuccess()
                }
            }

            public override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to check balances: " + error.getMessage())
                if (callback != null) callback.onFailure(error.getMessage())
            }
        })
    }

    /**
     * Check if userBorrows node exists for existing users, create if not (migration safety)
     */
    fun ensureUserBorrowsExist(uid: String?, callback: BalanceCallback?) {
        val userBorrowsRef: DatabaseReference = DeclareDatabase.getDBRefUserBorrows().child(uid)

        userBorrowsRef.addListenerForSingleValueEvent(object : ValueEventListener() {
            public override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    initializeUserBorrowsForNewUser(uid, callback)
                } else {
                    if (callback != null) callback.onSuccess()
                }
            }

            public override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to check userBorrows: " + error.getMessage())
                if (callback != null) callback.onFailure(error.getMessage())
            }
        })
    }

    /**
     * Update user's totalBillSpent atomically using Firebase transaction
     * totalBillSpent is the sum of paymentAmount in all transactions where user is in payorsList
     */
    fun updateTotalBillSpent(uid: String?, amountChange: Int, callback: BalanceCallback?) {
        val billSpentRef: DatabaseReference = DeclareDatabase.getDatabaseReference()
            .child(uid)
            .child("balances")
            .child("totalBillSpent")

        billSpentRef.runTransaction(object : Handler() {
            public override fun doTransaction(mutableData: MutableData): Transaction.Result {
                var currentValue: Int? = mutableData.getValue(Int::class.java)
                if (currentValue == null) {
                    currentValue = 0
                }
                mutableData.setValue(currentValue + amountChange)
                return Transaction.success(mutableData)
            }

            public override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                snapshot: DataSnapshot?
            ) {
                if (error != null) {
                    Log.e(TAG, "Failed to update totalBillSpent: " + error.getMessage())
                    if (callback != null) callback.onFailure(error.getMessage())
                } else if (committed) {
                    Log.d(TAG, "TotalBillSpent updated for user: " + uid)
                    if (callback != null) callback.onSuccess()
                }
            }
        })
    }

    /**
     * Update user's totalBillPayment atomically using Firebase transaction
     * totalBillPayment is the sum of user's individual amounts from amountsPaidList in all transactions
     */
    fun updateTotalBillPayment(uid: String?, amountChange: Int, callback: BalanceCallback?) {
        val billPaymentRef: DatabaseReference = DeclareDatabase.getDatabaseReference()
            .child(uid)
            .child("balances")
            .child("totalBillPayment")

        billPaymentRef.runTransaction(object : Handler() {
            public override fun doTransaction(mutableData: MutableData): Transaction.Result {
                var currentValue: Int? = mutableData.getValue(Int::class.java)
                if (currentValue == null) {
                    currentValue = 0
                }
                mutableData.setValue(currentValue + amountChange)
                return Transaction.success(mutableData)
            }

            public override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                snapshot: DataSnapshot?
            ) {
                if (error != null) {
                    Log.e(TAG, "Failed to update totalBillPayment: " + error.getMessage())
                    if (callback != null) callback.onFailure(error.getMessage())
                } else if (committed) {
                    Log.d(TAG, "TotalBillPayment updated for user: " + uid)
                    if (callback != null) callback.onSuccess()
                }
            }
        })
    }

    /**
     * Update user's totalIndividualSpent atomically using Firebase transaction
     * totalIndividualSpent is the sum of totalIndividualPayment for each transaction user participated in
     */
    fun updateTotalIndividualSpent(uid: String?, amountChange: Int, callback: BalanceCallback?) {
        val individualSpentRef: DatabaseReference = DeclareDatabase.getDatabaseReference()
            .child(uid)
            .child("balances")
            .child("totalIndividualSpent")

        individualSpentRef.runTransaction(object : Handler() {
            public override fun doTransaction(mutableData: MutableData): Transaction.Result {
                var currentValue: Int? = mutableData.getValue(Int::class.java)
                if (currentValue == null) {
                    currentValue = 0
                }
                mutableData.setValue(currentValue + amountChange)
                return Transaction.success(mutableData)
            }

            public override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                snapshot: DataSnapshot?
            ) {
                if (error != null) {
                    Log.e(TAG, "Failed to update totalIndividualSpent: " + error.getMessage())
                    if (callback != null) callback.onFailure(error.getMessage())
                } else if (committed) {
                    Log.d(TAG, "TotalIndividualSpent updated for user: " + uid)
                    if (callback != null) callback.onSuccess()
                }
            }
        })
    }

    /**
     * Update user's totaldebt atomically using Firebase transaction
     * totaldebt is the sum of borrow amounts where user is borrower with status != "Paid"
     */
    fun updateTotaldebt(uid: String?, amountChange: Int, callback: BalanceCallback?) {
        val debtRef: DatabaseReference = DeclareDatabase.getDatabaseReference()
            .child(uid)
            .child("balances")
            .child("totaldebt")

        debtRef.runTransaction(object : Handler() {
            public override fun doTransaction(mutableData: MutableData): Transaction.Result {
                var currentValue: Int? = mutableData.getValue(Int::class.java)
                if (currentValue == null) {
                    currentValue = 0
                }
                mutableData.setValue(currentValue + amountChange)
                return Transaction.success(mutableData)
            }

            public override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                snapshot: DataSnapshot?
            ) {
                if (error != null) {
                    Log.e(TAG, "Failed to update totaldebt: " + error.getMessage())
                    if (callback != null) callback.onFailure(error.getMessage())
                } else if (committed) {
                    Log.d(TAG, "Totaldebt updated for user: " + uid)
                    if (callback != null) callback.onSuccess()
                }
            }
        })
    }

    /**
     * Update user's totalreceivable atomically using Firebase transaction
     * totalreceivable is the sum of borrow amounts where user is lender with status != "Paid"
     */
    fun updateTotalreceivable(uid: String?, amountChange: Int, callback: BalanceCallback?) {
        val receivableRef: DatabaseReference = DeclareDatabase.getDatabaseReference()
            .child(uid)
            .child("balances")
            .child("totalreceivable")

        receivableRef.runTransaction(object : Handler() {
            public override fun doTransaction(mutableData: MutableData): Transaction.Result {
                var currentValue: Int? = mutableData.getValue(Int::class.java)
                if (currentValue == null) {
                    currentValue = 0
                }
                mutableData.setValue(currentValue + amountChange)
                return Transaction.success(mutableData)
            }

            public override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                snapshot: DataSnapshot?
            ) {
                if (error != null) {
                    Log.e(TAG, "Failed to update totalreceivable: " + error.getMessage())
                    if (callback != null) callback.onFailure(error.getMessage())
                } else if (committed) {
                    Log.d(TAG, "Totalreceivable updated for user: " + uid)
                    if (callback != null) callback.onSuccess()
                }
            }
        })
    }

    /**
     * Add borrow entry to userBorrows index
     */
    fun addBorrowerEntry(borrowerUid: String?, borrowId: String?, callback: BalanceCallback?) {
        val borrowerRef: DatabaseReference = DeclareDatabase.getDBRefUserBorrows()
            .child(borrowerUid)
            .child("asBorrower")
            .child(borrowId)

        borrowerRef.setValue(true)
            .addOnSuccessListener({ aVoid ->
                Log.d(TAG, "Borrower entry added for: " + borrowerUid)
                if (callback != null) callback.onSuccess()
            })
            .addOnFailureListener({ e ->
                Log.e(TAG, "Failed to add borrower entry: " + e.getMessage())
                if (callback != null) callback.onFailure(e.getMessage())
            })
    }

    /**
     * Add lender entry to userBorrows index
     */
    fun addLenderEntry(lenderUid: String?, borrowId: String?, callback: BalanceCallback?) {
        val lenderRef: DatabaseReference = DeclareDatabase.getDBRefUserBorrows()
            .child(lenderUid)
            .child("asLender")
            .child(borrowId)

        lenderRef.setValue(true)
            .addOnSuccessListener({ aVoid ->
                Log.d(TAG, "Lender entry added for: " + lenderUid)
                if (callback != null) callback.onSuccess()
            })
            .addOnFailureListener({ e ->
                Log.e(TAG, "Failed to add lender entry: " + e.getMessage())
                if (callback != null) callback.onFailure(e.getMessage())
            })
    }

    interface BalanceCallback {
        fun onSuccess()
        fun onFailure(error: String?)
    }
}

