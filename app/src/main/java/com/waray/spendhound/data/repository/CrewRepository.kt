package com.waray.spendhound.data.repository

import com.waray.spendhound.CrewMember
import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.User
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class CrewRepository {

    // Returns accepted crew members for the given user (both directions)
    suspend fun getCrewList(userId: Long): List<Pair<CrewMember, User>> {
        val asOwner = DeclareDatabase.crewMembersTable.select {
            filter { eq("owner_user_id", userId); eq("status", 1) }
        }.decodeList<CrewMember>()

        val asMember = DeclareDatabase.crewMembersTable.select {
            filter { eq("member_user_id", userId); eq("status", 1) }
        }.decodeList<CrewMember>()

        val all = asOwner + asMember
        return all.mapNotNull { crew ->
            val otherUserId = if (crew.ownerUserId == userId) crew.memberUserId else crew.ownerUserId
            otherUserId ?: return@mapNotNull null
            val user = DeclareDatabase.usersTable.select(
                Columns.list("user_id", "username", "profile_image_url", "user_type")
            ) { filter { eq("user_id", otherUserId) } }.decodeSingleOrNull<User>()
            user?.let { crew to it }
        }
    }

    // Returns pending invites received by userId (status = 3, member = userId)
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

    // Checks both directions to prevent duplicates, then inserts
    // Returns error message string or null on success
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

        // Guest users are auto-accepted (status = 1), registered users get pending (status = 3)
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

    suspend fun removeCrew(crewId: Long) {
        DeclareDatabase.crewMembersTable.delete { filter { eq("id", crewId) } }
    }

    // Fetch all registered users excluding current user (used for suggestions)
    suspend fun getAllUsers(currentUserId: Long): List<User> {
        return DeclareDatabase.usersTable.select(
            Columns.list("user_id", "username", "profile_image_url", "user_type")
        ) {
            filter {
                neq("user_id", currentUserId)
                eq("user_type", 1)
            }
        }.decodeList<User>().sortedBy { it.username?.lowercase() }
    }

    // Filter and sort locally — starts-with matches ranked above contains matches
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

    // Creates a guest user row; returns the new user or null on failure
    suspend fun createGuestUser(name: String, email: String?, phone: String?, invitedByUserId: Long): User? {
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

    // DM eligibility: both users must be registered (user_type = 1)
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

    suspend fun getDirectMessages(userId: Long, otherUserId: Long): List<com.waray.spendhound.DirectMessage> {
        return DeclareDatabase.directMessagesTable.select {
            filter {
                or {
                    and { eq("sender_id", userId); eq("recipient_id", otherUserId) }
                    and { eq("sender_id", otherUserId); eq("recipient_id", userId) }
                }
            }
        }.decodeList()
    }
}
