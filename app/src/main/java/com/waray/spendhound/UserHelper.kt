package com.waray.spendhound;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class for managing user UID to username mappings.
 * Provides caching and lookup functionality to efficiently resolve UIDs to display names.
 */
public class UserHelper {

    private static final String TAG = "UserHelper";
    
    // Cache for UID to username mappings
    private static final Map<String, String> uidToUsernameCache = new HashMap<>();
    
    // Cache for username to UID mappings
    private static final Map<String, String> usernameToUidCache = new HashMap<>();

    public interface UsernameCallback {
        void onUsernameRetrieved(String username);
        void onError(String error);
    }

    public interface UidCallback {
        void onUidRetrieved(String uid);
        void onError(String error);
    }

    public interface MultipleUsernamesCallback {
        void onUsernamesRetrieved(Map<String, String> uidToUsernameMap);
        void onError(String error);
    }

    public interface MultipleUidsCallback {
        void onUidsRetrieved(Map<String, String> usernameToUidMap);
        void onError(String error);
    }

    /**
     * Get username for a given UID. Uses cache if available.
     */
    public static void getUsernameByUid(String uid, UsernameCallback callback) {
        if (uid == null || uid.isEmpty()) {
            callback.onError("UID is null or empty");
            return;
        }

        // Check cache first
        if (uidToUsernameCache.containsKey(uid)) {
            callback.onUsernameRetrieved(uidToUsernameCache.get(uid));
            return;
        }

        // Fetch from Firebase
        DatabaseReference userRef = DeclareDatabase.getDatabaseReference().child(uid);
        userRef.child("username").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String username = dataSnapshot.getValue(String.class);
                    if (username != null) {
                        // Update both caches
                        uidToUsernameCache.put(uid, username);
                        usernameToUidCache.put(username, uid);
                        callback.onUsernameRetrieved(username);
                    } else {
                        callback.onError("Username is null for UID: " + uid);
                    }
                } else {
                    callback.onError("User not found for UID: " + uid);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Database error: " + databaseError.getMessage());
                callback.onError(databaseError.getMessage());
            }
        });
    }

    /**
     * Get UID for a given username. Uses cache if available.
     */
    public static void getUidByUsername(String username, UidCallback callback) {
        if (username == null || username.isEmpty()) {
            callback.onError("Username is null or empty");
            return;
        }

        // Check cache first
        if (usernameToUidCache.containsKey(username)) {
            callback.onUidRetrieved(usernameToUidCache.get(username));
            return;
        }

        // Fetch from Firebase - need to search all users
        DatabaseReference usersRef = DeclareDatabase.getDatabaseReference();
        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                    String userName = userSnapshot.child("username").getValue(String.class);
                    if (username.equals(userName)) {
                        String uid = userSnapshot.getKey();
                        // Update both caches
                        uidToUsernameCache.put(uid, username);
                        usernameToUidCache.put(username, uid);
                        callback.onUidRetrieved(uid);
                        return;
                    }
                }
                callback.onError("User not found with username: " + username);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Database error: " + databaseError.getMessage());
                callback.onError(databaseError.getMessage());
            }
        });
    }

    /**
     * Batch fetch usernames for multiple UIDs. Uses cache where available.
     */
    public static void getUsernamesByUids(List<String> uids, MultipleUsernamesCallback callback) {
        if (uids == null || uids.isEmpty()) {
            callback.onUsernamesRetrieved(new HashMap<>());
            return;
        }

        Map<String, String> result = new HashMap<>();
        int[] pendingCount = {0};

        for (String uid : uids) {
            if (uid == null || uid.isEmpty()) continue;

            // Check cache first
            if (uidToUsernameCache.containsKey(uid)) {
                result.put(uid, uidToUsernameCache.get(uid));
            } else {
                pendingCount[0]++;
            }
        }

        // If all were cached, return immediately
        if (pendingCount[0] == 0) {
            callback.onUsernamesRetrieved(result);
            return;
        }

        // Fetch missing from Firebase
        int[] completedCount = {0};
        for (String uid : uids) {
            if (uid == null || uid.isEmpty() || uidToUsernameCache.containsKey(uid)) continue;

            getUsernameByUid(uid, new UsernameCallback() {
                @Override
                public void onUsernameRetrieved(String username) {
                    result.put(uid, username);
                    completedCount[0]++;
                    if (completedCount[0] == pendingCount[0]) {
                        callback.onUsernamesRetrieved(result);
                    }
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "Error fetching username for UID " + uid + ": " + error);
                    result.put(uid, "Unknown User");
                    completedCount[0]++;
                    if (completedCount[0] == pendingCount[0]) {
                        callback.onUsernamesRetrieved(result);
                    }
                }
            });
        }
    }

    /**
     * Batch fetch UIDs for multiple usernames. Uses cache where available.
     */
    public static void getUidsByUsernames(List<String> usernames, MultipleUidsCallback callback) {
        if (usernames == null || usernames.isEmpty()) {
            callback.onUidsRetrieved(new HashMap<>());
            return;
        }

        Map<String, String> result = new HashMap<>();
        int[] pendingCount = {0};

        for (String username : usernames) {
            if (username == null || username.isEmpty()) continue;

            // Check cache first
            if (usernameToUidCache.containsKey(username)) {
                result.put(username, usernameToUidCache.get(username));
            } else {
                pendingCount[0]++;
            }
        }

        // If all were cached, return immediately
        if (pendingCount[0] == 0) {
            callback.onUidsRetrieved(result);
            return;
        }

        // Fetch all users once and resolve
        DatabaseReference usersRef = DeclareDatabase.getDatabaseReference();
        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                    String userName = userSnapshot.child("username").getValue(String.class);
                    String uid = userSnapshot.getKey();
                    
                    if (userName != null && uid != null) {
                        // Update caches
                        uidToUsernameCache.put(uid, userName);
                        usernameToUidCache.put(userName, uid);
                        
                        // Add to result if it's one we're looking for
                        if (usernames.contains(userName)) {
                            result.put(userName, uid);
                        }
                    }
                }
                callback.onUidsRetrieved(result);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Database error: " + databaseError.getMessage());
                callback.onError(databaseError.getMessage());
            }
        });
    }

    /**
     * Pre-load all users into cache. Call this on app startup for best performance.
     */
    public static void preloadAllUsers() {
        DatabaseReference usersRef = DeclareDatabase.getDatabaseReference();
        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                    String userName = userSnapshot.child("username").getValue(String.class);
                    String uid = userSnapshot.getKey();
                    
                    if (userName != null && uid != null) {
                        uidToUsernameCache.put(uid, userName);
                        usernameToUidCache.put(userName, uid);
                    }
                }
                Log.d(TAG, "Preloaded " + uidToUsernameCache.size() + " users into cache");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Failed to preload users: " + databaseError.getMessage());
            }
        });
    }

    /**
     * Get username from cache synchronously. Returns null if not cached.
     */
    public static String getCachedUsername(String uid) {
        return uidToUsernameCache.get(uid);
    }

    /**
     * Get UID from cache synchronously. Returns null if not cached.
     */
    public static String getCachedUid(String username) {
        return usernameToUidCache.get(username);
    }

    /**
     * Clear the cache. Call this when user data may have changed.
     */
    public static void clearCache() {
        uidToUsernameCache.clear();
        usernameToUidCache.clear();
    }

    /**
     * Update cache with a known UID-username mapping.
     */
    public static void updateCache(String uid, String username) {
        if (uid != null && username != null) {
            uidToUsernameCache.put(uid, username);
            usernameToUidCache.put(username, uid);
        }
    }
}
