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

/**
 * Adapter for displaying debt transactions where the current user is the borrower.
 */
class DebtTransactionAdapter(
    private val borrowTransactionList: List<BorrowTransaction?>,
    private var borrowerActionListener: OnBorrowerActionListener? = null
) : RecyclerView.Adapter<DebtTransactionAdapter.ViewHolder>() {

    private var clickListener: OnItemClickListener? = null

    interface OnItemClickListener {
        fun onItemClick(transaction: BorrowTransaction?, position: Int)
    }

    interface OnBorrowerActionListener {
        fun onPayClicked(transaction: BorrowTransaction?, position: Int)
        fun onRemoveClicked(transaction: BorrowTransaction?, position: Int)
        fun onTryAgainClicked(transaction: BorrowTransaction?, position: Int)
    }

    /**
     * Secondary constructor for setting the item click listener.
     */
    constructor(
        borrowTransactionList: List<BorrowTransaction?>,
        clickListener: OnItemClickListener?
    ) : this(borrowTransactionList) {
        this.clickListener = clickListener
    }

    fun setOnItemClickListener(listener: OnItemClickListener?) {
        this.clickListener = listener
    }

    fun setOnBorrowerActionListener(listener: OnBorrowerActionListener?) {
        this.borrowerActionListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.debt_row_layout, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, @SuppressLint("RecyclerView") position: Int) {
        val transaction = borrowTransactionList[position] ?: return

        // Bind data using property access
        holder.debtDateTV.text = formatDate(transaction.date)
        holder.debtBorroweeTV.text = transaction.borroweeDisplayName ?: transaction.borrowee
        holder.debtAmountBorrowedTV.text = CurrencyUtils.formatAmountWithCurrency(transaction.borrowedAmountStr ?: "0")
        holder.debtStatusTV.text = transaction.status

        // Reset visibility for layouts
        holder.unpaidActionsLayout.visibility = View.GONE
        holder.declinedActionsLayout.visibility = View.GONE
        holder.paymentSentDateTV.visibility = View.GONE

        val status = transaction.status
        var isPendingStatus = false
        var isPendingPayment = false
        var isPaid = false
        var isUnpaid = false
        var isDeclined = false

        val statusColor = when {
            "Paid".equals(status, ignoreCase = true) -> {
                isPaid = true
                ContextCompat.getColor(holder.itemView.context, R.color.green)
            }
            "Pending".equals(status, ignoreCase = true) || 
            "Paid Partially".equals(status, ignoreCase = true) ||
            "Pending Payment".equals(status, ignoreCase = true) -> {
                if ("Pending Payment".equals(status, ignoreCase = true)) isPendingPayment = true
                ContextCompat.getColor(holder.itemView.context, R.color.yellow)
            }
            "For Lender Approval".equals(status, ignoreCase = true) -> {
                isPendingStatus = true
                ContextCompat.getColor(holder.itemView.context, R.color.blue)
            }
            "Declined".equals(status, ignoreCase = true) -> {
                isDeclined = true
                ContextCompat.getColor(holder.itemView.context, R.color.red)
            }
            "Unpaid".equals(status, ignoreCase = true) -> {
                isUnpaid = true
                ContextCompat.getColor(holder.itemView.context, R.color.red)
            }
            else -> ContextCompat.getColor(holder.itemView.context, R.color.red)
        }
        
        holder.debtStatusTV.setTextColor(statusColor)

        // Conditional UI visibility
        if (isUnpaid) {
            holder.unpaidActionsLayout.visibility = View.VISIBLE
        } else if (isDeclined) {
            holder.declinedActionsLayout.visibility = View.VISIBLE
        }

        if (isPaid && !transaction.paymentSentDate.isNullOrEmpty()) {
            holder.paymentSentDateTV.visibility = View.VISIBLE
            holder.paymentSentDateTV.text = holder.itemView.context.getString(R.string.payment_sent_format, transaction.paymentSentDate)
        }

        // Card UI management
        val cardView = holder.itemView as MaterialCardView
        val density = holder.itemView.context.resources.displayMetrics.density
        
        val bgColorRes = when {
            isPaid -> R.color.paid_bg
            isUnpaid || isDeclined -> R.color.unpaid_bg
            isPendingPayment -> R.color.pending_payment_bg
            isPendingStatus -> R.color.pending_approval_bg
            else -> R.color.whitest
        }
        
        cardView.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.context, bgColorRes))
        cardView.cardElevation = 4 * density

        // Action Listeners
        holder.payBtn.setOnClickListener {
            borrowerActionListener?.onPayClicked(transaction, holder.bindingAdapterPosition)
        }

        holder.removeBtn.setOnClickListener {
            borrowerActionListener?.onRemoveClicked(transaction, holder.bindingAdapterPosition)
        }

        holder.tryAgainBtn.setOnClickListener {
            borrowerActionListener?.onTryAgainClicked(transaction, holder.bindingAdapterPosition)
        }

        holder.itemView.setOnClickListener {
            if (isPendingStatus) {
                clickListener?.onItemClick(transaction, holder.bindingAdapterPosition)
            }
        }
    }

    override fun getItemCount(): Int = borrowTransactionList.size

    companion object {
        fun formatDate(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            val formats = listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss",
                "MMMM-dd-yyyy"
            )
            for (fmt in formats) {
                try {
                    val date = java.text.SimpleDateFormat(fmt, java.util.Locale.getDefault()).parse(raw)
                    if (date != null) return java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(date)
                } catch (_: Exception) {}
            }
            return raw
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val debtDateTV: TextView = itemView.findViewById(R.id.debtDateTV)
        val debtBorroweeTV: TextView = itemView.findViewById(R.id.debtBorroweeTV)
        val debtAmountBorrowedTV: TextView = itemView.findViewById(R.id.debtAmountBorrowedTV)
        val debtStatusTV: TextView = itemView.findViewById(R.id.debtStatusTV)
        val paymentSentDateTV: TextView = itemView.findViewById(R.id.debtPaymentSentDateTV)
        val unpaidActionsLayout: LinearLayout = itemView.findViewById(R.id.unpaidActionsLayout)
        val declinedActionsLayout: LinearLayout = itemView.findViewById(R.id.declinedActionsLayout)
        val payBtn: TextView = itemView.findViewById(R.id.payBtn)
        val removeBtn: TextView = itemView.findViewById(R.id.removeBtn)
        val tryAgainBtn: TextView = itemView.findViewById(R.id.tryAgainBtn)
    }
}
