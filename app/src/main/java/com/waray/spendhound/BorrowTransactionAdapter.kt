package com.waray.spendhound

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class BorrowTransactionAdapter(private val borrowTransactionList: ArrayList<BorrowTransaction>) :
    RecyclerView.Adapter<BorrowTransactionAdapter.ViewHolder?>() {
    val checkedPositions: ArrayList<Int?>

    init {
        checkedPositions = ArrayList<Int?>()
    }

    // Add this method to retrieve a BorrowTransaction by its position
    fun getBorrowTransaction(position: Int): BorrowTransaction? {
        return borrowTransactionList.get(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.debt_rowcheckbox_layout, parent, false)
        return BorrowTransactionAdapter.ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, @SuppressLint("RecyclerView") position: Int) {
        val transaction = borrowTransactionList.get(position)

        // Bind data to the ViewHolder's views
        holder.cbDebtDateTV.setText(transaction.getDate())
        holder.cbDebtBorroweeTV.setText(transaction.getBorrowee())
        holder.cbDebtAmountBorrowedTV.setText(CurrencyUtils.formatAmountWithCurrency(transaction.getBorrowedAmountStr()))
        holder.cbDebtStatusTV.setText(transaction.getStatus())

        // Set status color based on status value
        val status = transaction.getStatus()
        val statusColor: Int
        if ("Paid".equals(status, ignoreCase = true)) {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.green)
        } else if ("Pending".equals(status, ignoreCase = true)) {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.yellow)
        } else if ("Paid Partially".equals(status, ignoreCase = true)) {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.yellow)
        } else {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.red)
        }
        holder.cbDebtStatusTV.setTextColor(statusColor)

        // Set click listener for payCheckBox
        holder.payCheckBox.setOnCheckedChangeListener(null) // To prevent triggering listener for recycled views
        holder.payCheckBox.setChecked(checkedPositions.contains(position))
        holder.payCheckBox.setOnCheckedChangeListener(object :
            CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
                if (isChecked) {
                    // Add position to checkedPositions
                    if (!checkedPositions.contains(position)) {
                        checkedPositions.add(position)
                    }
                } else {
                    // Remove position from checkedPositions
                    checkedPositions.remove(position as Int?)
                }
            }
        })
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

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var cbDebtDateTV: TextView
        var cbDebtBorroweeTV: TextView
        var cbDebtAmountBorrowedTV: TextView
        var cbDebtStatusTV: TextView
        var payCheckBox: CheckBox

        init {
            cbDebtDateTV = itemView.findViewById<TextView>(R.id.cbDebtDateTV)
            cbDebtBorroweeTV = itemView.findViewById<TextView>(R.id.cbDebtBorroweeTV)
            cbDebtAmountBorrowedTV = itemView.findViewById<TextView>(R.id.cbDebtAmountBorrowedTV)
            cbDebtStatusTV = itemView.findViewById<TextView>(R.id.cbDebtStatusTV)
            payCheckBox = itemView.findViewById<CheckBox>(R.id.payCheckBox)
        }
    }
}
