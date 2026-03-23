package com.waray.spendhound

import android.util.Log
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Helper class for managing user ID to username mappings using Supabase.
 * Provides caching and lookup functionality.
 * Updated to use Long for user IDs to match the database schema.
 */
object UserHelper {
    private const val TAG = "UserHelper"

    // Cache for ID to username mappings
    private val idToUsernameCache: MutableMap<Long, String> = HashMap()

    // Cache for username to ID mappings
    private val usernameToIdCache: MutableMap<String, Long> = HashMap()

    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Get username for a given ID. Uses cache if available.
     */
    fun getUsernameById(id: Long?, callback: UsernameCallback) {
        if (id == null) {
            callback.onError("ID is null")
            return
        }

        // Check cache first
        if (idToUsernameCache.containsKey(id)) {
            callback.onUsernameRetrieved(idToUsernameCache[id])
            return
        }

        // Fetch from Supabase
        scope.launch {
            try {
                val user = DeclareDatabase.usersTable.select(Columns.list("username")) {
                    filter {
                        eq("user_id", id)
                    }
                }.decodeSingleOrNull<User>()

                withContext(Dispatchers.Main) {
                    if (user?.username != null) {
                        idToUsernameCache[id] = user.username
                        usernameToIdCache[user.username] = id
                        callback.onUsernameRetrieved(user.username)
                    } else {
                        callback.onError("User not found for ID: $id")
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
     * Get ID for a given username. Uses cache if available.
     */
    fun getIdByUsername(username: String?, callback: IdCallback) {
        if (username == null || username.isEmpty()) {
            callback.onError("Username is null or empty")
            return
        }

        // Check cache first
        if (usernameToIdCache.containsKey(username)) {
            callback.onIdRetrieved(usernameToIdCache[username])
            return
        }

        // Fetch from Supabase
        scope.launch {
            try {
                val user = DeclareDatabase.usersTable.select(Columns.list("user_id")) {
                    filter {
                        eq("username", username)
                    }
                }.decodeSingleOrNull<User>()

                withContext(Dispatchers.Main) {
                    if (user?.id != null) {
                        idToUsernameCache[user.id] = username
                        usernameToIdCache[username] = user.id
                        callback.onIdRetrieved(user.id)
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
     * Batch fetch usernames for multiple IDs. Uses cache where available.
     */
    fun getUsernamesByIds(ids: List<Long?>?, callback: MultipleUsernamesCallback) {
        if (ids == null || ids.isEmpty()) {
            callback.onUsernamesRetrieved(HashMap())
            return
        }

        val result: MutableMap<Long?, String?> = HashMap()
        val missingIds = mutableListOf<Long>()

        for (id in ids) {
            if (id == null) continue
            if (idToUsernameCache.containsKey(id)) {
                result[id] = idToUsernameCache[id]
            } else {
                missingIds.add(id)
            }
        }

        if (missingIds.isEmpty()) {
            callback.onUsernamesRetrieved(result)
            return
        }

        scope.launch {
            try {
                val users = DeclareDatabase.usersTable.select(Columns.list("user_id", "username")) {
                    filter {
                        isIn("user_id", missingIds)
                    }
                }.decodeList<User>()

                withContext(Dispatchers.Main) {
                    for (user in users) {
                        if (user.id != null && user.username != null) {
                            idToUsernameCache[user.id] = user.username
                            usernameToIdCache[user.username] = user.id
                            result[user.id] = user.username
                        }
                    }
                    // Fill in "Unknown User" for any IDs not found
                    for (id in missingIds) {
                        if (!result.containsKey(id)) {
                            result[id] = "Unknown User"
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
     * Compatibility methods using String User IDs
     */
    fun getUsernameByUserId(userId: String?, callback: UsernameCallback) = getUsernameById(userId?.toLongOrNull(), callback)
    
    fun getUserIdByUsername(username: String?, callback: UserIdCallback) {
        getIdByUsername(username, object : IdCallback {
            override fun onIdRetrieved(id: Long?) {
                callback.onUserIdRetrieved(id?.toString())
            }
            override fun onError(error: String?) {
                callback.onError(error)
            }
        })
    }

    /**
     * Fetch all users from Supabase.
     */
    suspend fun getAllUsers(): List<User> {
        return withContext(Dispatchers.IO) {
            try {
                val users = DeclareDatabase.usersTable.select(Columns.list("user_id", "username")).decodeList<User>()
                // Update cache
                for (user in users) {
                    if (user.id != null && user.username != null) {
                        idToUsernameCache[user.id] = user.username
                        usernameToIdCache[user.username] = user.id
                    }
                }
                users
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching all users: ${e.message}")
                emptyList()
            }
        }
    }

    /**
     * Pre-load all users into cache.
     */
    fun preloadAllUsers() {
        scope.launch {
            try {
                val users = DeclareDatabase.usersTable.select(Columns.list("user_id", "username")).decodeList<User>()
                for (user in users) {
                    if (user.id != null && user.username != null) {
                        idToUsernameCache[user.id] = user.username
                        usernameToIdCache[user.username] = user.id
                    }
                }
                Log.d(TAG, "Preloaded ${idToUsernameCache.size} users into cache")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to preload users: ${e.message}")
            }
        }
    }

    fun updateCache(id: Long, username: String) {
        idToUsernameCache[id] = username
        usernameToIdCache[username] = id
    }

    fun getCachedUsername(id: Long?): String? = idToUsernameCache[id]
    fun getCachedId(username: String?): Long? = usernameToIdCache[username]
    fun clearCache() {
        idToUsernameCache.clear()
        usernameToIdCache.clear()
    }

    interface UsernameCallback {
        fun onUsernameRetrieved(username: String?)
        fun onError(error: String?)
    }

    interface IdCallback {
        fun onIdRetrieved(id: Long?)
        fun onError(error: String?)
    }

    interface UserIdCallback {
        fun onUserIdRetrieved(userId: String?)
        fun onError(error: String?)
    }

    interface MultipleUsernamesCallback {
        fun onUsernamesRetrieved(idToUsernameMap: MutableMap<Long?, String?>?)
        fun onError(error: String?)
    }
}
