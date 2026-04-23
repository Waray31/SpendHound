package com.waray.spendhound.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.GroupMember
import com.waray.spendhound.PayerGroup
import com.waray.spendhound.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class GroupDetailData(
    val group: PayerGroup,
    val members: List<Pair<GroupMember, User>>,
    val isAdmin: Boolean
)

class GroupDetailViewModel : ViewModel() {
    private val _groupData = MutableStateFlow<GroupDetailData?>(null)
    val groupData: StateFlow<GroupDetailData?> = _groupData

    private val cache = mutableMapOf<Long, GroupDetailData>()

    fun preloadGroup(groupId: Long, currentUserId: Long) {
        cache[groupId]?.let { _groupData.value = it; return }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val group = DeclareDatabase.groupsTable.select {
                    filter { eq("group_id", groupId) }
                }.decodeSingleOrNull<PayerGroup>() ?: return@launch

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
                val memberPairs = members.mapNotNull { m ->
                    val u = usersById[m.userId] ?: return@mapNotNull null
                    Pair(m, u)
                }

                val data = GroupDetailData(
                    group = group,
                    members = memberPairs,
                    isAdmin = members.any { it.userId == currentUserId && it.admin }
                )
                cache[groupId] = data
                _groupData.value = data
            } catch (_: Exception) {}
        }
    }
}
