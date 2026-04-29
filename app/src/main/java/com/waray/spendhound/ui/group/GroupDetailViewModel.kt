package com.waray.spendhound.ui.group

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.waray.spendhound.GroupMember
import com.waray.spendhound.PayerGroup
import com.waray.spendhound.SpendHoundApplication
import com.waray.spendhound.User
import com.waray.spendhound.data.repository.GroupDetailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class MemberWithUser(
    val member: GroupMember,
    val user: User
)

data class GroupDetailData(
    val group: PayerGroup,
    val members: List<MemberWithUser>,
    val isAdmin: Boolean
)

class GroupDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = GroupDetailRepository((app as SpendHoundApplication).database)

    private val _groupData = MutableStateFlow<GroupDetailData?>(null)
    val groupData: StateFlow<GroupDetailData?> = _groupData

    fun preloadGroup(groupId: Long, currentUserId: Long) {
        viewModelScope.launch {
            repo.getGroupDetail(groupId, currentUserId).collectLatest { _groupData.value = it }
        }
    }

    fun forceRefresh(groupId: Long, currentUserId: Long) {
        viewModelScope.launch {
            repo.invalidate(groupId)
            preloadGroup(groupId, currentUserId)
        }
    }
}
