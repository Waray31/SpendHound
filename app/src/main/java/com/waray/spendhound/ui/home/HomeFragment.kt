package com.waray.spendhound.ui.home

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.waray.spendhound.BalanceHelper
import com.waray.spendhound.CurrencyUtils
import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.MainActivity
import com.waray.spendhound.R
import com.waray.spendhound.RecentTransaction
import com.waray.spendhound.RecentTransactionAdapter
import com.waray.spendhound.User
import com.waray.spendhound.databinding.FragmentHomeBinding
import com.waray.spendhound.ui.multi_transaction.TransactionFull
import com.waray.spendhound.ui.multi_transaction.TransactionItemFull
import com.waray.spendhound.ui.multi_transaction.TransactionPayorTable
import com.waray.spendhound.ui.multi_transaction.TransactionSplitTable
import com.waray.spendhound.SkeletonAdapter
import com.waray.spendhound.utils.LoadingManager
import com.waray.spendhound.utils.PullToRefreshHelper
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {
    private var binding: FragmentHomeBinding? = null
    private var mAuth: Auth? = null

    private var btnWeekly: TextView? = null
    private var btnMonthly: TextView? = null
    private var dateRangeText: TextView? = null
    private var btnPrevious: ImageButton? = null
    private var btnNext: ImageButton? = null
    private var weeklyChartContainer: LinearLayout? = null
    private var monthlyLineChart: LineChart? = null
    private var isWeeklyMode = true
    private var currentWeekStart: Calendar = Calendar.getInstance()
    private val currentMonth: Calendar = Calendar.getInstance()

    private var youOweAmountTV: TextView? = null
    private var youreOwedAmountTV: TextView? = null

    private var transactionListRecycler: RecyclerView? = null
    private var recentEmptyState: LinearLayout? = null
    private var recentTransactionList: ArrayList<RecentTransaction> = ArrayList()
    private var recentAdapter: RecentTransactionAdapter? = null

    private var loadingManager: LoadingManager? = null
    private var currentUserNumericId: Long? = null
    private var pullToRefreshHelper: PullToRefreshHelper? = null
    private var rvSkeletonHome: RecyclerView? = null

    // Cache for Analytics Data to enable instant switching
    private var cachedDailyTotals: DoubleArray? = null
    private var cachedMonthlyEntries: List<Entry>? = null
    private var cachedMonthlyLabels: List<String>? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        val view = binding!!.root

        mAuth = DeclareDatabase.auth

        btnWeekly = view.findViewById(R.id.btnWeekly)
        btnMonthly = view.findViewById(R.id.btnMonthly)
        dateRangeText = view.findViewById(R.id.dateRangeText)
        btnPrevious = view.findViewById(R.id.btnPrevious)
        btnNext = view.findViewById(R.id.btnNext)
        weeklyChartContainer = view.findViewById(R.id.weeklyChartContainer)
        monthlyLineChart = view.findViewById(R.id.monthlyLineChart)

        youOweAmountTV = view.findViewById(R.id.youOweAmount)
        youreOwedAmountTV = view.findViewById(R.id.youreOwedAmount)

        transactionListRecycler = view.findViewById(R.id.transactionListRecycler)
        recentEmptyState = view.findViewById(R.id.recentEmptyState)
        rvSkeletonHome = view.findViewById(R.id.rvSkeletonHome)
        recentAdapter = RecentTransactionAdapter(recentTransactionList) {
            refreshAllData()
        }
        transactionListRecycler?.layoutManager = LinearLayoutManager(context)
        transactionListRecycler?.adapter = recentAdapter
        rvSkeletonHome?.layoutManager = LinearLayoutManager(context)
        rvSkeletonHome?.adapter = SkeletonAdapter(R.layout.item_skeleton_transaction, 5)

        recentAdapter?.setOnTransactionClickListener(object : RecentTransactionAdapter.OnTransactionClickListener {
            override fun onTransactionClick(transaction: RecentTransaction?) {
                try {
                    (activity as? MainActivity)?.navView?.selectedItemId = R.id.navigation_transactions
                } catch (e: Exception) {
                    Log.e("HomeFragment", "Error navigating to transactions", e)
                }
            }
        })

        initializeCurrentWeekStart()
        setupToggleListeners()
        setupNavigationListeners()
        updateDateRangeDisplay()
        setTextViews()
        setupSwipeRefresh(view)
        (activity as? AppCompatActivity)?.supportActionBar?.hide()

        return view
    }

    private fun setupSwipeRefresh(view: View) {
        val scrollView = view.findViewById<androidx.core.widget.NestedScrollView>(R.id.homeNestedScrollView)
        val indicator = view.findViewById<View>(R.id.pullRefreshIndicator_home)
        pullToRefreshHelper = PullToRefreshHelper(scrollView, indicator, { refreshAllData() })
    }

    override fun onResume() {
        super.onResume()
        refreshAllData()
    }

    internal fun refreshAllData() {
        val mainActivity = activity as? MainActivity ?: return
        val authId = mAuth?.currentUserOrNull()?.id ?: return

        showLoading()
        lifecycleScope.launch {
            try {
                val user = withContext(Dispatchers.IO) {
                    DeclareDatabase.usersTable.select(Columns.list("username", "user_id")) {
                        filter { eq("auth_id", authId) }
                    }.decodeSingleOrNull<User>()
                }
                if (user != null) {
                    mainActivity.currentNickname = user.username
                    currentUserNumericId = user.id
                    mainActivity.currentUserNumericId = user.id
                }

                mainActivity.getTotalMonthSpends { currentTotal ->
                    activity?.runOnUiThread {
                        updateTotalMonthSpendsUI()
                        pullToRefreshHelper?.stopRefreshing()
                    }
                    fetchMonthChangeText(currentTotal)
                }

                mainActivity.getEverydaySpends {
                    activity?.runOnUiThread {
                        updateWeeklyChartUI()
                    }
                }

                if (!isWeeklyMode) loadMonthlyChartData()

                fetchYouOweAndOwed()
                fetchRecentTransactions()

            } catch (e: Exception) {
                Log.e("HomeFragment", "Error refreshing data: ${e.message}")
                hideLoading()
            }
        }
    }

    private fun fetchYouOweAndOwed() {
        val userId = currentUserNumericId ?: return
        lifecycleScope.launch {
            try {
                val balance = withContext(Dispatchers.IO) {
                    DeclareDatabase.userBalanceTable.select {
                        filter { eq("user_id", userId) }
                    }.decodeSingleOrNull<com.waray.spendhound.UserBalance>()
                }
                withContext(Dispatchers.Main) {
                    youOweAmountTV?.text = CurrencyUtils.formatAmountWithCurrency(balance?.unpaidTotalGroup ?: 0.0)
                    youreOwedAmountTV?.text = CurrencyUtils.formatAmountWithCurrency(balance?.receivableTotalGroup ?: 0.0)
                }
            } catch (e: Exception) {
                Log.e("HomeFragment", "Error fetching balance: ${e.message}")
            }
        }
    }

    private fun fetchMonthChangeText(currentTotal: Double) {
        val userId = currentUserNumericId ?: return
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val monthSdf = SimpleDateFormat("MMMM-yyyy", Locale.getDefault())

        val lastMonth = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
        val lastMonthYear = monthSdf.format(lastMonth.time)

        val lastStart = (lastMonth.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val lastEnd = (lastMonth.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
        }

        lifecycleScope.launch {
            try {
                val userSplits = withContext(Dispatchers.IO) {
                    DeclareDatabase.transactionSplitsTable.select {
                        filter { eq("user_id", userId) }
                    }.decodeList<TransactionSplitTable>()
                }
                val involvedTxIds = userSplits.mapNotNull { it.transactionId }.toSet()
                val allTransactions = if (involvedTxIds.isNotEmpty()) withContext(Dispatchers.IO) {
                    DeclareDatabase.transactionsTable.select {
                        filter { isIn("id", involvedTxIds.toList()) }
                    }.decodeList<TransactionFull>()
                } else emptyList()

                val userSplitsByTx = userSplits.groupBy { it.transactionId }

                var lastMonthTotal = 0.0
                for (tx in allTransactions) {
                    val txId = tx.id ?: continue
                    if (txId !in userSplitsByTx) continue
                    val timestamp = try { sdf.parse(tx.createdAt ?: "")?.time ?: 0L } catch (e: Exception) { 0L }
                    if (timestamp !in lastStart.timeInMillis..lastEnd.timeInMillis) continue
                    lastMonthTotal += userSplitsByTx[txId]?.sumOf { it.amount } ?: 0.0
                }

                    withContext(Dispatchers.Main) {
                        val (label, arrowColor, arrow) = when {
                            lastMonthTotal == 0.0 && currentTotal == 0.0 -> Triple("● No data yet", "#FFFFFF", "●")
                            lastMonthTotal == 0.0 -> Triple("● No data from last month", "#FFFFFF", "●")
                            currentTotal == lastMonthTotal -> Triple("● No change from last month", "#FFFFFF", "●")
                            else -> {
                                val pct = ((currentTotal - lastMonthTotal) / lastMonthTotal * 100).toInt()
                                if (currentTotal > lastMonthTotal)
                                    Triple("↑ +$pct% from last month", "#FF4444", "↑")
                                else
                                    Triple("↓ ${pct}% from last month", "#00CC66", "↓")
                            }
                        }
                        val spannable = android.text.SpannableString(label)
                        spannable.setSpan(
                            android.text.style.ForegroundColorSpan(Color.parseColor(arrowColor)),
                            0, arrow.length,
                            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        spannable.setSpan(
                            android.text.style.ForegroundColorSpan(Color.parseColor("#FFFFFF")),
                            arrow.length, label.length,
                            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        binding?.monthChangeText?.text = spannable
                        binding?.textView2?.text = "↗  THIS MONTH'S SPENDING"
                    }
            } catch (e: Exception) {
                Log.e("HomeFragment", "Error fetching month change: ${e.message}")
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun fetchRecentTransactions() {
        val userId = currentUserNumericId ?: return
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Only fetch transactions the user is involved in
                val userSplits = DeclareDatabase.transactionSplitsTable.select {
                    filter { eq("user_id", userId) }
                }.decodeList<TransactionSplitTable>()
                val userPayors = DeclareDatabase.transactionPayorsTable.select {
                    filter { eq("user_id", userId) }
                }.decodeList<TransactionPayorTable>()

                val involvedIds = (userPayors.map { it.transactionId } +
                        userSplits.map { it.transactionId }).toSet()

                if (involvedIds.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        recentEmptyState?.visibility = View.VISIBLE
                        transactionListRecycler?.visibility = View.GONE
                        hideLoading()
                    }
                    return@launch
                }

                val allTransactions = DeclareDatabase.transactionsTable.select {
                    filter { isIn("id", involvedIds.toList()) }
                    order("created_at", Order.DESCENDING)
                    limit(5)
                }.decodeList<TransactionFull>()

                val txIds = allTransactions.mapNotNull { it.id }

                val readIds = if (txIds.isNotEmpty()) {
                    DeclareDatabase.transactionReadsTable.select {
                        filter {
                            eq("user_id", userId)
                            isIn("transaction_id", txIds)
                        }
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
                val itemsByTx = allItems.groupBy { it.transactionId }

                val allUserIds = (allPayors.map { it.userId } + allSplits.map { it.userId }).distinct()
                val usersById: Map<Long, String> = if (allUserIds.isNotEmpty()) {
                    DeclareDatabase.usersTable.select {
                        filter { isIn("user_id", allUserIds) }
                    }.decodeList<User>().associate { it.id!! to (it.username ?: "Unknown") }
                } else emptyMap()

                val newList = ArrayList<RecentTransaction>()
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

                for (tx in allTransactions) {
                    val txId = tx.id ?: continue
                    if (txId !in involvedIds) continue

                    val timestamp = try { sdf.parse(tx.createdAt ?: "")?.time ?: 0L } catch (e: Exception) { 0L }
                    val payors = payorsByTx[txId] ?: emptyList()
                    val splits = splitsByTx[txId] ?: emptyList()
                    val items = itemsByTx[txId] ?: emptyList()

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

                    val individualPayment = splits.groupBy { it.userId }.values.firstOrNull()?.sumOf { it.amount } ?: 0.0
                    val allMemberIds = splits.map { it.userId }.distinct()
                    val paidByUser = payors.groupBy { it.userId }.mapValues { e -> e.value.sumOf { it.currentAmountPaid } }
                    val allSettled = allMemberIds.isNotEmpty() && allMemberIds.all { (paidByUser[it] ?: 0.0) >= individualPayment }
                    val txStatus = if (allSettled) "Settled" else "Pending"

                    val itemPayorMap = items.associate { item ->
                        val itemId = item.id ?: 0L
                        val itemPayors = payors.filter { it.transactionItemsId == itemId }
                        val payorNames = itemPayors
                            .map { it.userId }
                            .mapNotNull { usersById[it] }
                            .joinToString(", ").ifEmpty { "-" }
                        itemId to payorNames
                    }

                    val rt = RecentTransaction(
                        txId, "$monthName - $day", tx.description, tx.description,
                        CurrencyUtils.formatAmountWithCurrency(tx.totalAmount),
                        getIconForType(tx.description),
                        timestamp.toString(),
                        timestamp,
                        payorNames, payorUserIds, amountsPaid, individualPayment,
                        "$monthName $day, $year", usersById[tx.createdBy] ?: "Unknown",
                        tx.createdBy?.toString(), "$monthName-$year", day, timeKey
                    )
                    rt.transactionItems = items
                    rt.transactionStatus = txStatus
                    rt.itemPayorMap = itemPayorMap
                    rt.creatorNumericId = tx.createdBy
                    rt.rawPayorRows = payors
                    rt.rawSplitRows = splits
                    rt.isUnread = txId !in readIds && tx.createdBy != userId
                    rt.groupId = tx.groupId
                    newList.add(rt)
                }

                newList.sortByDescending { it.timestamp }
                val recent = ArrayList(newList)

                withContext(Dispatchers.Main) {
                    recentTransactionList.clear()
                    recentTransactionList.addAll(recent)
                    recentAdapter?.notifyDataSetChanged()
                    context?.let { recentAdapter?.preloadAllImages(it) }
                    if (recent.isEmpty()) {
                        recentEmptyState?.visibility = View.VISIBLE
                        transactionListRecycler?.visibility = View.GONE
                    } else {
                        recentEmptyState?.visibility = View.GONE
                        transactionListRecycler?.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeFragment", "Error fetching recent transactions: ${e.message}")
            } finally {
                withContext(Dispatchers.Main) { hideLoading() }
            }
        }
    }

    private fun getIconForType(type: String?): Int = when (type) {
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

    private fun updateTotalMonthSpendsUI() {
        val mainActivity = activity as? MainActivity ?: return
        binding?.totalMonthSpends?.text = CurrencyUtils.formatAmountWithCurrency(mainActivity.totalMonthSpends)
    }

    private fun updateWeeklyChartUI(dailyTotals: DoubleArray? = null) {
        val mainActivity = activity as? MainActivity ?: return
        val b = binding ?: return
        val totals = dailyTotals ?: mainActivity.dailyTotals
        
        // Cache the data if it's the one from MainActivity
        if (dailyTotals == null) {
            cachedDailyTotals = totals.copyOf()
        }

        val maxHeightDp = 120.0
        val minHeightDp = 10.0
        val maxAmount = 2000.0

        val totalTextViews = arrayOf(
            b.totalday7, b.totalday6, b.totalday5,
            b.totalday4, b.totalday3, b.totalday2, b.totalday1
        )
        val barViews = arrayOf(
            b.day7Bar, b.day6Bar, b.day5Bar,
            b.day4Bar, b.day3Bar, b.day2Bar, b.day1Bar
        )

        for (i in 0..6) {
            val amount = totals[i]
            totalTextViews[i].text = if (amount > 0) String.format("%,d", Math.round(amount)) else "0"
            if (amount > 0) {
                totalTextViews[i].setTextColor(Color.parseColor("#FFBA08"))
                totalTextViews[i].setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                totalTextViews[i].setTextColor(Color.parseColor("#6c757d"))
                totalTextViews[i].setTypeface(null, android.graphics.Typeface.NORMAL)
            }
            val heightDp = if (amount <= 0) {
                minHeightDp
            } else {
                val scaled = (amount / maxAmount) * (maxHeightDp - minHeightDp) + minHeightDp
                scaled.coerceIn(minHeightDp, maxHeightDp)
            }
            val heightPx = (heightDp * resources.displayMetrics.density).toInt()
            val params = barViews[i].layoutParams
            params.height = heightPx
            barViews[i].layoutParams = params
            barViews[i].requestLayout()
        }
    }

    private fun setTextViews() { setTextViewsForWeek() }

    private fun getFormattedDay(calendar: Calendar): String =
        SimpleDateFormat("EEE", Locale.getDefault()).format(calendar.time)

    private fun initializeCurrentWeekStart() {
        currentWeekStart = Calendar.getInstance()
        currentWeekStart.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        currentWeekStart.set(Calendar.HOUR_OF_DAY, 0)
        currentWeekStart.set(Calendar.MINUTE, 0)
        currentWeekStart.set(Calendar.SECOND, 0)
        currentWeekStart.set(Calendar.MILLISECOND, 0)
    }

    private fun setupToggleListeners() {
        btnWeekly?.setOnClickListener {
            if (!isWeeklyMode) {
                isWeeklyMode = true
                updateToggleUI()
                updateDateRangeDisplay()
                showWeeklyChart()
                
                // If we have cached totals for this period, show them instantly
                if (cachedDailyTotals != null) {
                    updateWeeklyChartUI(cachedDailyTotals!!)
                    refreshWeeklyData(false) // silent refresh
                } else {
                    refreshWeeklyData(true) // show loading
                }
            }
        }
        btnMonthly?.setOnClickListener {
            if (isWeeklyMode) {
                isWeeklyMode = false
                updateToggleUI()
                updateDateRangeDisplay()
                showMonthlyChart()
                
                // If we have cached entries for this month, show them instantly
                if (cachedMonthlyEntries != null && cachedMonthlyLabels != null) {
                    setupLineChart(cachedMonthlyEntries!!, cachedMonthlyLabels!!)
                    loadMonthlyChartData(false) // silent refresh
                } else {
                    loadMonthlyChartData(true) // show loading
                }
            }
        }
    }

    private fun updateToggleUI() {
        if (isWeeklyMode) {
            btnWeekly?.setBackgroundResource(R.drawable.toggle_selected_background)
            btnWeekly?.setTextColor(ContextCompat.getColor(requireContext(), R.color.whitest))
            btnMonthly?.setBackgroundColor(Color.TRANSPARENT)
            btnMonthly?.setTextColor(Color.parseColor("#adb5bd"))
        } else {
            btnMonthly?.setBackgroundResource(R.drawable.toggle_selected_background)
            btnMonthly?.setTextColor(ContextCompat.getColor(requireContext(), R.color.whitest))
            btnWeekly?.setBackgroundColor(Color.TRANSPARENT)
            btnWeekly?.setTextColor(Color.parseColor("#adb5bd"))
        }
    }

    private fun setupNavigationListeners() {
        btnPrevious?.setOnClickListener {
            if (isWeeklyMode) {
                currentWeekStart.add(Calendar.WEEK_OF_YEAR, -1)
                cachedDailyTotals = null // Clear cache for new period
            } else {
                currentMonth.add(Calendar.MONTH, -1)
                cachedMonthlyEntries = null
                cachedMonthlyLabels = null
            }
            updateDateRangeDisplay()
            refreshData()
        }
        btnNext?.setOnClickListener {
            if (isWeeklyMode) {
                currentWeekStart.add(Calendar.WEEK_OF_YEAR, 1)
                cachedDailyTotals = null
            } else {
                currentMonth.add(Calendar.MONTH, 1)
                cachedMonthlyEntries = null
                cachedMonthlyLabels = null
            }
            updateDateRangeDisplay()
            refreshData()
        }
    }

    private fun updateDateRangeDisplay() {
        if (isWeeklyMode) {
            val weekEnd = currentWeekStart.clone() as Calendar
            weekEnd.add(Calendar.DAY_OF_YEAR, 6)
            val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
            val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
            dateRangeText?.text = String.format(
                "%s - %s, %s",
                dateFormat.format(currentWeekStart.time),
                dateFormat.format(weekEnd.time),
                yearFormat.format(weekEnd.time)
            )
        } else {
            dateRangeText?.text = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(currentMonth.time)
        }
    }

    private fun refreshData() {
        if (isWeeklyMode) refreshWeeklyData() else loadMonthlyChartData()
    }

    private fun refreshWeeklyData(showSkeleton: Boolean = true) {
        val mainActivity = activity as? MainActivity ?: return
        mainActivity.getEverydaySpendsForWeek(currentWeekStart) {
            activity?.runOnUiThread {
                updateWeeklyChartUI()
            }
        }
        setTextViewsForWeek()
    }

    private fun setTextViewsForWeek() {
        val b = binding ?: return
        val calendar = currentWeekStart.clone() as Calendar
        val today = Calendar.getInstance()
        val dayTextViews = arrayOf(b.day7, b.day6, b.day5, b.day4, b.day3, b.day2, b.day1)
        for (i in 0..6) {
            dayTextViews[i].text = getFormattedDay(calendar)
            if (isSameDay(calendar, today)) {
                dayTextViews[i].setTextColor(ContextCompat.getColor(requireContext(), R.color.yellow))
                dayTextViews[i].setTypeface(null, Typeface.BOLD)
            } else {
                dayTextViews[i].setTextColor(Color.WHITE)
                dayTextViews[i].setTypeface(null, Typeface.NORMAL)
            }
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean =
        cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)

    private fun showWeeklyChart() {
        weeklyChartContainer?.visibility = View.VISIBLE
        monthlyLineChart?.visibility = View.GONE
    }

    private fun showMonthlyChart() {
        weeklyChartContainer?.visibility = View.GONE
        monthlyLineChart?.visibility = View.VISIBLE
    }

    private fun loadMonthlyChartData(showSkeleton: Boolean = true) {
        fetchMonthlyChartData(showSkeleton)
    }

    private fun fetchMonthlyChartData(showSkeleton: Boolean = true) {
        val startOfMonth = currentMonth.clone() as Calendar
        startOfMonth.set(Calendar.DAY_OF_MONTH, 1)
        startOfMonth.set(Calendar.HOUR_OF_DAY, 0); startOfMonth.set(Calendar.MINUTE, 0)
        startOfMonth.set(Calendar.SECOND, 0); startOfMonth.set(Calendar.MILLISECOND, 0)

        val endOfMonth = currentMonth.clone() as Calendar
        endOfMonth.set(Calendar.DAY_OF_MONTH, endOfMonth.getActualMaximum(Calendar.DAY_OF_MONTH))
        endOfMonth.set(Calendar.HOUR_OF_DAY, 23); endOfMonth.set(Calendar.MINUTE, 59)
        endOfMonth.set(Calendar.SECOND, 59); endOfMonth.set(Calendar.MILLISECOND, 999)

        val userId = currentUserNumericId ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val allTransactions = withContext(Dispatchers.IO) {
                    DeclareDatabase.transactionsTable.select().decodeList<TransactionFull>()
                }
                val allSplits = withContext(Dispatchers.IO) {
                    DeclareDatabase.transactionSplitsTable.select().decodeList<TransactionSplitTable>()
                }

                val userSplitsByTx = allSplits
                    .filter { it.userId == userId }
                    .groupBy { it.transactionId }

                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val daysInMonth = endOfMonth.get(Calendar.DAY_OF_MONTH)
                val dailySpends = mutableMapOf<Int, Double>()

                for (tx in allTransactions) {
                    val txId = tx.id ?: continue
                    if (txId !in userSplitsByTx) continue
                    val timestamp = try { sdf.parse(tx.createdAt ?: "")?.time ?: 0L } catch (e: Exception) { 0L }
                    if (timestamp !in startOfMonth.timeInMillis..endOfMonth.timeInMillis) continue
                    val day = Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.DAY_OF_MONTH)
                    val userSplit = userSplitsByTx[txId]?.sumOf { it.amount } ?: 0.0
                    dailySpends[day] = (dailySpends[day] ?: 0.0) + userSplit
                }

                // Only include days that have data
                val daysWithData = dailySpends.entries
                    .filter { it.value > 0 }
                    .sortedBy { it.key }

                val entries = daysWithData.mapIndexed { index, (_, amount) ->
                    Entry(index.toFloat(), amount.toFloat())
                }
                val labels = daysWithData.map { it.key.toString() }

                // Cache for instant switching
                cachedMonthlyEntries = entries
                cachedMonthlyLabels = labels

                withContext(Dispatchers.Main) { 
                    setupLineChart(entries, labels) 
                }
            } catch (e: Exception) {
                Log.e("HomeFragment", "Error fetching monthly chart data: ${e.message}")
            }
        }
    }

    private fun setupLineChart(entries: List<Entry>, labels: List<String>) {
        val chart = monthlyLineChart ?: return

        if (entries.isEmpty()) {
            chart.clear()
            chart.setNoDataText("No spending data this month")
            chart.setNoDataTextColor(Color.parseColor("#adb5bd"))
            chart.invalidate()
            return
        }
        val dataSet = LineDataSet(entries, "Daily Spending")
        dataSet.color = Color.parseColor("#FFBA08")
        dataSet.valueTextColor = Color.WHITE
        dataSet.lineWidth = 2f
        dataSet.setCircleColor(Color.parseColor("#FFBA08"))
        dataSet.circleRadius = 4f
        dataSet.setDrawCircleHole(false)
        dataSet.setDrawValues(false)
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        dataSet.setDrawFilled(true)
        dataSet.fillColor = Color.parseColor("#FFBA08")
        dataSet.fillAlpha = 50

        chart.data = LineData(dataSet)
        chart.description?.isEnabled = false
        chart.setDrawGridBackground(false)
        chart.setDrawBorders(false)
        chart.legend?.isEnabled = false

        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.textColor = Color.WHITE
        xAxis.setDrawGridLines(false)
        xAxis.setDrawAxisLine(false)
        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        xAxis.granularity = 1f
        xAxis.labelCount = labels.size
        xAxis.labelRotationAngle = -45f
        xAxis.textSize = 10f

        val leftAxis = chart.axisLeft
        leftAxis.textColor = Color.parseColor("#adb5bd")
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = Color.parseColor("#3A3D4E")
        leftAxis.setDrawAxisLine(false)
        leftAxis.textSize = 10f

        chart.axisRight?.isEnabled = false
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)
        chart.animateX(500)
        chart.invalidate()
    }

    private fun showLoading() {
        if (recentTransactionList.isEmpty()) {
            rvSkeletonHome?.visibility = View.VISIBLE
            transactionListRecycler?.visibility = View.GONE
            recentEmptyState?.visibility = View.GONE
        }
    }

    private fun hideLoading() {
        rvSkeletonHome?.visibility = View.GONE
        if (recentTransactionList.isNotEmpty()) {
            transactionListRecycler?.visibility = View.VISIBLE
            recentEmptyState?.visibility = View.GONE
        } else {
            // If still empty after loading, the fetch function will handle showing the empty state
        }
    }
}
