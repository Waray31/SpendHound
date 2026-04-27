package com.waray.spendhound.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.waray.spendhound.RecentTransaction
import com.waray.spendhound.SpendHoundApplication
import com.waray.spendhound.data.repository.HomeData
import com.waray.spendhound.data.repository.HomeRepository
import com.waray.spendhound.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as SpendHoundApplication).database
    private val homeRepo = HomeRepository(db)
    private val txRepo = TransactionRepository(db)

    private val _homeData = MutableStateFlow<HomeData?>(null)
    val homeData: StateFlow<HomeData?> = _homeData

    private val _recentTransactions = MutableStateFlow<List<RecentTransaction>>(emptyList())
    val recentTransactions: StateFlow<List<RecentTransaction>> = _recentTransactions

    fun load(userId: Long) {
        viewModelScope.launch {
            homeRepo.getHomeData(userId).collectLatest { _homeData.value = it }
        }
        viewModelScope.launch {
            txRepo.getRecentTransactions(userId).collectLatest { _recentTransactions.value = it }
        }
    }

    fun invalidate(userId: Long) {
        viewModelScope.launch {
            homeRepo.invalidate(userId)
            txRepo.invalidate(userId)
            load(userId)
        }
    }
}
