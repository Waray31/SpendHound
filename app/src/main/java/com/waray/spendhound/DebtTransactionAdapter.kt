package com.waray.spendhound

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class DebtTransactionAdapter : RecyclerView.Adapter<DebtTransactionAdapter.ViewHolder?> {
    private val borrowTransactionList: ArrayList<BorrowTransaction>
    val checkedPositions: ArrayList<Int?>?
    private var clickListener: OnItemClickListener? = null
    private var borrowerActionListener: OnBorrowerActionListener? = null

    interface OnItemClickListener {
        fun onItemClick(transaction: BorrowTransaction?, position: Int)
    }

    interface OnBorrowerActionListener {
        fun onPayClicked(transaction: BorrowTransaction?, position: Int)
        fun onRemoveClicked(transaction: BorrowTransaction?, position: Int)
        fun onTryAgainClicked(transaction: BorrowTransaction?, position: Int)
    }

    constructor(borrowTransactionList: ArrayList<BorrowTransaction>) {
        this.borrowTransactionList = borrowTransactionList
        checkedPositions = ArrayList<Int?>()
    }

    constructor(
        borrowTransactionList: ArrayList<BorrowTransaction>,
        clickListener: OnItemClickListener?
    ) {
        this.borrowTransactionList = borrowTransactionList
        this.clickListener = clickListener
        checkedPositions = ArrayList<Int?>()
    }

    constructor(
        borrowTransactionList: ArrayList<BorrowTransaction>,
        borrowerActionListener: OnBorrowerActionListener?
    ) {
        this.borrowTransactionList = borrowTransactionList
        this.borrowerActionListener = borrowerActionListener
        checkedPositions = ArrayList<Int?>()
    }

    fun setOnItemClickListener(listener: OnItemClickListener?) {
        this.clickListener = listener
    }

    fun setOnBorrowerActionListener(listener: OnBorrowerActionListener?) {
        this.borrowerActionListener = listener
    }

    // Add this method to retrieve a BorrowTransaction by its position
    fun getBorrowTransaction(position: Int): BorrowTransaction? {
        return borrowTransactionList.get(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.debt_row_layout, parent, false)
        return DebtTransactionAdapter.ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, @SuppressLint("RecyclerView") position: Int) {
        val transaction = borrowTransactionList.get(position)

        // Bind data to the ViewHolder's views
        holder.debtDateTV.setText(transaction.getDate())
        holder.debtBorroweeTV.setText(transaction.getBorrowee())
        holder.debtAmountBorrowedTV.setText(CurrencyUtils.formatAmountWithCurrency(transaction.getBorrowedAmountStr()))
        holder.debtStatusTV.setText(transaction.getStatus())

        // Hide all action layouts by default
        holder.unpaidActionsLayout.setVisibility(View.GONE)
        holder.declinedActionsLayout.setVisibility(View.GONE)
        holder.paymentSentDateTV.setVisibility(View.GONE)

        // Set status color based on status value
        val status = transaction.getStatus()
        val statusColor: Int
        var isPendingStatus = false
        var isPendingPayment = false
        var isPaid = false
        var isUnpaid = false
        var isDeclined = false
        if ("Paid".equals(status, ignoreCase = true)) {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.green)
            isPaid = true
        } else if ("Pending".equals(status, ignoreCase = true)) {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.yellow)
        } else if ("Paid Partially".equals(status, ignoreCase = true)) {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.yellow)
        } else if ("Pending Payment".equals(status, ignoreCase = true)) {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.yellow)
            isPendingPayment = true
        } else if ("For Lender Approval".equals(status, ignoreCase = true)) {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.blue)
            isPendingStatus = true
        } else if ("Declined".equals(status, ignoreCase = true)) {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.red)
            isDeclined = true
        } else if ("Unpaid".equals(status, ignoreCase = true)) {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.red)
            isUnpaid = true
        } else {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.red)
        }
        holder.debtStatusTV.setTextColor(statusColor)

        // Show action buttons based on status
        if (isUnpaid) {
            holder.unpaidActionsLayout.setVisibility(View.VISIBLE)
        } else if (isDeclined) {
            holder.declinedActionsLayout.setVisibility(View.VISIBLE)
        }

        // Show payment sent date for Paid status
        if (isPaid && transaction.getPaymentSentDate() != null && !transaction.getPaymentSentDate()
                .isEmpty()
        ) {
            holder.paymentSentDateTV.setVisibility(View.VISIBLE)
            holder.paymentSentDateTV.setText(": " + transaction.getPaymentSentDate())
        }

        // Cast to MaterialCardView for elevation and background
        val cardView = holder.itemView as MaterialCardView
        val density = holder.itemView.getContext().getResources().getDisplayMetrics().density

        // Set background tint and elevation based on status
        if (isPaid) {
            cardView.setCardBackgroundColor(
                ContextCompat.getColor(
                    holder.itemView.getContext(),
                    R.color.paid_bg
                )
            )
            cardView.setCardElevation(4 * density)
        } else if (isUnpaid) {
            cardView.setCardBackgroundColor(
                ContextCompat.getColor(
                    holder.itemView.getContext(),
                    R.color.unpaid_bg
                )
            )
            cardView.setCardElevation(4 * density)
        } else if (isPendingPayment) {
            cardView.setCardBackgroundColor(
                ContextCompat.getColor(
                    holder.itemView.getContext(),
                    R.color.pending_payment_bg
                )
            )
            cardView.setCardElevation(4 * density)
        } else if (isPendingStatus) {
            cardView.setCardBackgroundColor(
                ContextCompat.getColor(
                    holder.itemView.getContext(),
                    R.color.pending_approval_bg
                )
            )
            cardView.setCardElevation(4 * density)
        } else if (isDeclined) {
            cardView.setCardBackgroundColor(
                ContextCompat.getColor(
                    holder.itemView.getContext(),
                    R.color.unpaid_bg
                )
            )
            cardView.setCardElevation(4 * density)
        } else {
            cardView.setCardBackgroundColor(
                ContextCompat.getColor(
                    holder.itemView.getContext(),
                    R.color.whitest
                )
            )
            cardView.setCardElevation(4 * density)
        }

        // Set click listeners for action buttons
        holder.payBtn.setOnClickListener(View.OnClickListener { v: View? ->
            if (borrowerActionListener != null) {
                borrowerActionListener!!.onPayClicked(transaction, holder.getAdapterPosition())
            }
        })

        holder.removeBtn.setOnClickListener(View.OnClickListener { v: View? ->
            if (borrowerActionListener != null) {
                borrowerActionListener!!.onRemoveClicked(transaction, holder.getAdapterPosition())
            }
        })

        holder.tryAgainBtn.setOnClickListener(View.OnClickListener { v: View? ->
            if (borrowerActionListener != null) {
                borrowerActionListener!!.onTryAgainClicked(transaction, holder.getAdapterPosition())
            }
        })

        // Set click listener for pending items to navigate to PendingStatusActivity
        val finalIsPendingStatus = isPendingStatus
        holder.itemView.setOnClickListener(View.OnClickListener { v: View? ->
            if (clickListener != null && finalIsPendingStatus) {
                clickListener!!.onItemClick(transaction, position)
            }
        })
    }


    override fun getItemCount(): Int {
        return borrowTransactionList.size
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var debtDateTV: TextView
        var debtBorroweeTV: TextView
        var debtAmountBorrowedTV: TextView
        var debtStatusTV: TextView
        var paymentSentDateTV: TextView
        var unpaidActionsLayout: LinearLayout
        var declinedActionsLayout: LinearLayout
        var payBtn: TextView
        var removeBtn: TextView
        var tryAgainBtn: TextView

        init {
            debtDateTV = itemView.findViewById<TextView>(R.id.debtDateTV)
            debtBorroweeTV = itemView.findViewById<TextView>(R.id.debtBorroweeTV)
            debtAmountBorrowedTV = itemView.findViewById<TextView>(R.id.debtAmountBorrowedTV)
            debtStatusTV = itemView.findViewById<TextView>(R.id.debtStatusTV)
            paymentSentDateTV = itemView.findViewById<TextView>(R.id.debtPaymentSentDateTV)
            unpaidActionsLayout = itemView.findViewById<LinearLayout>(R.id.unpaidActionsLayout)
            declinedActionsLayout = itemView.findViewById<LinearLayout>(R.id.declinedActionsLayout)
            payBtn = itemView.findViewById<TextView>(R.id.payBtn)
            removeBtn = itemView.findViewById<TextView>(R.id.removeBtn)
            tryAgainBtn = itemView.findViewById<TextView>(R.id.tryAgainBtn)
        }
    }
}
