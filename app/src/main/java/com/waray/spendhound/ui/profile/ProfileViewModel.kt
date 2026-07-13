package com.waray.spendhound.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.waray.spendhound.SpendHoundApplication
import com.waray.spendhound.data.repository.ProfileData
import com.waray.spendhound.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ProfileViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as SpendHoundApplication).database
    private val repo = ProfileRepository(db)

    private val _profile = MutableStateFlow<ProfileData?>(null)
    val profile: StateFlow<ProfileData?> = _profile

    private val _groups = MutableStateFlow<List<ProfileGroupItem>?>(null)
    val groups: StateFlow<List<ProfileGroupItem>?> = _groups

    fun load(userId: Long, authId: String) {
        viewModelScope.launch {
            try {
                repo.getProfile(userId, authId).collectLatest { _profile.value = it }
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "Error loading profile: ${e.message}")
            }
        }
        viewModelScope.launch {
            try {
                repo.getProfileGroups(userId).collectLatest { _groups.value = it }
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "Error loading profile groups: ${e.message}")
            }
        }
    }

    fun invalidate(userId: Long, authId: String) {
        viewModelScope.launch {
            repo.invalidate(userId)
            load(userId, authId)
        }
    }
}
