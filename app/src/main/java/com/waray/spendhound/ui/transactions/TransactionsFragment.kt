package com.waray.spendhound.ui.transactions

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
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
import com.waray.spendhound.ui.multi_transaction.TransactionItemFull
import com.waray.spendhound.ui.multi_transaction.TransactionPayorTable
import com.waray.spendhound.ui.multi_transaction.TransactionSplitTable
import io.github.jan.supabase.gotrue.Auth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

class TransactionsFragment : Fragment() {
    private var recyclerView: RecyclerView? = null
    private var adapter: RecentTransactionAdapter? = null
    private var transactionList: ArrayList<RecentTransaction> = ArrayList()
    private var dateRangeSpinner: Spinner? = null
    private var groupSpinner: Spinner? = null
    private var currentMonthTextView: TextView? = null
    private var transactionCountTextView: TextView? = null
    private var loadingProgressBar: ProgressBar? = null
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

    private var pendingLoads = 0
    private var isTabClickEnabled = true
    private var swipeRefreshLayout: SwipeRefreshLayout? = null

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
        loadingProgressBar = root.findViewById(R.id.loadingProgressBar)
        emptyStateLayout = root.findViewById(R.id.emptyStateLayout)

        allTabTV = root.findViewById(R.id.allTabTV)
        paidTabTV = root.findViewById(R.id.paidTabTV)
        unpaidTabTV = root.findViewById(R.id.unpaidTabTV)
        pendingTabTV = root.findViewById(R.id.pendingTabTV)

        setupStatusTabs()

        swipeRefreshLayout = root.findViewById(R.id.swipeRefreshLayout_transactions)
        swipeRefreshLayout?.setOnRefreshListener {
            refreshTransactions()
            swipeRefreshLayout?.isRefreshing = false
        }

        adapter = RecentTransactionAdapter(transactionList, { refreshTransactions() }, null)
        recyclerView?.layoutManager = LinearLayoutManager(context)
        recyclerView?.adapter = adapter
    }

    private fun setupStatusTabs() {
        allTabTV?.let { setStatusTabSelected(it) }

        allTabTV?.setOnClickListener {
            if (!isTabClickEnabled) return@setOnClickListener
            selectedStatusTab = "All"
            allTabTV?.let { setStatusTabSelected(it) }
            refreshTransactions()
        }
        paidTabTV?.setOnClickListener {
            if (!isTabClickEnabled) return@setOnClickListener
            selectedStatusTab = "Settled"
            paidTabTV?.let { setStatusTabSelected(it) }
            refreshTransactions()
        }
        unpaidTabTV?.setOnClickListener {
            if (!isTabClickEnabled) return@setOnClickListener
            selectedStatusTab = "Pending"
            unpaidTabTV?.let { setStatusTabSelected(it) }
            refreshTransactions()
        }
        pendingTabTV?.setOnClickListener {
            if (!isTabClickEnabled) return@setOnClickListener
            selectedStatusTab = "Pending"
            pendingTabTV?.let { setStatusTabSelected(it) }
            refreshTransactions()
        }
    }

    private fun setStatusTabSelected(selectedTab: TextView) {
        allTabTV?.setBackgroundResource(0)
        paidTabTV?.setBackgroundResource(0)
        unpaidTabTV?.setBackgroundResource(0)
        pendingTabTV?.setBackgroundResource(0)
        selectedTab.setBackgroundResource(R.drawable.bg_status_tab_selected)
    }

    internal fun refreshTransactions() {
        fetchTransactionsInRange(startDate, endDate)
    }

    private fun getCurrentUserAndGroups() {
        val authUserId = mAuth?.currentUserOrNull()?.id ?: return
        showLoading()
        lifecycleScope.launch {
            try {
                val user = DeclareDatabase.usersTable.select {
                    filter { eq("auth_id", authUserId) }
                }.decodeSingleOrNull<User>()

                if (user != null) {
                    currentUserNumericId = user.id
                    loadUserGroups()
                    fetchTransactionsInRange(startDate, endDate)
                }
            } catch (e: Exception) {
                Log.e("TransactionsFragment", "Error getting user info", e)
            } finally {
                hideLoading()
            }
        }
    }

    private fun loadUserGroups() {
        val currentUserIdLong = currentUserNumericId ?: return
        showLoading()
        lifecycleScope.launch {
            try {
                // Get group IDs the current user belongs to via group_members table
                val myGroupIds = DeclareDatabase.groupMembersTable.select {
                    filter { eq("user_id", currentUserIdLong) }
                }.decodeList<GroupMember>().mapNotNull { it.groupId }.toSet()

                val allGroups = DeclareDatabase.groupsTable.select().decodeList<PayerGroup>()

                groupNames?.clear()
                groupIds?.clear()
                groupNames?.add("All group")
                groupIds?.add(-1L)

                allGroups.filter { it.groupId in myGroupIds }.forEach {
                    groupNames?.add(it.groupName ?: "")
                    groupIds?.add(it.groupId)
                }
                groupAdapter?.notifyDataSetChanged()
            } catch (e: Exception) {
                Log.e("TransactionsFragment", "Error loading groups", e)
            } finally {
                hideLoading()
            }
        }
    }

    private fun setupGroupSpinner() {
        if (context == null || groupSpinner == null) return

        groupAdapter = SpinnerItemMonths(requireContext(), groupNames!!)
        groupSpinner?.adapter = groupAdapter

        groupSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedGroupId = groupIds?.get(position)
                refreshTransactions()
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
                    0 -> { customDateActive = false; setThisMonth(); updateCurrentMonthText(); refreshTransactions() }
                    1 -> { customDateActive = false; setLastMonth(); updateCurrentMonthText(); refreshTransactions() }
                    2 -> { customDateActive = false; setAllTime(); updateCurrentMonthText(); refreshTransactions() }
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

    @SuppressLint("NotifyDataSetChanged")
    private fun fetchTransactionsInRange(start: Long, end: Long) {
        val currentUserId = currentUserNumericId ?: return
        showLoading()
        emptyStateLayout?.visibility = View.GONE
        transactionList.clear()
        adapter?.notifyDataSetChanged()

        lifecycleScope.launch {
            try {
                val allTransactions = DeclareDatabase.transactionsTable.select().decodeList<TransactionFull>()
                val allPayors = DeclareDatabase.transactionPayorsTable.select().decodeList<TransactionPayorTable>()
                val allSplits = DeclareDatabase.transactionSplitsTable.select().decodeList<TransactionSplitTable>()
                val allItems = DeclareDatabase.transactionItemsTable.select().decodeList<TransactionItemFull>()

                val involvedIds = (allPayors.filter { it.userId == currentUserId }.map { it.transactionId } +
                        allSplits.filter { it.userId == currentUserId }.map { it.transactionId }).toSet()

                val payorsByTx = allPayors.groupBy { it.transactionId }
                val splitsByTx = allSplits.groupBy { it.transactionId }
                val itemsByTx  = allItems.groupBy { it.transactionId }

                val allUserIds = (allPayors.map { it.userId } + allSplits.map { it.userId }).toSet().toList()
                val usersById: Map<Long, String> = if (allUserIds.isNotEmpty()) {
                    DeclareDatabase.usersTable.select {
                        filter { isIn("user_id", allUserIds) }
                    }.decodeList<User>().associate { it.id!! to (it.username ?: "Unknown") }
                } else emptyMap()

                for (tx in allTransactions) {
                    val txId = tx.id ?: continue
                    if (txId !in involvedIds) continue

                    val timestamp = parseCreatedAt(tx.createdAt)
                    if (timestamp !in start..end) continue
                    if (selectedGroupId != -1L && tx.groupId != selectedGroupId) continue

                    val payors = payorsByTx[txId] ?: emptyList()
                    val splits = splitsByTx[txId] ?: emptyList()
                    val items  = itemsByTx[txId]  ?: emptyList()
                    if (!matchesStatusFilter(payors, splits, selectedStatusTab)) continue

                    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
                    val monthName = cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())
                    val year = cal.get(Calendar.YEAR).toString()
                    val day = cal.get(Calendar.DAY_OF_MONTH).toString()
                    val timeKey = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(cal.time)

                    // Unique contributors across all payors + splits
                    val contributorIds = (payors.map { it.userId } + splits.map { it.userId }).distinct()
                    val payorUserIds = contributorIds.map { it.toString() }.toMutableList<String?>()
                    val payorNames = contributorIds.map { usersById[it] ?: "Unknown" }.toMutableList<String?>()

                    // amountsPaid per contributor = sum of their payor amounts across all items
                    val amountsPaid = contributorIds.map { uid ->
                        payors.filter { it.userId == uid }.sumOf { it.currentAmountPaid } as Double?
                    }.toMutableList()

                    // individualPayment per contributor = sum of their split amounts across all items
                    val individualPayment = if (splits.isNotEmpty()) {
                        val splitPerUser = splits.groupBy { it.userId }
                        splitPerUser.values.firstOrNull()?.sumOf { it.amount } ?: 0.0
                    } else 0.0

                    // Compute overall status
                    val txStatus = computeStatus(payors, splits)

                    // itemPayorMap: itemId -> payor usernames from transaction_payors table
                    val itemPayorMap = items.associate { item ->
                        val itemId = item.id ?: 0L
                        val itemPayors = payors.filter { it.transactionItemsId == itemId }
                        val payorNames = itemPayors
                            .map { it.userId }
                            .mapNotNull { usersById[it] }
                            .joinToString(", ").ifEmpty { "-" }
                        itemId to payorNames
                    }

                    val createdByName = tx.createdBy?.let { usersById[it] } ?: "Unknown"

                    val rt = RecentTransaction(
                        txId,
                        "$monthName - $day",
                        tx.description,
                        tx.description,
                        CurrencyUtils.formatAmountWithCurrency(tx.totalAmount),
                        getIconForTransactionType(tx.description),
                        "$year-$monthName-$day $timeKey",
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
                    transactionList.add(rt)
                }

                transactionList.sortWith { t1, t2 ->
                    val d1 = t1.sortDateTime; val d2 = t2.sortDateTime
                    if (d1 != null && d2 != null) d2.compareTo(d1) else 0
                }

                adapter?.notifyDataSetChanged()
                context?.let { adapter?.preloadAllImages(it) }

                val count = transactionList.size
                transactionCountTextView?.text = String.format(Locale.getDefault(), "%d %s", count, if (count == 1) "transaction" else "transactions")

                if (transactionList.isEmpty()) {
                    emptyStateLayout?.visibility = View.VISIBLE
                    recyclerView?.visibility = View.GONE
                } else {
                    emptyStateLayout?.visibility = View.GONE
                    recyclerView?.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Log.e("TransactionsFragment", "Error fetching transactions", e)
            } finally {
                hideLoading()
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

    private fun showLoading() {
        pendingLoads++
        isTabClickEnabled = false
        loadingProgressBar?.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        pendingLoads = max(0, pendingLoads - 1)
        if (pendingLoads == 0) {
            isTabClickEnabled = true
            loadingProgressBar?.visibility = View.GONE
        }
    }
}
