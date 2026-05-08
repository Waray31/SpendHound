package com.waray.spendhound.ui.multi_transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.PayerContribution
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

    private val _transactionTitle = MutableStateFlow("")
    val transactionTitle: StateFlow<String> = _transactionTitle.asStateFlow()

    private val _currentUserNumericId = MutableStateFlow<Long?>(null)
    
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

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
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun onGroupSelected(groupId: Long) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val fetchedMembers = repository.getGroupMembers(groupId)
                _members.value = fetchedMembers

                if (!_isMultiplePayorsMode.value) {
                    val currentId = _currentUserNumericId.value
                    val user = fetchedMembers.find { it.id == currentId }
                    if (user != null && currentId != null) {
                        updateGlobalPayors(listOf(PayorEntry(currentId, user.username ?: "Me")))
                    }
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Failed to fetch members")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setTransactionTitle(title: String) {
        _transactionTitle.value = title
    }

    fun setMultiplePayorsMode(isMultiple: Boolean) {
        _isMultiplePayorsMode.value = isMultiple
    }

    fun addTransaction() {
        val currentList = _transactions.value.toMutableList()
        currentList.add(TransactionEntry())
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

    private val _globalPayors = MutableStateFlow<List<PayorEntry>>(emptyList())
    val globalPayors: StateFlow<List<PayorEntry>> = _globalPayors.asStateFlow()

    fun updateGlobalPayors(selectedPayors: List<PayorEntry>) {
        _globalPayors.value = selectedPayors
        val currentList = _transactions.value.map { transaction ->
            // In single payor mode, set the payor amount to the transaction amount
            val payorsWithAmounts = selectedPayors.map { payor ->
                payor.copy(amount = transaction.amount)
            }.toMutableList()
            transaction.copy(payors = payorsWithAmounts)
        }
        _transactions.value = currentList
    }

    fun submit(groupId: Long, requireTitle: Boolean = true) {
        val creatorId = _currentUserNumericId.value
        if (creatorId == null) {
            _uiState.value = UiState.Error("User session not found")
            return
        }

        if (requireTitle && _transactionTitle.value.isBlank()) {
            _uiState.value = UiState.Error("Please enter a transaction title")
            return
        }

        if (_transactions.value.any { it.amount <= 0 }) {
            _uiState.value = UiState.Error("Please fill in all amounts")
            return
        }

        if (_transactions.value.any { it.payors.isEmpty() }) {
            _uiState.value = UiState.Error("Please select who paid for each transaction")
            return
        }

        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val result = repository.submitTransactions(
                groupId, creatorId, _transactionTitle.value, _transactions.value, _members.value
            )
            result.onSuccess {
                _uiState.value = UiState.Success
            }.onFailure {
                _uiState.value = UiState.Error(it.message ?: "Submission failed")
            }
        }
    }

    fun updateItemPaymentConfig(position: Int, payers: List<PayerContribution>, participants: List<String>) {
        val currentList = _transactions.value.toMutableList()
        if (position in currentList.indices) {
            // Convert to existing PayorEntry format for compatibility
            val payorEntries = payers.map { payer ->
                PayorEntry(
                    userId = payer.payerId.toLongOrNull() ?: 0L,
                    username = payer.payerName,
                    amount = payer.amount
                )
            }.toMutableList()
            
            currentList[position] = currentList[position].copy(payors = payorEntries)
            _transactions.value = currentList
            calculateTotals()
        }
    }

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        object Success : UiState()
        data class Error(val message: String) : UiState()
    }
}
