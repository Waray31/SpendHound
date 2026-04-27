package com.waray.spendhound.data.repository

import com.waray.spendhound.BorrowNowTransaction
import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.GroupMember
import com.waray.spendhound.GroupMessage
import com.waray.spendhound.MessageRead
import com.waray.spendhound.PayerGroup
import com.waray.spendhound.TransactionRead
import com.waray.spendhound.User
import com.waray.spendhound.data.local.AppDatabase
import com.waray.spendhound.data.local.CacheKeys
import com.waray.spendhound.ui.multi_transaction.TransactionFull
import com.waray.spendhound.ui.multi_transaction.TransactionSplitTable
import com.waray.spendhound.ui.profile.ProfileGroupItem
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow

data class ProfileData(
    val nickname: String,
    val profileImageUrl: String?,
    val transactionsCount: Int,
    val groupsCount: Int,
    val activeBorrowsCount: Int
)

class ProfileRepository(private val db: AppDatabase) {

    fun getProfile(userId: Long, authId: String): Flow<ProfileData> = db.cachedFlow(
        key = CacheKeys.profile(userId),
        staleTtlMs = CacheKeys.STALE_PROFILE,
        type = typeOf<ProfileData>()
    ) {
        val user = DeclareDatabase.usersTable.select(
            Columns.list("user_id", "username", "profile_image_url")
        ) { filter { eq("auth_id", authId) } }.decodeSingleOrNull<User>()

        val txCount = try {
            val splitTxIds = DeclareDatabase.transactionSplitsTable.select(Columns.list("transaction_id")) {
                filter { eq("user_id", userId) }
            }.decodeList<TransactionSplitTable>().mapNotNull { it.transactionId }.toSet()
            if (splitTxIds.isEmpty()) 0
            else DeclareDatabase.transactionsTable.select(Columns.list("id")) {
                filter { isIn("id", splitTxIds.toList()); eq("status", 2) }
            }.decodeList<TransactionFull>().size
        } catch (e: Exception) { 0 }

        val groupCount = try {
            DeclareDatabase.groupMembersTable.select(Columns.list("group_id")) {
                filter { eq("user_id", userId) }
            }.decodeList<GroupMember>().size
        } catch (e: Exception) { 0 }

        val borrowCount = try {
            DeclareDatabase.borrowsTable.select(Columns.list("id")) {
                filter {
                    eq("borrower_id", userId)
                    or { eq("status", 1); eq("status", 2); eq("status", 7) }
                }
            }.decodeList<BorrowNowTransaction>().size
        } catch (e: Exception) { 0 }

        ProfileData(
            nickname = user?.username ?: "",
            profileImageUrl = user?.profileImageUrl,
            transactionsCount = txCount,
            groupsCount = groupCount,
            activeBorrowsCount = borrowCount
        )
    }

    fun getProfileGroups(userId: Long): Flow<List<ProfileGroupItem>> = db.cachedFlow(
        key = CacheKeys.profileGroups(userId),
        staleTtlMs = CacheKeys.STALE_PROFILE,
        type = typeOf<List<ProfileGroupItem>>()
    ) {
        val groupIds = DeclareDatabase.groupMembersTable.select(Columns.list("group_id")) {
            filter { eq("user_id", userId) }
        }.decodeList<GroupMember>().mapNotNull { it.groupId }
        if (groupIds.isEmpty()) return@cachedFlow emptyList<ProfileGroupItem>()

        groupIds.map { groupId ->
            val group = DeclareDatabase.groupsTable.select {
                filter { eq("group_id", groupId) }
            }.decodeSingle<PayerGroup>()

            val memberCount = DeclareDatabase.groupMembersTable.select(Columns.list("user_id")) {
                filter { eq("group_id", groupId) }
            }.decodeList<GroupMember>().size

            val lastReadMsg = DeclareDatabase.messageReadsTable.select(Columns.list("message_id")) {
                filter { eq("group_id", groupId); eq("user_id", userId) }
                order("message_id", Order.DESCENDING); limit(1)
            }.decodeSingleOrNull<MessageRead>()

            val unreadMessages = if (lastReadMsg != null) {
                DeclareDatabase.groupMessagesTable.select(Columns.list("id")) {
                    filter { eq("group_id", groupId); gt("id", lastReadMsg.messageId!!); neq("user_id", userId) }
                }.decodeList<GroupMessage>().size
            } else {
                DeclareDatabase.groupMessagesTable.select(Columns.list("id")) {
                    filter { eq("group_id", groupId); neq("user_id", userId) }
                }.decodeList<GroupMessage>().size
            }

            val readTxIds = DeclareDatabase.transactionReadsTable.select(Columns.list("transaction_id")) {
                filter { eq("group_id", groupId); eq("user_id", userId) }
            }.decodeList<TransactionRead>().mapNotNull { it.transactionId }.toSet()

            val unreadTx = DeclareDatabase.transactionsTable.select(Columns.list("id", "created_by")) {
                filter { eq("group_id", groupId); neq("created_by", userId) }
            }.decodeList<TransactionFull>().count { it.id !in readTxIds }

            val latestMsg = DeclareDatabase.groupMessagesTable.select(Columns.list("created_at")) {
                filter { eq("group_id", groupId) }
                order("created_at", Order.DESCENDING); limit(1)
            }.decodeSingleOrNull<GroupMessage>()?.createdAt ?: ""

            val latestTx = DeclareDatabase.transactionsTable.select(Columns.list("created_at")) {
                filter { eq("group_id", groupId) }
                order("created_at", Order.DESCENDING); limit(1)
            }.decodeSingleOrNull<TransactionFull>()?.createdAt ?: ""

            Pair(ProfileGroupItem(group, memberCount, unreadTx, unreadMessages), maxOf(latestMsg, latestTx))
        }.sortedByDescending { it.second }.take(3).map { it.first }
    }

    suspend fun invalidate(userId: Long) {
        db.jsonBlobDao().delete(CacheKeys.profile(userId))
        db.jsonBlobDao().delete(CacheKeys.profileGroups(userId))
    }
}
