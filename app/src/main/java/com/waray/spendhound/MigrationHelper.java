package com.waray.spendhound;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper class for migrating existing data to the new UID-based structure.
 * This handles backwards compatibility during the transition period.
 *
 * Run this migration once for existing users to populate userBorrows index.
 */
public class MigrationHelper {

    private static final String TAG = "MigrationHelper";

    public interface MigrationCallback {
        void onComplete(int migratedCount);

        void onError(String error);
    }

    /**
     * Migrate existing borrows to populate the userBorrows index.
     * This scans all borrows and creates index entries for both borrowers and lenders.
     */
    public static void migrateUserBorrowsIndex(MigrationCallback callback) {
        DatabaseReference borrowsRef = DeclareDatabase.getDBRefBorrows();

        borrowsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                int[] migratedCount = {0};

                for (DataSnapshot monthSnapshot : dataSnapshot.getChildren()) {
                    for (DataSnapshot daySnapshot : monthSnapshot.getChildren()) {
                        for (DataSnapshot borrowSnapshot : daySnapshot.getChildren()) {
                            // Check if this is the new structure (has borrowId field)
                            BorrowNowTransaction transaction = borrowSnapshot.getValue(BorrowNowTransaction.class);

                            if (transaction != null && transaction.getBorrowId() != null
                                    && transaction.getBorrowerID() != null && transaction.getLenderID() != null) {

                                String borrowId = transaction.getBorrowId();
                                String borrowerID = transaction.getBorrowerID();
                                String lenderID = transaction.getLenderID();

                                // Add to borrower's index
                                BalanceHelper.addBorrowerEntry(borrowerID, borrowId, null);

                                // Add to lender's index
                                BalanceHelper.addLenderEntry(lenderID, borrowId, null);

                                migratedCount[0]++;
                            }
                        }
                    }
                }

                Log.d(TAG, "Migration complete. Migrated " + migratedCount[0] + " borrows.");
                if (callback != null) {
                    callback.onComplete(migratedCount[0]);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Migration failed: " + error.getMessage());
                if (callback != null) {
                    callback.onError(error.getMessage());
                }
            }
        });
    }

    /**
     * Migrate user balances from flat fields to nested balances object.
     * This checks each user and creates the balances node if missing.
     */
    public static void migrateUserBalances(MigrationCallback callback) {
        DatabaseReference usersRef = DeclareDatabase.getDatabaseReference();

        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                int[] migratedCount = {0};

                for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                    String uid = userSnapshot.getKey();

                    // Check if balances node exists
                    if (!userSnapshot.hasChild("balances")) {
                        // Create new balances with all fields initialized to 0
                        UserBalance balance = new UserBalance(0.0, 0.0, 0.0, 0.0, 0.0);
                        usersRef.child(uid).child("balances").setValue(balance);

                        migratedCount[0]++;
                        Log.d(TAG, "Migrated balances for user: " + uid);
                    }
                }

                Log.d(TAG, "User balance migration complete. Migrated " + migratedCount[0] + " users.");
                if (callback != null) {
                    callback.onComplete(migratedCount[0]);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "User balance migration failed: " + error.getMessage());
                if (callback != null) {
                    callback.onError(error.getMessage());
                }
            }
        });
    }

    /**
     * Migrate existing users with old structure to new structure.
     * This includes:
     * 1. Creating balances node from flat fields (balanced, unpaid, owed, debt)
     * 2. Initializing userBorrows node if missing
     * 3. Adding totalBorrowed and totalLent fields
     */
    public static void migrateExistingUsers(MigrationCallback callback) {
        DatabaseReference usersRef = DeclareDatabase.getDatabaseReference();
        DatabaseReference userBorrowsRef = DeclareDatabase.getDBRefUserBorrows();

        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                int[] migratedCount = {0};
                int totalUsers = (int) dataSnapshot.getChildrenCount();
                int[] processedCount = {0};

                if (totalUsers == 0) {
                    if (callback != null) {
                        callback.onComplete(0);
                    }
                    return;
                }

                for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                    String uid = userSnapshot.getKey();
                    if (uid == null) {
                        processedCount[0]++;
                        continue;
                    }

                    boolean needsMigration = false;
                    Map<String, Object> updates = new HashMap<>();

                    // Check and migrate balances
                    if (!userSnapshot.hasChild("balances")) {
                        needsMigration = true;

                        // Get existing flat balance fields
                        int balanced = 0;
                        int unpaid = 0;
                        int owed = 0;
                        int debt = 0;

                        if (userSnapshot.hasChild("balanced")) {
                            Integer val = userSnapshot.child("balanced").getValue(Integer.class);
                            if (val != null) balanced = val;
                        }
                        if (userSnapshot.hasChild("unpaid")) {
                            Integer val = userSnapshot.child("unpaid").getValue(Integer.class);
                            if (val != null) unpaid = val;
                        }
                        if (userSnapshot.hasChild("owed")) {
                            Integer val = userSnapshot.child("owed").getValue(Integer.class);
                            if (val != null) owed = val;
                        }
                        if (userSnapshot.hasChild("debt")) {
                            Integer val = userSnapshot.child("debt").getValue(Integer.class);
                            if (val != null) debt = val;
                        }

                        // Create balances map
                        Map<String, Object> balancesMap = new HashMap<>();
                        balancesMap.put("currentBalance", balanced);
                        balancesMap.put("unpaid", unpaid);
                        balancesMap.put("owed", owed);
                        balancesMap.put("debt", debt);
                        balancesMap.put("totalBorrowed", 0.0);
                        balancesMap.put("totalLent", 0.0);

                        updates.put("balances", balancesMap);
                    }

                    // Apply updates to user if needed
                    if (needsMigration) {
                        usersRef.child(uid).updateChildren(updates)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Migrated user structure for: " + uid);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to migrate user: " + uid + ", error: " + e.getMessage());
                                });
                        migratedCount[0]++;
                    }

                    // Check and initialize userBorrows
                    String finalUid = uid;
                    userBorrowsRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot borrowsSnapshot) {
                            if (!borrowsSnapshot.exists()) {
                                // Initialize userBorrows for this user
                                Map<String, Object> initialBorrows = new HashMap<>();
                                initialBorrows.put("asBorrower", new HashMap<String, Boolean>());
                                initialBorrows.put("asLender", new HashMap<String, Boolean>());

                                userBorrowsRef.child(finalUid).setValue(initialBorrows)
                                        .addOnSuccessListener(aVoid -> {
                                            Log.d(TAG, "Initialized userBorrows for: " + finalUid);
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e(TAG, "Failed to init userBorrows: " + finalUid);
                                        });
                            }

                            processedCount[0]++;
                            // Check if all users processed
                            if (processedCount[0] >= totalUsers) {
                                Log.d(TAG, "Existing users migration complete. Migrated " + migratedCount[0] + " users.");
                                if (callback != null) {
                                    callback.onComplete(migratedCount[0]);
                                }
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            processedCount[0]++;
                            if (processedCount[0] >= totalUsers && callback != null) {
                                callback.onComplete(migratedCount[0]);
                            }
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Existing users migration failed: " + error.getMessage());
                if (callback != null) {
                    callback.onError(error.getMessage());
                }
            }
        });
    }

    /**
     * Convert old borrow structure to new structure.
     * Old: borrows/{month}/{day}/{username}/{time}
     * New: borrows/{month}/{day}/{borrowId}
     * <p>
     * Note: This is a destructive migration. Make sure to backup data first!
     */
    public static void migrateBorrowStructure(MigrationCallback callback) {
        DatabaseReference borrowsRef = DeclareDatabase.getDBRefBorrows();

        borrowsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                int[] migratedCount = {0};
                Map<String, Object> updates = new HashMap<>();

                for (DataSnapshot monthSnapshot : dataSnapshot.getChildren()) {
                    String month = monthSnapshot.getKey();

                    for (DataSnapshot daySnapshot : monthSnapshot.getChildren()) {
                        String day = daySnapshot.getKey();

                        for (DataSnapshot userOrBorrowSnapshot : daySnapshot.getChildren()) {
                            // Check if this is old structure (has time children) or new structure
                            boolean isOldStructure = false;

                            for (DataSnapshot child : userOrBorrowSnapshot.getChildren()) {
                                // Old structure has time-based keys like "12:30:45"
                                String key = child.getKey();
                                if (key != null && key.contains(":")) {
                                    isOldStructure = true;
                                    break;
                                }
                            }

                            if (isOldStructure) {
                                String username = userOrBorrowSnapshot.getKey();

                                for (DataSnapshot timeSnapshot : userOrBorrowSnapshot.getChildren()) {
                                    String time = timeSnapshot.getKey();

                                    // Read the old transaction
                                    String borrowerID = timeSnapshot.child("borrowerID").getValue(String.class);
                                    String lenderID = timeSnapshot.child("lenderID").getValue(String.class);
                                    String date = timeSnapshot.child("date").getValue(String.class);
                                    String lender = timeSnapshot.child("lender").getValue(String.class);
                                    String borrowedAmountStr = timeSnapshot.child("borrowedAmountStr").getValue(String.class);
                                    String status = timeSnapshot.child("status").getValue(String.class);

                                    if (borrowerID != null && lenderID != null) {
                                        // Generate new borrowId
                                        String borrowId = borrowsRef.child(month).child(day).push().getKey();

                                        if (borrowId != null) {
                                            // Create new structure
                                            BorrowNowTransaction newTransaction = new BorrowNowTransaction(
                                                    borrowId,
                                                    borrowerID,
                                                    lenderID,
                                                    username, // borrowerName
                                                    date,
                                                    lender, // lenderName
                                                    borrowedAmountStr,
                                                    status,
                                                    System.currentTimeMillis()
                                            );

                                            // Add to updates
                                            String newPath = month + "/" + day + "/" + borrowId;
                                            updates.put(newPath, newTransaction);

                                            // Mark old path for deletion
                                            String oldPath = month + "/" + day + "/" + username + "/" + time;
                                            updates.put(oldPath, null);

                                            migratedCount[0]++;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (!updates.isEmpty()) {
                    borrowsRef.updateChildren(updates)
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "Borrow structure migration complete. Migrated " + migratedCount[0] + " borrows.");
                                if (callback != null) {
                                    callback.onComplete(migratedCount[0]);
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Borrow structure migration failed: " + e.getMessage());
                                if (callback != null) {
                                    callback.onError(e.getMessage());
                                }
                            });
                } else {
                    Log.d(TAG, "No old borrow structure found to migrate.");
                    if (callback != null) {
                        callback.onComplete(0);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Borrow structure migration failed: " + error.getMessage());
                if (callback != null) {
                    callback.onError(error.getMessage());
                }
            }
        });
    }

    /**
     * Run all migrations in sequence.
     * Call this from a settings screen or app startup for existing users.
     */
    public static void runAllMigrations(MigrationCallback callback) {
        Log.d(TAG, "Starting full migration...");

        // Step 1: Migrate user balances
        migrateUserBalances(new MigrationCallback() {
            @Override
            public void onComplete(int migratedCount) {
                Log.d(TAG, "Step 1 complete: Migrated " + migratedCount + " user balances");

                // Step 2: Migrate userBorrows index
                migrateUserBorrowsIndex(new MigrationCallback() {
                    @Override
                    public void onComplete(int migratedCount2) {
                        Log.d(TAG, "Step 2 complete: Migrated " + migratedCount2 + " borrow indexes");

                        if (callback != null) {
                            callback.onComplete(migratedCount + migratedCount2);
                        }
                    }

                    @Override
                    public void onError(String error) {
                        if (callback != null) {
                            callback.onError(error);
                        }
                    }
                });
            }

            @Override
            public void onError(String error) {
                if (callback != null) {
                    callback.onError(error);
                }
            }
        });
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
    public static void recalculateUserBalancesFromData(MigrationCallback callback) {
        Log.d(TAG, "Starting balance recalculation from transactions and borrows...");

        // Map to store aggregated user balances: uid -> UserBalance
        Map<String, UserBalance> userBalances = new HashMap<>();

        // Step 1: Scan all transactions
        DatabaseReference transactionsRef = DeclareDatabase.getDBRefTransaction();
        transactionsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                int transactionCount = 0;
                int errorCount = 0;
                try {
                    // Iterate through months
                    for (DataSnapshot monthSnapshot : dataSnapshot.getChildren()) {
                        // Iterate through days
                        for (DataSnapshot daySnapshot : monthSnapshot.getChildren()) {
                            // Iterate through timestamps
                            for (DataSnapshot timeSnapshot : daySnapshot.getChildren()) {
                                try {
                                    Transaction transaction = timeSnapshot.getValue(Transaction.class);
                                    if (transaction != null) {
                                        transactionCount++;
                                        processTransactionForBalance(transaction, userBalances);
                                    }
                                } catch (Exception e) {
                                    errorCount++;
                                    Log.w(TAG, "Error processing individual transaction: " + e.getMessage());
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error scanning transactions: " + e.getMessage());
                }
                Log.d(TAG, "Scanned " + transactionCount + " transactions (" + errorCount + " errors)");

                // Step 2: Scan all borrows
                DatabaseReference borrowsRef = DeclareDatabase.getDBRefBorrows();
                borrowsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot borrowDataSnapshot) {
                        int borrowCount = 0;
                        int borrowErrorCount = 0;
                        try {
                            // Iterate through months
                            for (DataSnapshot monthSnapshot : borrowDataSnapshot.getChildren()) {
                                // Iterate through days
                                for (DataSnapshot daySnapshot : monthSnapshot.getChildren()) {
                                    // Iterate through borrow IDs
                                    for (DataSnapshot borrowSnapshot : daySnapshot.getChildren()) {
                                        try {
                                            BorrowNowTransaction borrow = borrowSnapshot.getValue(BorrowNowTransaction.class);
                                            if (borrow != null) {
                                                borrowCount++;
                                                processBorrowForBalance(borrow, userBalances);
                                            }
                                        } catch (Exception e) {
                                            borrowErrorCount++;
                                            Log.w(TAG, "Error processing individual borrow: " + e.getMessage());
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Error scanning borrows: " + e.getMessage());
                        }
                        Log.d(TAG, "Scanned " + borrowCount + " borrows (" + borrowErrorCount + " errors)");

                        // Step 3: Write all aggregated balances to database
                        writeBalancesToDatabase(userBalances, callback);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error scanning borrows: " + error.getMessage());
                        if (callback != null) {
                            callback.onError(error.getMessage());
                        }
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error scanning transactions: " + error.getMessage());
                if (callback != null) {
                    callback.onError(error.getMessage());
                }
            }
        });
    }

    /**
     * Process a single transaction and aggregate user balance data
     */
    private static void processTransactionForBalance(Transaction transaction, Map<String, UserBalance> userBalances) {
        try {
            double paymentAmount = transaction.getPaymentAmount();
            double totalIndividualPayment = transaction.getTotalIndividualPayment();
            String posterUID = transaction.getUsernamePost();
            java.util.List<String> payorsList = transaction.getPayorsList();
            java.util.List<Double> amountsPaidList = transaction.getAmountsPaidList();

            // Validate required fields
            if (posterUID == null || posterUID.isEmpty()) {
                Log.w(TAG, "Skipping transaction with missing posterUID");
                return;
            }

            // Update poster's totalBillSpent
            UserBalance posterBalance = userBalances.computeIfAbsent(posterUID, k -> new UserBalance());
            posterBalance.setTotalBillSpent(posterBalance.getTotalBillSpent() + paymentAmount);
            userBalances.put(posterUID, posterBalance);

            // Update payors' totalBillPayment and totalIndividualSpent
            if (payorsList != null && amountsPaidList != null) {
                for (int i = 0; i < payorsList.size() && i < amountsPaidList.size(); i++) {
                    String payorUID = payorsList.get(i);
                    Double amountPaid = amountsPaidList.get(i);

                    if (payorUID != null && !payorUID.isEmpty() && amountPaid != null) {
                        UserBalance payorBalance = userBalances.computeIfAbsent(payorUID, k -> new UserBalance());
                        payorBalance.setTotalBillPayment(payorBalance.getTotalBillPayment() + amountPaid);
                        payorBalance.setTotalIndividualSpent(payorBalance.getTotalIndividualSpent() + totalIndividualPayment);
                        userBalances.put(payorUID, payorBalance);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Unexpected error processing transaction: " + e.getMessage());
        }
    }

    /**
     * Process a single borrow and aggregate user balance data
     */
    private static void processBorrowForBalance(BorrowNowTransaction borrow, Map<String, UserBalance> userBalances) {
        try {
            String status = borrow.getStatus();

            // Only process if status is not "Paid"
            if (status != null && status.equals("Paid")) {
                return;
            }

            String borrowerID = borrow.getBorrowerID();
            String lenderID = borrow.getLenderID();
            String borrowedAmountStr = borrow.getBorrowedAmountStr();

            // Validate required fields
            if (borrowerID == null || borrowerID.isEmpty() || lenderID == null || lenderID.isEmpty()) {
                Log.w(TAG, "Skipping borrow with missing borrowerID or lenderID");
                return;
            }

            try {
                int borrowAmount = Integer.parseInt(borrowedAmountStr);

                // Update borrower's totaldebt
                UserBalance borrowerBalance = userBalances.computeIfAbsent(borrowerID, k -> new UserBalance());
                borrowerBalance.setTotaldebt(borrowerBalance.getTotaldebt() + borrowAmount);
                userBalances.put(borrowerID, borrowerBalance);

                // Update lender's totalreceivable
                UserBalance lenderBalance = userBalances.computeIfAbsent(lenderID, k -> new UserBalance());
                lenderBalance.setTotalreceivable(lenderBalance.getTotalreceivable() + borrowAmount);
                userBalances.put(lenderID, lenderBalance);
            } catch (NumberFormatException e) {
                Log.w(TAG, "Error parsing borrow amount: " + borrowedAmountStr + ", skipping this borrow");
            }
        } catch (Exception e) {
            Log.w(TAG, "Unexpected error processing borrow: " + e.getMessage());
        }
    }

    /**
     * Write all aggregated balances to the database
     */
    private static void writeBalancesToDatabase(Map<String, UserBalance> userBalances, MigrationCallback callback) {
        Log.d(TAG, "Writing " + userBalances.size() + " user balances to database...");

        DatabaseReference usersRef = DeclareDatabase.getDatabaseReference();
        int[] completedCount = {0};
        int totalCount = userBalances.size();

        if (totalCount == 0) {
            Log.d(TAG, "Balance recalculation complete (no users to update)");
            if (callback != null) callback.onComplete(0);
            return;
        }

        for (Map.Entry<String, UserBalance> entry : userBalances.entrySet()) {
            String uid = entry.getKey();
            UserBalance balance = entry.getValue();

            usersRef.child(uid).child("balances").setValue(balance)
                    .addOnSuccessListener(aVoid -> {
                        completedCount[0]++;
                        Log.d(TAG, "Updated balance for user " + uid + " (" + completedCount[0] + "/" + totalCount + ")");

                        if (completedCount[0] == totalCount) {
                            Log.d(TAG, "Balance recalculation complete!");
                            if (callback != null) callback.onComplete(totalCount);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to write balance for user " + uid + ": " + e.getMessage());
                        completedCount[0]++;

                        if (completedCount[0] == totalCount) {
                            Log.d(TAG, "Balance recalculation completed with errors");
                            if (callback != null) callback.onComplete(totalCount);
                        }
                    });
        }
    }
}

