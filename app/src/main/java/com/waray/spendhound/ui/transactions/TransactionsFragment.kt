package com.waray.spendhound.ui.transactions

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.waray.spendhound.utils.PullInterceptLayout
import com.waray.spendhound.utils.PullToRefreshHelper
import com.google.android.material.datepicker.MaterialDatePicker
import com.waray.spendhound.CurrencyUtils
import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.GroupMember
import com.waray.spendhound.MainActivity
import com.waray.spendhound.PayerGroup
import com.waray.spendhound.R
import com.waray.spendhound.RecentTransaction
import com.waray.spendhound.RecentTransactionAdapter
import com.waray.spendhound.SpinnerItemMonths
import com.waray.spendhound.User
import com.waray.spendhound.ui.multi_transaction.TransactionFull
import io.github.jan.supabase.postgrest.query.Columns
import com.waray.spendhound.ui.multi_transaction.TransactionItemFull
import com.waray.spendhound.ui.multi_transaction.TransactionPayorTable
import com.waray.spendhound.ui.multi_transaction.TransactionSplitTable
import com.waray.spendhound.SkeletonAdapter
import com.waray.spendhound.utils.LoadingManager
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TransactionsFragment : Fragment() {
    private var recyclerView: RecyclerView? = null
    private var adapter: RecentTransactionAdapter? = null
    private var transactionList: ArrayList<RecentTransaction> = ArrayList()
    private var dateRangeSpinner: Spinner? = null
    private var groupSpinner: Spinner? = null
    private var currentMonthTextView: TextView? = null
    private var transactionCountTextView: TextView? = null
    private var loadingProgressBar: View? = null
    private var emptyStateLayout: LinearLayout? = null
    private var mAuth: Auth? = null
    private var currentUserNumericId: Long? = null

    private var startDate: Long = 0L
    private var endDate: Long = 0L

    private var groupNames: MutableList<String?>? = null
    private var groupIds: MutableList<Long?>? = null
    private var selectedGroupId: Long? = -1L
    private var groupAdapter: SpinnerItemMonths? = null

    private var allTabTV: TextView? = null
    private var paidTabTV: TextView? = null
    private var unpaidTabTV: TextView? = null
    private var pendingTabTV: TextView? = null
    private var selectedStatusTab = "All"

    private var isTabClickEnabled = true
    private var pullToRefreshHelper: PullToRefreshHelper? = null
    private var loadingManager: LoadingManager? = null
    private var rvSkeletonTransactions: RecyclerView? = null
    private var fullTransactions: List<RecentTransaction> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root: View = inflater.inflate(R.layout.fragment_transactions, container, false)

        mAuth = DeclareDatabase.auth
        transactionList = ArrayList()
        groupNames = ArrayList()
        groupIds = ArrayList()

        initViews(root)

        groupNames?.add("All group")
        groupIds?.add(-1L)
        setupGroupSpinner()

        getCurrentUserAndGroups()
        setupDateRangeSpinner()

        return root
    }

    private fun initViews(root: View) {
        recyclerView = root.findViewById(R.id.allTransactionsRecyclerView)
        dateRangeSpinner = root.findViewById(R.id.dateRangeSpinner)
        groupSpinner = root.findViewById(R.id.groupSpinner)
        currentMonthTextView = root.findViewById(R.id.currentMonthTextView)
        transactionCountTextView = root.findViewById(R.id.transactionCountTextView)
        loadingProgressBar = root.findViewById(R.id.rvSkeletonTransactions)
        emptyStateLayout = root.findViewById(R.id.emptyStateLayout)
        rvSkeletonTransactions = root.findViewById(R.id.rvSkeletonTransactions)

        allTabTV = root.findViewById(R.id.allTabTV)
        paidTabTV = root.findViewById(R.id.paidTabTV)
        unpaidTabTV = root.findViewById(R.id.unpaidTabTV)
        pendingTabTV = root.findViewById(R.id.pendingTabTV)

        setupStatusTabs()

        val scrollView = root.findViewById<androidx.core.widget.NestedScrollView>(R.id.transactionsNestedScrollView)
        val indicator = root.findViewById<View>(R.id.pullRefreshIndicator_transactions)
        val rootLayout = root as PullInterceptLayout
        pullToRefreshHelper = PullToRefreshHelper(scrollView, indicator, { refreshTransactions() }, rootLayout)
        rootLayout.onInterceptCallback = { event -> pullToRefreshHelper?.onInterceptTouch(event) ?: false }

        rvSkeletonTransactions?.layoutManager = LinearLayoutManager(context)
        rvSkeletonTransactions?.adapter = SkeletonAdapter(R.layout.item_skeleton_transaction)

        adapter = RecentTransactionAdapter(transactionList, { refreshTransactions() }) { tx ->
            if (tx == null) return@RecentTransactionAdapter
            
            android.util.Log.d("TX_DEBUG", "Transaction clicked (Global): ID=${tx.transactionId}, isUnread=${tx.isUnread}, userId=$currentUserNumericId")

            // Toggle expansion
            tx.isExpanded = !tx.isExpanded
            val pos = transactionList.indexOf(tx)

            if (tx.isUnread && currentUserNumericId != null) {
                markTransactionsRead(listOf(tx), currentUserNumericId!!)
            }

            if (pos != -1) {
                android.util.Log.d("TX_DEBUG", "Toggling expansion for index $pos")
                adapter?.notifyItemChanged(pos)
            }
        }
        recyclerView?.layoutManager = LinearLayoutManager(context)
        recyclerView?.adapter = adapter
    }

    private fun setupStatusTabs() {
        allTabTV?.let { setStatusTabSelected(it) }

        allTabTV?.setOnClickListener {
            if (!isTabClickEnabled) return@setOnClickListener
            selectedStatusTab = "All"
            allTabTV?.let { setStatusTabSelected(it) }
            applyStatusFilter()
        }
        paidTabTV?.setOnClickListener {
            if (!isTabClickEnabled) return@setOnClickListener
            selectedStatusTab = "Settled"
            paidTabTV?.let { setStatusTabSelected(it) }
            applyStatusFilter()
        }
        unpaidTabTV?.setOnClickListener {
            if (!isTabClickEnabled) return@setOnClickListener
            selectedStatusTab = "Pending"
            unpaidTabTV?.let { setStatusTabSelected(it) }
            applyStatusFilter()
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
        adapter?.notifyDataSetChanged()

        val count = transactionList.size
        transactionCountTextView?.text = String.format(Locale.getDefault(), "%d %s", count, if (count == 1) "transaction" else "transactions")
        
        if (transactionList.isEmpty()) {
            emptyStateLayout?.visibility = View.VISIBLE
            recyclerView?.visibility = View.GONE
        } else {
            emptyStateLayout?.visibility = View.GONE
            recyclerView?.visibility = View.VISIBLE
        }
    }

    private fun setStatusTabSelected(selectedTab: TextView) {
        allTabTV?.setBackgroundResource(0)
        paidTabTV?.setBackgroundResource(0)
        unpaidTabTV?.setBackgroundResource(0)
        pendingTabTV?.setBackgroundResource(0)
        selectedTab.setBackgroundResource(R.drawable.spinner_border_grey)
    }

    internal fun refreshTransactions(forceSkeleton: Boolean = false) {
        fetchTransactionsInRange(startDate, endDate, forceSkeleton)
    }

    private fun getCurrentUserAndGroups() {
        val authUserId = mAuth?.currentUserOrNull()?.id ?: return
        showLoading(true)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val user = DeclareDatabase.usersTable.select {
                    filter { eq("auth_id", authUserId) }
                }.decodeSingleOrNull<User>()

                withContext(Dispatchers.Main) {
                    if (user?.id != null) {
                        currentUserNumericId = user.id
                        loadUserGroups()
                        fetchTransactionsInRange(startDate, endDate, true)
                    } else {
                        hideLoading()
                    }
                }
            } catch (e: Exception) {
                Log.e("TransactionsFragment", "Error getting user info", e)
                withContext(Dispatchers.Main) { hideLoading() }
            }
        }
    }

    private fun loadUserGroups() {
        val currentUserIdLong = currentUserNumericId ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val myGroupIds = DeclareDatabase.groupMembersTable.select {
                    filter { eq("user_id", currentUserIdLong) }
                }.decodeList<GroupMember>().mapNotNull { it.groupId }.toSet()

                val allGroups = DeclareDatabase.groupsTable.select {
                    filter { isIn("group_id", myGroupIds.toList()) }
                }.decodeList<PayerGroup>()

                withContext(Dispatchers.Main) {
                    groupNames?.clear()
                    groupIds?.clear()
                    groupNames?.add("All group")
                    groupIds?.add(-1L)
                    allGroups.forEach {
                        groupNames?.add(it.groupName ?: "")
                        groupIds?.add(it.groupId)
                    }
                    groupAdapter?.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                Log.e("TransactionsFragment", "Error loading groups", e)
            }
        }
    }

    private fun setupGroupSpinner() {
        if (context == null || groupSpinner == null) return

        groupAdapter = SpinnerItemMonths(requireContext(), groupNames!!)
        groupSpinner?.adapter = groupAdapter

        groupSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newGroupId = groupIds?.get(position)
                if (selectedGroupId != newGroupId) {
                    selectedGroupId = newGroupId
                    refreshTransactions(true)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private var customDateActive = false

    private fun setupDateRangeSpinner() {
        val options = mutableListOf<String?>("This Month", "Last Month", "All", "Custom Date")
        val spinnerAdapter = SpinnerItemMonths(requireContext(), options)
        dateRangeSpinner?.adapter = spinnerAdapter

        setThisMonth()
        updateCurrentMonthText()

        currentMonthTextView?.setOnClickListener {
            if (customDateActive) showDateRangePickerDialog()
        }

        dateRangeSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    0 -> { customDateActive = false; setThisMonth(); updateCurrentMonthText(); refreshTransactions(true) }
                    1 -> { customDateActive = false; setLastMonth(); updateCurrentMonthText(); refreshTransactions(true) }
                    2 -> { customDateActive = false; setAllTime(); updateCurrentMonthText(); refreshTransactions(true) }
                    3 -> showDateRangePickerDialog()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

    }

    private fun setThisMonth() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        startDate = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
        endDate = cal.timeInMillis
    }

    private fun setLastMonth() {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -1)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        startDate = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
        endDate = cal.timeInMillis
    }

    private fun setAllTime() {
        startDate = 0L
        endDate = Long.MAX_VALUE
    }

    private fun showDateRangePickerDialog() {
        val safeStart = if (startDate == 0L || startDate == Long.MAX_VALUE)
            Calendar.getInstance().also { it.set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
        else startDate
        val safeEnd = if (endDate == Long.MAX_VALUE) Calendar.getInstance().timeInMillis else endDate

        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select Date Range")
            .setSelection(androidx.core.util.Pair(safeStart, safeEnd))
            .build()
        picker.show(childFragmentManager, "DATE_RANGE_PICKER")
        picker.addOnPositiveButtonClickListener { selection ->
            startDate = selection.first ?: safeStart
            val selectedEnd = selection.second ?: selection.first ?: safeEnd
            endDate = selectedEnd + 86400000 - 1
            val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
            val label = "${sdf.format(startDate)} - ${sdf.format(selectedEnd)}"
            customDateActive = true
            updateCurrentMonthText(customLabel = label)
            refreshTransactions()
        }
        picker.addOnCancelListener {
            if (!customDateActive) dateRangeSpinner?.setSelection(0)
        }
    }

    private fun updateCurrentMonthText(customLabel: String? = null) {
        val text = customLabel ?: when {
            startDate == 0L && endDate == Long.MAX_VALUE -> "All Time"
            else -> {
                val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                val start = sdf.format(startDate)
                val end = sdf.format(endDate)
                if (start == end) start else "$start - $end"
            }
        }
        currentMonthTextView?.text = text
    }

    private fun fetchTransactionsInRange(start: Long, end: Long, forceSkeleton: Boolean = false) {
        val currentUserId = currentUserNumericId ?: return
        showLoading(forceSkeleton)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val userSplits = DeclareDatabase.transactionSplitsTable.select {
                    filter { eq("user_id", currentUserId) }
                }.decodeList<TransactionSplitTable>()
                val userPayors = DeclareDatabase.transactionPayorsTable.select {
                    filter { eq("user_id", currentUserId) }
                }.decodeList<TransactionPayorTable>()

                val involvedIds = (userPayors.map { it.transactionId } +
                        userSplits.map { it.transactionId }).toSet()

                if (involvedIds.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        if (!isAdded) return@withContext
                        transactionList.clear()
                        adapter?.notifyDataSetChanged()
                        emptyStateLayout?.visibility = View.VISIBLE
                        recyclerView?.visibility = View.GONE
                        hideLoading()
                        pullToRefreshHelper?.stopRefreshing()
                    }
                    return@launch
                }

                val allTransactions = DeclareDatabase.transactionsTable.select {
                    filter { isIn("id", involvedIds.toList()) }
                    order("created_at", Order.DESCENDING)
                    limit(200)
                }.decodeList<TransactionFull>()

                val txIds = allTransactions.mapNotNull { it.id }

                val readTxIds = if (currentUserId != null) {
                    DeclareDatabase.transactionReadsTable.select(Columns.list("transaction_id")) {
                        filter { eq("user_id", currentUserId) }
                    }.decodeList<com.waray.spendhound.TransactionRead>().mapNotNull { it.transactionId }.toSet()
                } else emptySet()

                val allPayors = DeclareDatabase.transactionPayorsTable.select {
                    filter { isIn("transaction_id", txIds) }
                }.decodeList<TransactionPayorTable>()
                val allSplits = DeclareDatabase.transactionSplitsTable.select {
                    filter { isIn("transaction_id", txIds) }
                }.decodeList<TransactionSplitTable>()
                val allItems = DeclareDatabase.transactionItemsTable.select {
                    filter { isIn("transaction_id", txIds) }
                }.decodeList<TransactionItemFull>()

                val payorsByTx = allPayors.groupBy { it.transactionId }
                val splitsByTx = allSplits.groupBy { it.transactionId }
                val itemsByTx  = allItems.groupBy { it.transactionId }

                val allUserIds = (allPayors.map { it.userId } + allSplits.map { it.userId }).distinct()
                val usersById: Map<Long, String> = if (allUserIds.isNotEmpty()) {
                    DeclareDatabase.usersTable.select {
                        filter { isIn("user_id", allUserIds) }
                    }.decodeList<User>().associate { it.id!! to (it.username ?: "Unknown") }
                } else emptyMap()

                // Build result list on IO thread (pure data work)
                val result = mutableListOf<RecentTransaction>()
                for (tx in allTransactions) {
                    val txId = tx.id ?: continue
                    if (txId !in involvedIds) continue

                    val timestamp = parseCreatedAt(tx.createdAt)
                    if (timestamp !in start..end) continue
                    if (selectedGroupId != -1L && tx.groupId != selectedGroupId) continue

                    val payors = payorsByTx[txId] ?: emptyList()
                    val splits = splitsByTx[txId] ?: emptyList()
                    val items  = itemsByTx[txId]  ?: emptyList()

                    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
                    val monthName = cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())
                    val year = cal.get(Calendar.YEAR).toString()
                    val day = cal.get(Calendar.DAY_OF_MONTH).toString()
                    val timeKey = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(cal.time)

                    val contributorIds = (payors.map { it.userId } + splits.map { it.userId }).distinct()
                    val payorUserIds = contributorIds.map { it.toString() }.toMutableList<String?>()
                    val payorNames = contributorIds.map { usersById[it] ?: "Unknown" }.toMutableList<String?>()
                    val amountsPaid = contributorIds.map { uid ->
                        payors.filter { it.userId == uid }.sumOf { it.currentAmountPaid } as Double?
                    }.toMutableList()
                    val individualPayment = splits.groupBy { it.userId }.values.firstOrNull()?.sumOf { it.amount } ?: 0.0
                    val txStatus = computeStatus(payors, splits)
                    val itemPayorMap = items.associate { item ->
                        val itemId = item.id ?: 0L
                        val names = payors.filter { it.transactionItemsId == itemId }
                            .mapNotNull { usersById[it.userId] }.joinToString(", ").ifEmpty { "-" }
                        itemId to names
                    }
                    val rt = RecentTransaction(
                        txId, "$monthName - $day", tx.description, tx.description,
                        CurrencyUtils.formatAmountWithCurrency(tx.totalAmount),
                        getIconForTransactionType(tx.description),
                        "$year-$monthName-$day $timeKey", timestamp,
                        payorNames, payorUserIds, amountsPaid, individualPayment,
                        "$monthName $day, $year", tx.createdBy?.let { usersById[it] } ?: "Unknown",
                        tx.createdBy?.toString(), "$monthName-$year", day, timeKey
                    )
                    rt.transactionItems = items
                    rt.transactionStatus = txStatus
                    rt.itemPayorMap = itemPayorMap
                    rt.creatorNumericId = tx.createdBy
                    rt.rawPayorRows = payors
                    rt.rawSplitRows = splits
                    rt.isUnread = tx.groupId != null && txId !in readTxIds && tx.createdBy != currentUserId
                    rt.groupId = tx.groupId
                    result.add(rt)
                }
                result.sortWith { t1, t2 -> t2.timestamp.compareTo(t1.timestamp) }

                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    fullTransactions = result
                    applyStatusFilter()
                    hideLoading()
                    pullToRefreshHelper?.stopRefreshing()
                    
                    currentUserId?.let { uid ->
                        // Automatically mark the current viewable set as read if needed
                        // Or just rely on the click listener. The prompt implies we want 
                        // initial data if there's none.
                        // markTransactionsRead(result, uid)
                    }
                }
            } catch (e: Exception) {
                Log.e("TransactionsFragment", "Error fetching transactions", e)
                withContext(Dispatchers.Main) {
                    hideLoading()
                    pullToRefreshHelper?.stopRefreshing()
                }
            }
        }
    }

    private fun markTransactionsRead(transactions: List<RecentTransaction>, userId: Long) {
        val unreadTxIds = transactions.filter { it.isUnread }.mapNotNull { (it.transactionId to it.groupId) }
        if (unreadTxIds.isEmpty()) return
        
        android.util.Log.d("TX_DEBUG", "markTransactionsRead (Global): unreadTxIds=$unreadTxIds, userId=$userId")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.getDefault()).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                val now = sdf.format(java.util.Date())

                unreadTxIds.forEach { (txId, gid) ->
                    if (txId == null || gid == null) return@forEach
                    
                    val existing = DeclareDatabase.transactionReadsTable.select {
                        filter {
                            eq("user_id", userId)
                            eq("transaction_id", txId)
                        }
                        limit(1)
                    }.decodeSingleOrNull<com.waray.spendhound.TransactionRead>()

                    if (existing == null) {
                        DeclareDatabase.transactionReadsTable.insert(
                            com.waray.spendhound.TransactionReadInsert(
                                transactionId = txId,
                                userId = userId,
                                groupId = gid,
                                readAt = now
                            )
                        )
                    }
                }
                
                withContext(Dispatchers.Main) {
                    transactions.forEach { it.isUnread = false }
                    transactions.forEach { tx ->
                        val index = transactionList.indexOf(tx)
                        if (index != -1) {
                            adapter?.notifyItemChanged(index)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("TransactionsFragment", "Error marking transactions as read", e)
                e.printStackTrace()
            }
        }
    }

    private fun parseCreatedAt(createdAt: String?): Long {
        if (createdAt == null) return 0L
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(createdAt)?.time ?: 0L
        } catch (e: Exception) { 0L }
    }

    private fun computeStatus(
        payors: List<TransactionPayorTable>,
        splits: List<TransactionSplitTable>
    ): String {
        if (splits.isEmpty()) return "Pending"
        val individualOwed = splits.groupBy { it.userId }.values.firstOrNull()?.sumOf { it.amount } ?: 0.0
        val allMemberIds = splits.map { it.userId }.distinct()
        val paidByUser = payors.groupBy { it.userId }.mapValues { e -> e.value.sumOf { it.currentAmountPaid } }
        val allSettled = allMemberIds.all { (paidByUser[it] ?: 0.0) >= individualOwed }
        return if (allSettled) "Settled" else "Pending"
    }

    private fun matchesStatusFilter(
        payors: List<TransactionPayorTable>,
        splits: List<TransactionSplitTable>,
        statusFilter: String?
    ): Boolean {
        if ("All".equals(statusFilter, ignoreCase = true)) return true
        val computed = computeStatus(payors, splits)
        return computed.equals(statusFilter, ignoreCase = true)
    }

    private fun getIconForTransactionType(transactionType: String?): Int {
        return when (transactionType) {
            "Electricity" -> R.drawable.lightning_bolt
            "Water" -> R.drawable.faucet
            "Rent" -> R.drawable.house
            "Internet" -> R.drawable.internet
            "Online Shopping" -> R.drawable.online_shopping
            "Travel" -> R.drawable.travel
            "Groceries" -> R.drawable.groceries
            "Foods" -> R.drawable.hamburger
            "House Necessity" -> R.drawable.necessities
            "Transportation" -> R.drawable.vehicles
            else -> R.drawable.others
        }
    }

    private fun showLoading(force: Boolean = false) {
        if (force || transactionList.isEmpty()) {
            rvSkeletonTransactions?.visibility = View.VISIBLE
            recyclerView?.visibility = View.GONE
            emptyStateLayout?.visibility = View.GONE
        }
        isTabClickEnabled = false
    }

    private fun hideLoading() {
        rvSkeletonTransactions?.visibility = View.GONE
        isTabClickEnabled = true
        if (transactionList.isNotEmpty()) {
            recyclerView?.visibility = View.VISIBLE
            emptyStateLayout?.visibility = View.GONE
        }
    }
}
