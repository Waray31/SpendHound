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

                        // Create balances node
                        UserBalance balance = new UserBalance(balanced, unpaid, owed, debt, 0, 0);
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
                        balancesMap.put("totalBorrowed", 0);
                        balancesMap.put("totalLent", 0);

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
     *
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
}

