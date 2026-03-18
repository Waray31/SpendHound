package com.waray.spendhound

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
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
import com.google.firebase.database.DatabaseReference
import io.github.jan.supabase.gotrue.auth

class RecentTransactionAdapter(
    private val recentTransactionList: ArrayList<RecentTransaction>?,
    private var clickListener: OnTransactionClickListener? = null
) : RecyclerView.Adapter<RecentTransactionAdapter.ViewHolder>() {

    interface OnTransactionClickListener {
        fun onTransactionClick(transaction: RecentTransaction?)
    }

    constructor(recentTransactionList: ArrayList<RecentTransaction>?) : this(recentTransactionList, null)

    fun setOnTransactionClickListener(listener: OnTransactionClickListener?) {
        this.clickListener = listener
    }

    fun preloadAllImages(context: Context?) {
        if (recentTransactionList == null || context == null) return
        for (transaction in recentTransactionList) {
            val uids = transaction.getPayorUids()
            if (uids != null && !uids.isEmpty()) {
                PayorAdapter.preCacheUids(context, uids)
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

        holder.dateTextView.text = transaction.getMostRecentDate()
        holder.typeTextView.text = transaction.getMostRecentTransactionType()
        holder.amountTextView.text = transaction.getMostRecentPaymentAmountStr()
        holder.iconImageView.setImageResource(transaction.getIconResource())

        val isExpanded = transaction.isExpanded()
        holder.expandableLayout.visibility = if (isExpanded) View.VISIBLE else View.GONE
        holder.detailsTextView.text = if (isExpanded) "Hide Details <" else "See Details >"

        if (isExpanded) {
            holder.loadingOverlay.visibility = View.VISIBLE
            holder.createdByTextView.text = transaction.getCreatedBy() ?: "Unknown"

            val payorsUids = transaction.getPayorUids()
            val payorsNames = transaction.getPayorsList()
            val amountsPaid = transaction.getAmountsPaidList()
            val individualPayment = transaction.getTotalIndividualPayment()

            val currentUid = DeclareDatabase.auth.currentUserOrNull()?.id
            val isCreator = transaction.getCreatedByUid() != null && transaction.getCreatedByUid() == currentUid

            if (payorsUids != null) {
                val payorAdapter = PayorAdapter(
                    payorsUids,
                    payorsNames,
                    amountsPaid,
                    individualPayment,
                    object : OnPayorClickListener {
                        override fun onPayorClick(index: Int, paid: Double) {}
                        override fun onPartialClick(index: Int, currentPaid: Double) {
                            showEditAmountDialog(holder.itemView.context, { newAmount ->
                                (holder.payorsRecyclerView.adapter as? PayorAdapter)?.updatePartialAmount(index, newAmount)
                            }, currentPaid)
                        }
                    })

                payorAdapter.setOnLoadingCompleteListener { holder.loadingOverlay.visibility = View.GONE }
                payorAdapter.setOnDataChangedListener { hasChanges ->
                    holder.saveTransactionBtn.isEnabled = hasChanges
                    holder.saveTransactionBtn.alpha = if (hasChanges) 1.0f else 0.5f
                }

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
                    }

                    holder.cancelTransactionBtn.setOnClickListener {
                        holder.loadingOverlay.visibility = View.VISIBLE
                        payorAdapter.setEditMode(false)
                        holder.editTransactionBtn.visibility = View.VISIBLE
                        holder.saveTransactionBtn.visibility = View.GONE
                        holder.cancelTransactionBtn.visibility = View.GONE
                    }

                    holder.saveTransactionBtn.setOnClickListener {
                        holder.loadingOverlay.visibility = View.VISIBLE
                        val updatedAmounts = payorAdapter.getAmountsPaid()
                        saveTransactionChanges(holder.itemView.context, transaction, updatedAmounts, holder.adapterPosition, {
                            payorAdapter.saveChanges()
                            holder.editTransactionBtn.visibility = View.VISIBLE
                            holder.saveTransactionBtn.visibility = View.GONE
                            holder.cancelTransactionBtn.visibility = View.GONE
                        }, { holder.loadingOverlay.visibility = View.GONE })
                    }
                } else {
                    holder.editTransactionBtn.visibility = View.GONE
                }
            } else {
                holder.loadingOverlay.visibility = View.GONE
                holder.editTransactionBtn.visibility = View.GONE
            }

            val details = transaction.getMostRecentDetails()
            holder.fullDetailsTextView.text = if (!details.isNullOrEmpty() && details != "See Details >") details else "No additional details"
        } else {
            holder.loadingOverlay.visibility = View.GONE
        }

        holder.mainContent.setOnClickListener {
            transaction.setExpanded(!transaction.isExpanded())
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
        val my = transaction.getMonthYear()
        val d = transaction.getDay()
        val tk = transaction.getTimeKey()

        if (my == null || d == null || tk == null) {
            Toast.makeText(context, "Error: Reference not found", Toast.LENGTH_SHORT).show()
            onComplete.run()
            return
        }

        DeclareDatabase.getDBRefTransaction().child(my).child(d).child(tk).child("amountsPaidList")
            .setValue(updatedAmounts).addOnSuccessListener {
                Toast.makeText(context, "Transaction updated", Toast.LENGTH_SHORT).show()
                transaction.setAmountsPaidList(ArrayList(updatedAmounts))
                onSuccess.run()
                notifyItemChanged(position)
            }.addOnFailureListener {
                Toast.makeText(context, "Update failed", Toast.LENGTH_SHORT).show()
                onComplete.run()
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
