package com.waray.spendhound

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.ImageView
import com.google.firebase.auth.FirebaseAuth

class RecentTransactionAdapter : RecyclerView.Adapter<RecentTransactionAdapter.ViewHolder?> {
    private val recentTransactionList: ArrayList<RecentTransaction>?
    private var clickListener: OnTransactionClickListener? = null

    interface OnTransactionClickListener {
        fun onTransactionClick(transaction: RecentTransaction?)
    }

    constructor(recentTransactionList: ArrayList<RecentTransaction>?) {
        this.recentTransactionList = recentTransactionList
    }

    constructor(
        recentTransactionList: ArrayList<RecentTransaction>?,
        clickListener: OnTransactionClickListener?
    ) {
        this.recentTransactionList = recentTransactionList
        this.clickListener = clickListener
    }

    fun setOnTransactionClickListener(listener: OnTransactionClickListener?) {
        this.clickListener = listener
    }

    /**
     * Proactively pre-caches all payor profile images for the entire transaction list.
     * This helps reduce loading time when a transaction is expanded.
     */
    fun preloadAllImages(context: Context?) {
        if (recentTransactionList == null || context == null) return
        for (transaction in recentTransactionList) {
            val uids = transaction.getPayorUids()
            if (uids != null && !uids.isEmpty()) {
                PayorAdapter.Companion.preCacheUids(context, uids)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView: View = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.fragment_transaction, parent, false)
        return RecentTransactionAdapter.ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val transaction = recentTransactionList!!.get(position)

        // Bind data to the ViewHolder's views
        holder.dateTextView.setText(transaction.getMostRecentDate())
        holder.typeTextView.setText(transaction.getMostRecentTransactionType())
        holder.amountTextView.setText(transaction.getMostRecentPaymentAmountStr())
        holder.iconImageView.setImageResource(transaction.getIconResource())

        // Handle expansion
        val isExpanded = transaction.isExpanded()
        holder.expandableLayout.setVisibility(if (isExpanded) View.VISIBLE else View.GONE)
        holder.detailsTextView.setText(if (isExpanded) "Hide Details <" else "See Details >")

        if (isExpanded) {
            holder.loadingOverlay.setVisibility(View.VISIBLE)

            // Created By Section
            holder.createdByTextView.setText(if (transaction.getCreatedBy() != null) transaction.getCreatedBy() else "Unknown")

            // Payors Section
            val payorsUids = transaction.getPayorUids()
            val payorsNames = transaction.getPayorsList()
            val amountsPaid = transaction.getAmountsPaidList()
            val individualPayment = transaction.getTotalIndividualPayment()

            val currentUid: String? = FirebaseAuth.getInstance().getCurrentUser().getUid()
            val isCreator =
                transaction.getCreatedByUid() != null && transaction.getCreatedByUid() == currentUid

            if (payorsUids != null) {
                val payorAdapter = PayorAdapter(
                    payorsUids,
                    payorsNames,
                    amountsPaid,
                    individualPayment,
                    object : OnPayorClickListener {
                        override fun onPayorClick(index: Int, paid: Double) {
                            // Regular click behavior if needed
                        }

                        override fun onPartialClick(index: Int, currentPaid: Double) {
                            showEditAmountDialog(
                                holder.itemView.getContext(),
                                OnAmountEnteredListener { newAmount: Double ->
                                    (holder.payorsRecyclerView.getAdapter() as PayorAdapter).updatePartialAmount(
                                        index,
                                        newAmount
                                    )
                                },
                                currentPaid
                            )
                        }
                    })


                // Set loading listener
                payorAdapter.setOnLoadingCompleteListener(OnLoadingCompleteListener {
                    holder.loadingOverlay.setVisibility(
                        View.GONE
                    )
                })


                // Set data changed listener to enable/disable save button
                payorAdapter.setOnDataChangedListener(OnDataChangedListener { hasChanges: Boolean ->
                    holder.saveTransactionBtn.setEnabled(hasChanges)
                    holder.saveTransactionBtn.setAlpha(if (hasChanges) 1.0f else 0.5f)
                })

                holder.payorsRecyclerView.setLayoutManager(
                    LinearLayoutManager(
                        holder.itemView.getContext(),
                        LinearLayoutManager.HORIZONTAL,
                        false
                    )
                )
                holder.payorsRecyclerView.setAdapter(payorAdapter)

                // Proactively load all images to ensure progress bar doesn't stop too early
                payorAdapter.startLoadingAllImages(holder.itemView.getContext())

                // Setup Edit/Save/Cancel buttons
                if (isCreator) {
                    holder.editTransactionBtn.setVisibility(View.VISIBLE)
                    holder.editTransactionBtn.setOnClickListener(View.OnClickListener { v: View? ->
                        holder.loadingOverlay.setVisibility(View.VISIBLE)
                        payorAdapter.setEditMode(true)
                        holder.editTransactionBtn.setVisibility(View.GONE)
                        holder.saveTransactionBtn.setVisibility(View.VISIBLE)
                        holder.cancelTransactionBtn.setVisibility(View.VISIBLE)
                        // Initial state: no changes yet
                        holder.saveTransactionBtn.setEnabled(false)
                        holder.saveTransactionBtn.setAlpha(0.5f)
                    })

                    holder.cancelTransactionBtn.setOnClickListener(View.OnClickListener { v: View? ->
                        holder.loadingOverlay.setVisibility(View.VISIBLE)
                        payorAdapter.setEditMode(false)
                        holder.editTransactionBtn.setVisibility(View.VISIBLE)
                        holder.saveTransactionBtn.setVisibility(View.GONE)
                        holder.cancelTransactionBtn.setVisibility(View.GONE)
                    })

                    holder.saveTransactionBtn.setOnClickListener(View.OnClickListener { v: View? ->
                        holder.loadingOverlay.setVisibility(View.VISIBLE)
                        val updatedAmounts = payorAdapter.getAmountsPaid()
                        saveTransactionChanges(
                            holder.itemView.getContext(),
                            transaction,
                            updatedAmounts,
                            holder.getAdapterPosition(),
                            Runnable {
                                payorAdapter.saveChanges()
                                holder.editTransactionBtn.setVisibility(View.VISIBLE)
                                holder.saveTransactionBtn.setVisibility(View.GONE)
                                holder.cancelTransactionBtn.setVisibility(View.GONE)
                            },
                            Runnable { holder.loadingOverlay.setVisibility(View.GONE) })
                    })
                } else {
                    holder.editTransactionBtn.setVisibility(View.GONE)
                }
            } else {
                holder.loadingOverlay.setVisibility(View.GONE)
                holder.editTransactionBtn.setVisibility(View.GONE)
            }

            // Details Section
            val details = transaction.getMostRecentDetails()
            if (details != null && !details.isEmpty() && (details != "See Details >")) {
                holder.fullDetailsTextView.setText(details)
            } else {
                holder.fullDetailsTextView.setText("No additional details")
            }
        } else {
            holder.loadingOverlay.setVisibility(View.GONE)
        }

        // Set click listener to toggle expansion
        holder.mainContent.setOnClickListener(View.OnClickListener { v: View? ->
            transaction.setExpanded(!transaction.isExpanded())
            notifyItemChanged(holder.getAdapterPosition())
            if (clickListener != null) {
                clickListener!!.onTransactionClick(transaction)
            }
        })
    }

    private fun showEditAmountDialog(
        context: Context?,
        listener: OnAmountEnteredListener,
        currentAmount: Double
    ) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Edit Amount Paid")

        val input: EditText = EditText(context)
        input.setInputType(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        input.setText(currentAmount.toString())
        builder.setView(input)

        builder.setPositiveButton(
            "Save",
            DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
                val newAmountStr = input.getText().toString()
                if (!newAmountStr.isEmpty()) {
                    val newAmount = newAmountStr.toDouble()
                    listener.onAmountEntered(newAmount)
                }
            })
        builder.setNegativeButton(
            "Cancel",
            DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int -> dialog.cancel() })

        builder.show()
    }

    private interface OnAmountEnteredListener {
        fun onAmountEntered(amount: Double)
    }

    private fun saveTransactionChanges(
        context: Context?,
        transaction: RecentTransaction,
        updatedAmounts: MutableList<Double?>,
        position: Int,
        onSuccess: Runnable,
        onComplete: Runnable
    ) {
        val monthYear = transaction.getMonthYear()
        val day = transaction.getDay()
        val timeKey = transaction.getTimeKey()

        if (monthYear == null || day == null || timeKey == null) {
            Toast.makeText(
                context,
                "Error: Could not find transaction reference",
                Toast.LENGTH_SHORT
            ).show()
            onComplete.run()
            return
        }

        val ref: DatabaseReference = DeclareDatabase.getDBRefTransaction()
            .child(monthYear)
            .child(day)
            .child(timeKey)
            .child("amountsPaidList")

        ref.setValue(updatedAmounts).addOnSuccessListener({ aVoid ->
            Toast.makeText(context, "Transaction updated successfully", Toast.LENGTH_SHORT).show()
            transaction.setAmountsPaidList(ArrayList<Double?>(updatedAmounts))
            onSuccess.run()
            notifyItemChanged(position)
        }).addOnFailureListener({ e ->
            Toast.makeText(context, "Failed to update transaction", Toast.LENGTH_SHORT).show()
            onComplete.run()
        })
    }

    val itemCount: Int
        get() = recentTransactionList!!.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var dateTextView: TextView
        var typeTextView: TextView
        var detailsTextView: TextView
        var amountTextView: TextView
        var iconImageView: ImageView

        var mainContent: View
        var expandableLayout: View
        var createdByTextView: TextView
        var payorsRecyclerView: RecyclerView
        var fullDetailsTextView: TextView
        var loadingOverlay: View
        var editTransactionBtn: Button
        var saveTransactionBtn: Button
        var cancelTransactionBtn: Button

        init {
            dateTextView = itemView.findViewById<TextView>(R.id.dateTextView)
            typeTextView = itemView.findViewById<TextView>(R.id.transactionTypeTextView)
            detailsTextView = itemView.findViewById<TextView>(R.id.detailsTextView)
            amountTextView = itemView.findViewById<TextView>(R.id.paymentAmountTextView)
            iconImageView = itemView.findViewById<ImageView>(R.id.iconImageView)

            mainContent = itemView.findViewById<View>(R.id.main_content)
            expandableLayout = itemView.findViewById<View>(R.id.expandable_layout)
            createdByTextView = itemView.findViewById<TextView>(R.id.createdByTextView)
            payorsRecyclerView = itemView.findViewById<RecyclerView>(R.id.payorsRecyclerView)
            fullDetailsTextView = itemView.findViewById<TextView>(R.id.fullDetailsTextView)
            loadingOverlay = itemView.findViewById<View>(R.id.loadingOverlay_transaction)
            editTransactionBtn = itemView.findViewById<Button>(R.id.editTransaction_btn)
            saveTransactionBtn = itemView.findViewById<Button>(R.id.saveTransaction_btn)
            cancelTransactionBtn = itemView.findViewById<Button>(R.id.cancelTransaction_btn)
        }
    }
}
