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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.waray.spendhound.BorrowNowTransaction
import com.waray.spendhound.CurrencyUtils
import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.MainActivity
import com.waray.spendhound.R
import com.waray.spendhound.RecentTransaction
import com.waray.spendhound.RecentTransactionAdapter
import com.waray.spendhound.Transaction
import com.waray.spendhound.User
import com.waray.spendhound.databinding.FragmentHomeBinding
import com.waray.spendhound.ui.multi_transaction.TransactionFull
import com.waray.spendhound.ui.multi_transaction.TransactionItemFull
import com.waray.spendhound.ui.multi_transaction.TransactionPayorTable
import com.waray.spendhound.ui.multi_transaction.TransactionSplitTable
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {
    private var day7TextView: TextView? = null
    private var day6TextView: TextView? = null
    private var day5TextView: TextView? = null
    private var day4TextView: TextView? = null
    private var day3TextView: TextView? = null
    private var day2TextView: TextView? = null
    private var day1TextView: TextView? = null
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
    private var recentTransactionList: ArrayList<RecentTransaction> = ArrayList()
    private var recentAdapter: RecentTransactionAdapter? = null

    private var loadingOverlay_home: View? = null
    private var pendingLoads = 0

    private var currentUserNumericId: Long? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        val view = binding!!.root

        loadingOverlay_home = view.findViewById(R.id.loadingOverlay_home)

        day7TextView = view.findViewById(R.id.day7)
        day6TextView = view.findViewById(R.id.day6)
        day5TextView = view.findViewById(R.id.day5)
        day4TextView = view.findViewById(R.id.day4)
        day3TextView = view.findViewById(R.id.day3)
        day2TextView = view.findViewById(R.id.day2)
        day1TextView = view.findViewById(R.id.day1)

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
        recentAdapter = RecentTransactionAdapter(recentTransactionList) { transaction ->
            if (transaction?.isExpanded == true) {
                (activity as? MainActivity)?.hideNavigation()
            } else {
                (activity as? MainActivity)?.unhideNavigation()
            }
        }
        transactionListRecycler?.layoutManager = LinearLayoutManager(context)
        transactionListRecycler?.adapter = recentAdapter

        initializeCurrentWeekStart()
        setupToggleListeners()
        setupNavigationListeners()
        updateDateRangeDisplay()
        setTextViews()
        (activity as? AppCompatActivity)?.supportActionBar?.hide()

        return view
    }

    override fun onResume() {
        super.onResume()
        refreshAllData()
    }

    private fun refreshAllData() {
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
                }

                mainActivity.getTotalMonthSpends {
                    activity?.runOnUiThread {
                        updateTotalMonthSpendsUI()
                        hideLoading()
                    }
                }

                mainActivity.getEverydaySpends {
                    activity?.runOnUiThread {
                        updateWeeklyChartUI()
                        hideLoading()
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
        showLoading()
        lifecycleScope.launch {
            try {
                val borrows = withContext(Dispatchers.IO) {
                    DeclareDatabase.borrowsTable.select().decodeList<BorrowNowTransaction>()
                }
                // You Owe: user is borrower, status not Paid(3)/Declined(4)/Removed(6)
                val youOwe = borrows
                    .filter { it.borrowerId == userId && it.statusInt !in listOf(3, 4, 6) }
                    .sumOf { it.borrowedAmount ?: 0.0 }

                // You're Owed: user is lender, status not Paid(3)/Declined(4)/Removed(6)
                val youreOwed = borrows
                    .filter { it.lenderId == userId && it.statusInt !in listOf(3, 4, 6) }
                    .sumOf { it.borrowedAmount ?: 0.0 }

                withContext(Dispatchers.Main) {
                    youOweAmountTV?.text = CurrencyUtils.formatAmountWithCurrency(youOwe)
                    youreOwedAmountTV?.text = CurrencyUtils.formatAmountWithCurrency(youreOwed)
                }
            } catch (e: Exception) {
                Log.e("HomeFragment", "Error fetching borrow summary: ${e.message}")
            } finally {
                withContext(Dispatchers.Main) { hideLoading() }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun fetchRecentTransactions() {
        val userId = currentUserNumericId ?: return
        showLoading()
        lifecycleScope.launch {
            try {
                val allTransactions = withContext(Dispatchers.IO) {
                    DeclareDatabase.transactionsTable.select().decodeList<TransactionFull>()
                }
                val allPayors = withContext(Dispatchers.IO) {
                    DeclareDatabase.transactionPayorsTable.select().decodeList<TransactionPayorTable>()
                }
                val allSplits = withContext(Dispatchers.IO) {
                    DeclareDatabase.transactionSplitsTable.select().decodeList<TransactionSplitTable>()
                }
                val allItems = withContext(Dispatchers.IO) {
                    DeclareDatabase.transactionItemsTable.select().decodeList<TransactionItemFull>()
                }

                val involvedIds = (allPayors.filter { it.userId == userId }.map { it.transactionId } +
                        allSplits.filter { it.userId == userId }.map { it.transactionId }).toSet()

                val payorsByTx = allPayors.groupBy { it.transactionId }
                val splitsByTx = allSplits.groupBy { it.transactionId }
                val itemsByTx = allItems.groupBy { it.transactionId }
                val payorsByItem = allPayors.groupBy { it.transactionItemsId }

                val allUserIds = (allPayors.map { it.userId } + allSplits.map { it.userId }).toSet().toList()
                val usersById: Map<Long, String> = if (allUserIds.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        DeclareDatabase.usersTable.select {
                            filter { isIn("user_id", allUserIds) }
                        }.decodeList<User>().associate { it.id!! to (it.username ?: "Unknown") }
                    }
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
                        payors.filter { it.userId == uid }.sumOf { it.amount } as Double?
                    }.toMutableList()

                    val individualPayment = splits.groupBy { it.userId }.values.firstOrNull()?.sumOf { it.amount } ?: 0.0
                    val allSettled = payors.groupBy { it.userId }.mapValues { e -> e.value.sumOf { it.amount } }
                        .values.all { it >= individualPayment }
                    val txStatus = if (payors.isEmpty()) "Pending" else if (allSettled) "Settled" else "Pending"

                    val itemPayorMap = items.associate { item ->
                        val itemId = item.id ?: 0L
                        itemId to (payorsByItem[itemId]?.firstOrNull()?.let { usersById[it.userId] } ?: "-")
                    }

                    val rt = RecentTransaction(
                        txId, "$monthName - $day", tx.description, tx.description,
                        CurrencyUtils.formatAmountWithCurrency(tx.totalAmount),
                        getIconForType(tx.description),
                        "$year-$monthName-$day $timeKey",
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
                    newList.add(rt)
                }

                newList.sortWith { t1, t2 ->
                    val d1 = t1.sortDateTime; val d2 = t2.sortDateTime
                    if (d1 != null && d2 != null) d2.compareTo(d1) else 0
                }

                // Keep only 5 most recent
                val recent = ArrayList(newList.take(5))

                withContext(Dispatchers.Main) {
                    recentTransactionList.clear()
                    recentTransactionList.addAll(recent)
                    recentAdapter?.notifyDataSetChanged()
                    context?.let { recentAdapter?.preloadAllImages(it) }
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

    private fun updateWeeklyChartUI() {
        val mainActivity = activity as? MainActivity ?: return
        val dailyTotals = mainActivity.dailyTotals
        val maxTotal = dailyTotals.maxOrNull() ?: 1.0
        val scaleFactor = if (maxTotal > 0) 100.0 / maxTotal else 0.0

        val totalTextViews = arrayOf(
            binding?.totalday7, binding?.totalday6, binding?.totalday5,
            binding?.totalday4, binding?.totalday3, binding?.totalday2, binding?.totalday1
        )
        val barViews = arrayOf(
            binding?.day7Bar, binding?.day6Bar, binding?.day5Bar,
            binding?.day4Bar, binding?.day3Bar, binding?.day2Bar, binding?.day1Bar
        )

        for (i in 0..6) {
            val amount = dailyTotals[i]
            totalTextViews[i]?.text = if (amount > 0) CurrencyUtils.formatAmount(amount) else "0"
            barViews[i]?.let {
                val params = it.layoutParams
                val heightInDp = (amount * scaleFactor).coerceAtLeast(10.0).toInt()
                params.height = (heightInDp * resources.displayMetrics.density).toInt()
                it.layoutParams = params
            }
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
                refreshWeeklyData()
            }
        }
        btnMonthly?.setOnClickListener {
            if (isWeeklyMode) {
                isWeeklyMode = false
                updateToggleUI()
                updateDateRangeDisplay()
                showMonthlyChart()
                loadMonthlyChartData()
            }
        }
    }

    private fun updateToggleUI() {
        if (isWeeklyMode) {
            btnWeekly?.setBackgroundResource(R.drawable.toggle_selected_background)
            btnWeekly?.setTextColor(ContextCompat.getColor(requireContext(), R.color.darkBlue))
            btnMonthly?.setBackgroundColor(Color.TRANSPARENT)
            btnMonthly?.setTextColor(Color.parseColor("#adb5bd"))
        } else {
            btnMonthly?.setBackgroundResource(R.drawable.toggle_selected_background)
            btnMonthly?.setTextColor(ContextCompat.getColor(requireContext(), R.color.darkBlue))
            btnWeekly?.setBackgroundColor(Color.TRANSPARENT)
            btnWeekly?.setTextColor(Color.parseColor("#adb5bd"))
        }
    }

    private fun setupNavigationListeners() {
        btnPrevious?.setOnClickListener {
            if (isWeeklyMode) currentWeekStart.add(Calendar.WEEK_OF_YEAR, -1)
            else currentMonth.add(Calendar.MONTH, -1)
            updateDateRangeDisplay()
            refreshData()
        }
        btnNext?.setOnClickListener {
            if (isWeeklyMode) currentWeekStart.add(Calendar.WEEK_OF_YEAR, 1)
            else currentMonth.add(Calendar.MONTH, 1)
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

    private fun refreshWeeklyData() {
        val mainActivity = activity as? MainActivity ?: return
        showLoading()
        mainActivity.getEverydaySpendsForWeek(currentWeekStart) {
            activity?.runOnUiThread {
                updateWeeklyChartUI()
                hideLoading()
            }
        }
        setTextViewsForWeek()
    }

    private fun setTextViewsForWeek() {
        val calendar = currentWeekStart.clone() as Calendar
        val today = Calendar.getInstance()
        val dayTextViews = arrayOf(day7TextView, day6TextView, day5TextView, day4TextView, day3TextView, day2TextView, day1TextView)
        for (i in 0..6) {
            dayTextViews[i]?.let {
                it.text = getFormattedDay(calendar)
                if (isSameDay(calendar, today)) {
                    it.setTextColor(ContextCompat.getColor(requireContext(), R.color.yellow))
                    it.setTypeface(null, Typeface.BOLD)
                } else {
                    it.setTextColor(Color.WHITE)
                    it.setTypeface(null, Typeface.NORMAL)
                }
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

    private fun loadMonthlyChartData() {
        val mainActivity = activity as? MainActivity ?: return
        showLoading()
        val username = mainActivity.currentNickname
        if (username.isNullOrEmpty()) {
            mainActivity.getCurrentNickname { nickname -> fetchMonthlyChartData(nickname) }
        } else {
            fetchMonthlyChartData(username)
        }
    }

    private fun fetchMonthlyChartData(username: String?) {
        val startOfMonth = currentMonth.clone() as Calendar
        startOfMonth.set(Calendar.DAY_OF_MONTH, 1)
        startOfMonth.set(Calendar.HOUR_OF_DAY, 0); startOfMonth.set(Calendar.MINUTE, 0)
        startOfMonth.set(Calendar.SECOND, 0); startOfMonth.set(Calendar.MILLISECOND, 0)

        val endOfMonth = currentMonth.clone() as Calendar
        endOfMonth.set(Calendar.DAY_OF_MONTH, endOfMonth.getActualMaximum(Calendar.DAY_OF_MONTH))
        endOfMonth.set(Calendar.HOUR_OF_DAY, 23); endOfMonth.set(Calendar.MINUTE, 59)
        endOfMonth.set(Calendar.SECOND, 59); endOfMonth.set(Calendar.MILLISECOND, 999)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val transactions = DeclareDatabase.client.from("transactions").select {
                    filter {
                        gte("timestamp", startOfMonth.timeInMillis)
                        lte("timestamp", endOfMonth.timeInMillis)
                    }
                }.decodeList<Transaction>()

                val daysInMonth = endOfMonth.get(Calendar.DAY_OF_MONTH)
                val dailySpends = mutableMapOf<Int, Double>()
                val labels = mutableListOf<String>()
                for (day in 1..daysInMonth) { dailySpends[day] = 0.0; labels.add(day.toString()) }

                val mainActivity = activity as? MainActivity
                for (transaction in transactions) {
                    if (mainActivity?.isUserInvolved(transaction, username) == true) {
                        val d = Calendar.getInstance().apply { timeInMillis = transaction.timestamp }.get(Calendar.DAY_OF_MONTH)
                        dailySpends[d] = (dailySpends[d] ?: 0.0) + transaction.paymentAmount
                    }
                }

                val entries = dailySpends.entries.sortedBy { it.key }.map { (day, amount) ->
                    Entry((day - 1).toFloat(), amount.toFloat())
                }
                withContext(Dispatchers.Main) { setupLineChart(entries, labels) }
            } catch (e: Exception) {
                Log.e("HomeFragment", "Error fetching monthly chart data: ${e.message}")
            } finally {
                withContext(Dispatchers.Main) { hideLoading() }
            }
        }
    }

    private fun setupLineChart(entries: List<Entry>, labels: List<String>) {
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

        monthlyLineChart?.data = LineData(dataSet)
        monthlyLineChart?.description?.isEnabled = false
        monthlyLineChart?.setDrawGridBackground(false)
        monthlyLineChart?.setDrawBorders(false)
        monthlyLineChart?.legend?.isEnabled = false

        val xAxis = monthlyLineChart!!.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.textColor = Color.WHITE
        xAxis.setDrawGridLines(false)
        xAxis.setDrawAxisLine(false)
        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        xAxis.granularity = 1f
        xAxis.labelRotationAngle = -45f
        xAxis.textSize = 10f

        val leftAxis = monthlyLineChart!!.axisLeft
        leftAxis.textColor = Color.parseColor("#adb5bd")
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = Color.parseColor("#3A3D4E")
        leftAxis.setDrawAxisLine(false)
        leftAxis.textSize = 10f

        monthlyLineChart?.axisRight?.isEnabled = false
        monthlyLineChart?.setTouchEnabled(true)
        monthlyLineChart?.isDragEnabled = true
        monthlyLineChart?.setScaleEnabled(true)
        monthlyLineChart?.setPinchZoom(true)
        monthlyLineChart?.animateX(500)
        monthlyLineChart?.invalidate()
    }

    private fun showLoading() {
        pendingLoads++
        loadingOverlay_home?.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        pendingLoads = Math.max(0, pendingLoads - 1)
        if (pendingLoads == 0) loadingOverlay_home?.visibility = View.GONE
    }
}
