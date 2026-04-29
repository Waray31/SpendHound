package com.waray.spendhound.data.repository

import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.GroupMember
import com.waray.spendhound.PayerGroup
import com.waray.spendhound.User
import com.waray.spendhound.data.local.AppDatabase
import com.waray.spendhound.data.local.CacheKeys
import com.waray.spendhound.ui.group.GroupDetailData
import com.waray.spendhound.ui.group.MemberWithUser
import kotlinx.coroutines.flow.Flow

class GroupDetailRepository(private val db: AppDatabase) {

    fun getGroupDetail(groupId: Long, currentUserId: Long): Flow<GroupDetailData> = db.cachedFlow(
        key = CacheKeys.groupDetail(groupId),
        staleTtlMs = CacheKeys.STALE_GROUP_DETAIL,
        type = typeOf<GroupDetailData>()
    ) { fetchGroupDetail(groupId, currentUserId) }

    suspend fun invalidate(groupId: Long) {
        db.jsonBlobDao().delete(CacheKeys.groupDetail(groupId))
    }

    private suspend fun fetchGroupDetail(groupId: Long, currentUserId: Long): GroupDetailData {
        val group = DeclareDatabase.groupsTable.select {
            filter { eq("group_id", groupId) }
        }.decodeSingleOrNull<PayerGroup>() ?: PayerGroup(groupId = groupId)

        val members = DeclareDatabase.groupMembersTable.select {
            filter { eq("group_id", groupId) }
        }.decodeList<GroupMember>()

        val userIds = members.mapNotNull { it.userId }
        val users = if (userIds.isNotEmpty()) {
            DeclareDatabase.usersTable.select {
                filter { isIn("user_id", userIds) }
            }.decodeList<User>()
        } else emptyList()

        val usersById = users.associateBy { it.id }
        val memberList = members.mapNotNull { m ->
            val u = usersById[m.userId] ?: return@mapNotNull null
            MemberWithUser(m, u)
        }

        return GroupDetailData(
            group = group,
            members = memberList,
            isAdmin = members.any { it.userId == currentUserId && it.admin }
        )
    }
}
