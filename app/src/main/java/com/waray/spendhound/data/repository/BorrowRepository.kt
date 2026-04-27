package com.waray.spendhound.data.repository

import com.waray.spendhound.BorrowNowTransaction
import com.waray.spendhound.BorrowTransaction
import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.OwedTransaction
import com.waray.spendhound.User
import com.waray.spendhound.data.local.AppDatabase
import com.waray.spendhound.data.local.CacheKeys
import kotlinx.coroutines.flow.Flow

data class BorrowData(
    val owedList: List<OwedTransaction>,
    val debtList: List<BorrowTransaction>
)

class BorrowRepository(private val db: AppDatabase) {

    fun getBorrowData(userId: Long): Flow<BorrowData> = db.cachedFlow(
        key = CacheKeys.borrows(userId),
        staleTtlMs = CacheKeys.STALE_BORROWS,
        type = typeOf<BorrowData>()
    ) {
        val owed = DeclareDatabase.borrowsTable.select {
            filter { eq("lender_id", userId) }
        }.decodeList<BorrowNowTransaction>()

        val debt = DeclareDatabase.borrowsTable.select {
            filter { eq("borrower_id", userId) }
        }.decodeList<BorrowNowTransaction>()

        val allIds = (owed.mapNotNull { it.borrowerId } + debt.mapNotNull { it.lenderId }).distinct()
        val usersById = if (allIds.isNotEmpty()) {
            DeclareDatabase.usersTable.select {
                filter { isIn("user_id", allIds) }
            }.decodeList<User>().associate { it.id!! to (it.username ?: it.id.toString()) }
        } else emptyMap()

        BorrowData(
            owedList = owed.map { b ->
                OwedTransaction(
                    date = b.createdAt,
                    borrower = usersById[b.borrowerId] ?: b.borrowerId?.toString(),
                    borrowedAmountStr = b.borrowedAmount?.toString(),
                    status = b.getStatus(),
                    paymentSentDate = b.paymentSentDate,
                    borrowId = b.id?.toString(),
                    monthYear = b.monthYear,
                    day = null
                )
            },
            debtList = debt.map { b ->
                BorrowTransaction(
                    date = b.createdAt,
                    borrowee = b.lenderId?.toString(),
                    borrowedAmountStr = b.borrowedAmount?.toString(),
                    status = b.getStatus(),
                    borroweeDisplayName = usersById[b.lenderId] ?: b.lenderId?.toString(),
                    paymentSentDate = b.paymentSentDate,
                    borrowId = b.id?.toString(),
                    monthYear = b.monthYear,
                    day = null
                )
            }
        )
    }

    suspend fun invalidate(userId: Long) {
        db.jsonBlobDao().delete(CacheKeys.borrows(userId))
    }
}
