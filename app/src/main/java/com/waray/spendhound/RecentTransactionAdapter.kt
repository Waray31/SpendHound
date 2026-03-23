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
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecentTransactionAdapter(
    private val recentTransactionList: ArrayList<RecentTransaction>?,
    private var clickListener: OnTransactionClickListener? = null
) : RecyclerView.Adapter<RecentTransactionAdapter.ViewHolder>() {

    private val scope = CoroutineScope(Dispatchers.Main)

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
        val itemView: View = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_transaction, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val transaction = recentTransactionList?.get(position) ?: return

        holder.dateTextView.text = transaction.mostRecentDate
        holder.typeTextView.text = transaction.mostRecentTransactionType
        holder.amountTextView.text = transaction.mostRecentPaymentAmountStr
        holder.iconImageView.setImageResource(transaction.iconResource)

        val isExpanded = transaction.isExpanded
        holder.expandableLayout.visibility = if (isExpanded) View.VISIBLE else View.GONE
        holder.detailsTextView.text = if (isExpanded) "Hide Details <" else "See Details >"

        if (isExpanded) {
            holder.loadingOverlay.visibility = View.VISIBLE
            holder.createdByTextView.text = transaction.createdBy ?: "Unknown"

            val payorUserIds = transaction.payorUserIds
            val payorsNames = transaction.payorsList
            val amountsPaid = transaction.amountsPaidList
            val individualPayment = transaction.totalIndividualPayment

            val currentUserId = DeclareDatabase.auth.currentUserOrNull()?.id
            val isCreator = transaction.createdByUserId != null && transaction.createdByUserId == currentUserId

            if (payorUserIds != null) {
                val payorAdapter = PayorAdapter(
                    payorUserIds,
                    payorsNames,
                    amountsPaid ?: mutableListOf(),
                    individualPayment,
                    object : PayorAdapter.OnPayorClickListener {
                        override fun onPayorClick(index: Int, paid: Double) {}
                        override fun onPartialClick(index: Int, currentPaid: Double) {
                            showEditAmountDialog(holder.itemView.context, { newAmount ->
                                (holder.payorsRecyclerView.adapter as? PayorAdapter)?.updatePartialAmount(index, newAmount)
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
                        holder.saveTransactionBtn.alpha = if (hasChanges) 1.0f else 0.5f
                    }
                })

                holder.payorsRecyclerView.layoutManager = LinearLayoutManager(holder.itemView.context, LinearLayoutManager.HORIZONTAL, false)
                holder.payorsRecyclerView.adapter = payorAdapter
                payorAdapter.startLoadingAllImages(holder.itemView.context)

                if (isCreator) {
                    holder.editTransactionBtn.visibility = View.VISIBLE
                    holder.editTransactionBtn.setOnClickListener {
                        holder.loadingOverlay.visibility = View.VISIBLE
                        payorAdapter.setEditMode(true)
                        holder.editTransactionBtn.visibility = View.GONE
                        holder.saveTransactionBtn.visibility = View.VISIBLE
                        holder.cancelTransactionBtn.visibility = View.VISIBLE
                        holder.saveTransactionBtn.isEnabled = false
                        holder.saveTransactionBtn.alpha = 0.5f
                        holder.loadingOverlay.visibility = View.GONE
                    }

                    holder.cancelTransactionBtn.setOnClickListener {
                        holder.loadingOverlay.visibility = View.VISIBLE
                        payorAdapter.setEditMode(false)
                        holder.editTransactionBtn.visibility = View.VISIBLE
                        holder.saveTransactionBtn.visibility = View.GONE
                        holder.cancelTransactionBtn.visibility = View.GONE
                        holder.loadingOverlay.visibility = View.GONE
                    }

                    holder.saveTransactionBtn.setOnClickListener {
                        holder.loadingOverlay.visibility = View.VISIBLE
                        val updatedAmounts = payorAdapter.amountsPaid
                        if (updatedAmounts != null) {
                            saveTransactionChanges(holder.itemView.context, transaction, updatedAmounts, holder.adapterPosition, {
                                payorAdapter.saveChanges()
                                holder.editTransactionBtn.visibility = View.VISIBLE
                                holder.saveTransactionBtn.visibility = View.GONE
                                holder.cancelTransactionBtn.visibility = View.GONE
                                holder.loadingOverlay.visibility = View.GONE
                            }, { holder.loadingOverlay.visibility = View.GONE })
                        }
                    }
                } else {
                    holder.editTransactionBtn.visibility = View.GONE
                }
            } else {
                holder.loadingOverlay.visibility = View.GONE
                holder.editTransactionBtn.visibility = View.GONE
            }

            val details = transaction.mostRecentDetails
            holder.fullDetailsTextView.text = if (!details.isNullOrEmpty() && details != "See Details >") details else "No additional details"
        } else {
            holder.loadingOverlay.visibility = View.GONE
        }

        holder.mainContent.setOnClickListener {
            transaction.isExpanded = !transaction.isExpanded
            notifyItemChanged(holder.adapterPosition)
            clickListener?.onTransactionClick(transaction)
        }
    }

    private fun showEditAmountDialog(context: Context, onAmountEntered: (Double) -> Unit, currentAmount: Double) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Edit Amount Paid")
        val input = EditText(context)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.setText(currentAmount.toString())
        builder.setView(input)
        builder.setPositiveButton("Save") { _, _ ->
            val amount = input.text.toString().toDoubleOrNull() ?: 0.0
            onAmountEntered(amount)
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun saveTransactionChanges(context: Context, transaction: RecentTransaction, updatedAmounts: MutableList<Double?>, position: Int, onSuccess: Runnable, onComplete: Runnable) {
        val id = transaction.transactionId

        if (id == null) {
            Toast.makeText(context, "Error: Transaction ID not found", Toast.LENGTH_SHORT).show()
            onComplete.run()
            return
        }

        scope.launch {
            try {
                DeclareDatabase.transactionsTable.update({
                    set("amount_paid_list", updatedAmounts.filterNotNull())
                }) {
                    filter {
                        eq("id", id)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Transaction updated successfully", Toast.LENGTH_SHORT).show()
                    transaction.amountsPaidList = updatedAmounts
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
        val dateTextView: TextView = itemView.findViewById(R.id.dateTextView)
        val typeTextView: TextView = itemView.findViewById(R.id.transactionTypeTextView)
        val detailsTextView: TextView = itemView.findViewById(R.id.detailsTextView)
        val amountTextView: TextView = itemView.findViewById(R.id.paymentAmountTextView)
        val iconImageView: ImageView = itemView.findViewById(R.id.iconImageView)
        val mainContent: View = itemView.findViewById(R.id.main_content)
        val expandableLayout: View = itemView.findViewById(R.id.expandable_layout)
        val createdByTextView: TextView = itemView.findViewById(R.id.createdByTextView)
        val payorsRecyclerView: RecyclerView = itemView.findViewById(R.id.payorsRecyclerView)
        val fullDetailsTextView: TextView = itemView.findViewById(R.id.fullDetailsTextView)
        val loadingOverlay: View = itemView.findViewById(R.id.loadingOverlay_transaction)
        val editTransactionBtn: Button = itemView.findViewById(R.id.editTransaction_btn)
        val saveTransactionBtn: Button = itemView.findViewById(R.id.saveTransaction_btn)
        val cancelTransactionBtn: Button = itemView.findViewById(R.id.cancelTransaction_btn)
    }
}
