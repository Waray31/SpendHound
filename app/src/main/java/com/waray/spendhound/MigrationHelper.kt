package com.waray.spendhound

import android.util.Log
import com.google.firebase.database.DataSnapshot

/**
 * Helper class for migrating existing data to the new UID-based structure.
 * This handles backwards compatibility during the transition period.
 * 
 * Run this migration once for existing users to populate userBorrows index.
 */
object MigrationHelper {
    private const val TAG = "MigrationHelper"

    /**
     * Migrate existing borrows to populate the userBorrows index.
     * This scans all borrows and creates index entries for both borrowers and lenders.
     */
    fun migrateUserBorrowsIndex(callback: MigrationCallback?) {
        val borrowsRef: DatabaseReference = DeclareDatabase.getDBRefBorrows()

        borrowsRef.addListenerForSingleValueEvent(object : ValueEventListener() {
            public override fun onDataChange(dataSnapshot: DataSnapshot) {
                val migratedCount = intArrayOf(0)

                for (monthSnapshot in dataSnapshot.getChildren()) {
                    for (daySnapshot in monthSnapshot.getChildren()) {
                        for (borrowSnapshot in daySnapshot.getChildren()) {
                            // Check if this is the new structure (has borrowId field)
                            val transaction: BorrowNowTransaction? =
                                borrowSnapshot.getValue(BorrowNowTransaction::class.java)

                            if (transaction != null && transaction.getBorrowId() != null && transaction.getBorrowerID() != null && transaction.getLenderID() != null) {
                                val borrowId = transaction.getBorrowId()
                                val borrowerID = transaction.getBorrowerID()
                                val lenderID = transaction.getLenderID()

                                // Add to borrower's index
                                BalanceHelper.addBorrowerEntry(borrowerID, borrowId, null)

                                // Add to lender's index
                                BalanceHelper.addLenderEntry(lenderID, borrowId, null)

                                migratedCount[0]++
                            }
                        }
                    }
                }

                Log.d(TAG, "Migration complete. Migrated " + migratedCount[0] + " borrows.")
                if (callback != null) {
                    callback.onComplete(migratedCount[0])
                }
            }

            public override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Migration failed: " + error.getMessage())
                if (callback != null) {
                    callback.onError(error.getMessage())
                }
            }
        })
    }

    /**
     * Migrate user balances from flat fields to nested balances object.
     * This checks each user and creates the balances node if missing.
     */
    fun migrateUserBalances(callback: MigrationCallback?) {
        val usersRef: DatabaseReference = DeclareDatabase.getDatabaseReference()

        usersRef.addListenerForSingleValueEvent(object : ValueEventListener() {
            public override fun onDataChange(dataSnapshot: DataSnapshot) {
                val migratedCount = intArrayOf(0)

                for (userSnapshot in dataSnapshot.getChildren()) {
                    val uid: String? = userSnapshot.getKey()

                    // Check if balances node exists
                    if (!userSnapshot.hasChild("balances")) {
                        // Create new balances with all fields initialized to 0
                        val balance = UserBalance(0.0, 0.0, 0.0, 0.0, 0.0)
                        usersRef.child(uid).child("balances").setValue(balance)

                        migratedCount[0]++
                        Log.d(TAG, "Migrated balances for user: " + uid)
                    }
                }

                Log.d(
                    TAG,
                    "User balance migration complete. Migrated " + migratedCount[0] + " users."
                )
                if (callback != null) {
                    callback.onComplete(migratedCount[0])
                }
            }

            public override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "User balance migration failed: " + error.getMessage())
                if (callback != null) {
                    callback.onError(error.getMessage())
                }
            }
        })
    }

    /**
     * Migrate existing users with old structure to new structure.
     * This includes:
     * 1. Creating balances node from flat fields (balanced, unpaid, owed, debt)
     * 2. Initializing userBorrows node if missing
     * 3. Adding totalBorrowed and totalLent fields
     */
    fun migrateExistingUsers(callback: MigrationCallback?) {
        val usersRef: DatabaseReference = DeclareDatabase.getDatabaseReference()
        val userBorrowsRef: DatabaseReference = DeclareDatabase.getDBRefUserBorrows()

        usersRef.addListenerForSingleValueEvent(object : ValueEventListener() {
            public override fun onDataChange(dataSnapshot: DataSnapshot) {
                val migratedCount = intArrayOf(0)
                val totalUsers = dataSnapshot.getChildrenCount() as Int
                val processedCount = intArrayOf(0)

                if (totalUsers == 0) {
                    if (callback != null) {
                        callback.onComplete(0)
                    }
                    return
                }

                for (userSnapshot in dataSnapshot.getChildren()) {
                    val uid: String? = userSnapshot.getKey()
                    if (uid == null) {
                        processedCount[0]++
                        continue
                    }

                    var needsMigration = false
                    val updates: MutableMap<String?, Any?> = HashMap<String?, Any?>()

                    // Check and migrate balances
                    if (!userSnapshot.hasChild("balances")) {
                        needsMigration = true

                        // Get existing flat balance fields
                        var balanced = 0
                        var unpaid = 0
                        var owed = 0
                        var debt = 0

                        if (userSnapshot.hasChild("balanced")) {
                            val `val`: Int? =
                                userSnapshot.child("balanced").getValue(Int::class.java)
                            if (`val` != null) balanced = `val`
                        }
                        if (userSnapshot.hasChild("unpaid")) {
                            val `val`: Int? = userSnapshot.child("unpaid").getValue(Int::class.java)
                            if (`val` != null) unpaid = `val`
                        }
                        if (userSnapshot.hasChild("owed")) {
                            val `val`: Int? = userSnapshot.child("owed").getValue(Int::class.java)
                            if (`val` != null) owed = `val`
                        }
                        if (userSnapshot.hasChild("debt")) {
                            val `val`: Int? = userSnapshot.child("debt").getValue(Int::class.java)
                            if (`val` != null) debt = `val`
                        }

                        // Create balances map
                        val balancesMap: MutableMap<String?, Any?> = HashMap<String?, Any?>()
                        balancesMap.put("currentBalance", balanced)
                        balancesMap.put("unpaid", unpaid)
                        balancesMap.put("owed", owed)
                        balancesMap.put("debt", debt)
                        balancesMap.put("totalBorrowed", 0.0)
                        balancesMap.put("totalLent", 0.0)

                        updates.put("balances", balancesMap)
                    }

                    // Apply updates to user if needed
                    if (needsMigration) {
                        usersRef.child(uid).updateChildren(updates)
                            .addOnSuccessListener({ aVoid ->
                                Log.d(TAG, "Migrated user structure for: " + uid)
                            })
                            .addOnFailureListener({ e ->
                                Log.e(
                                    TAG,
                                    "Failed to migrate user: " + uid + ", error: " + e.getMessage()
                                )
                            })
                        migratedCount[0]++
                    }

                    // Check and initialize userBorrows
                    val finalUid: String? = uid
                    userBorrowsRef.child(uid)
                        .addListenerForSingleValueEvent(object : ValueEventListener() {
                            public override fun onDataChange(borrowsSnapshot: DataSnapshot) {
                                if (!borrowsSnapshot.exists()) {
                                    // Initialize userBorrows for this user
                                    val initialBorrows: MutableMap<String?, Any?> =
                                        HashMap<String?, Any?>()
                                    initialBorrows.put("asBorrower", HashMap<String?, Boolean?>())
                                    initialBorrows.put("asLender", HashMap<String?, Boolean?>())

                                    userBorrowsRef.child(finalUid).setValue(initialBorrows)
                                        .addOnSuccessListener({ aVoid ->
                                            Log.d(TAG, "Initialized userBorrows for: " + finalUid)
                                        })
                                        .addOnFailureListener({ e ->
                                            Log.e(TAG, "Failed to init userBorrows: " + finalUid)
                                        })
                                }

                                processedCount[0]++
                                // Check if all users processed
                                if (processedCount[0] >= totalUsers) {
                                    Log.d(
                                        TAG,
                                        "Existing users migration complete. Migrated " + migratedCount[0] + " users."
                                    )
                                    if (callback != null) {
                                        callback.onComplete(migratedCount[0])
                                    }
                                }
                            }

                            public override fun onCancelled(error: DatabaseError) {
                                processedCount[0]++
                                if (processedCount[0] >= totalUsers && callback != null) {
                                    callback.onComplete(migratedCount[0])
                                }
                            }
                        })
                }
            }

            public override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Existing users migration failed: " + error.getMessage())
                if (callback != null) {
                    callback.onError(error.getMessage())
                }
            }
        })
    }

    /**
     * Convert old borrow structure to new structure.
     * Old: borrows/{month}/{day}/{username}/{time}
     * New: borrows/{month}/{day}/{borrowId}
     * 
     * 
     * Note: This is a destructive migration. Make sure to backup data first!
     */
    fun migrateBorrowStructure(callback: MigrationCallback?) {
        val borrowsRef: DatabaseReference = DeclareDatabase.getDBRefBorrows()

        borrowsRef.addListenerForSingleValueEvent(object : ValueEventListener() {
            public override fun onDataChange(dataSnapshot: DataSnapshot) {
                val migratedCount = intArrayOf(0)
                val updates: MutableMap<String?, Any?> = HashMap<String?, Any?>()

                for (monthSnapshot in dataSnapshot.getChildren()) {
                    val month: String? = monthSnapshot.getKey()

                    for (daySnapshot in monthSnapshot.getChildren()) {
                        val day: String? = daySnapshot.getKey()

                        for (userOrBorrowSnapshot in daySnapshot.getChildren()) {
                            // Check if this is old structure (has time children) or new structure
                            var isOldStructure = false

                            for (child in userOrBorrowSnapshot.getChildren()) {
                                // Old structure has time-based keys like "12:30:45"
                                val key: String? = child.getKey()
                                if (key != null && key.contains(":")) {
                                    isOldStructure = true
                                    break
                                }
                            }

                            if (isOldStructure) {
                                val username: String? = userOrBorrowSnapshot.getKey()

                                for (timeSnapshot in userOrBorrowSnapshot.getChildren()) {
                                    val time: String? = timeSnapshot.getKey()

                                    // Read the old transaction
                                    val borrowerID: String? =
                                        timeSnapshot.child("borrowerID").getValue(
                                            String::class.java
                                        )
                                    val lenderID: String? = timeSnapshot.child("lenderID").getValue(
                                        String::class.java
                                    )
                                    val date: String? =
                                        timeSnapshot.child("date").getValue(String::class.java)
                                    val lender: String? = timeSnapshot.child("lender").getValue(
                                        String::class.java
                                    )
                                    val borrowedAmountStr: String? =
                                        timeSnapshot.child("borrowedAmountStr").getValue(
                                            String::class.java
                                        )
                                    val status: String? = timeSnapshot.child("status").getValue(
                                        String::class.java
                                    )

                                    if (borrowerID != null && lenderID != null) {
                                        // Generate new borrowId
                                        val borrowId: String? =
                                            borrowsRef.child(month).child(day).push().getKey()

                                        if (borrowId != null) {
                                            // Create new structure
                                            val newTransaction = BorrowNowTransaction(
                                                borrowId,
                                                borrowerID,
                                                lenderID,
                                                username,  // borrowerName
                                                date,
                                                lender,  // lenderName
                                                borrowedAmountStr,
                                                status,
                                                System.currentTimeMillis()
                                            )

                                            // Add to updates
                                            val newPath = month + "/" + day + "/" + borrowId
                                            updates.put(newPath, newTransaction)

                                            // Mark old path for deletion
                                            val oldPath =
                                                month + "/" + day + "/" + username + "/" + time
                                            updates.put(oldPath, null)

                                            migratedCount[0]++
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (!updates.isEmpty()) {
                    borrowsRef.updateChildren(updates)
                        .addOnSuccessListener({ aVoid ->
                            Log.d(
                                TAG,
                                "Borrow structure migration complete. Migrated " + migratedCount[0] + " borrows."
                            )
                            if (callback != null) {
                                callback.onComplete(migratedCount[0])
                            }
                        })
                        .addOnFailureListener({ e ->
                            Log.e(TAG, "Borrow structure migration failed: " + e.getMessage())
                            if (callback != null) {
                                callback.onError(e.getMessage())
                            }
                        })
                } else {
                    Log.d(TAG, "No old borrow structure found to migrate.")
                    if (callback != null) {
                        callback.onComplete(0)
                    }
                }
            }

            public override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Borrow structure migration failed: " + error.getMessage())
                if (callback != null) {
                    callback.onError(error.getMessage())
                }
            }
        })
    }

    /**
     * Run all migrations in sequence.
     * Call this from a settings screen or app startup for existing users.
     */
    fun runAllMigrations(callback: MigrationCallback?) {
        Log.d(TAG, "Starting full migration...")

        // Step 1: Migrate user balances
        migrateUserBalances(object : MigrationCallback {
            override fun onComplete(migratedCount: Int) {
                Log.d(TAG, "Step 1 complete: Migrated " + migratedCount + " user balances")

                // Step 2: Migrate userBorrows index
                migrateUserBorrowsIndex(object : MigrationCallback {
                    override fun onComplete(migratedCount2: Int) {
                        Log.d(
                            TAG,
                            "Step 2 complete: Migrated " + migratedCount2 + " borrow indexes"
                        )

                        if (callback != null) {
                            callback.onComplete(migratedCount + migratedCount2)
                        }
                    }

                    override fun onError(error: String?) {
                        if (callback != null) {
                            callback.onError(error)
                        }
                    }
                })
            }

            override fun onError(error: String?) {
                if (callback != null) {
                    callback.onError(error)
                }
            }
        })
    }

    /**
     * Recalculate all user balance fields from transaction and borrow data.
     * This scans all transactions and borrows to compute:
     * - totalBillSpent: Sum of paymentAmount where user is in payorsList
     * - totalBillPayment: Sum of user's amounts from amountsPaidList
     * - totalIndividualSpent: Sum of totalIndividualPayment per transaction
     * - totaldebt: Sum of borrow amounts where user is borrower (status != "Paid")
     * - totalreceivable: Sum of borrow amounts where user is lender (status != "Paid")
     */
    fun recalculateUserBalancesFromData(callback: MigrationCallback?) {
        Log.d(TAG, "Starting balance recalculation from transactions and borrows...")

        // Map to store aggregated user balances: uid -> UserBalance
        val userBalances: MutableMap<String?, UserBalance> = HashMap<String?, UserBalance>()

        // Step 1: Scan all transactions
        val transactionsRef: DatabaseReference = DeclareDatabase.getDBRefTransaction()
        transactionsRef.addListenerForSingleValueEvent(object : ValueEventListener() {
            public override fun onDataChange(dataSnapshot: DataSnapshot) {
                var transactionCount = 0
                var errorCount = 0
                try {
                    // Iterate through months
                    for (monthSnapshot in dataSnapshot.getChildren()) {
                        // Iterate through days
                        for (daySnapshot in monthSnapshot.getChildren()) {
                            // Iterate through timestamps
                            for (timeSnapshot in daySnapshot.getChildren()) {
                                try {
                                    val transaction: Transaction? = timeSnapshot.getValue(
                                        Transaction::class.java
                                    )
                                    if (transaction != null) {
                                        transactionCount++
                                        processTransactionForBalance(transaction, userBalances)
                                    }
                                } catch (e: Exception) {
                                    errorCount++
                                    Log.w(
                                        TAG,
                                        "Error processing individual transaction: " + e.message
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error scanning transactions: " + e.message)
                }
                Log.d(
                    TAG,
                    "Scanned " + transactionCount + " transactions (" + errorCount + " errors)"
                )

                // Step 2: Scan all borrows
                val borrowsRef: DatabaseReference = DeclareDatabase.getDBRefBorrows()
                borrowsRef.addListenerForSingleValueEvent(object : ValueEventListener() {
                    public override fun onDataChange(borrowDataSnapshot: DataSnapshot) {
                        var borrowCount = 0
                        var borrowErrorCount = 0
                        try {
                            // Iterate through months
                            for (monthSnapshot in borrowDataSnapshot.getChildren()) {
                                // Iterate through days
                                for (daySnapshot in monthSnapshot.getChildren()) {
                                    // Iterate through borrow IDs
                                    for (borrowSnapshot in daySnapshot.getChildren()) {
                                        try {
                                            val borrow: BorrowNowTransaction? =
                                                borrowSnapshot.getValue(BorrowNowTransaction::class.java)
                                            if (borrow != null) {
                                                borrowCount++
                                                processBorrowForBalance(borrow, userBalances)
                                            }
                                        } catch (e: Exception) {
                                            borrowErrorCount++
                                            Log.w(
                                                TAG,
                                                "Error processing individual borrow: " + e.message
                                            )
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error scanning borrows: " + e.message)
                        }
                        Log.d(
                            TAG,
                            "Scanned " + borrowCount + " borrows (" + borrowErrorCount + " errors)"
                        )

                        // Step 3: Write all aggregated balances to database
                        writeBalancesToDatabase(userBalances, callback)
                    }

                    public override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Error scanning borrows: " + error.getMessage())
                        if (callback != null) {
                            callback.onError(error.getMessage())
                        }
                    }
                })
            }

            public override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error scanning transactions: " + error.getMessage())
                if (callback != null) {
                    callback.onError(error.getMessage())
                }
            }
        })
    }

    /**
     * Process a single transaction and aggregate user balance data
     */
    private fun processTransactionForBalance(
        transaction: Transaction,
        userBalances: MutableMap<String?, UserBalance>
    ) {
        try {
            val paymentAmount = transaction.getPaymentAmount()
            val totalIndividualPayment = transaction.getTotalIndividualPayment()
            val posterUID = transaction.getUsernamePost()
            val payorsList = transaction.getPayorsList()
            val amountsPaidList = transaction.getAmountsPaidList()

            // Validate required fields
            if (posterUID == null || posterUID.isEmpty()) {
                Log.w(TAG, "Skipping transaction with missing posterUID")
                return
            }

            // Update poster's totalBillSpent
            val posterBalance =
                userBalances.computeIfAbsent(posterUID) { k: String? -> UserBalance() }
            posterBalance.setTotalBillSpent(posterBalance.getTotalBillSpent() + paymentAmount)
            userBalances.put(posterUID, posterBalance)

            // Update payors' totalBillPayment and totalIndividualSpent
            if (payorsList != null && amountsPaidList != null) {
                var i = 0
                while (i < payorsList.size && i < amountsPaidList.size) {
                    val payorUID = payorsList.get(i)
                    val amountPaid = amountsPaidList.get(i)

                    if (payorUID != null && !payorUID.isEmpty() && amountPaid != null) {
                        val payorBalance =
                            userBalances.computeIfAbsent(payorUID) { k: String? -> UserBalance() }
                        payorBalance.setTotalBillPayment(payorBalance.getTotalBillPayment() + amountPaid)
                        payorBalance.setTotalIndividualSpent(payorBalance.getTotalIndividualSpent() + totalIndividualPayment)
                        userBalances.put(payorUID, payorBalance)
                    }
                    i++
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected error processing transaction: " + e.message)
        }
    }

    /**
     * Process a single borrow and aggregate user balance data
     */
    private fun processBorrowForBalance(
        borrow: BorrowNowTransaction,
        userBalances: MutableMap<String?, UserBalance>
    ) {
        try {
            val status = borrow.getStatus()

            // Only process if status is not "Paid"
            if (status != null && status == "Paid") {
                return
            }

            val borrowerID = borrow.getBorrowerID()
            val lenderID = borrow.getLenderID()
            val borrowedAmountStr = borrow.getBorrowedAmountStr()

            // Validate required fields
            if (borrowerID == null || borrowerID.isEmpty() || lenderID == null || lenderID.isEmpty()) {
                Log.w(TAG, "Skipping borrow with missing borrowerID or lenderID")
                return
            }

            try {
                val borrowAmount = borrowedAmountStr.toInt()

                // Update borrower's totaldebt
                val borrowerBalance =
                    userBalances.computeIfAbsent(borrowerID) { k: String? -> UserBalance() }
                borrowerBalance.setTotaldebt(borrowerBalance.getTotaldebt() + borrowAmount)
                userBalances.put(borrowerID, borrowerBalance)

                // Update lender's totalreceivable
                val lenderBalance =
                    userBalances.computeIfAbsent(lenderID) { k: String? -> UserBalance() }
                lenderBalance.setTotalreceivable(lenderBalance.getTotalreceivable() + borrowAmount)
                userBalances.put(lenderID, lenderBalance)
            } catch (e: NumberFormatException) {
                Log.w(
                    TAG,
                    "Error parsing borrow amount: " + borrowedAmountStr + ", skipping this borrow"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected error processing borrow: " + e.message)
        }
    }

    /**
     * Write all aggregated balances to the database
     */
    private fun writeBalancesToDatabase(
        userBalances: MutableMap<String?, UserBalance>,
        callback: MigrationCallback?
    ) {
        Log.d(TAG, "Writing " + userBalances.size + " user balances to database...")

        val usersRef: DatabaseReference = DeclareDatabase.getDatabaseReference()
        val completedCount = intArrayOf(0)
        val totalCount = userBalances.size

        if (totalCount == 0) {
            Log.d(TAG, "Balance recalculation complete (no users to update)")
            if (callback != null) callback.onComplete(0)
            return
        }

        for (entry in userBalances.entries) {
            val uid = entry.key
            val balance: UserBalance? = entry.value

            usersRef.child(uid).child("balances").setValue(balance)
                .addOnSuccessListener({ aVoid ->
                    completedCount[0]++
                    Log.d(
                        TAG,
                        "Updated balance for user " + uid + " (" + completedCount[0] + "/" + totalCount + ")"
                    )
                    if (completedCount[0] == totalCount) {
                        Log.d(TAG, "Balance recalculation complete!")
                        if (callback != null) callback.onComplete(totalCount)
                    }
                })
                .addOnFailureListener({ e ->
                    Log.e(TAG, "Failed to write balance for user " + uid + ": " + e.getMessage())
                    completedCount[0]++
                    if (completedCount[0] == totalCount) {
                        Log.d(TAG, "Balance recalculation completed with errors")
                        if (callback != null) callback.onComplete(totalCount)
                    }
                })
        }
    }

    interface MigrationCallback {
        fun onComplete(migratedCount: Int)

        fun onError(error: String?)
    }
}

