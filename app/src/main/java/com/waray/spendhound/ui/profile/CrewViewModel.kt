package com.waray.spendhound.ui.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waray.spendhound.CrewMember
import com.waray.spendhound.User
import com.waray.spendhound.data.repository.CrewRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CrewViewModel : ViewModel() {

    private val repo = CrewRepository()

    private val _crewList = MutableStateFlow<List<Pair<CrewMember, User>>>(emptyList())
    val crewList: StateFlow<List<Pair<CrewMember, User>>> = _crewList

    private val _pendingInvites = MutableStateFlow<List<Pair<CrewMember, User>>>(emptyList())
    val pendingInvites: StateFlow<List<Pair<CrewMember, User>>> = _pendingInvites

    private val _searchResults = MutableStateFlow<List<User>>(emptyList())
    val searchResults: StateFlow<List<User>> = _searchResults

    private val _allUsers = MutableStateFlow<List<User>>(emptyList())
    val allUsers: StateFlow<List<User>> = _allUsers

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError

    private val _actionSuccess = MutableStateFlow<String?>(null)
    val actionSuccess: StateFlow<String?> = _actionSuccess

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun loadCrew(userId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repo.getCrewListFlow(userId).collect { list ->
                    _crewList.value = list
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e("CrewDebug", "loadCrew EXCEPTION: ${e.message}", e)
                _actionError.value = "Failed to load crew."
            } finally {
                _isLoading.value = false
            }
        }
        viewModelScope.launch {
            try {
                val pending = repo.getPendingInvites(userId)
                _pendingInvites.value = pending
            } catch (e: Exception) {
                Log.e("CrewDebug", "getPendingInvites EXCEPTION: ${e.message}", e)
            }
        }
    }

    fun reloadCrew(userId: Long) {
        Log.d("CrewDebug", "reloadCrew called userId=$userId")
        viewModelScope.launch {
            try {
                repo.invalidateCrew(userId)
                Log.d("CrewDebug", "reloadCrew cache invalidated")
                _isLoading.value = true
                repo.getCrewListFlow(userId).collect { list ->
                    Log.d("CrewDebug", "reloadCrew crewList emit size=${list.size}")
                    _crewList.value = list
                    _isLoading.value = false
                }
                Log.d("CrewDebug", "reloadCrew flow COMPLETED")
            } catch (e: Exception) {
                Log.e("CrewDebug", "reloadCrew EXCEPTION: ${e.message}", e)
                _actionError.value = "Failed to reload crew."
                _isLoading.value = false
            }
        }
    }

    fun loadAllUsers(currentUserId: Long) {
        viewModelScope.launch {
            try {
                val users = repo.getAllUsers(currentUserId)
                _allUsers.value = users
                _searchResults.value = users
            } catch (e: Exception) {
                _actionError.value = "Failed to load users."
            }
        }
    }

    fun searchUsers(query: String, currentUserId: Long) {
        _searchResults.value = if (query.isBlank()) {
            _allUsers.value
        } else {
            repo.filterAndSort(query, _allUsers.value)
        }
    }

    fun clearSearch() { _searchResults.value = _allUsers.value }

    fun sendInvite(ownerUserId: Long, memberUserId: Long, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val error = try { repo.sendInvite(ownerUserId, memberUserId) }
            catch (e: Exception) { e.message }
            onDone(error)
        }
    }

    fun createGuestAndInvite(name: String, email: String?, phone: String?, invitedByUserId: Long, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val guest = repo.createGuestUser(name, email, invitedByUserId)
                    ?: return@launch onDone("Failed to create guest user.")
                val error = repo.sendInvite(invitedByUserId, guest.id!!)
                onDone(error)
            } catch (e: Exception) {
                onDone(e.message)
            }
        }
    }

    fun respondToInvite(crewId: Long, accept: Boolean, userId: Long) {
        viewModelScope.launch {
            try {
                repo.respondToInvite(crewId, accept)
                repo.invalidateCrew(userId)
                reloadCrew(userId)
            } catch (e: Exception) {
                _actionError.value = "Failed to respond to invite."
            }
        }
    }

    fun removeCrew(crewId: Long, userId: Long) {
        viewModelScope.launch {
            try {
                repo.removeCrew(crewId)
                repo.invalidateCrew(userId)
                reloadCrew(userId)
            } catch (e: Exception) {
                _actionError.value = "Failed to remove crew member."
            }
        }
    }

    fun clearError() { _actionError.value = null }
    fun clearSuccess() { _actionSuccess.value = null }
}
