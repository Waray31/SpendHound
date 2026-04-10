package com.waray.spendhound

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.waray.spendhound.ui.multi_transaction.TransactionPayorInsert
import com.waray.spendhound.ui.multi_transaction.TransactionPayorTable
import com.waray.spendhound.ui.multi_transaction.TransactionSplitTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettleBottomSheet : BottomSheetDialogFragment() {

    private val scope = CoroutineScope(Dispatchers.Main)

    var transaction: RecentTransaction? = null
    var onSettleSaved: (() -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.bottom_sheet_settle, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val tx = transaction ?: return

        val isSingle = tx.transactionItems.size == 1
        val title = if (isSingle) tx.transactionItems[0].category ?: tx.mostRecentTransactionType ?: "Transaction"
                    else tx.mostRecentTransactionType ?: "Transaction"

        view.findViewById<TextView>(R.id.settleTitleTV).text = "Settle: $title"
        view.findViewById<TextView>(R.id.settleTotalTV).text = "Total: ${tx.mostRecentPaymentAmountStr}  •  Each owes: ${CurrencyUtils.formatAmountWithCurrency(tx.totalIndividualPayment)}"

        val amounts = tx.amountsPaidList?.map { it ?: 0.0 }?.toMutableList()
            ?: MutableList(tx.payorUserIds?.size ?: 0) { 0.0 }

        val recycler = view.findViewById<RecyclerView>(R.id.settlePayorsRecyclerView)
        val adapter = SettlePayorAdapter(tx, amounts)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        val loadingLayout = view.findViewById<LinearLayout>(R.id.settleLoadingLayout)
        val saveBtn = view.findViewById<Button>(R.id.settleSaveBtn)
        val cancelBtn = view.findViewById<Button>(R.id.settleCancelBtn)

        cancelBtn.setOnClickListener { dismiss() }

        saveBtn.setOnClickListener {
            val updated = adapter.getAmounts()
            loadingLayout.visibility = View.VISIBLE
            saveBtn.isEnabled = false
            cancelBtn.isEnabled = false
            saveTransactionChanges(tx, updated,
                onSuccess = {
                    loadingLayout.visibility = View.GONE
                    Toast.makeText(requireContext(), "Transaction updated", Toast.LENGTH_SHORT).show()
                    onSettleSaved?.invoke()
                    dismiss()
                },
                onError = { msg ->
                    loadingLayout.visibility = View.GONE
                    saveBtn.isEnabled = true
                    cancelBtn.isEnabled = true
                    Toast.makeText(requireContext(), "Update failed: $msg", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun saveTransactionChanges(
        transaction: RecentTransaction,
        updatedAmounts: List<Double>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val id = transaction.transactionId ?: run { onError("Transaction ID not found"); return }

        scope.launch {
            try {
                val payorUserIds = transaction.payorUserIds ?: emptyList()

                payorUserIds.forEachIndexed { index, userIdStr ->
                    val userId = userIdStr?.toLongOrNull() ?: return@forEachIndexed
                    val newTotalAmount = updatedAmounts.getOrElse(index) { 0.0 }

                    val userSplitRows = transaction.rawSplitRows.filter { it.userId == userId }
                    val userPayorRows = transaction.rawPayorRows.filter { it.userId == userId }
                    val totalOwed = userSplitRows.sumOf { it.amount }.takeIf { it > 0 } ?: 1.0

                    withContext(Dispatchers.IO) {
                        userSplitRows.forEach { splitRow ->
                            val itemId = splitRow.transactionItemsId ?: return@forEach
                            val splitAmount = splitRow.amount
                            val itemPaidAmount = when {
                                newTotalAmount <= 0.0 -> 0.0
                                newTotalAmount >= totalOwed -> splitAmount
                                else -> newTotalAmount * (splitAmount / totalOwed)
                            }
                            val excess = if (itemPaidAmount > splitAmount) itemPaidAmount - splitAmount else 0.0
                            val status = when {
                                itemPaidAmount == 0.0 -> 0
                                itemPaidAmount >= splitAmount -> 1
                                else -> 2
                            }
                            val existingRow = userPayorRows.firstOrNull { it.transactionItemsId == itemId }
                            if (existingRow != null) {
                                DeclareDatabase.transactionPayorsTable.update({
                                    set("current_amount_paid", itemPaidAmount as Double?)
                                    set("excess_amount", excess as Double?)
                                    set("status", status as Int?)
                                }) { filter { eq("transaction_id", id); eq("user_id", userId); eq("transaction_items_id", itemId) } }
                            } else {
                                DeclareDatabase.transactionPayorsTable.insert(
                                    TransactionPayorInsert(
                                        transactionId = id, userId = userId,
                                        initialAmountPaid = itemPaidAmount, currentAmountPaid = itemPaidAmount,
                                        excessAmount = excess, transactionItemsId = itemId, status = status
                                    )
                                )
                            }
                        }
                    }
                }

                val allMemberIds = transaction.rawSplitRows.map { it.userId }.distinct()
                val paidByUser = transaction.rawPayorRows
                    .groupBy { it.userId }
                    .mapValues { e -> e.value.sumOf { it.currentAmountPaid } }
                val allSettled = allMemberIds.isNotEmpty() &&
                    allMemberIds.all { (paidByUser[it] ?: 0.0) >= transaction.totalIndividualPayment }

                withContext(Dispatchers.IO) {
                    DeclareDatabase.transactionsTable.update({ set("status", (if (allSettled) 3 else 2) as Int?) }) {
                        filter { eq("id", id) }
                    }
                }

                withContext(Dispatchers.Main) {
                    transaction.amountsPaidList = updatedAmounts.map { it as Double? }.toMutableList()
                    transaction.transactionStatus = if (allSettled) "Settled" else "Pending"
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.message ?: "Unknown error") }
            }
        }
    }

    // ── Inner adapter ──────────────────────────────────────────────────────────

    inner class SettlePayorAdapter(
        private val tx: RecentTransaction,
        private val amounts: MutableList<Double>
    ) : RecyclerView.Adapter<SettlePayorAdapter.VH>() {

        fun getAmounts(): List<Double> = amounts.toList()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_settle_payor_row, parent, false))

        override fun getItemCount() = tx.payorUserIds?.size ?: 0

        override fun onBindViewHolder(holder: VH, position: Int) {
            val name = tx.payorsList?.getOrNull(position) ?: "User"
            val userId = tx.payorUserIds?.getOrNull(position)
            val owed = tx.totalIndividualPayment
            val paid = amounts.getOrElse(position) { 0.0 }

            holder.nameTV.text = name
            holder.owedTV.text = "Owes: ${CurrencyUtils.formatAmountWithCurrency(owed)}"
            holder.amountInput.setText(if (paid > 0) paid.toString() else "")
            holder.amountInput.hint = "0.00"

            holder.unpaidBtn.setOnClickListener {
                amounts[position] = 0.0
                holder.amountInput.setText("")
            }
            holder.paidBtn.setOnClickListener {
                amounts[position] = owed
                holder.amountInput.setText(owed.toString())
            }
            holder.amountInput.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    amounts[position] = holder.amountInput.text.toString().toDoubleOrNull() ?: 0.0
                }
            }

            val cachedUrl = PayorAdapter.sDownloadUrlCache[userId]
            if (cachedUrl != null) {
                Glide.with(holder.itemView.context).load(cachedUrl)
                    .circleCrop().diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.placeholder_profile_image)
                    .into(holder.avatar)
            } else if (userId != null) {
                scope.launch {
                    try {
                        val url = withContext(Dispatchers.IO) {
                            DeclareDatabase.profileImagesBucket.publicUrl("$userId/$userId.jpg")
                        }
                        PayorAdapter.sDownloadUrlCache[userId] = url
                        Glide.with(holder.itemView.context).load(url)
                            .circleCrop().diskCacheStrategy(DiskCacheStrategy.ALL)
                            .placeholder(R.drawable.placeholder_profile_image)
                            .into(holder.avatar)
                    } catch (e: Exception) {
                        holder.avatar.setImageResource(R.drawable.placeholder_profile_image)
                    }
                }
            }
        }

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val avatar: ImageView = view.findViewById(R.id.settlePayorImage)
            val nameTV: TextView = view.findViewById(R.id.settlePayorName)
            val owedTV: TextView = view.findViewById(R.id.settlePayorOwed)
            val amountInput: EditText = view.findViewById(R.id.settleAmountInput)
            val unpaidBtn: Button = view.findViewById(R.id.settleUnpaidBtn)
            val paidBtn: Button = view.findViewById(R.id.settlePaidBtn)
        }
    }
}
