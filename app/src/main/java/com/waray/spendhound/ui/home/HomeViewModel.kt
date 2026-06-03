package com.waray.spendhound.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.PayerGroup
import com.waray.spendhound.RecentTransaction
import com.waray.spendhound.SpendHoundApplication
import com.waray.spendhound.data.repository.HomeData
import com.waray.spendhound.data.repository.HomeRepository
import com.waray.spendhound.data.repository.TransactionRepository
import com.waray.spendhound.ui.multi_transaction.TransactionFull
import com.waray.spendhound.ui.multi_transaction.TransactionSplitTable
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class HomeGroupState(
    val groupId: Long? = null, // null for "All"
    val groupName: String = "All Spending",
    val homeData: HomeData? = null,
    val recentTransactions: List<RecentTransaction> = emptyList(),
    val weeklyTotals: DoubleArray = DoubleArray(7) { 0.0 }
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as SpendHoundApplication).database
    private val homeRepo = HomeRepository(db)
    private val txRepo = TransactionRepository(db)

    private val _groupStates = MutableStateFlow<List<HomeGroupState>>(emptyList())
    val groupStates: StateFlow<List<HomeGroupState>> = _groupStates

    // Legacy flows for compatibility while refactoring HomeFragment
    private val _homeData = MutableStateFlow<HomeData?>(null)
    val homeData: StateFlow<HomeData?> = _homeData

    private val _recentTransactions = MutableStateFlow<List<RecentTransaction>>(emptyList())
    val recentTransactions: StateFlow<List<RecentTransaction>> = _recentTransactions

    private var currentUserId: Long? = null

    fun load(userId: Long) {
        currentUserId = userId
        
        // Immediate emission of "All Spending" to prevent display delay
        if (_groupStates.value.isEmpty()) {
            _groupStates.value = listOf(HomeGroupState(groupId = null, groupName = "All Spending"))
            // Load "All" data immediately from cache if possible
            loadDataForGroup(userId, null)
        }

        viewModelScope.launch {
            // Fetch user's groups in background
            val groups = fetchUserGroups(userId)
            val groupIds = listOf(null) + groups.map { it.groupId }
            
            // Merge existing state with new groups found
            val updatedStates = groupIds.map { id ->
                _groupStates.value.find { it.groupId == id } ?: HomeGroupState(
                    groupId = id,
                    groupName = if (id == null) "All Spending" else groups.find { it.groupId == id }?.groupName ?: "Unknown Group"
                )
            }
            
            // Only update if there's actually a change in group count or identity
            if (updatedStates.size != _groupStates.value.size || updatedStates.map { it.groupId } != _groupStates.value.map { it.groupId }) {
                _groupStates.value = updatedStates
            }

            // Start loading data for new groups (excluding All which is already loading/loaded)
            groupIds.filter { it != null }.forEach { id ->
                loadDataForGroup(userId, id)
            }
        }
    }

    private suspend fun fetchUserGroups(userId: Long): List<PayerGroup> = withContext(Dispatchers.IO) {
        try {
            val memberships = DeclareDatabase.groupMembersTable.select {
                filter { eq("user_id", userId) }
            }.decodeList<com.waray.spendhound.GroupMember>()
            
            val groupIds = memberships.mapNotNull { it.groupId }
            if (groupIds.isEmpty()) return@withContext emptyList()
            
            DeclareDatabase.groupsTable.select {
                filter { isIn("group_id", groupIds) }
            }.decodeList<PayerGroup>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun loadDataForGroup(userId: Long, groupId: Long?) {
        viewModelScope.launch {
            combine(
                homeRepo.getHomeData(userId, groupId),
                txRepo.getRecentTransactions(userId, groupId)
            ) { hData, recentTx ->
                val weeklyTotals = calculateWeeklyTotals(userId, groupId)
                hData to (recentTx to weeklyTotals)
            }.collectLatest { (hData, pair) ->
                val (recentTx, weeklyTotals) = pair
                updateGroupState(groupId, hData, recentTx, weeklyTotals)
                
                // Keep legacy flows updated with "All" data (groupId == null)
                if (groupId == null) {
                    _homeData.value = hData
                    _recentTransactions.value = recentTx
                }
            }
        }
    }

    private fun updateGroupState(groupId: Long?, hData: HomeData, recentTx: List<RecentTransaction>, weeklyTotals: DoubleArray) {
        val currentStates = _groupStates.value.toMutableList()
        val index = currentStates.indexOfFirst { it.groupId == groupId }
        if (index != -1) {
            currentStates[index] = currentStates[index].copy(
                homeData = hData,
                recentTransactions = recentTx,
                weeklyTotals = weeklyTotals
            )
            _groupStates.value = currentStates
        }
    }

    private suspend fun calculateWeeklyTotals(userId: Long, groupId: Long?): DoubleArray = withContext(Dispatchers.IO) {
        val now = Calendar.getInstance()
        val weekStart = now.clone() as Calendar
        weekStart.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        weekStart.set(Calendar.HOUR_OF_DAY, 0); weekStart.set(Calendar.MINUTE, 0)
        weekStart.set(Calendar.SECOND, 0); weekStart.set(Calendar.MILLISECOND, 0)
        val weekEnd = weekStart.clone() as Calendar
        weekEnd.add(Calendar.DAY_OF_YEAR, 6)
        weekEnd.set(Calendar.HOUR_OF_DAY, 23); weekEnd.set(Calendar.MINUTE, 59); weekEnd.set(Calendar.SECOND, 59)

        val sMillis = weekStart.timeInMillis
        val eMillis = weekEnd.timeInMillis

        try {
            val allTransactions = DeclareDatabase.transactionsTable.select {
                filter { if (groupId != null) eq("group_id", groupId) }
            }.decodeList<TransactionFull>()
            
            val allSplits = DeclareDatabase.transactionSplitsTable.select {
                filter { eq("user_id", userId) }
            }.decodeList<TransactionSplitTable>()

            val userSplitsByTx = allSplits.groupBy { it.transactionId }

            val totals = DoubleArray(7) { 0.0 }
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

            for (tx in allTransactions) {
                val txId = tx.id ?: continue
                if (txId !in userSplitsByTx) continue
                val timestamp = try { sdf.parse(tx.createdAt ?: "")?.time ?: 0L } catch (e: Exception) { 0L }
                if (timestamp !in sMillis..eMillis) continue

                val transCal = Calendar.getInstance().apply { timeInMillis = timestamp }
                val index = transCal.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sun..6=Sat
                val userSplit = userSplitsByTx[txId]?.sumOf { it.amount } ?: 0.0
                totals[index] += userSplit
            }
            totals
        } catch (e: Exception) {
            DoubleArray(7) { 0.0 }
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
