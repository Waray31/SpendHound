package com.waray.spendhound.data.repository

import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.GroupMember
import com.waray.spendhound.GroupMessage
import com.waray.spendhound.GroupsActivity
import com.waray.spendhound.MessageRead
import com.waray.spendhound.PayerGroup
import com.waray.spendhound.TransactionRead
import com.waray.spendhound.User
import com.waray.spendhound.data.local.AppDatabase
import com.waray.spendhound.data.local.CacheKeys
import com.waray.spendhound.ui.multi_transaction.TransactionFull
import com.waray.spendhound.ui.multi_transaction.TransactionPayorTable
import kotlinx.coroutines.flow.Flow

data class GroupListItem(
    val group: PayerGroup,
    val members: List<User>,
    val cardData: GroupsActivity.GroupCardData
)

class GroupsListRepository(private val db: AppDatabase) {

    fun getGroups(userId: Long, allUsers: List<User>): Flow<List<GroupListItem>> = db.cachedFlow(
        key = CacheKeys.groupsList(userId),
        staleTtlMs = CacheKeys.STALE_GROUPS_LIST,
        type = typeOf<List<GroupListItem>>()
    ) { fetchGroups(userId, allUsers) }

    suspend fun invalidate(userId: Long) {
        db.jsonBlobDao().delete(CacheKeys.groupsList(userId))
    }

    private suspend fun fetchGroups(userId: Long, allUsers: List<User>): List<GroupListItem> {
        val myGroupIds = DeclareDatabase.groupMembersTable.select {
            filter { eq("user_id", userId) }
        }.decodeList<GroupMember>().mapNotNull { it.groupId }.toSet()

        val allGroups = DeclareDatabase.groupsTable.select().decodeList<PayerGroup>()
            .filter { it.groupId in myGroupIds }

        val allMembers = DeclareDatabase.groupMembersTable.select {
            filter { isIn("group_id", myGroupIds.toList()) }
        }.decodeList<GroupMember>()
        val membersByGroup = allMembers.groupBy { it.groupId }

        val groupIds = allGroups.mapNotNull { it.groupId }
        val allTransactions = if (groupIds.isNotEmpty()) DeclareDatabase.transactionsTable.select {
            filter { isIn("group_id", groupIds) }
            order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
        }.decodeList<TransactionFull>() else emptyList()

        val allTxIds = allTransactions.mapNotNull { it.id }
        val allPayors = if (allTxIds.isNotEmpty()) DeclareDatabase.transactionPayorsTable.select {
            filter { isIn("transaction_id", allTxIds) }
        }.decodeList<TransactionPayorTable>() else emptyList()

        val allMessages = if (groupIds.isNotEmpty()) DeclareDatabase.groupMessagesTable.select {
            filter { isIn("group_id", groupIds); eq("is_deleted", false) }
            order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            limit(1000)
        }.decodeList<GroupMessage>() else emptyList()

        val readReceipts = DeclareDatabase.messageReadsTable.select {
            filter { eq("user_id", userId) }
        }.decodeList<MessageRead>()
        val maxReadByGroup = readReceipts.groupBy { it.groupId }
            .mapValues { e -> e.value.maxOfOrNull { it.messageId ?: 0L } ?: 0L }

        val txReadReceipts = DeclareDatabase.transactionReadsTable.select {
            filter { eq("user_id", userId) }
        }.decodeList<TransactionRead>()
        val maxTxReadByGroup = txReadReceipts.groupBy { it.groupId }
            .mapValues { e -> e.value.maxOfOrNull { it.transactionId ?: 0L } ?: 0L }

        val lastActivityMap = mutableMapOf<Long, Long>()
        val result = allGroups.map { group ->
            val gid = group.groupId ?: 0L
            val txs = allTransactions.filter { it.groupId == gid }
            val groupMsgs = allMessages.filter { it.groupId == gid }
            val txIds = txs.mapNotNull { it.id }
            val totalExpenses = txs.sumOf { it.totalAmount }
            val activeCount = txs.count { (it.status ?: 0) == 2 }
            val settledAmount = allPayors.filter { it.transactionId in txIds && it.status == 1 }.sumOf { it.currentAmountPaid }
            val maxReadId = maxReadByGroup[gid] ?: 0L
            val unreadMsgs = groupMsgs.count { it.userId != userId && it.id != null && it.id!! > maxReadId }
            val maxTxReadId = maxTxReadByGroup[gid] ?: 0L
            val unreadTxs = txs.count { it.createdBy != userId && it.id != null && it.id!! > maxTxReadId }
            val cardData = GroupsActivity.GroupCardData(totalExpenses, activeCount, settledAmount, unreadMsgs + unreadTxs)

            val lastTxTime = txs.firstOrNull()?.createdAt?.let { parseIsoTime(it) } ?: 0L
            val lastMsgTime = groupMsgs.firstOrNull()?.createdAt?.let { parseIsoTime(it) } ?: 0L
            lastActivityMap[gid] = maxOf(lastTxTime, lastMsgTime)

            val memberIds = membersByGroup[gid]?.mapNotNull { it.userId } ?: emptyList()
            val members = allUsers.filter { it.id in memberIds }
            GroupListItem(group, members, cardData)
        }.sortedByDescending { lastActivityMap[it.group.groupId] ?: 0L }

        return result
    }

    private fun parseIsoTime(iso: String): Long = try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        sdf.parse(iso.take(19))?.time ?: 0L
    } catch (e: Exception) { 0L }
}
