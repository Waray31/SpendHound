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

class OwedTransactionAdapter(
    private val owedTransactionList: List<OwedTransaction?>,
    private var lenderActionListener: OnLenderActionListener? = null
) : RecyclerView.Adapter<OwedTransactionAdapter.ViewHolder>() {

    private var clickListener: OnItemClickListener? = null

    interface OnItemClickListener {
        fun onItemClick(transaction: OwedTransaction?, position: Int)
    }

    interface OnLenderActionListener {
        fun onNotYetClicked(transaction: OwedTransaction?, position: Int)
        fun onReceivedClicked(transaction: OwedTransaction?, position: Int)
        fun onDeclineClicked(transaction: OwedTransaction?, position: Int)
        fun onApprovedClicked(transaction: OwedTransaction?, position: Int)
    }

    // Secondary constructor for backward compatibility
    constructor(
        owedTransactionList: List<OwedTransaction?>,
        clickListener: OnItemClickListener?
    ) : this(owedTransactionList) {
        this.clickListener = clickListener
    }

    fun setOnItemClickListener(listener: OnItemClickListener?) {
        this.clickListener = listener
    }

    fun setOnLenderActionListener(listener: OnLenderActionListener?) {
        this.lenderActionListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.owed_row_layout, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, @SuppressLint("RecyclerView") position: Int) {
        val transaction = owedTransactionList[position] ?: return

        // Bind data to the ViewHolder's views
        holder.owedDateTV.text = DebtTransactionAdapter.formatDate(transaction.date)
        holder.owedBorroweeTV.text = transaction.borrower
        holder.owedAmountBorrowedTV.text = CurrencyUtils.formatAmountWithCurrency(transaction.borrowedAmountStr ?: "0")
        holder.owedStatusTV.text = transaction.status

        // Hide all action layouts by default
        holder.pendingPaymentActionsLayout.visibility = View.GONE
        holder.lenderApprovalActionsLayout.visibility = View.GONE
        holder.paymentSentDateTV.visibility = View.GONE

        // Set status color based on status value
        val status = transaction.status
        val statusColor: Int
        var isPendingStatus = false
        var isPendingPayment = false
        var isPaid = false
        var isUnpaid = false
        
        when {
            "Paid".equals(status, ignoreCase = true) -> {
                statusColor = ContextCompat.getColor(holder.itemView.context, R.color.green)
                isPaid = true
            }
            "Pending".equals(status, ignoreCase = true) || 
            "Paid Partially".equals(status, ignoreCase = true) || 
            "Pending Payment".equals(status, ignoreCase = true) -> {
                statusColor = ContextCompat.getColor(holder.itemView.context, R.color.yellow)
                if ("Pending Payment".equals(status, ignoreCase = true)) isPendingPayment = true
            }
            "For Lender Approval".equals(status, ignoreCase = true) -> {
                statusColor = ContextCompat.getColor(holder.itemView.context, R.color.blue)
                isPendingStatus = true
            }
            "Unpaid".equals(status, ignoreCase = true) -> {
                statusColor = ContextCompat.getColor(holder.itemView.context, R.color.red)
                isUnpaid = true
            }
            else -> {
                statusColor = ContextCompat.getColor(holder.itemView.context, R.color.red)
            }
        }
        holder.owedStatusTV.setTextColor(statusColor)

        // Show action buttons based on status
        if (isPendingPayment) {
            holder.pendingPaymentActionsLayout.visibility = View.VISIBLE
        } else if (isPendingStatus) {
            holder.lenderApprovalActionsLayout.visibility = View.VISIBLE
        }

        // Show payment sent date for Paid status
        val paymentSentDate = transaction.paymentSentDate
        if (isPaid && !paymentSentDate.isNullOrEmpty()) {
            holder.paymentSentDateTV.visibility = View.VISIBLE
            holder.paymentSentDateTV.text = ": $paymentSentDate"
        }

        // Cast to MaterialCardView for elevation and background
        val cardView = holder.itemView as MaterialCardView
        val density = holder.itemView.context.resources.displayMetrics.density

        // Set background tint and elevation based on status
        val bgColorRes = when {
            isPaid -> R.color.paid_bg
            isUnpaid -> R.color.unpaid_bg
            isPendingPayment -> R.color.pending_payment_bg
            isPendingStatus -> R.color.pending_approval_bg
            else -> R.color.whitest
        }
        
        cardView.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.context, bgColorRes))
        cardView.cardElevation = 4 * density

        // Set click listeners for action buttons
        holder.notYetBtn.setOnClickListener {
            lenderActionListener?.onNotYetClicked(transaction, holder.bindingAdapterPosition)
        }

        holder.receivedBtn.setOnClickListener {
            lenderActionListener?.onReceivedClicked(transaction, holder.bindingAdapterPosition)
        }

        holder.declineBtn.setOnClickListener {
            lenderActionListener?.onDeclineClicked(transaction, holder.bindingAdapterPosition)
        }

        holder.approvedBtn.setOnClickListener {
            lenderActionListener?.onApprovedClicked(transaction, holder.bindingAdapterPosition)
        }

        // Set click listener for item
        holder.itemView.setOnClickListener {
            if (clickListener != null && isPendingStatus) {
                clickListener?.onItemClick(transaction, holder.bindingAdapterPosition)
            }
        }
    }

    override fun getItemCount(): Int = owedTransactionList.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val owedDateTV: TextView = itemView.findViewById(R.id.owedDateTV)
        val owedBorroweeTV: TextView = itemView.findViewById(R.id.owedBorroweeTV)
        val owedAmountBorrowedTV: TextView = itemView.findViewById(R.id.owedAmountBorrowedTV)
        val owedStatusTV: TextView = itemView.findViewById(R.id.owedStatusTV)
        val paymentSentDateTV: TextView = itemView.findViewById(R.id.owedPaymentSentDateTV)
        val pendingPaymentActionsLayout: LinearLayout = itemView.findViewById(R.id.pendingPaymentActionsLayout)
        val lenderApprovalActionsLayout: LinearLayout = itemView.findViewById(R.id.lenderApprovalActionsLayout)
        val notYetBtn: TextView = itemView.findViewById(R.id.notYetBtn)
        val receivedBtn: TextView = itemView.findViewById(R.id.receivedBtn)
        val declineBtn: TextView = itemView.findViewById(R.id.declineBtn)
        val approvedBtn: TextView = itemView.findViewById(R.id.approvedBtn)
    }
}
