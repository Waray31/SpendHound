package com.waray.spendhound

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.waray.spendhound.ui.multi_transaction.TransactionPayorTable
import com.waray.spendhound.ui.multi_transaction.TransactionFull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class SettlementHistoryActivity : AppCompatActivity() {

    private lateinit var rvHistory: RecyclerView
    private lateinit var loadingLayout: View
    private lateinit var emptyState: View
    private lateinit var btnBack: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settlement_history)

        rvHistory = findViewById(R.id.rvHistory)
        loadingLayout = findViewById(R.id.loadingLayout)
        emptyState = findViewById(R.id.emptyState)
        btnBack = findViewById(R.id.btnBack)

        btnBack.setOnClickListener { finish() }

        loadHistory()
    }

    private fun loadHistory() {
        loadingLayout.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Fetch transaction payors where status is settled (1) and paidTo is not null
                // We show all settlements regardless of where group is it from
                val payors = DeclareDatabase.transactionPayorsTable.select {
                    filter { 
                        eq("status", 1)
                    }
                }.decodeList<TransactionPayorTable>().filter { it.paidTo != null }

                if (payors.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        loadingLayout.visibility = View.GONE
                        emptyState.visibility = View.VISIBLE
                    }
                    return@launch
                }

                val txIds = payors.map { it.transactionId }.distinct()
                val transactions = DeclareDatabase.transactionsTable.select {
                    filter { 
                        isIn("id", txIds)
                    }
                }.decodeList<TransactionFull>().associateBy { it.id }

                // Filter payors whose transactions exist
                val filteredPayors = payors.filter { transactions.containsKey(it.transactionId) }

                val userIds = (filteredPayors.map { it.userId } + filteredPayors.mapNotNull { it.paidTo }).distinct()
                val users = DeclareDatabase.usersTable.select {
                    filter { isIn("user_id", userIds) }
                }.decodeList<User>().associateBy { it.id }

                val historyItems = filteredPayors.groupBy { 
                    listOf(it.userId, it.paidTo, it.updatedAt ?: it.createdAt)
                }.mapNotNull { (key, payorRows) ->
                    val userId = key[0] as Long
                    val paidTo = key[1] as Long
                    val timestamp = key[2] as String

                    val payer = users[userId] ?: return@mapNotNull null
                    val recipient = users[paidTo] ?: return@mapNotNull null
                    
                    val totalAmount = payorRows.sumOf { it.currentAmountPaid }
                    val breakdowns = payorRows.map { p ->
                        TransactionBreakdown(
                            description = transactions[p.transactionId]?.description ?: "Settlement",
                            amount = p.currentAmountPaid
                        )
                    }

                    SettlementHistoryItem(
                        title = "${payer.username} paid to ${recipient.username}",
                        date = timestamp,
                        amount = totalAmount,
                        breakdowns = breakdowns
                    )
                }.sortedByDescending { it.date }

                withContext(Dispatchers.Main) {
                    loadingLayout.visibility = View.GONE
                    if (historyItems.isEmpty()) {
                        emptyState.visibility = View.VISIBLE
                    } else {
                        emptyState.visibility = View.GONE
                        rvHistory.adapter = HistoryAdapter(historyItems)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingLayout.visibility = View.GONE
                    emptyState.visibility = View.VISIBLE
                }
            }
        }
    }

    private data class TransactionBreakdown(
        val description: String,
        val amount: Double
    )

    private data class SettlementHistoryItem(
        val title: String,
        val date: String,
        val amount: Double,
        val breakdowns: List<TransactionBreakdown>
    )

    private inner class HistoryAdapter(private val items: List<SettlementHistoryItem>) : 
        RecyclerView.Adapter<HistoryAdapter.VH>() {

        private val expandedPositions = mutableSetOf<Int>()

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvTransactionTitle)
            val tvDate: TextView = view.findViewById(R.id.tvDate)
            val tvAmount: TextView = view.findViewById(R.id.tvAmount)
            val detailsContainer: View = view.findViewById(R.id.detailsContainer)
            val divider: View = view.findViewById(R.id.detailsDivider)
            val breakdownContainer: LinearLayout = view.findViewById(R.id.breakdownContainer)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = 
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_settlement_history, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvTitle.text = item.title
            holder.tvAmount.text = CurrencyUtils.formatAmountWithCurrency(item.amount)

            // Format date
            try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val date = inputFormat.parse(item.date.take(19))
                val outputFormat = SimpleDateFormat("MMM d, yyyy • hh:mm a", Locale.getDefault())
                holder.tvDate.text = outputFormat.format(date!!)
            } catch (e: Exception) {
                holder.tvDate.text = item.date
            }

            val isExpanded = expandedPositions.contains(position)
            holder.detailsContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE
            holder.divider.visibility = if (isExpanded) View.VISIBLE else View.GONE

            if (isExpanded) {
                holder.breakdownContainer.removeAllViews()
                item.breakdowns.forEach { breakdown ->
                    val row = LayoutInflater.from(holder.itemView.context).inflate(R.layout.item_settlement_breakdown_row, holder.breakdownContainer, false)
                    row.findViewById<TextView>(R.id.tvDescription).text = breakdown.description
                    row.findViewById<TextView>(R.id.tvAmount).text = CurrencyUtils.formatAmountWithCurrency(breakdown.amount)
                    holder.breakdownContainer.addView(row)
                }
            }

            holder.itemView.setOnClickListener {
                if (isExpanded) expandedPositions.remove(position)
                else expandedPositions.add(position)
                notifyItemChanged(position)
            }
        }

        override fun getItemCount() = items.size
    }
}
