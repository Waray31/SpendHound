package com.waray.spendhound

import android.app.AlertDialog
import android.content.Context
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
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecentTransactionAdapter(
    private val recentTransactionList: ArrayList<RecentTransaction>?,
    private var clickListener: OnTransactionClickListener? = null
) : RecyclerView.Adapter<RecentTransactionAdapter.ViewHolder>() {

    private val scope = CoroutineScope(Dispatchers.Main)

    // Cache: authId -> numeric user_id
    private var cachedCurrentNumericId: Long? = null

    fun interface OnTransactionClickListener {
        fun onTransactionClick(transaction: RecentTransaction?)
    }

    constructor(recentTransactionList: ArrayList<RecentTransaction>?) : this(recentTransactionList, null)

    fun setOnTransactionClickListener(listener: OnTransactionClickListener?) {
        this.clickListener = listener
    }

    fun preloadAllImages(context: Context?) {
        if (recentTransactionList == null || context == null) return
        for (transaction in recentTransactionList) {
            val userIds = transaction.payorUserIds
            if (userIds != null && userIds.isNotEmpty()) {
                PayorAdapter.preCacheUserIds(context, userIds)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_transaction, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val transaction = recentTransactionList?.get(position) ?: return

        holder.dateTextView.text = transaction.mostRecentDate
        holder.typeTextView.text = transaction.mostRecentTransactionType ?: "Transaction"
        holder.amountTextView.text = transaction.mostRecentPaymentAmountStr
        holder.iconImageView.setImageResource(transaction.iconResource)

        // Status: Settled = green, Pending = yellow
        val status = transaction.transactionStatus
        holder.detailsTextView.text = status
        holder.detailsTextView.setTextColor(
            ContextCompat.getColor(holder.itemView.context,
                if (status == "Settled") R.color.green else R.color.yellow)
        )

        val isExpanded = transaction.isExpanded
        holder.expandableLayout.visibility = if (isExpanded) View.VISIBLE else View.GONE

        if (isExpanded) {
            holder.loadingOverlay.visibility = View.VISIBLE
            holder.createdByTextView.text = transaction.createdBy ?: "Unknown"
            holder.fullDetailsTextView.text =
                transaction.mostRecentDetails?.takeIf { it.isNotBlank() } ?: "No additional details"

            buildItemsTable(holder, transaction)
            setupPayors(holder, transaction)
        } else {
            holder.loadingOverlay.visibility = View.GONE
        }

        holder.mainContent.setOnClickListener {
            transaction.isExpanded = !transaction.isExpanded
            notifyItemChanged(holder.adapterPosition)
            clickListener?.onTransactionClick(transaction)
        }
    }

    private fun setupPayors(holder: ViewHolder, transaction: RecentTransaction) {
        val payorUserIds   = transaction.payorUserIds
        val payorsNames    = transaction.payorsList
        val amountsPaid    = transaction.amountsPaidList
        val individualPayment = transaction.totalIndividualPayment

        if (payorUserIds == null) {
            holder.loadingOverlay.visibility = View.GONE
            holder.editTransactionBtn.visibility = View.GONE
            return
        }

        val payorAdapter = PayorAdapter(
            payorUserIds, payorsNames,
            amountsPaid ?: mutableListOf(),
            individualPayment,
            object : PayorAdapter.OnPayorClickListener {
                override fun onPayorClick(index: Int, paid: Double) {}
                override fun onPartialClick(index: Int, currentPaid: Double) {
                    showEditAmountDialog(holder.itemView.context, { newAmount ->
                        (holder.payorsRecyclerView.adapter as? PayorAdapter)
                            ?.updatePartialAmount(index, newAmount)
                    }, currentPaid)
                }
            })

        payorAdapter.setOnLoadingCompleteListener(object : PayorAdapter.OnLoadingCompleteListener {
            override fun onLoadingComplete() {
                holder.loadingOverlay.visibility = View.GONE
            }
        })

        payorAdapter.setOnDataChangedListener(object : PayorAdapter.OnDataChangedListener {
            override fun onDataChanged(hasChanges: Boolean) {
                holder.saveTransactionBtn.isEnabled = hasChanges
                holder.saveTransactionBtn.alpha = if (hasChanges) 1f else 0.5f
            }
        })

        holder.payorsRecyclerView.layoutManager =
            LinearLayoutManager(holder.itemView.context, LinearLayoutManager.HORIZONTAL, false)
        holder.payorsRecyclerView.adapter = payorAdapter
        payorAdapter.startLoadingAllImages(holder.itemView.context)

        // Resolve creator check asynchronously using numeric ID
        resolveIsCreator(transaction) { isCreator ->
            if (isCreator) {
                holder.editTransactionBtn.visibility = View.VISIBLE
                holder.editTransactionBtn.setOnClickListener {
                    payorAdapter.setEditMode(true)
                    holder.editTransactionBtn.visibility = View.GONE
                    holder.saveTransactionBtn.visibility = View.VISIBLE
                    holder.cancelTransactionBtn.visibility = View.VISIBLE
                    holder.saveTransactionBtn.isEnabled = false
                    holder.saveTransactionBtn.alpha = 0.5f
                }
                holder.cancelTransactionBtn.setOnClickListener {
                    payorAdapter.setEditMode(false)
                    holder.editTransactionBtn.visibility = View.VISIBLE
                    holder.saveTransactionBtn.visibility = View.GONE
                    holder.cancelTransactionBtn.visibility = View.GONE
                }
                holder.saveTransactionBtn.setOnClickListener {
                    holder.loadingOverlay.visibility = View.VISIBLE
                    val updated = payorAdapter.amountsPaid
                    if (updated != null) {
                        saveTransactionChanges(
                            holder.itemView.context, transaction, updated,
                            holder.adapterPosition,
                            {
                                payorAdapter.saveChanges()
                                holder.editTransactionBtn.visibility = View.VISIBLE
                                holder.saveTransactionBtn.visibility = View.GONE
                                holder.cancelTransactionBtn.visibility = View.GONE
                                holder.loadingOverlay.visibility = View.GONE
                            },
                            { holder.loadingOverlay.visibility = View.GONE }
                        )
                    }
                }
            } else {
                holder.editTransactionBtn.visibility = View.GONE
            }
        }
    }

    /** Resolves whether the current user is the creator using numeric user_id comparison. */
    private fun resolveIsCreator(transaction: RecentTransaction, callback: (Boolean) -> Unit) {
        val creatorId = transaction.creatorNumericId ?: run { callback(false); return }

        // Use cached value if available
        if (cachedCurrentNumericId != null) {
            callback(cachedCurrentNumericId == creatorId)
            return
        }

        val authId = DeclareDatabase.auth.currentUserOrNull()?.id ?: run { callback(false); return }

        scope.launch {
            try {
                val user = withContext(Dispatchers.IO) {
                    DeclareDatabase.usersTable.select {
                        filter { eq("auth_id", authId) }
                    }.decodeSingleOrNull<User>()
                }
                cachedCurrentNumericId = user?.id
                callback(cachedCurrentNumericId == creatorId)
            } catch (e: Exception) {
                callback(false)
            }
        }
    }

    private fun buildItemsTable(holder: ViewHolder, transaction: RecentTransaction) {
        holder.itemsTableContainer.removeAllViews()
        val inflater = LayoutInflater.from(holder.itemView.context)
        for (item in transaction.transactionItems) {
            val row = inflater.inflate(R.layout.item_transaction_item_row, holder.itemsTableContainer, false)
            row.findViewById<ImageView>(R.id.ivItemCategory).setImageResource(getCategoryIcon(item.category))
            row.findViewById<TextView>(R.id.tvItemAmount).text = CurrencyUtils.formatAmountWithCurrency(item.amount)
            row.findViewById<TextView>(R.id.tvItemPaidBy).text = transaction.itemPayorMap[item.id] ?: "-"
            row.findViewById<TextView>(R.id.tvItemDescription).text = item.itemDescription?.takeIf { it.isNotBlank() } ?: "-"
            holder.itemsTableContainer.addView(row)
        }
    }

    private fun getCategoryIcon(category: String?): Int = when (category) {
        "Electricity"     -> R.drawable.lightning_bolt
        "Water"           -> R.drawable.faucet
        "Rent"            -> R.drawable.house
        "Internet"        -> R.drawable.internet
        "Online Shopping" -> R.drawable.online_shopping
        "Travel"          -> R.drawable.travel
        "Groceries"       -> R.drawable.groceries
        "Foods"           -> R.drawable.hamburger
        "House Necessity" -> R.drawable.necessities
        "Transportation"  -> R.drawable.vehicles
        else              -> R.drawable.others
    }

    private fun showEditAmountDialog(context: Context, onAmountEntered: (Double) -> Unit, currentAmount: Double) {
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(currentAmount.toString())
        }
        AlertDialog.Builder(context)
            .setTitle("Edit Amount Paid")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                onAmountEntered(input.text.toString().toDoubleOrNull() ?: 0.0)
            }
            .setNegativeButton("Cancel") { d, _ -> d.cancel() }
            .show()
    }

    private fun saveTransactionChanges(
        context: Context,
        transaction: RecentTransaction,
        updatedAmounts: MutableList<Double?>,
        position: Int,
        onSuccess: Runnable,
        onComplete: Runnable
    ) {
        val id = transaction.transactionId ?: run {
            Toast.makeText(context, "Error: Transaction ID not found", Toast.LENGTH_SHORT).show()
            onComplete.run(); return
        }

        scope.launch {
            try {
                val payorUserIds = transaction.payorUserIds ?: emptyList()

                payorUserIds.forEachIndexed { index, userIdStr ->
                    val userId = userIdStr?.toLongOrNull() ?: return@forEachIndexed
                    val newTotalAmount = updatedAmounts.getOrNull(index) ?: return@forEachIndexed

                    val userSplitRows = transaction.rawSplitRows.filter { it.userId == userId }
                    val userPayorRows = transaction.rawPayorRows.filter { it.userId == userId }
                    val totalOwed = userSplitRows.sumOf { it.amount }.takeIf { it > 0 } ?: 1.0

                    withContext(Dispatchers.IO) {
                        userSplitRows.forEach { splitRow ->
                            val itemId = splitRow.transactionItemsId ?: return@forEach
                            val splitAmount = splitRow.amount // what this user owes for this item

                            // Compute how much of newTotalAmount applies to this item
                            val itemPaidAmount = when {
                                newTotalAmount <= 0.0 -> 0.0
                                newTotalAmount >= totalOwed -> splitAmount  // fully paid → exact split amount
                                else -> newTotalAmount * (splitAmount / totalOwed) // partial → proportional
                            }

                            val existingRow = userPayorRows.firstOrNull { it.transactionItemsId == itemId }
                            if (existingRow != null) {
                                DeclareDatabase.transactionPayorsTable.update({
                                    set("amount", itemPaidAmount)
                                }) {
                                    filter {
                                        eq("transaction_id", id)
                                        eq("user_id", userId)
                                        eq("transaction_items_id", itemId)
                                    }
                                }
                            } else {
                                DeclareDatabase.transactionPayorsTable.insert(
                                    com.waray.spendhound.ui.multi_transaction.TransactionPayorInsert(
                                        transactionId = id,
                                        userId = userId,
                                        amount = itemPaidAmount,
                                        transactionItemsId = itemId
                                    )
                                )
                            }
                        }
                    }
                }

                // Update transaction status: 3=Settled, 2=Pending
                val allSettled = updatedAmounts.all { (it ?: 0.0) >= transaction.totalIndividualPayment }
                val newStatus = if (allSettled) 3 else 2
                withContext(Dispatchers.IO) {
                    DeclareDatabase.transactionsTable.update({ set("status", newStatus) }) {
                        filter { eq("id", id) }
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Transaction updated", Toast.LENGTH_SHORT).show()
                    transaction.amountsPaidList = updatedAmounts
                    transaction.transactionStatus = if (allSettled) "Settled" else "Pending"
                    onSuccess.run()
                    notifyItemChanged(position)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    onComplete.run()
                }
            }
        }
    }

    override fun getItemCount(): Int = recentTransactionList?.size ?: 0

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dateTextView: TextView           = itemView.findViewById(R.id.dateTextView)
        val typeTextView: TextView           = itemView.findViewById(R.id.transactionTypeTextView)
        val detailsTextView: TextView        = itemView.findViewById(R.id.detailsTextView)
        val amountTextView: TextView         = itemView.findViewById(R.id.paymentAmountTextView)
        val iconImageView: ImageView         = itemView.findViewById(R.id.iconImageView)
        val mainContent: View                = itemView.findViewById(R.id.main_content)
        val expandableLayout: View           = itemView.findViewById(R.id.expandable_layout)
        val itemsTableContainer: LinearLayout = itemView.findViewById(R.id.itemsTableContainer)
        val createdByTextView: TextView      = itemView.findViewById(R.id.createdByTextView)
        val payorsRecyclerView: RecyclerView = itemView.findViewById(R.id.payorsRecyclerView)
        val fullDetailsTextView: TextView    = itemView.findViewById(R.id.fullDetailsTextView)
        val loadingOverlay: View             = itemView.findViewById(R.id.loadingOverlay_transaction)
        val editTransactionBtn: Button       = itemView.findViewById(R.id.editTransaction_btn)
        val saveTransactionBtn: Button       = itemView.findViewById(R.id.saveTransaction_btn)
        val cancelTransactionBtn: Button     = itemView.findViewById(R.id.cancelTransaction_btn)
    }
}
