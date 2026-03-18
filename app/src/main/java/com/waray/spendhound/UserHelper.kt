package com.waray.spendhound

import android.util.Log
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Helper class for managing user UID to username mappings using Supabase.
 * Provides caching and lookup functionality to efficiently resolve UIDs to display names.
 */
object UserHelper {
    private const val TAG = "UserHelper"

    // Cache for UID to username mappings
    private val uidToUsernameCache: MutableMap<String, String> = HashMap()

    // Cache for username to UID mappings
    private val usernameToUidCache: MutableMap<String, String> = HashMap()

    private val scope = CoroutineScope(Dispatchers.IO)

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
            callback.onUsernameRetrieved(uidToUsernameCache[uid])
            return
        }

        // Fetch from Supabase
        scope.launch {
            try {
                val user = DeclareDatabase.usersTable.select(Columns.list("username")) {
                    filter {
                        eq("id", uid)
                    }
                }.decodeSingleOrNull<User>()

                withContext(Dispatchers.Main) {
                    if (user?.username != null) {
                        uidToUsernameCache[uid] = user.username
                        usernameToUidCache[user.username] = uid
                        callback.onUsernameRetrieved(user.username)
                    } else {
                        callback.onError("User not found for UID: $uid")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "Supabase error: ${e.message}")
                    callback.onError(e.message)
                }
            }
        }
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
            callback.onUidRetrieved(usernameToUidCache[username])
            return
        }

        // Fetch from Supabase
        scope.launch {
            try {
                val user = DeclareDatabase.usersTable.select(Columns.list("id")) {
                    filter {
                        eq("username", username)
                    }
                }.decodeSingleOrNull<User>()

                withContext(Dispatchers.Main) {
                    if (user?.id != null) {
                        uidToUsernameCache[user.id] = username
                        usernameToUidCache[username] = user.id
                        callback.onUidRetrieved(user.id)
                    } else {
                        callback.onError("User not found with username: $username")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "Supabase error: ${e.message}")
                    callback.onError(e.message)
                }
            }
        }
    }

    /**
     * Batch fetch usernames for multiple UIDs. Uses cache where available.
     */
    fun getUsernamesByUids(uids: MutableList<String?>?, callback: MultipleUsernamesCallback) {
        if (uids == null || uids.isEmpty()) {
            callback.onUsernamesRetrieved(HashMap())
            return
        }

        val result: MutableMap<String?, String?> = HashMap()
        val missingUids = mutableListOf<String>()

        for (uid in uids) {
            if (uid == null || uid.isEmpty()) continue
            if (uidToUsernameCache.containsKey(uid)) {
                result[uid] = uidToUsernameCache[uid]
            } else {
                missingUids.add(uid)
            }
        }

        if (missingUids.isEmpty()) {
            callback.onUsernamesRetrieved(result)
            return
        }

        scope.launch {
            try {
                val users = DeclareDatabase.usersTable.select(Columns.list("id", "username")) {
                    filter {
                        isIn("id", missingUids)
                    }
                }.decodeList<User>()

                withContext(Dispatchers.Main) {
                    for (user in users) {
                        if (user.id != null && user.username != null) {
                            uidToUsernameCache[user.id] = user.username
                            usernameToUidCache[user.username] = user.id
                            result[user.id] = user.username
                        }
                    }
                    // Fill in "Unknown User" for any UIDs not found
                    for (uid in missingUids) {
                        if (!result.containsKey(uid)) {
                            result[uid] = "Unknown User"
                        }
                    }
                    callback.onUsernamesRetrieved(result)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "Error fetching usernames: ${e.message}")
                    callback.onError(e.message)
                }
            }
        }
    }

    /**
     * Pre-load all users into cache. Call this on app startup for best performance.
     */
    fun preloadAllUsers() {
        scope.launch {
            try {
                val users = DeclareDatabase.usersTable.select(Columns.list("id", "username")).decodeList<User>()
                for (user in users) {
                    if (user.id != null && user.username != null) {
                        uidToUsernameCache[user.id] = user.username
                        usernameToUidCache[user.username] = user.id
                    }
                }
                Log.d(TAG, "Preloaded ${uidToUsernameCache.size} users into cache")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to preload users: ${e.message}")
            }
        }
    }

    fun getCachedUsername(uid: String?): String? = uidToUsernameCache[uid]
    fun getCachedUid(username: String?): String? = usernameToUidCache[username]
    fun clearCache() {
        uidToUsernameCache.clear()
        usernameToUidCache.clear()
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
}
