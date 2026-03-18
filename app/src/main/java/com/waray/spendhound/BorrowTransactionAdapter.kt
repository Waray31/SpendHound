package com.waray.spendhound

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class BorrowTransactionAdapter(private val borrowTransactionList: ArrayList<BorrowTransaction>) :
    RecyclerView.Adapter<BorrowTransactionAdapter.ViewHolder>() {
    val checkedPositions: ArrayList<Int> = ArrayList()

    fun getBorrowTransaction(position: Int): BorrowTransaction? {
        return borrowTransactionList[position]
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.debt_rowcheckbox_layout, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, @SuppressLint("RecyclerView") position: Int) {
        val transaction = borrowTransactionList[position]

        holder.cbDebtDateTV.text = transaction.getDate()
        holder.cbDebtBorroweeTV.text = transaction.getBorrowee()
        holder.cbDebtAmountBorrowedTV.text = CurrencyUtils.formatAmountWithCurrency(transaction.getBorrowedAmountStr() ?: "")
        holder.cbDebtStatusTV.text = transaction.getStatus()

        val status = transaction.getStatus()
        val statusColor: Int = if ("Paid".equals(status, ignoreCase = true)) {
            ContextCompat.getColor(holder.itemView.context, R.color.green)
        } else if ("Pending".equals(status, ignoreCase = true)) {
            ContextCompat.getColor(holder.itemView.context, R.color.yellow)
        } else if ("Paid Partially".equals(status, ignoreCase = true)) {
            ContextCompat.getColor(holder.itemView.context, R.color.yellow)
        } else {
            ContextCompat.getColor(holder.itemView.context, R.color.red)
        }
        holder.cbDebtStatusTV.setTextColor(statusColor)

        holder.payCheckBox.setOnCheckedChangeListener(null)
        holder.payCheckBox.isChecked = checkedPositions.contains(position)
        holder.payCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!checkedPositions.contains(position)) {
                    checkedPositions.add(position)
                }
            } else {
                checkedPositions.remove(position)
            }
        }
    }

    fun selectAll() {
        checkedPositions.clear()
        for (i in borrowTransactionList.indices) {
            checkedPositions.add(i)
        }
        notifyDataSetChanged()
    }

    fun deselectAll() {
        checkedPositions.clear()
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return borrowTransactionList.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var cbDebtDateTV: TextView = itemView.findViewById(R.id.cbDebtDateTV)
        var cbDebtBorroweeTV: TextView = itemView.findViewById(R.id.cbDebtBorroweeTV)
        var cbDebtAmountBorrowedTV: TextView = itemView.findViewById(R.id.cbDebtAmountBorrowedTV)
        var cbDebtStatusTV: TextView = itemView.findViewById(R.id.cbDebtStatusTV)
        var payCheckBox: CheckBox = itemView.findViewById(R.id.payCheckBox)
    }
}
