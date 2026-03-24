package com.waray.spendhound.ui.multi_transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.PayerGroup
import com.waray.spendhound.User
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MultiTransactionViewModel(
    private val repository: MultiTransactionRepository = MultiTransactionRepository()
) : ViewModel() {

    private val _groups = MutableStateFlow<List<PayerGroup>>(emptyList())
    val groups: StateFlow<List<PayerGroup>> = _groups.asStateFlow()

    private val _members = MutableStateFlow<List<User>>(emptyList())
    val members: StateFlow<List<User>> = _members.asStateFlow()

    private val _isMultiplePayorsMode = MutableStateFlow(false)
    val isMultiplePayorsMode: StateFlow<Boolean> = _isMultiplePayorsMode.asStateFlow()

    private val _transactions = MutableStateFlow<List<TransactionEntry>>(listOf(TransactionEntry()))
    val transactions: StateFlow<List<TransactionEntry>> = _transactions.asStateFlow()

    private val _totalAmount = MutableStateFlow(0.0)
    val totalAmount: StateFlow<Double> = _totalAmount.asStateFlow()

    private val _currentUserNumericId = MutableStateFlow<Long?>(null)
    
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        fetchGroups()
        fetchCurrentUser()
    }

    private fun fetchCurrentUser() {
        val authId = DeclareDatabase.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch {
            try {
                val user = DeclareDatabase.client.postgrest.from("users").select {
                    filter { eq("auth_id", authId) }
                }.decodeSingleOrNull<User>()
                _currentUserNumericId.value = user?.id
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    private fun fetchGroups() {
        viewModelScope.launch {
            try {
                val fetchedGroups = repository.getGroups()
                _groups.value = fetchedGroups
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Failed to fetch groups")
            }
        }
    }

    fun onGroupSelected(groupId: Long) {
        viewModelScope.launch {
            try {
                val fetchedMembers = repository.getGroupMembers(groupId)
                _members.value = fetchedMembers
                
                // If in single mode, default payor to current user if they are in the group
                if (!_isMultiplePayorsMode.value) {
                    val currentId = _currentUserNumericId.value
                    val user = fetchedMembers.find { it.id == currentId }
                    if (user != null && currentId != null) {
                        updateGlobalPayors(listOf(PayorEntry(currentId, user.username ?: "Me")))
                    }
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Failed to fetch members")
            }
        }
    }

    fun setMultiplePayorsMode(isMultiple: Boolean) {
        _isMultiplePayorsMode.value = isMultiple
    }

    fun addTransaction() {
        val currentList = _transactions.value.toMutableList()
        val lastEntry = currentList.lastOrNull()
        val newEntry = TransactionEntry(
            category = lastEntry?.category ?: "General",
            payors = lastEntry?.payors?.map { it.copy() }?.toMutableList() ?: mutableListOf()
        )
        currentList.add(newEntry)
        _transactions.value = currentList
        calculateTotals()
    }

    fun removeTransaction(position: Int) {
        val currentList = _transactions.value.toMutableList()
        if (position in currentList.indices) {
            currentList.removeAt(position)
            _transactions.value = currentList
            calculateTotals()
        }
    }

    fun calculateTotals() {
        var total = 0.0
        _transactions.value.forEach { total += it.amount }
        _totalAmount.value = total
    }

    fun updatePayorsForTransaction(position: Int, selectedPayors: List<PayorEntry>) {
        val currentList = _transactions.value.toMutableList()
        if (position in currentList.indices) {
            currentList[position] = currentList[position].copy(payors = selectedPayors.toMutableList())
            _transactions.value = currentList
        }
    }

    fun updateGlobalPayors(selectedPayors: List<PayorEntry>) {
        val currentList = _transactions.value.map { it.copy(payors = selectedPayors.toMutableList()) }
        _transactions.value = currentList
    }

    fun submit(groupId: Long) {
        val creatorId = _currentUserNumericId.value
        if (creatorId == null) {
            _uiState.value = UiState.Error("User session not found")
            return
        }

        if (_transactions.value.any { it.title.isBlank() || it.amount <= 0 }) {
            _uiState.value = UiState.Error("Please fill in all titles and amounts")
            return
        }

        if (_transactions.value.any { it.payors.isEmpty() }) {
            _uiState.value = UiState.Error("Please select who paid for each transaction")
            return
        }

        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val result = repository.submitTransactions(groupId, creatorId, _transactions.value, _members.value)
            result.onSuccess {
                _uiState.value = UiState.Success
            }.onFailure {
                _uiState.value = UiState.Error(it.message ?: "Submission failed")
            }
        }
    }

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        object Success : UiState()
        data class Error(val message: String) : UiState()
    }
}
