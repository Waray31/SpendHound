package com.waray.spendhound

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.waray.spendhound.BreakdownAdapter.BreakdownViewHolder
import java.util.Locale

/**
 * RecyclerView Adapter for displaying breakdown items in the financial breakdown dialog.
 */
class BreakdownAdapter : RecyclerView.Adapter<BreakdownViewHolder?> {
    private val breakdownItems: MutableList<BreakdownItem>
    private val context: Context

    constructor(context: Context) {
        this.context = context
        this.breakdownItems = ArrayList<BreakdownItem>()
    }

    constructor(context: Context, breakdownItems: MutableList<BreakdownItem?>?) {
        this.context = context
        this.breakdownItems =
            if (breakdownItems != null) breakdownItems else ArrayList<BreakdownItem>()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BreakdownViewHolder {
        val view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_breakdown, parent, false)
        return BreakdownViewHolder(view)
    }

    override fun onBindViewHolder(holder: BreakdownViewHolder, position: Int) {
        val item = breakdownItems.get(position)

        // Set person name
        holder.personName.setText(item.getPersonName())

        // Set date
        holder.date.setText(item.getDate())

        // Set amount with peso sign
        holder.amount.setText(CurrencyUtils.formatAmountWithCurrency(item.getAmount()))

        // Set status
        val status = item.getStatus()
        holder.status.setText(if (status != null) status else "")

        // Set description if available
        if (item.getDescription() != null && !item.getDescription().isEmpty()) {
            holder.description.setText(item.getDescription())
            holder.description.setVisibility(View.VISIBLE)
        } else {
            holder.description.setVisibility(View.GONE)
        }

        // Set status indicator color based on status
        val indicatorColor: Int
        val statusTextColor: Int

        if (status != null) {
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
        } else {
            indicatorColor = ContextCompat.getColor(context, R.color.grey)
            statusTextColor = ContextCompat.getColor(context, R.color.grey)
        }

        holder.statusIndicator.setBackgroundColor(indicatorColor)
        holder.status.setTextColor(statusTextColor)

        // Set amount color based on category
        if (item.getCategory() != null) {
            when (item.getCategory()) {
                BreakdownItem.Category.BALANCE -> holder.amount.setTextColor(
                    ContextCompat.getColor(
                        context,
                        R.color.green
                    )
                )

                BreakdownItem.Category.UNPAID, BreakdownItem.Category.DEBT -> holder.amount.setTextColor(
                    ContextCompat.getColor(context, R.color.red)
                )

                BreakdownItem.Category.OWE -> holder.amount.setTextColor(
                    ContextCompat.getColor(
                        context,
                        R.color.yellow
                    )
                )

                else -> holder.amount.setTextColor(
                    ContextCompat.getColor(
                        context,
                        R.color.darkBlue
                    )
                )
            }
        }
    }

    override fun getItemCount(): Int {
        return breakdownItems.size
    }

    fun updateData(newItems: MutableList<BreakdownItem?>?) {
        this.breakdownItems.clear()
        if (newItems != null) {
            this.breakdownItems.addAll(newItems)
        }
        notifyDataSetChanged()
    }

    fun clearData() {
        this.breakdownItems.clear()
        notifyDataSetChanged()
    }

    internal class BreakdownViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var statusIndicator: View
        var personName: TextView
        var date: TextView
        var description: TextView
        var amount: TextView
        var status: TextView

        init {
            statusIndicator = itemView.findViewById<View>(R.id.statusIndicator)
            personName = itemView.findViewById<TextView>(R.id.breakdownPersonName)
            date = itemView.findViewById<TextView>(R.id.breakdownDate)
            description = itemView.findViewById<TextView>(R.id.breakdownDescription)
            amount = itemView.findViewById<TextView>(R.id.breakdownAmount)
            status = itemView.findViewById<TextView>(R.id.breakdownStatus)
        }
    }
}
