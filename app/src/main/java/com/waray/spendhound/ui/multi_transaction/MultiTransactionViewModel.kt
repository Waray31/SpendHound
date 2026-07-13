package com.waray.spendhound.ui.multi_transaction

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.PayerContribution
import com.waray.spendhound.PayerGroup
import com.waray.spendhound.SpendHoundApplication
import com.waray.spendhound.User
import com.waray.spendhound.data.local.AppDatabase
import com.waray.spendhound.data.local.CachedJsonBlob
import com.waray.spendhound.data.local.PendingTransaction
import com.waray.spendhound.utils.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class MultiTransactionViewModel @JvmOverloads constructor(
    application: android.app.Application
) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val pendingTransactionDao = database.pendingTransactionDao()
    private val repository: MultiTransactionRepository = MultiTransactionRepository(database)

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

    val averageSplitPerIncludedMember: StateFlow<Double> = combine(
        _totalAmount,
        _transactions,
        _members
    ) { total, transactions, members ->
        val userOwedMap = mutableMapOf<Long, Double>()
        transactions.forEach { entry ->
            val participants = if (entry.includedMemberIds.isEmpty()) members.mapNotNull { it.id } else entry.includedMemberIds
            if (participants.isNotEmpty()) {
                val split = entry.amount / participants.size
                participants.forEach { uid ->
                    userOwedMap[uid] = (userOwedMap[uid] ?: 0.0) + split
                }
            }
        }
        val participatingUsers = userOwedMap.filter { it.value > 0.01 }
        if (participatingUsers.isNotEmpty()) {
            participatingUsers.values.average()
        } else {
            0.0
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0.0)

    private val _transactionTitle = MutableStateFlow("")
    val transactionTitle: StateFlow<String> = _transactionTitle.asStateFlow()

    private val _currentUserNumericId = MutableStateFlow<Long?>(null)
    
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        fetchGroups()
        fetchCurrentUser()
    }

    private fun fetchCurrentUser() {
        viewModelScope.launch {
            // First, try to load from cache immediately
            try {
                val cachedId = database.jsonBlobDao().get("current_user_id")
                if (cachedId != null) {
                    _currentUserNumericId.value = cachedId.json.toLongOrNull()
                    android.util.Log.d("MultiTransactionVM", "Loaded user ID from cache: ${_currentUserNumericId.value}")
                }
                
                val cachedUser = database.jsonBlobDao().get("current_user_obj")
                if (cachedUser != null) {
                    _currentUser.value = AppJson.decodeFromString<User>(cachedUser.json)
                }
            } catch (e: Exception) {
                android.util.Log.e("MultiTransactionVM", "Error loading user from cache", e)
            }

            // Then, if online, try to refresh from network
            val authId = DeclareDatabase.auth.currentUserOrNull()?.id
            if (authId != null && NetworkMonitor.isOnline.value) {
                try {
                    val user = DeclareDatabase.usersTable.select {
                        filter { eq("auth_id", authId) }
                    }.decodeSingleOrNull<User>()
                    
                    if (user != null) {
                        _currentUserNumericId.value = user.id
                        _currentUser.value = user
                        // Update cache
                        database.jsonBlobDao().upsert(CachedJsonBlob("current_user_id", user.id.toString(), System.currentTimeMillis()))
                        database.jsonBlobDao().upsert(CachedJsonBlob("current_user_obj", Json.encodeToString(user), System.currentTimeMillis()))
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MultiTransactionVM", "Error fetching user from network", e)
                }
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

    fun onGroupSelected(groupId: Long, resetTransactions: Boolean = true) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val fetchedMembers = repository.getGroupMembers(groupId)
                _members.value = fetchedMembers

                if (resetTransactions) {
                    // Reset transactions to single empty item when group changes
                    _transactions.value = listOf(TransactionEntry())
                    calculateTotals()
                }

                // Remove automatic payor assignment - let users configure payments manually
                // if (!_isMultiplePayorsMode.value) {
                //     val currentId = _currentUserNumericId.value
                //     val user = fetchedMembers.find { it.id == currentId }
                //     if (user != null && currentId != null) {
                //         updateGlobalPayors(listOf(PayorEntry(currentId, user.username ?: "Me")))
                //     }
                // }
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
        
        // Debug logging
        android.util.Log.d("MultiTransactionVM", "calculateTotals - total: $total, transactions: ${_transactions.value.size}")
        _transactions.value.forEachIndexed { index, tx ->
            android.util.Log.d("MultiTransactionVM", "Transaction $index: amount=${tx.amount}")
        }
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

    fun submit(groupId: Long?, requireTitle: Boolean = true) {
        val creatorId = _currentUserNumericId.value
        if (creatorId == null) {
            val isOnline = NetworkMonitor.isOnline.value
            val msg = if (isOnline) "User session not found. Please try again in a moment." 
                      else "User session not found. Please connect to the internet to initialize your offline profile."
            _uiState.value = UiState.Error(msg)
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

        viewModelScope.launch {
            _uiState.value = UiState.Loading
            
            try {
                if (NetworkMonitor.isOnline.value && groupId != null) {
                    if (_transactions.value.any { it.payors.isEmpty() }) {
                        _uiState.value = UiState.Error("Please select who paid for each transaction")
                        return@launch
                    }

                    val result = repository.submitTransactions(
                        groupId, creatorId, _transactionTitle.value, _transactions.value, _members.value
                    )
                    result.onSuccess {
                        _uiState.value = UiState.Success
                    }.onFailure {
                        _uiState.value = UiState.Error(it.message ?: "Submission failed")
                    }
                } else {
                    // Offline branch or unassigned
                    val currentUser = _currentUser.value
                    val currentTransactionsSnapshot = _transactions.value.toList()
                    
                    val updatedTransactions = currentTransactionsSnapshot.map { entry ->
                        if (entry.payors.isEmpty() && currentUser != null) {
                            entry.copy(payors = mutableListOf(PayorEntry(creatorId, currentUser.username ?: "Me", entry.amount)))
                        } else {
                            if (entry.payors.size == 1) {
                                entry.copy(payors = mutableListOf(entry.payors[0].copy(amount = entry.amount)))
                            } else entry
                        }
                    }

                    val newTotalAmount = updatedTransactions.sumOf { it.amount }
                    val itemsJson = AppJson.encodeToString(updatedTransactions)
                    val localId = UUID.randomUUID().toString()
                    val pending = PendingTransaction(
                        localId = localId,
                        groupId = groupId,
                        createdByUserId = creatorId,
                        description = _transactionTitle.value,
                        totalAmount = newTotalAmount,
                        itemsJson = itemsJson,
                        createdAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                    )
                    
                    withContext(Dispatchers.IO) {
                        pendingTransactionDao.insert(pending)
                    }
                    SpendHoundApplication.scheduleSync(getApplication())
                    _uiState.value = UiState.SuccessOffline
                }
            } catch (e: Exception) {
                android.util.Log.e("MultiTransactionVM", "Error submitting transaction", e)
                _uiState.value = UiState.Error("Failed to save: ${e.message}")
            }
        }
    }

    fun updateItemPaymentConfig(
        position: Int, 
        payers: List<PayerContribution>, 
        participants: List<String>, 
        coveredByMap: Map<String, String> = emptyMap(),
        splitMode: Int = 0,
        customSplitMap: Map<String, Double> = emptyMap()
    ) {
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
            
            // Convert participant IDs to Long list
            val includedMemberIds = participants.mapNotNull { it.toLongOrNull() }

            // Convert coveredByMap String keys/values to Long
            val longCoveredByMap = coveredByMap.mapNotNull { entry ->
                val coveredId = entry.key.toLongOrNull()
                val covererId = entry.value.toLongOrNull()
                if (coveredId != null && covererId != null) {
                    coveredId to covererId
                } else null
            }.toMap()

            // Convert customSplitMap String keys to Long
            val longCustomSplitMap = customSplitMap.mapNotNull { entry ->
                val userId = entry.key.toLongOrNull()
                if (userId != null) {
                    userId to entry.value
                } else null
            }.toMap()
            
            currentList[position] = currentList[position].copy(
                payors = payorEntries,
                includedMemberIds = includedMemberIds,
                coveredByMap = longCoveredByMap,
                splitMode = splitMode,
                customSplitMap = longCustomSplitMap
            )
            _transactions.value = currentList
            calculateTotals()
        }
    }
    
    fun updateTransactionAmount(position: Int, amount: Double) {
        val currentList = _transactions.value.toMutableList()
        if (position in currentList.indices) {
            currentList[position] = currentList[position].copy(amount = amount)
            _transactions.value = currentList
            calculateTotals()
        }
    }
    
    fun updateTransactionCategory(position: Int, category: String) {
        val currentList = _transactions.value.toMutableList()
        if (position in currentList.indices) {
            currentList[position] = currentList[position].copy(category = category)
            _transactions.value = currentList
        }
    }

    fun updateItemTitle(position: Int, title: String) {
        val currentList = _transactions.value.toMutableList()
        if (position in currentList.indices) {
            currentList[position] = currentList[position].copy(title = title)
            _transactions.value = currentList
        }
    }

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        object Success : UiState()
        object SuccessOffline : UiState()
        data class Error(val message: String) : UiState()
    }
    
    fun loadExistingTransaction(
        transaction: TransactionFull,
        items: List<TransactionItemFull>,
        payors: List<TransactionPayorTable>,
        splits: List<TransactionSplitTable>
    ) {
        // Convert existing transaction data to TransactionEntry format
        val transactionEntries = items.map { item ->
            val itemPayors = payors.filter { it.transactionItemsId == item.id }
            val payorEntries = itemPayors.map { payor ->
                val user = _members.value.find { it.id == payor.userId }
                PayorEntry(
                    userId = payor.userId,
                    username = user?.username ?: "Unknown",
                    amount = payor.currentAmountPaid
                )
            }.toMutableList()
            
            val itemSplits = splits.filter { it.transactionItemsId == item.id }
            val includedMemberIds = itemSplits.map { it.userId }
            
            val coveredByMap = itemSplits
                .filter { it.coveredByUserId != null }
                .associate { it.userId to it.coveredByUserId!! }
            
            TransactionEntry(
                title = item.itemDescription ?: "",
                amount = item.amount,
                category = item.category ?: "",
                payors = payorEntries,
                includedMemberIds = includedMemberIds,
                coveredByMap = coveredByMap
            )
        }
        
        _transactions.value = transactionEntries
        _transactionTitle.value = transaction.description ?: ""
        calculateTotals()
    }
    
    fun updateTransaction(transactionId: Long, groupId: Long, requireTitle: Boolean = true) {
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
            if (NetworkMonitor.isOnline.value) {
                _uiState.value = UiState.Error("Please select who paid for each transaction")
                return
            } else {
                // In offline mode, if no payors selected, default to current user paying everything
                val currentUser = _currentUser.value
                if (currentUser != null) {
                    val updatedTransactions = _transactions.value.map { entry ->
                        if (entry.payors.isEmpty()) {
                            entry.copy(payors = mutableListOf(PayorEntry(creatorId, currentUser.username ?: "Me", entry.amount)))
                        } else entry
                    }
                    _transactions.value = updatedTransactions
                } else {
                    _uiState.value = UiState.Error("User session not found")
                    return
                }
            }
        }

        viewModelScope.launch {
            _uiState.value = UiState.Loading

            if (NetworkMonitor.isOnline.value) {
                val result = repository.updateTransaction(
                    transactionId, groupId, creatorId, _transactionTitle.value, _transactions.value, _members.value
                )
                result.onSuccess {
                    _uiState.value = UiState.Success
                }.onFailure {
                    _uiState.value = UiState.Error(it.message ?: "Update failed")
                }
            } else {
                _uiState.value = UiState.Error("Cannot update synced transactions while offline.")
            }
        }
    }

    fun updatePendingTransaction(localId: String, groupId: Long?, requireTitle: Boolean = true) {
        val creatorId = _currentUserNumericId.value
        if (creatorId == null) {
            _uiState.value = UiState.Error("User session not found")
            return
        }

        val isOnline = NetworkMonitor.isOnline.value
        val currentTransactionsSnapshot = _transactions.value.toList()

        // Validation for online submission
        if (isOnline && requireTitle && _transactionTitle.value.isBlank() && currentTransactionsSnapshot.size > 1) {
            _uiState.value = UiState.Error("Please enter a transaction title")
            return
        }

        if (currentTransactionsSnapshot.any { it.amount <= 0 }) {
            _uiState.value = UiState.Error("Please fill in all amounts")
            return
        }

        viewModelScope.launch {
            _uiState.value = UiState.Loading
            
            try {
                // Check if all details are met for cloud submission
                val allPayorsValid = currentTransactionsSnapshot.all { entry ->
                    val totalPaid = entry.payors.sumOf { it.amount }
                    entry.payors.isNotEmpty() && Math.abs(totalPaid - entry.amount) < 0.01
                }
                
                val detailsMet = groupId != null && 
                                 currentTransactionsSnapshot.all { it.amount > 0 && it.category.isNotBlank() } &&
                                 allPayorsValid

                if (isOnline && detailsMet) {
                    // Directly save to cloud database
                    val result = repository.submitTransactions(
                        groupId!!, creatorId, _transactionTitle.value, currentTransactionsSnapshot, _members.value, status = 2
                    )
                    result.onSuccess {
                        // Success! Delete the offline pending record
                        withContext(Dispatchers.IO) {
                            pendingTransactionDao.delete(localId)
                        }
                        _uiState.value = UiState.Success
                        return@launch
                    }.onFailure {
                        // If direct sync fails, fall back to updating the local record
                        performLocalPendingSave(localId, groupId, creatorId, currentTransactionsSnapshot)
                        _uiState.value = UiState.Error("Cloud sync failed (${it.message}), but changes were saved offline.")
                        return@launch
                    }
                } else {
                    // Offline mode or details not met - save local update
                    performLocalPendingSave(localId, groupId, creatorId, currentTransactionsSnapshot)
                    _uiState.value = UiState.SuccessOffline
                }
            } catch (e: Exception) {
                android.util.Log.e("MultiTransactionVM", "Error in updatePendingTransaction", e)
                _uiState.value = UiState.Error("Failed to save: ${e.message}")
            }
        }
    }

    private suspend fun performLocalPendingSave(localId: String, groupId: Long?, creatorId: Long, transactions: List<TransactionEntry>) {
        try {
            val currentUser = _currentUser.value
            val updatedTransactions = transactions.map { entry ->
                if (entry.payors.isEmpty() && currentUser != null) {
                    entry.copy(payors = mutableListOf(PayorEntry(creatorId, currentUser.username ?: "Me", entry.amount)))
                } else {
                    // Ensure amount in payor matches transaction amount if only 1 payor
                    if (entry.payors.size == 1) {
                        entry.copy(payors = mutableListOf(entry.payors[0].copy(amount = entry.amount)))
                    } else entry
                }
            }

            val itemsJson = AppJson.encodeToString(updatedTransactions)
            val pending = PendingTransaction(
                localId = localId,
                groupId = groupId,
                createdByUserId = creatorId,
                description = _transactionTitle.value,
                totalAmount = updatedTransactions.sumOf { it.amount },
                itemsJson = itemsJson,
                createdAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                status = 0
            )
            withContext(Dispatchers.IO) {
                pendingTransactionDao.insert(pending)
            }
            
            // Only schedule if context is valid
            SpendHoundApplication.scheduleSync(getApplication())
        } catch (e: Exception) {
            android.util.Log.e("MultiTransactionVM", "Local save failed: ${e.message}")
            throw e
        }
    }


    fun loadPendingTransactionForEdit(pending: PendingTransaction) {
        val entries = try {
            AppJson.decodeFromString<List<TransactionEntry>>(pending.itemsJson)
        } catch (e: Exception) {
            emptyList()
        }
        _transactions.value = entries
        _transactionTitle.value = pending.description ?: ""
        calculateTotals()
    }
}
