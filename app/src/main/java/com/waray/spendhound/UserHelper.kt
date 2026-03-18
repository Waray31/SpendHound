package com.waray.spendhound

import android.util.Log
import com.google.firebase.database.DataSnapshot

/**
 * Helper class for managing user UID to username mappings.
 * Provides caching and lookup functionality to efficiently resolve UIDs to display names.
 */
object UserHelper {
    private const val TAG = "UserHelper"

    // Cache for UID to username mappings
    private val uidToUsernameCache: MutableMap<String?, String?> = HashMap<String?, String?>()

    // Cache for username to UID mappings
    private val usernameToUidCache: MutableMap<String?, String?> = HashMap<String?, String?>()

    /**
     * Get username for a given UID. Uses cache if available.
     */
    fun getUsernameByUid(uid: String?, callback: UsernameCallback) {
        if (uid == null || uid.isEmpty()) {
            callback.onError("UID is null or empty")
            return
        }

        // Check cache first
        if (uidToUsernameCache.containsKey(uid)) {
            callback.onUsernameRetrieved(uidToUsernameCache.get(uid))
            return
        }

        // Fetch from Firebase
        val userRef: DatabaseReference = DeclareDatabase.getDatabaseReference().child(uid)
        userRef.child("username").addListenerForSingleValueEvent(object : ValueEventListener() {
            public override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    val username: String? = dataSnapshot.getValue(String::class.java)
                    if (username != null) {
                        // Update both caches
                        uidToUsernameCache.put(uid, username)
                        usernameToUidCache.put(username, uid)
                        callback.onUsernameRetrieved(username)
                    } else {
                        callback.onError("Username is null for UID: " + uid)
                    }
                } else {
                    callback.onError("User not found for UID: " + uid)
                }
            }

            public override fun onCancelled(databaseError: DatabaseError) {
                Log.e(TAG, "Database error: " + databaseError.getMessage())
                callback.onError(databaseError.getMessage())
            }
        })
    }

    /**
     * Get UID for a given username. Uses cache if available.
     */
    fun getUidByUsername(username: String?, callback: UidCallback) {
        if (username == null || username.isEmpty()) {
            callback.onError("Username is null or empty")
            return
        }

        // Check cache first
        if (usernameToUidCache.containsKey(username)) {
            callback.onUidRetrieved(usernameToUidCache.get(username))
            return
        }

        // Fetch from Firebase - need to search all users
        val usersRef: DatabaseReference = DeclareDatabase.getDatabaseReference()
        usersRef.addListenerForSingleValueEvent(object : ValueEventListener() {
            public override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (userSnapshot in dataSnapshot.getChildren()) {
                    val userName: String? =
                        userSnapshot.child("username").getValue(String::class.java)
                    if (username == userName) {
                        val uid: String? = userSnapshot.getKey()
                        // Update both caches
                        uidToUsernameCache.put(uid, username)
                        usernameToUidCache.put(username, uid)
                        callback.onUidRetrieved(uid)
                        return
                    }
                }
                callback.onError("User not found with username: " + username)
            }

            public override fun onCancelled(databaseError: DatabaseError) {
                Log.e(TAG, "Database error: " + databaseError.getMessage())
                callback.onError(databaseError.getMessage())
            }
        })
    }

    /**
     * Batch fetch usernames for multiple UIDs. Uses cache where available.
     */
    fun getUsernamesByUids(uids: MutableList<String?>?, callback: MultipleUsernamesCallback) {
        if (uids == null || uids.isEmpty()) {
            callback.onUsernamesRetrieved(HashMap<String?, String?>())
            return
        }

        val result: MutableMap<String?, String?> = HashMap<String?, String?>()
        val pendingCount = intArrayOf(0)

        for (uid in uids) {
            if (uid == null || uid.isEmpty()) continue

            // Check cache first
            if (uidToUsernameCache.containsKey(uid)) {
                result.put(uid, uidToUsernameCache.get(uid))
            } else {
                pendingCount[0]++
            }
        }

        // If all were cached, return immediately
        if (pendingCount[0] == 0) {
            callback.onUsernamesRetrieved(result)
            return
        }

        // Fetch missing from Firebase
        val completedCount = intArrayOf(0)
        for (uid in uids) {
            if (uid == null || uid.isEmpty() || uidToUsernameCache.containsKey(uid)) continue

            getUsernameByUid(uid, object : UsernameCallback {
                override fun onUsernameRetrieved(username: String?) {
                    result.put(uid, username)
                    completedCount[0]++
                    if (completedCount[0] == pendingCount[0]) {
                        callback.onUsernamesRetrieved(result)
                    }
                }

                override fun onError(error: String?) {
                    Log.e(TAG, "Error fetching username for UID " + uid + ": " + error)
                    result.put(uid, "Unknown User")
                    completedCount[0]++
                    if (completedCount[0] == pendingCount[0]) {
                        callback.onUsernamesRetrieved(result)
                    }
                }
            })
        }
    }

    /**
     * Batch fetch UIDs for multiple usernames. Uses cache where available.
     */
    fun getUidsByUsernames(usernames: MutableList<String?>?, callback: MultipleUidsCallback) {
        if (usernames == null || usernames.isEmpty()) {
            callback.onUidsRetrieved(HashMap<String?, String?>())
            return
        }

        val result: MutableMap<String?, String?> = HashMap<String?, String?>()
        val pendingCount = intArrayOf(0)

        for (username in usernames) {
            if (username == null || username.isEmpty()) continue

            // Check cache first
            if (usernameToUidCache.containsKey(username)) {
                result.put(username, usernameToUidCache.get(username))
            } else {
                pendingCount[0]++
            }
        }

        // If all were cached, return immediately
        if (pendingCount[0] == 0) {
            callback.onUidsRetrieved(result)
            return
        }

        // Fetch all users once and resolve
        val usersRef: DatabaseReference = DeclareDatabase.getDatabaseReference()
        usersRef.addListenerForSingleValueEvent(object : ValueEventListener() {
            public override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (userSnapshot in dataSnapshot.getChildren()) {
                    val userName: String? =
                        userSnapshot.child("username").getValue(String::class.java)
                    val uid: String? = userSnapshot.getKey()

                    if (userName != null && uid != null) {
                        // Update caches
                        uidToUsernameCache.put(uid, userName)
                        usernameToUidCache.put(userName, uid)


                        // Add to result if it's one we're looking for
                        if (usernames.contains(userName)) {
                            result.put(userName, uid)
                        }
                    }
                }
                callback.onUidsRetrieved(result)
            }

            public override fun onCancelled(databaseError: DatabaseError) {
                Log.e(TAG, "Database error: " + databaseError.getMessage())
                callback.onError(databaseError.getMessage())
            }
        })
    }

    /**
     * Pre-load all users into cache. Call this on app startup for best performance.
     */
    fun preloadAllUsers() {
        val usersRef: DatabaseReference = DeclareDatabase.getDatabaseReference()
        usersRef.addListenerForSingleValueEvent(object : ValueEventListener() {
            public override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (userSnapshot in dataSnapshot.getChildren()) {
                    val userName: String? =
                        userSnapshot.child("username").getValue(String::class.java)
                    val uid: String? = userSnapshot.getKey()

                    if (userName != null && uid != null) {
                        uidToUsernameCache.put(uid, userName)
                        usernameToUidCache.put(userName, uid)
                    }
                }
                Log.d(TAG, "Preloaded " + uidToUsernameCache.size + " users into cache")
            }

            public override fun onCancelled(databaseError: DatabaseError) {
                Log.e(TAG, "Failed to preload users: " + databaseError.getMessage())
            }
        })
    }

    /**
     * Get username from cache synchronously. Returns null if not cached.
     */
    fun getCachedUsername(uid: String?): String? {
        return uidToUsernameCache.get(uid)
    }

    /**
     * Get UID from cache synchronously. Returns null if not cached.
     */
    fun getCachedUid(username: String?): String? {
        return usernameToUidCache.get(username)
    }

    /**
     * Clear the cache. Call this when user data may have changed.
     */
    fun clearCache() {
        uidToUsernameCache.clear()
        usernameToUidCache.clear()
    }

    /**
     * Update cache with a known UID-username mapping.
     */
    fun updateCache(uid: String?, username: String?) {
        if (uid != null && username != null) {
            uidToUsernameCache.put(uid, username)
            usernameToUidCache.put(username, uid)
        }
    }

    interface UsernameCallback {
        fun onUsernameRetrieved(username: String?)
        fun onError(error: String?)
    }

    interface UidCallback {
        fun onUidRetrieved(uid: String?)
        fun onError(error: String?)
    }

    interface MultipleUsernamesCallback {
        fun onUsernamesRetrieved(uidToUsernameMap: MutableMap<String?, String?>?)
        fun onError(error: String?)
    }

    interface MultipleUidsCallback {
        fun onUidsRetrieved(usernameToUidMap: MutableMap<String?, String?>?)
        fun onError(error: String?)
    }
}
