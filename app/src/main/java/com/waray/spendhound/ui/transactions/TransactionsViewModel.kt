package com.waray.spendhound.ui.transactions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.waray.spendhound.RecentTransaction
import com.waray.spendhound.SpendHoundApplication
import com.waray.spendhound.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TransactionsViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as SpendHoundApplication).database
    private val repo = TransactionRepository(db)

    private val _transactions = MutableStateFlow<List<RecentTransaction>?>(null)
    val transactions: StateFlow<List<RecentTransaction>?> = _transactions

    fun load(userId: Long) {
        viewModelScope.launch {
            try {
                repo.getTransactions(userId).collectLatest { _transactions.value = it }
            } catch (e: Exception) {
                android.util.Log.e("TransactionsViewModel", "Error loading transactions: ${e.message}")
            }
        }
    }

    fun invalidate(userId: Long) {
        viewModelScope.launch {
            repo.invalidate(userId)
            load(userId)
        }
    }
}
