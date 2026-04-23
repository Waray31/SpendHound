package com.waray.spendhound

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.waray.spendhound.ui.multi_transaction.TransactionFull
import com.waray.spendhound.ui.multi_transaction.TransactionItemFull
import com.waray.spendhound.ui.multi_transaction.TransactionPayorTable
import com.waray.spendhound.ui.multi_transaction.TransactionSplitTable
import com.waray.spendhound.utils.PullInterceptLayout
import com.waray.spendhound.utils.PullToRefreshHelper
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class GroupExpensesFragment : Fragment() {

    companion object {
        fun newInstance(groupId: Long) = GroupExpensesFragment().apply {
            arguments = Bundle().also { it.putLong("group_id", groupId) }
        }
    }

    private var groupId: Long = -1
    private val transactionList = ArrayList<RecentTransaction>()
    private lateinit var adapter: RecentTransactionAdapter
    private lateinit var rvSkeleton: RecyclerView
    private var pullToRefreshHelper: PullToRefreshHelper? = null
    private var fullTransactions: List<RecentTransaction> = emptyList()

    private var selectedStatusTab = "All"
    private var isTabClickEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        groupId = arguments?.getLong("group_id") ?: -1
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        inflater.inflate(R.layout.fragment_group_expenses, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rv = view.findViewById<RecyclerView>(R.id.rvExpenses)
        rvSkeleton = view.findViewById(R.id.rvSkeleton)
        adapter = RecentTransactionAdapter(transactionList, { loadExpenses() }, null)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter
        rvSkeleton.layoutManager = LinearLayoutManager(requireContext())
        rvSkeleton.adapter = SkeletonAdapter(R.layout.item_skeleton_transaction)

        val scrollView = view.findViewById<NestedScrollView>(R.id.expensesScrollView)
        val indicator = view.findViewById<View>(R.id.pullRefreshIndicator_expenses)
        val rootLayout = view as PullInterceptLayout
        pullToRefreshHelper = PullToRefreshHelper(scrollView, indicator, { loadExpenses() }, rootLayout)
        rootLayout.onInterceptCallback = { event -> pullToRefreshHelper?.onInterceptTouch(event) ?: false }

        setupStatusTabs(view)
        loadExpenses()
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) loadExpenses()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadExpenses(forceSkeleton: Boolean = false) {
        showLoading(forceSkeleton)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val allTransactions = DeclareDatabase.transactionsTable.select {
                    filter { eq("group_id", groupId) }
                    order("created_at", Order.DESCENDING)
                    limit(100)
                }.decodeList<TransactionFull>()

                val txIds = allTransactions.mapNotNull { it.id }
                if (txIds.isEmpty()) {
                    withContext(Dispatchers.Main) { 
                        fullTransactions = emptyList()
                        transactionList.clear()
                        adapter.notifyDataSetChanged()
                        showEmpty() 
                    }
                    return@launch
                }

                val allPayors = DeclareDatabase.transactionPayorsTable.select {
                    filter { isIn("transaction_id", txIds) }
                }.decodeList<TransactionPayorTable>()

                val allSplits = DeclareDatabase.transactionSplitsTable.select {
                    filter { isIn("transaction_id", txIds) }
                }.decodeList<TransactionSplitTable>()

                val allItems = DeclareDatabase.transactionItemsTable.select {
                    filter { isIn("transaction_id", txIds) }
                }.decodeList<TransactionItemFull>()

                val allUserIds = (allPayors.map { it.userId } + allSplits.map { it.userId }).distinct()
                val usersById: Map<Long, String> = if (allUserIds.isNotEmpty()) {
                    DeclareDatabase.usersTable.select {
                        filter { isIn("user_id", allUserIds) }
                    }.decodeList<User>().associate { it.id!! to (it.username ?: "Unknown") }
                } else emptyMap()

                val payorsByTx = allPayors.groupBy { it.transactionId }
                val splitsByTx = allSplits.groupBy { it.transactionId }
                val itemsByTx  = allItems.groupBy { it.transactionId }

                val result = ArrayList<RecentTransaction>()

                for (tx in allTransactions) {
                    val txId = tx.id ?: continue
                    val payors = payorsByTx[txId] ?: emptyList()
                    val splits = splitsByTx[txId] ?: emptyList()
                    val items  = itemsByTx[txId]  ?: emptyList()

                    val timestamp = parseCreatedAt(tx.createdAt)
                    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
                    val monthName = cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())
                    val year = cal.get(Calendar.YEAR).toString()
                    val day = cal.get(Calendar.DAY_OF_MONTH).toString()
                    val timeKey = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(cal.time)

                    val contributorIds = (payors.map { it.userId } + splits.map { it.userId }).distinct()
                    val payorNames = contributorIds.map { usersById[it] ?: "Unknown" }.toMutableList<String?>()
                    val payorUserIds = contributorIds.map { it.toString() }.toMutableList<String?>()
                    val amountsPaid = contributorIds.map { uid ->
                        payors.filter { it.userId == uid }.sumOf { it.currentAmountPaid } as Double?
                    }.toMutableList()

                    val individualPayment = splits.groupBy { it.userId }
                        .values.firstOrNull()?.sumOf { it.amount } ?: 0.0

                    val txStatus = computeStatus(payors, splits)

                    val itemPayorMap = items.associate { item ->
                        val itemId = item.id ?: 0L
                        val names = allPayors.filter { it.transactionItemsId == itemId }
                            .map { usersById[it.userId] ?: "Unknown" }
                            .joinToString(", ").ifEmpty { "-" }
                        itemId to names
                    }

                    val createdByName = tx.createdBy?.let { usersById[it] } ?: "Unknown"

                    val rt = RecentTransaction(
                        txId,
                        formatSmartDate(tx.createdAt),
                        tx.description,
                        tx.description,
                        CurrencyUtils.formatAmountWithCurrency(tx.totalAmount),
                        getCategoryIcon(items.maxByOrNull { it.amount }?.category),
                        "$year-$monthName-$day $timeKey",
                        timestamp,
                        payorNames,
                        payorUserIds,
                        amountsPaid,
                        individualPayment,
                        "$monthName $day, $year",
                        createdByName,
                        tx.createdBy?.toString(),
                        "$monthName-$year",
                        day,
                        timeKey
                    )
                    rt.transactionItems = items
                    rt.transactionStatus = txStatus
                    rt.itemPayorMap = itemPayorMap
                    rt.creatorNumericId = tx.createdBy
                    rt.rawPayorRows = payors
                    rt.rawSplitRows = splits
                    result.add(rt)
                }

                result.sortByDescending { it.timestamp }

                requireActivity().runOnUiThread {
                    fullTransactions = result
                    applyStatusFilter()
                    hideLoading()
                    pullToRefreshHelper?.stopRefreshing()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    hideLoading()
                    pullToRefreshHelper?.stopRefreshing()
                }
            }
        }
    }

    private fun applyStatusFilter() {
        val filtered = if (selectedStatusTab == "All") {
            fullTransactions
        } else {
            fullTransactions.filter { it.transactionStatus.equals(selectedStatusTab, ignoreCase = true) }
        }

        transactionList.clear()
        transactionList.addAll(filtered)
        adapter.notifyDataSetChanged()
        context?.let { adapter.preloadAllImages(it) }
        
        if (transactionList.isEmpty()) showEmpty() else showList()
    }

    private fun formatSmartDate(createdAt: String?): String {
        if (createdAt.isNullOrBlank()) return ""
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).also {
                it.timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = parser.parse(createdAt.take(19)) ?: return ""
            SimpleDateFormat("MMM d", Locale.getDefault()).format(date).uppercase()
        } catch (_: Exception) { "" }
    }

    private fun computeStatus(payors: List<TransactionPayorTable>, splits: List<TransactionSplitTable>): String {
        if (splits.isEmpty()) return "Pending"
        val individualOwed = splits.groupBy { it.userId }.values.firstOrNull()?.sumOf { it.amount } ?: 0.0
        val paidByUser = payors.groupBy { it.userId }.mapValues { e -> e.value.sumOf { it.currentAmountPaid } }
        val allSettled = splits.map { it.userId }.distinct().all { (paidByUser[it] ?: 0.0) >= individualOwed }
        return if (allSettled) "Settled" else "Pending"
    }

    private fun parseCreatedAt(createdAt: String?): Long {
        if (createdAt == null) return 0L
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(createdAt)?.time ?: 0L
        } catch (_: Exception) { 0L }
    }

    private fun getCategoryIcon(category: String?): Int = when (category) {
        "Electricity"     -> R.drawable.lightning_bolt
        "Water"           -> R.drawable.faucet
        "Rent"            -> R.drawable.house
        "Internet"        -> R.drawable.internet
        "Online Shopping" -> R.drawable.online_shopping
        "Travel"          -> R.drawable.travel
        "Groceries"       -> R.drawable.groceries
        "Foods"           -> R.drawable.hamburger
        "House Necessity" -> R.drawable.necessities
        "Transportation"  -> R.drawable.vehicles
        else              -> R.drawable.others
    }

    private fun showEmpty() {
        view?.findViewById<RecyclerView>(R.id.rvExpenses)?.visibility = View.GONE
        view?.findViewById<View>(R.id.emptyExpenses)?.visibility = View.VISIBLE
        hideLoading()
    }

    private fun showList() {
        view?.findViewById<RecyclerView>(R.id.rvExpenses)?.visibility = View.VISIBLE
        view?.findViewById<View>(R.id.emptyExpenses)?.visibility = View.GONE
    }

    private fun showLoading(force: Boolean = false) {
        if (force || transactionList.isEmpty()) {
            rvSkeleton.visibility = View.VISIBLE
            view?.findViewById<RecyclerView>(R.id.rvExpenses)?.visibility = View.GONE
            view?.findViewById<View>(R.id.emptyExpenses)?.visibility = View.GONE
        }
        isTabClickEnabled = false
    }

    private fun hideLoading() {
        rvSkeleton.visibility = View.GONE
        isTabClickEnabled = true
        if (transactionList.isNotEmpty()) showList()
    }

    private fun setupStatusTabs(view: View) {
        val allTab = view.findViewById<TextView>(R.id.allTabTV)
        val paidTab = view.findViewById<TextView>(R.id.paidTabTV)
        val unpaidTab = view.findViewById<TextView>(R.id.unpaidTabTV)

        setStatusTabSelected(allTab, allTab, paidTab, unpaidTab)

        allTab.setOnClickListener {
            if (!isTabClickEnabled) return@setOnClickListener
            selectedStatusTab = "All"
            setStatusTabSelected(allTab, allTab, paidTab, unpaidTab)
            applyStatusFilter()
        }
        paidTab.setOnClickListener {
            if (!isTabClickEnabled) return@setOnClickListener
            selectedStatusTab = "Settled"
            setStatusTabSelected(paidTab, allTab, paidTab, unpaidTab)
            applyStatusFilter()
        }
        unpaidTab.setOnClickListener {
            if (!isTabClickEnabled) return@setOnClickListener
            selectedStatusTab = "Pending"
            setStatusTabSelected(unpaidTab, allTab, paidTab, unpaidTab)
            applyStatusFilter()
        }
    }

    private fun setStatusTabSelected(selected: TextView, vararg all: TextView) {
        all.forEach { it.setBackgroundResource(0) }
        selected.setBackgroundResource(R.drawable.spinner_border_grey)
    }
}
