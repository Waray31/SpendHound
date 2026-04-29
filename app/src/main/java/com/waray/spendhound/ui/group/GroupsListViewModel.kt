package com.waray.spendhound.ui.group

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.waray.spendhound.SpendHoundApplication
import com.waray.spendhound.User
import com.waray.spendhound.data.repository.GroupListItem
import com.waray.spendhound.data.repository.GroupsListRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GroupsListViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as SpendHoundApplication).database
    private val repo = GroupsListRepository(db)

    private val _groups = MutableStateFlow<List<GroupListItem>>(emptyList())
    val groups: StateFlow<List<GroupListItem>> = _groups

    fun load(userId: Long, allUsers: List<User>) {
        viewModelScope.launch {
            repo.getGroups(userId, allUsers).collectLatest { _groups.value = it }
        }
    }

    fun invalidate(userId: Long, allUsers: List<User>) {
        viewModelScope.launch {
            repo.invalidate(userId)
            load(userId, allUsers)
        }
    }

    fun forceRefresh(userId: Long, allUsers: List<User>) {
        viewModelScope.launch {
            repo.invalidate(userId)
            repo.getGroups(userId, allUsers).collectLatest { _groups.value = it }
        }
    }
}
