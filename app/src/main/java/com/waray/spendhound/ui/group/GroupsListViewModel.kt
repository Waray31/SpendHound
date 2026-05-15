package com.waray.spendhound.ui.group

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.waray.spendhound.SpendHoundApplication
import com.waray.spendhound.User
import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.data.repository.GroupListItem
import com.waray.spendhound.data.repository.GroupsListRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GroupsListViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as SpendHoundApplication).database
    private val repo = GroupsListRepository(db)

    private val _groups = MutableStateFlow<List<GroupListItem>?>(null)
    val groups: StateFlow<List<GroupListItem>?> = _groups

    fun load(userId: Long) {
        viewModelScope.launch {
            try {
                // First fetch all users if not already available
                val allUsers = DeclareDatabase.usersTable.select().decodeList<User>()
                repo.getGroups(userId, allUsers).collectLatest { 
                    android.util.Log.d("GroupsViewModel", "Emitting groups: ${it.size}")
                    _groups.value = it 
                }
            } catch (e: Exception) {
                android.util.Log.e("GroupsViewModel", "Error loading groups", e)
                // Do not set to emptyList() here, let it stay null (skeleton) 
                // or we could add an error state later.
            }
        }
    }

    fun invalidate(userId: Long) {
        viewModelScope.launch {
            _groups.value = null // Clear state to show skeleton
            repo.invalidate(userId)
            load(userId)
        }
    }

    fun forceRefresh(userId: Long) {
        viewModelScope.launch {
            _groups.value = null // Clear state to show skeleton
            repo.invalidate(userId)
            load(userId)
        }
    }
}
