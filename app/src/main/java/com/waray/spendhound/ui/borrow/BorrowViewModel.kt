package com.waray.spendhound.ui.borrow

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.waray.spendhound.SpendHoundApplication
import com.waray.spendhound.data.repository.BorrowData
import com.waray.spendhound.data.repository.BorrowRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BorrowViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as SpendHoundApplication).database
    private val repo = BorrowRepository(db)

    private val _borrowData = MutableStateFlow<BorrowData?>(null)
    val borrowData: StateFlow<BorrowData?> = _borrowData

    fun load(userId: Long) {
        viewModelScope.launch {
            try {
                repo.getBorrowData(userId).collectLatest { _borrowData.value = it }
            } catch (e: Exception) {
                android.util.Log.e("BorrowViewModel", "Error loading borrow data: ${e.message}")
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
