package com.waray.spendhound

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CheckedTransactionsAdapter(private val checkedTransactions: ArrayList<BorrowTransaction>) :
    RecyclerView.Adapter<CheckedTransactionsAdapter.ViewHolder?>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_checked_transaction, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val transaction = checkedTransactions[position]
        holder.dateTextView.text = transaction.date
        holder.borroweeTextView.text = transaction.borrowee
        holder.amountTextView.text = transaction.borrowedAmountStr
        holder.statusTextView.text = transaction.status
    }

    override fun getItemCount(): Int {
        return checkedTransactions.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var dateTextView: TextView = itemView.findViewById(R.id.dateTextView)
        var borroweeTextView: TextView = itemView.findViewById(R.id.borroweeTextView)
        var amountTextView: TextView = itemView.findViewById(R.id.amountTextView)
        var statusTextView: TextView = itemView.findViewById(R.id.statusTextView)
    }
}
