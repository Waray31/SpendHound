package com.waray.spendhound

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class OwedTransactionAdapter : RecyclerView.Adapter<OwedTransactionAdapter.ViewHolder?> {
    private val owedTransactionList: ArrayList<OwedTransaction>
    private var clickListener: OnItemClickListener? = null
    private var lenderActionListener: OnLenderActionListener? = null

    interface OnItemClickListener {
        fun onItemClick(transaction: OwedTransaction?, position: Int)
    }

    interface OnLenderActionListener {
        fun onNotYetClicked(transaction: OwedTransaction?, position: Int)
        fun onReceivedClicked(transaction: OwedTransaction?, position: Int)
        fun onDeclineClicked(transaction: OwedTransaction?, position: Int)
        fun onApprovedClicked(transaction: OwedTransaction?, position: Int)
    }

    constructor(owedTransactionList: ArrayList<OwedTransaction>) {
        this.owedTransactionList = owedTransactionList
    }

    constructor(
        owedTransactionList: ArrayList<OwedTransaction>,
        clickListener: OnItemClickListener?
    ) {
        this.owedTransactionList = owedTransactionList
        this.clickListener = clickListener
    }

    constructor(
        owedTransactionList: ArrayList<OwedTransaction>,
        lenderActionListener: OnLenderActionListener?
    ) {
        this.owedTransactionList = owedTransactionList
        this.lenderActionListener = lenderActionListener
    }

    fun setOnItemClickListener(listener: OnItemClickListener?) {
        this.clickListener = listener
    }

    fun setOnLenderActionListener(listener: OnLenderActionListener?) {
        this.lenderActionListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.owed_row_layout, parent, false)
        return OwedTransactionAdapter.ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val transaction = owedTransactionList.get(position)

        // Bind data to the ViewHolder's views
        holder.owedDateTV.setText(transaction.getDate())
        holder.owedBorroweeTV.setText(transaction.getBorrower())
        holder.owedAmountBorrowedTV.setText(CurrencyUtils.formatAmountWithCurrency(transaction.getBorrowedAmountStr()))
        holder.owedStatusTV.setText(transaction.getStatus())

        // Hide all action layouts by default
        holder.pendingPaymentActionsLayout.setVisibility(View.GONE)
        holder.lenderApprovalActionsLayout.setVisibility(View.GONE)
        holder.paymentSentDateTV.setVisibility(View.GONE)

        // Set status color based on status value
        val status = transaction.getStatus()
        val statusColor: Int
        var isPendingStatus = false
        var isPendingPayment = false
        var isPaid = false
        var isUnpaid = false
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
        } else if ("Unpaid".equals(status, ignoreCase = true)) {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.red)
            isUnpaid = true
        } else {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.red)
        }
        holder.owedStatusTV.setTextColor(statusColor)

        // Show action buttons based on status
        if (isPendingPayment) {
            holder.pendingPaymentActionsLayout.setVisibility(View.VISIBLE)
        } else if (isPendingStatus) {
            holder.lenderApprovalActionsLayout.setVisibility(View.VISIBLE)
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
        holder.notYetBtn.setOnClickListener(View.OnClickListener { v: View? ->
            if (lenderActionListener != null) {
                lenderActionListener!!.onNotYetClicked(transaction, holder.getAdapterPosition())
            }
        })

        holder.receivedBtn.setOnClickListener(View.OnClickListener { v: View? ->
            if (lenderActionListener != null) {
                lenderActionListener!!.onReceivedClicked(transaction, holder.getAdapterPosition())
            }
        })

        holder.declineBtn.setOnClickListener(View.OnClickListener { v: View? ->
            if (lenderActionListener != null) {
                lenderActionListener!!.onDeclineClicked(transaction, holder.getAdapterPosition())
            }
        })

        holder.approvedBtn.setOnClickListener(View.OnClickListener { v: View? ->
            if (lenderActionListener != null) {
                lenderActionListener!!.onApprovedClicked(transaction, holder.getAdapterPosition())
            }
        })

        // Set click listener for item
        val finalIsPendingStatus = isPendingStatus
        holder.itemView.setOnClickListener(View.OnClickListener { v: View? ->
            if (clickListener != null && finalIsPendingStatus) {
                clickListener!!.onItemClick(transaction, position)
            }
        })
    }

    override fun getItemCount(): Int {
        return owedTransactionList.size
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var owedDateTV: TextView
        var owedBorroweeTV: TextView
        var owedAmountBorrowedTV: TextView
        var owedStatusTV: TextView
        var paymentSentDateTV: TextView
        var pendingPaymentActionsLayout: LinearLayout
        var lenderApprovalActionsLayout: LinearLayout
        var notYetBtn: TextView
        var receivedBtn: TextView
        var declineBtn: TextView
        var approvedBtn: TextView

        init {
            owedDateTV = itemView.findViewById<TextView>(R.id.owedDateTV)
            owedBorroweeTV = itemView.findViewById<TextView>(R.id.owedBorroweeTV)
            owedAmountBorrowedTV = itemView.findViewById<TextView>(R.id.owedAmountBorrowedTV)
            owedStatusTV = itemView.findViewById<TextView>(R.id.owedStatusTV)
            paymentSentDateTV = itemView.findViewById<TextView>(R.id.owedPaymentSentDateTV)
            pendingPaymentActionsLayout =
                itemView.findViewById<LinearLayout>(R.id.pendingPaymentActionsLayout)
            lenderApprovalActionsLayout =
                itemView.findViewById<LinearLayout>(R.id.lenderApprovalActionsLayout)
            notYetBtn = itemView.findViewById<TextView>(R.id.notYetBtn)
            receivedBtn = itemView.findViewById<TextView>(R.id.receivedBtn)
            declineBtn = itemView.findViewById<TextView>(R.id.declineBtn)
            approvedBtn = itemView.findViewById<TextView>(R.id.approvedBtn)
        }
    }
}
