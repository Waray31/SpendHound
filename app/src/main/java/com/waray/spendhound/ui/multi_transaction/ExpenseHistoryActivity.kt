package com.waray.spendhound.ui.multi_transaction

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.R
import com.waray.spendhound.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ExpenseHistoryActivity : AppCompatActivity() {

    private lateinit var rvHistory: RecyclerView
    private lateinit var loadingLayout: View
    private lateinit var emptyState: View
    private lateinit var btnBack: View
    private var transactionId: Long = -1

    companion object {
        private const val EXTRA_TRANSACTION_ID = "EXTRA_TRANSACTION_ID"
        fun start(context: Context, transactionId: Long) {
            val intent = Intent(context, ExpenseHistoryActivity::class.java).apply {
                putExtra(EXTRA_TRANSACTION_ID, transactionId)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expense_history)

        transactionId = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1)
        if (transactionId == -1L) {
            finish()
            return
        }

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
                val history = DeclareDatabase.transactionHistoryTable.select {
                    filter { eq("transaction_id", transactionId) }
                }.decodeList<TransactionHistoryFull>()

                if (history.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        loadingLayout.visibility = View.GONE
                        emptyState.visibility = View.VISIBLE
                    }
                    return@launch
                }

                val userIds = history.map { it.userId }.distinct()
                val users = DeclareDatabase.usersTable.select {
                    filter { isIn("user_id", userIds) }
                }.decodeList<User>().associateBy { it.id }

                history.forEach {
                    it.userName = users[it.userId]?.username ?: "Unknown User"
                }

                val sortedHistory = history.sortedByDescending { it.createdAt }

                withContext(Dispatchers.Main) {
                    loadingLayout.visibility = View.GONE
                    rvHistory.adapter = HistoryAdapter(sortedHistory)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingLayout.visibility = View.GONE
                    emptyState.visibility = View.VISIBLE
                }
            }
        }
    }

    private inner class HistoryAdapter(private val items: List<TransactionHistoryFull>) :
        RecyclerView.Adapter<HistoryAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvAction: TextView = view.findViewById(R.id.tvAction)
            val tvDate: TextView = view.findViewById(R.id.tvDate)
            val tvUserName: TextView = view.findViewById(R.id.tvUserName)
            val tvDetails: TextView = view.findViewById(R.id.tvDetails)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_expense_history, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvAction.text = item.action
            holder.tvUserName.text = item.userName
            holder.tvDetails.text = item.details

            // Format date
            try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val date = inputFormat.parse(item.createdAt?.take(19) ?: "")
                val outputFormat = SimpleDateFormat("MMM d, yyyy • hh:mm a", Locale.getDefault())
                holder.tvDate.text = outputFormat.format(date!!)
            } catch (e: Exception) {
                holder.tvDate.text = item.createdAt
            }
        }

        override fun getItemCount() = items.size
    }
}
