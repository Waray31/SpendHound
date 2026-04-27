package com.waray.spendhound.data.repository

import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.UserBalance
import com.waray.spendhound.data.local.AppDatabase
import com.waray.spendhound.data.local.CacheKeys
import com.waray.spendhound.ui.multi_transaction.TransactionFull
import com.waray.spendhound.ui.multi_transaction.TransactionSplitTable
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class HomeData(
    val totalMonthSpends: Double,
    val youOweAmount: Double,
    val youreOwedAmount: Double
)

class HomeRepository(private val db: AppDatabase) {

    fun getHomeData(userId: Long): Flow<HomeData> = db.cachedFlow(
        key = CacheKeys.home(userId),
        staleTtlMs = CacheKeys.STALE_HOME,
        type = typeOf<HomeData>()
    ) {
        val balance = DeclareDatabase.userBalanceTable.select(
            Columns.list("unpaid_total_group", "receivable_total_group")
        ) { filter { eq("user_id", userId) } }.decodeSingleOrNull<UserBalance>()

        val splits = DeclareDatabase.transactionSplitsTable.select {
            filter { eq("user_id", userId) }
        }.decodeList<TransactionSplitTable>()

        val involvedIds = splits.mapNotNull { it.transactionId }.toSet()
        val thisMonthTotal = if (involvedIds.isNotEmpty()) {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val cal = Calendar.getInstance()
            val monthStart = (cal.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val monthEnd = (cal.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
            }
            val txs = DeclareDatabase.transactionsTable.select {
                filter { isIn("id", involvedIds.toList()) }
            }.decodeList<TransactionFull>()
            val splitsByTx = splits.groupBy { it.transactionId }
            txs.sumOf { tx ->
                val ts = try { sdf.parse(tx.createdAt ?: "")?.time ?: 0L } catch (e: Exception) { 0L }
                if (ts in monthStart.timeInMillis..monthEnd.timeInMillis)
                    splitsByTx[tx.id]?.sumOf { it.amount } ?: 0.0
                else 0.0
            }
        } else 0.0

        HomeData(
            totalMonthSpends = thisMonthTotal,
            youOweAmount = balance?.unpaidTotalGroup ?: 0.0,
            youreOwedAmount = balance?.receivableTotalGroup ?: 0.0
        )
    }

    suspend fun invalidate(userId: Long) {
        db.jsonBlobDao().delete(CacheKeys.home(userId))
        db.jsonBlobDao().delete(CacheKeys.homeRecent(userId))
    }
}
