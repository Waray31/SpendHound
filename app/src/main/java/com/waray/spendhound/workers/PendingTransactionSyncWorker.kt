package com.waray.spendhound.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.waray.spendhound.TransactionState
import com.waray.spendhound.data.local.AppDatabase
import com.waray.spendhound.ui.multi_transaction.AppJson
import com.waray.spendhound.ui.multi_transaction.MultiTransactionRepository
import com.waray.spendhound.ui.multi_transaction.TransactionEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class PendingTransactionSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Ensure DeclareDatabase is initialized in this process
            com.waray.spendhound.DeclareDatabase.initialize(applicationContext)
            
            val database = AppDatabase.getInstance(applicationContext)
            val pendingDao = database.pendingTransactionDao()
            val repository = MultiTransactionRepository(database)
            
            val allPending = try {
                pendingDao.getAll()
            } catch (e: Exception) {
                return@withContext Result.failure()
            }

            if (allPending.isEmpty()) return@withContext Result.success()

            var hasFailure = false

            for (pending in allPending) {
                val groupId = pending.groupId
                if (groupId == null) continue // Cannot sync without a group

                val entries = try {
                    AppJson.decodeFromString<List<TransactionEntry>>(pending.itemsJson)
                } catch (e: Exception) {
                    continue
                }

                // Check if all details are met for this transaction
                val allPayorsValid = entries.all { entry ->
                    val totalPaid = entry.payors.sumOf { it.amount }
                    entry.payors.isNotEmpty() && Math.abs(totalPaid - entry.amount) < 0.01
                }
                
                val isComplete = entries.isNotEmpty() && 
                                 entries.all { it.amount > 0 && it.category.isNotBlank() } &&
                                 allPayorsValid &&
                                 (entries.size == 1 || !pending.description.isNullOrBlank())

                if (!isComplete) continue

                // Fetch group members to satisfy repository requirements
                val members = try {
                    repository.getGroupMembers(groupId)
                } catch (e: Exception) {
                    hasFailure = true
                    continue
                }

                // Attempt submission
                val result = repository.submitTransactions(
                    groupId = groupId,
                    createdBy = pending.createdByUserId,
                    title = pending.description ?: "",
                    entries = entries,
                    groupMembers = members,
                    status = 2
                )

                if (result.isSuccess) {
                    pendingDao.delete(pending.localId)
                    TransactionState.notifyChange()
                } else {
                    hasFailure = true
                }
            }

            if (hasFailure) Result.retry() else Result.success()
        } catch (e: Exception) {
            android.util.Log.e("SyncWorker", "Error in sync worker: ${e.message}")
            Result.retry()
        }
    }
}
