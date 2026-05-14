package com.waray.spendhound.data.repository

import android.util.Log
import com.waray.spendhound.CrewMember
import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.DirectMessage
import com.waray.spendhound.SpendHoundApplication
import com.waray.spendhound.User
import com.waray.spendhound.data.local.AppDatabase
import com.waray.spendhound.data.local.CacheKeys
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class CrewWithUser(
    val crewId: Long?,
    val ownerUserId: Long?,
    val memberUserId: Long?,
    val status: Int?,
    val userId: Long?,
    val username: String?,
    val profileImageUrl: String?,
    val userType: Int?,
    val lastMessage: String? = null,
    val lastMessageSenderId: Long? = null,
    val unreadCount: Int = 0
)

class CrewRepository(private val db: AppDatabase) {

    companion object {
        private val dmCache = java.util.concurrent.ConcurrentHashMap<String, List<DirectMessage>>()

        private fun getCacheKey(id1: Long, id2: Long): String =
            if (id1 < id2) "$id1-$id2" else "$id2-$id1"

        fun getCachedMessages(id1: Long, id2: Long): List<DirectMessage>? =
            dmCache[getCacheKey(id1, id2)]

        fun setCachedMessages(id1: Long, id2: Long, messages: List<DirectMessage>) {
            dmCache[getCacheKey(id1, id2)] = messages
        }
    }

    constructor() : this(AppDatabase.getInstance(SpendHoundApplication.instance))

    fun getCrewListFlow(userId: Long): Flow<List<Pair<CrewMember, User>>> {
        val flow: Flow<List<CrewWithUser>> = db.cachedFlow(
            key = CacheKeys.profileCrew(userId),
            staleTtlMs = CacheKeys.STALE_CREW,
            type = typeOf<List<CrewWithUser>>()
        ) {
            fetchCrewList(userId).map { (crew, user) ->
                CrewWithUser(
                    crewId = crew.id, ownerUserId = crew.ownerUserId, memberUserId = crew.memberUserId,
                    status = crew.status, userId = user.id, username = user.username,
                    profileImageUrl = user.profileImageUrl, userType = user.userType,
                    lastMessage = crew.lastMessage, lastMessageSenderId = crew.lastMessageSenderId,
                    unreadCount = crew.unreadCount
                )
            }
        }
        return flow.map { list ->
            list.map { c ->
                CrewMember(
                    id = c.crewId, ownerUserId = c.ownerUserId,
                    memberUserId = c.memberUserId, status = c.status,
                    lastMessage = c.lastMessage, lastMessageSenderId = c.lastMessageSenderId,
                    unreadCount = c.unreadCount
                ) to User(
                    id = c.userId, username = c.username,
                    profileImageUrl = c.profileImageUrl, userType = c.userType
                )
            }
        }
    }

    suspend fun getCrewList(userId: Long): List<Pair<CrewMember, User>> = fetchCrewList(userId)

    private suspend fun fetchCrewList(userId: Long): List<Pair<CrewMember, User>> {
        val asOwner = try {
            DeclareDatabase.crewMembersTable.select {
                filter { eq("owner_user_id", userId); eq("status", 1) }
            }.decodeList<CrewMember>()
        } catch (e: Exception) {
            Log.e("CrewDebug", "fetchCrewList asOwner EXCEPTION: ${e.message}"); emptyList()
        }

        val asMember = try {
            DeclareDatabase.crewMembersTable.select {
                filter { eq("member_user_id", userId); eq("status", 1) }
            }.decodeList<CrewMember>()
        } catch (e: Exception) {
            Log.e("CrewDebug", "fetchCrewList asMember EXCEPTION: ${e.message}"); emptyList()
        }

        return (asOwner + asMember).mapNotNull { crew ->
            val otherUserId = if (crew.ownerUserId == userId) crew.memberUserId else crew.ownerUserId
            otherUserId ?: return@mapNotNull null
            val user = try {
                DeclareDatabase.usersTable.select(
                    Columns.list("user_id", "username", "profile_image_url", "user_type")
                ) { filter { eq("user_id", otherUserId) } }.decodeSingleOrNull<User>()
            } catch (e: Exception) {
                Log.e("CrewDebug", "fetchCrewList user fetch EXCEPTION: ${e.message}"); null
            } ?: return@mapNotNull null

            if (user.userType == 1) {
                try {
                    val lastDm = DeclareDatabase.directMessagesTable.select {
                        filter {
                            or {
                                and { eq("sender_id", userId); eq("recipient_id", otherUserId) }
                                and { eq("sender_id", otherUserId); eq("recipient_id", userId) }
                            }
                        }
                        order("sent_at", Order.DESCENDING)
                        limit(1)
                    }.decodeList<DirectMessage>().firstOrNull()

                    val unread = try {
                        DeclareDatabase.directMessagesTable.select(Columns.list("id")) {
                            filter {
                                eq("sender_id", otherUserId)
                                eq("recipient_id", userId)
                                filter("read_at", FilterOperator.IS, "null")
                            }
                        }.decodeList<DirectMessage>().size
                    } catch (e: Exception) { 0 }

                    crew.copy(
                        lastMessage = lastDm?.content,
                        lastMessageSenderId = lastDm?.senderId,
                        unreadCount = unread
                    ) to user
                } catch (e: Exception) {
                    Log.e("CrewDebug", "fetchCrewList DM preview EXCEPTION: ${e.message}")
                    crew.copy(lastMessage = null, lastMessageSenderId = null, unreadCount = 0) to user
                }
            } else {
                crew.copy(lastMessage = null, lastMessageSenderId = null, unreadCount = 0) to user
            }
        }
    }

    suspend fun getPendingInvites(userId: Long): List<Pair<CrewMember, User>> {
        val pending = DeclareDatabase.crewMembersTable.select {
            filter { eq("member_user_id", userId); eq("status", 3) }
        }.decodeList<CrewMember>()
        return pending.mapNotNull { crew ->
            val owner = DeclareDatabase.usersTable.select(
                Columns.list("user_id", "username", "profile_image_url", "user_type")
            ) { filter { eq("user_id", crew.ownerUserId!!) } }.decodeSingleOrNull<User>()
            owner?.let { crew to it }
        }
    }

    suspend fun sendInvite(ownerUserId: Long, memberUserId: Long): String? {
        if (ownerUserId == memberUserId) return "You cannot invite yourself."
        val existing = DeclareDatabase.crewMembersTable.select {
            filter {
                or {
                    and { eq("owner_user_id", ownerUserId); eq("member_user_id", memberUserId) }
                    and { eq("owner_user_id", memberUserId); eq("member_user_id", ownerUserId) }
                }
            }
        }.decodeList<CrewMember>()
        if (existing.isNotEmpty()) return "Crew relationship already exists."
        val memberUser = DeclareDatabase.usersTable.select(
            Columns.list("user_id", "user_type")
        ) { filter { eq("user_id", memberUserId) } }.decodeSingleOrNull<User>()
            ?: return "User not found."
        val status = if (memberUser.userType == 2) 1 else 3
        DeclareDatabase.crewMembersTable.insert(buildJsonObject {
            put("owner_user_id", ownerUserId)
            put("member_user_id", memberUserId)
            put("status", status)
        })
        return null
    }

    suspend fun respondToInvite(crewId: Long, accept: Boolean) {
        val newStatus = if (accept) 1 else 2
        DeclareDatabase.crewMembersTable.update(buildJsonObject {
            put("status", newStatus)
            put("responded_at", java.time.Instant.now().toString())
        }) { filter { eq("id", crewId) } }
    }

    suspend fun invalidateCrew(userId: Long) {
        db.jsonBlobDao().delete(CacheKeys.profileCrew(userId))
    }

    suspend fun removeCrew(crewId: Long) {
        DeclareDatabase.crewMembersTable.delete { filter { eq("id", crewId) } }
    }

    suspend fun getAllUsers(currentUserId: Long): List<User> {
        return DeclareDatabase.usersTable.select(
            Columns.list("user_id", "username", "profile_image_url", "user_type")
        ) {
            filter { neq("user_id", currentUserId); eq("user_type", 1) }
        }.decodeList<User>().sortedBy { it.username?.lowercase() }
    }

    fun filterAndSort(query: String, allUsers: List<User>): List<User> {
        if (query.isBlank()) return allUsers
        val q = query.lowercase()
        val startsWith = allUsers.filter { it.username?.lowercase()?.startsWith(q) == true }
            .sortedBy { it.username?.lowercase() }
        val contains = allUsers.filter {
            it.username?.lowercase()?.contains(q) == true &&
            it.username?.lowercase()?.startsWith(q) == false
        }.sortedBy { it.username?.lowercase() }
        return startsWith + contains
    }

    suspend fun createGuestUser(name: String, email: String?, invitedByUserId: Long): User? {
        val token = java.util.UUID.randomUUID().toString()
        val data = buildJsonObject {
            put("username", name)
            put("user_type", 2)
            put("invited_by_user_id", invitedByUserId)
            put("guest_token", token)
            if (!email.isNullOrBlank()) put("email", email)
        }
        return DeclareDatabase.usersTable.insert(data) {
            select(Columns.list("user_id", "username", "user_type", "guest_token"))
        }.decodeSingleOrNull<User>()
    }

    suspend fun canSendDm(senderId: Long, recipientId: Long): Boolean {
        val users = DeclareDatabase.usersTable.select(
            Columns.list("user_id", "user_type")
        ) { filter { isIn("user_id", listOf(senderId, recipientId)) } }.decodeList<User>()
        return users.size == 2 && users.all { it.userType == 1 }
    }

    suspend fun sendDirectMessage(senderId: Long, recipientId: Long, content: String) {
        DeclareDatabase.directMessagesTable.insert(buildJsonObject {
            put("sender_id", senderId)
            put("recipient_id", recipientId)
            put("content", content)
        })
    }

    suspend fun getDirectMessages(userId: Long, otherUserId: Long): List<DirectMessage> {
        getCachedMessages(userId, otherUserId)?.let { return it }
        val messages = DeclareDatabase.directMessagesTable.select {
            filter {
                or {
                    and { eq("sender_id", userId); eq("recipient_id", otherUserId) }
                    and { eq("sender_id", otherUserId); eq("recipient_id", userId) }
                }
            }
        }.decodeList<DirectMessage>()
        setCachedMessages(userId, otherUserId, messages)
        return messages
    }
}
