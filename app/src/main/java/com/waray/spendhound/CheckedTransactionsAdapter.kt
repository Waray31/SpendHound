package com.waray.spendhound

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CheckedTransactionsAdapter(private val checkedTransactions: ArrayList<BorrowTransaction>) :
    RecyclerView.Adapter<CheckedTransactionsAdapter.ViewHolder?>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_checked_transaction, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val transaction = checkedTransactions.get(position)
        holder.dateTextView.setText(transaction.getDate())
        holder.borroweeTextView.setText(transaction.getBorrowee())
        holder.amountTextView.setText(transaction.getBorrowedAmountStr())
        holder.statusTextView.setText(transaction.getStatus())
    }

    override fun getItemCount(): Int {
        return checkedTransactions.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var dateTextView: TextView
        var borroweeTextView: TextView
        var amountTextView: TextView
        var statusTextView: TextView

        init {
            dateTextView = itemView.findViewById<TextView>(R.id.dateTextView)
            borroweeTextView = itemView.findViewById<TextView>(R.id.borroweeTextView)
            amountTextView = itemView.findViewById<TextView>(R.id.amountTextView)
            statusTextView = itemView.findViewById<TextView>(R.id.statusTextView)
        }
    }
}

