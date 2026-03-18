package com.waray.spendhound

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

/**
 * RecyclerView Adapter for displaying breakdown items in the financial breakdown dialog.
 */
class BreakdownAdapter(private val context: Context) : 
    RecyclerView.Adapter<BreakdownAdapter.BreakdownViewHolder>() {

    private val breakdownItems: MutableList<BreakdownItem> = ArrayList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BreakdownViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_breakdown, parent, false)
        return BreakdownViewHolder(view)
    }

    override fun onBindViewHolder(holder: BreakdownViewHolder, position: Int) {
        val item = breakdownItems[position]

        // Set person name
        holder.personName.text = item.personName ?: "Unknown"

        // Set date
        holder.date.text = item.date ?: "N/A"

        // Set amount with currency formatting
        holder.amount.text = CurrencyUtils.formatAmountWithCurrency(item.amount)

        // Set status
        val status = item.status ?: ""
        holder.status.text = status

        // Set description if available
        if (!item.description.isNullOrEmpty()) {
            holder.description.text = item.description
            holder.description.visibility = View.VISIBLE
        } else {
            holder.description.visibility = View.GONE
        }

        // Set status indicator color based on status
        val indicatorColor: Int
        val statusTextColor: Int

        when (status.lowercase(Locale.getDefault())) {
            "paid", "completed", "settled" -> {
                indicatorColor = ContextCompat.getColor(context, R.color.green)
                statusTextColor = ContextCompat.getColor(context, R.color.green)
            }
            "pending", "awaiting" -> {
                indicatorColor = ContextCompat.getColor(context, R.color.yellow)
                statusTextColor = ContextCompat.getColor(context, R.color.yellow)
            }
            "overdue", "rejected" -> {
                indicatorColor = ContextCompat.getColor(context, R.color.red)
                statusTextColor = ContextCompat.getColor(context, R.color.red)
            }
            else -> {
                indicatorColor = ContextCompat.getColor(context, R.color.grey)
                statusTextColor = ContextCompat.getColor(context, R.color.grey)
            }
        }

        holder.statusIndicator.setBackgroundColor(indicatorColor)
        holder.status.setTextColor(statusTextColor)

        // Set amount color based on category
        item.category?.let {
            when (it) {
                BreakdownItem.Category.BALANCE -> holder.amount.setTextColor(
                    ContextCompat.getColor(context, R.color.green)
                )
                BreakdownItem.Category.UNPAID, BreakdownItem.Category.DEBT -> holder.amount.setTextColor(
                    ContextCompat.getColor(context, R.color.red)
                )
                BreakdownItem.Category.OWE -> holder.amount.setTextColor(
                    ContextCompat.getColor(context, R.color.yellow)
                )
            }
        } ?: run {
            holder.amount.setTextColor(ContextCompat.getColor(context, R.color.darkBlue))
        }
    }

    override fun getItemCount(): Int = breakdownItems.size

    fun updateData(newItems: List<BreakdownItem?>?) {
        this.breakdownItems.clear()
        newItems?.filterNotNull()?.let {
            this.breakdownItems.addAll(it)
        }
        notifyDataSetChanged()
    }

    fun clearData() {
        this.breakdownItems.clear()
        notifyDataSetChanged()
    }

    class BreakdownViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val statusIndicator: View = itemView.findViewById(R.id.statusIndicator)
        val personName: TextView = itemView.findViewById(R.id.breakdownPersonName)
        val date: TextView = itemView.findViewById(R.id.breakdownDate)
        val description: TextView = itemView.findViewById(R.id.breakdownDescription)
        val amount: TextView = itemView.findViewById(R.id.breakdownAmount)
        val status: TextView = itemView.findViewById(R.id.breakdownStatus)
    }
}
