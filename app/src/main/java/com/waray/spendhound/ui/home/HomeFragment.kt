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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
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
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
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
    private var netBalanceLayout: LinearLayout? = null
    private var netBalanceLabel: TextView? = null
    private var netBalanceAmount: TextView? = null
    private var netBalanceIcon: ImageView? = null

    private var transactionListRecycler: RecyclerView? = null
    private var recentEmptyState: LinearLayout? = null
    private var recentTransactionList: ArrayList<RecentTransaction> = ArrayList()
    private var recentAdapter: RecentTransactionAdapter? = null

    private var loadingManager: LoadingManager? = null
    private var currentUserNumericId: Long? = null
    private var pullToRefreshHelper: PullToRefreshHelper? = null
    private var rvSkeletonHome: RecyclerView? = null

    private var cachedDailyTotals: DoubleArray? = null
    private var cachedMonthlyEntries: List<Entry>? = null
    private var cachedMonthlyLabels: List<String>? = null
    private var hasLoadedOnce = false
    private var lastSeenUpdate: Long = 0L

    private val viewModel: HomeViewModel by activityViewModels()
    private var cardAdapter: CardStackAdapter? = null
    private var currentGroupStates: List<HomeGroupState> = emptyList()

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
        recentAdapter = RecentTransactionAdapter(recentTransactionList, {
            invalidateAndRefresh()
        }, null, null)
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

        setupViewPager()
        initializeCurrentWeekStart()
        setupToggleListeners()
        setupNavigationListeners()
        updateDateRangeDisplay()
        setTextViews()
        setupSwipeRefresh(view)
        (activity as? AppCompatActivity)?.supportActionBar?.hide()

        return view
    }

    private fun setupViewPager() {
        cardAdapter = CardStackAdapter()
        
        // Pre-populate if ViewModel already has data (for tab revisits)
        val initialStates = viewModel.groupStates.value
        if (initialStates.isNotEmpty()) {
            cardAdapter?.submitList(initialStates)
            currentGroupStates = initialStates
        }

        binding?.spendingCardViewPager?.apply {
            adapter = cardAdapter
            offscreenPageLimit = 5
            setPageTransformer(StackedPageTransformer())
            
            if (initialStates.isNotEmpty()) {
                val startPos = 1000 - (1000 % initialStates.size)
                setCurrentItem(startPos, false)
                // Force an immediate UI update for the bottom part
                updateUIForState(initialStates[0])
            }

            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    if (currentGroupStates.isNotEmpty()) {
                        val state = currentGroupStates[position % currentGroupStates.size]
                        updateUIForState(state)
                    }
                }
            })
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lastSeenUpdate = com.waray.spendhound.TransactionState.lastUpdateTimestamp
        
        // Immediate load if cached ID exists to prevent blank screen
        (activity as? MainActivity)?.currentUserNumericId?.let { cachedId ->
            if (currentUserNumericId == null) {
                currentUserNumericId = cachedId
                viewModel.load(cachedId)
            }
        }

        // Still check auth and update user info in background
        val authId = mAuth?.currentUserOrNull()?.id
        if (authId != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val user = withContext(Dispatchers.IO) {
                    DeclareDatabase.usersTable.select(Columns.list("username", "user_id")) {
                        filter { eq("auth_id", authId) }
                    }.decodeSingleOrNull<User>()
                }
                if (user != null) {
                    val isNewId = currentUserNumericId != user.id
                    currentUserNumericId = user.id
                    (activity as? MainActivity)?.currentNickname = user.username
                    (activity as? MainActivity)?.currentUserNumericId = user.id
                    if (isNewId) {
                        user.id?.let { viewModel.load(it) }
                    }
                }
            }
        }
        
        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.groupStates.collectLatest { states ->
                if (states.isEmpty()) return@collectLatest
                
                val pager = binding?.spendingCardViewPager ?: return@collectLatest
                val wasFragmentRecreated = currentGroupStates.isEmpty()
                val sizeChanged = currentGroupStates.size != states.size
                
                // If the list content is identical, skip update
                if (!wasFragmentRecreated && currentGroupStates == states) return@collectLatest
                
                if (wasFragmentRecreated || sizeChanged) {
                    currentGroupStates = states
                    cardAdapter?.submitList(states)
                    
                    pager.post {
                        val currentPos = pager.currentItem
                        val targetPos = 1000 - (1000 % states.size)
                        
                        // Only jump to center if we are at the wrong start position
                        if (wasFragmentRecreated || Math.abs(currentPos - targetPos) > states.size) {
                            pager.setCurrentItem(targetPos, false)
                        }
                        updateUIForState(states[pager.currentItem % states.size])
                        pager.requestLayout()
                        pager.invalidate()
                    }
                } else {
                    currentGroupStates = states
                    cardAdapter?.updateDataOnly(states)
                    updateUIForState(states[pager.currentItem % states.size])
                }
                
                hasLoadedOnce = true
            }
        }
    }

    private var lastObservedState: HomeGroupState? = null

    private fun updateUIForState(state: HomeGroupState) {
        // structural equality check to ensure silent update
        if (lastObservedState == state && hasLoadedOnce) return
        lastObservedState = state

        state.homeData?.let { data ->
            youOweAmountTV?.text = CurrencyUtils.formatAmountWithCurrency(data.youOweAmount)
            youreOwedAmountTV?.text = CurrencyUtils.formatAmountWithCurrency(data.youreOwedAmount)
            updateNetBalanceUI(data.netBalance)
            (activity as? MainActivity)?.totalMonthSpends = data.totalMonthSpends
        } ?: run {
            // Show placeholder/zero values if data is still loading
            youOweAmountTV?.text = CurrencyUtils.formatAmountWithCurrency(0.0)
            youreOwedAmountTV?.text = CurrencyUtils.formatAmountWithCurrency(0.0)
            updateNetBalanceUI(0.0)
        }
        
        updateRecentTransactionsUI(state.recentTransactions)
        updateWeeklyChartUI(state.weeklyTotals.toDoubleArray())
        
        if (!isWeeklyMode) fetchMonthlyChartData(state.groupId)
    }

    private fun setupSwipeRefresh(view: View) {
        val scrollView = view.findViewById<androidx.core.widget.NestedScrollView>(R.id.homeNestedScrollView)
        val indicator = view.findViewById<View>(R.id.pullRefreshIndicator_home)
        pullToRefreshHelper = PullToRefreshHelper(scrollView, indicator, { invalidateAndRefresh() })
    }

    override fun onResume() {
        super.onResume()
        
        val needsRefresh = com.waray.spendhound.TransactionState.lastUpdateTimestamp > lastSeenUpdate
        if (needsRefresh) {
            lastSeenUpdate = com.waray.spendhound.TransactionState.lastUpdateTimestamp
            currentUserNumericId?.let { viewModel.invalidate(it) }
        }
    }

    internal fun refreshAllData() {
        val userId = currentUserNumericId ?: return
        viewModel.invalidate(userId)
        pullToRefreshHelper?.stopRefreshing()
    }

    private fun invalidateAndRefresh() {
        val userId = currentUserNumericId ?: return
        viewModel.invalidate(userId)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateRecentTransactionsUI(list: List<RecentTransaction>) {
        if (list.isEmpty() && recentTransactionList.isEmpty()) {
            if (!hasLoadedOnce) showLoading()
            return
        }
        
        // Prevent refresh if list content is identical
        if (recentTransactionList == list) {
            pullToRefreshHelper?.stopRefreshing()
            return
        }

        val scrollView = binding?.homeNestedScrollView
        val scrollY = scrollView?.scrollY ?: 0
        recentTransactionList.clear()
        recentTransactionList.addAll(list)
        recentAdapter?.notifyDataSetChanged()
        context?.let { recentAdapter?.preloadAllImages(it) }
        hideLoading()
        if (list.isEmpty()) {
            recentEmptyState?.visibility = View.VISIBLE
            transactionListRecycler?.visibility = View.GONE
        } else {
            recentEmptyState?.visibility = View.GONE
            transactionListRecycler?.visibility = View.VISIBLE
        }
        hasLoadedOnce = true
        scrollView?.post { scrollView.scrollTo(0, scrollY) }
        pullToRefreshHelper?.stopRefreshing()
    }

    private fun updateNetBalanceUI(netBalance: Double) {
        if (abs(netBalance) < 0.01) {
            netBalanceLayout?.visibility = View.GONE
            return
        }

        netBalanceLayout?.visibility = View.VISIBLE
        netBalanceAmount?.text = CurrencyUtils.formatAmountWithCurrency(abs(netBalance))
        
        if (netBalance < 0) {
            netBalanceLabel?.text = getString(R.string.label_net_debt).uppercase()
            netBalanceAmount?.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange))
            netBalanceIcon?.setImageResource(R.drawable.ic_you_owe)
            netBalanceIcon?.backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.orange))
        } else {
            netBalanceLabel?.text = getString(R.string.label_net_receivable).uppercase()
            netBalanceAmount?.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
            netBalanceIcon?.setImageResource(R.drawable.ic_youre_owed)
            netBalanceIcon?.backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.green))
        }
    }

    private fun updateWeeklyChartUI(dailyTotals: DoubleArray) {
        val b = binding ?: return
        cachedDailyTotals = dailyTotals.copyOf()

        val maxHeightDp = 120.0
        val minHeightDp = 10.0
        val maxAmount = (dailyTotals.maxOrNull() ?: 2000.0).coerceAtLeast(2000.0)

        val totalTextViews = arrayOf(b.totalday7, b.totalday6, b.totalday5, b.totalday4, b.totalday3, b.totalday2, b.totalday1)
        val barViews = arrayOf(b.day7Bar, b.day6Bar, b.day5Bar, b.day4Bar, b.day3Bar, b.day2Bar, b.day1Bar)

        for (i in 0..6) {
            val amount = dailyTotals[i]
            totalTextViews[i].text = if (amount > 0) String.format("%,d", Math.round(amount)) else "0"
            if (amount > 0) {
                totalTextViews[i].setTextColor(Color.parseColor("#FFBA08"))
                totalTextViews[i].setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                totalTextViews[i].setTextColor(Color.parseColor("#6c757d"))
                totalTextViews[i].setTypeface(null, android.graphics.Typeface.NORMAL)
            }
            val heightDp = if (amount <= 0) minHeightDp
            else ((amount / maxAmount) * (maxHeightDp - minHeightDp) + minHeightDp).coerceIn(minHeightDp, maxHeightDp)
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
                val currentPos = binding?.spendingCardViewPager?.currentItem ?: 0
                if (currentPos < currentGroupStates.size) updateWeeklyChartUI(currentGroupStates[currentPos].weeklyTotals.toDoubleArray())
            }
        }
        btnMonthly?.setOnClickListener {
            if (isWeeklyMode) {
                isWeeklyMode = false
                updateToggleUI()
                updateDateRangeDisplay()
                showMonthlyChart()
                val currentPos = binding?.spendingCardViewPager?.currentItem ?: 0
                if (currentPos < currentGroupStates.size) fetchMonthlyChartData(currentGroupStates[currentPos].groupId)
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
            if (isWeeklyMode) { currentWeekStart.add(Calendar.WEEK_OF_YEAR, -1) }
            else { currentMonth.add(Calendar.MONTH, -1); cachedMonthlyEntries = null; cachedMonthlyLabels = null }
            updateDateRangeDisplay()
            refreshData()
        }
        btnNext?.setOnClickListener {
            if (isWeeklyMode) { currentWeekStart.add(Calendar.WEEK_OF_YEAR, 1) }
            else { currentMonth.add(Calendar.MONTH, 1); cachedMonthlyEntries = null; cachedMonthlyLabels = null }
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
            dateRangeText?.text = String.format("%s - %s, %s", dateFormat.format(currentWeekStart.time), dateFormat.format(weekEnd.time), yearFormat.format(weekEnd.time))
        } else {
            dateRangeText?.text = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(currentMonth.time)
        }
    }

    private fun refreshData() {
        val currentPos = binding?.spendingCardViewPager?.currentItem ?: 0
        val groupId = if (currentPos < currentGroupStates.size) currentGroupStates[currentPos].groupId else null
        if (isWeeklyMode) refreshWeeklyData(groupId) else fetchMonthlyChartData(groupId)
    }

    private fun refreshWeeklyData(groupId: Long?) {
        val userId = currentUserNumericId ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val totals = calculateWeeklyTotalsForRange(userId, groupId, currentWeekStart)
            activity?.runOnUiThread { updateWeeklyChartUI(totals) }
        }
        setTextViewsForWeek()
    }

    private suspend fun calculateWeeklyTotalsForRange(userId: Long, groupId: Long?, start: Calendar): DoubleArray = withContext(Dispatchers.IO) {
        val weekStart = start.clone() as Calendar
        val weekEnd = weekStart.clone() as Calendar
        weekEnd.add(Calendar.DAY_OF_YEAR, 6)
        weekEnd.set(Calendar.HOUR_OF_DAY, 23); weekEnd.set(Calendar.MINUTE, 59); weekEnd.set(Calendar.SECOND, 59)

        try {
            val allTransactions = DeclareDatabase.transactionsTable.select {
                filter { if (groupId != null) eq("group_id", groupId) }
            }.decodeList<TransactionFull>()
            val allSplits = DeclareDatabase.transactionSplitsTable.select {
                filter { eq("user_id", userId) }
            }.decodeList<TransactionSplitTable>()
            val userSplitsByTx = allSplits.groupBy { it.transactionId }
            val totals = DoubleArray(7) { 0.0 }
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            for (tx in allTransactions) {
                val txId = tx.id ?: continue
                if (txId !in userSplitsByTx) continue
                val timestamp = try { sdf.parse(tx.createdAt ?: "")?.time ?: 0L } catch (e: Exception) { 0L }
                if (timestamp !in weekStart.timeInMillis..weekEnd.timeInMillis) continue
                val transCal = Calendar.getInstance().apply { timeInMillis = timestamp }
                val index = transCal.get(Calendar.DAY_OF_WEEK) - 1
                totals[index] += userSplitsByTx[txId]?.sumOf { it.amount } ?: 0.0
            }
            totals
        } catch (e: Exception) { DoubleArray(7) { 0.0 } }
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

    private fun fetchMonthlyChartData(groupId: Long?) {
        val startOfMonth = currentMonth.clone() as Calendar
        startOfMonth.set(Calendar.DAY_OF_MONTH, 1)
        startOfMonth.set(Calendar.HOUR_OF_DAY, 0); startOfMonth.set(Calendar.MINUTE, 0)
        startOfMonth.set(Calendar.SECOND, 0); startOfMonth.set(Calendar.MILLISECOND, 0)

        val endOfMonth = currentMonth.clone() as Calendar
        endOfMonth.set(Calendar.DAY_OF_MONTH, endOfMonth.getActualMaximum(Calendar.DAY_OF_MONTH))
        endOfMonth.set(Calendar.HOUR_OF_DAY, 23); endOfMonth.set(Calendar.MINUTE, 59)
        endOfMonth.set(Calendar.SECOND, 59); endOfMonth.set(Calendar.MILLISECOND, 999)

        val userId = currentUserNumericId ?: return

        val lifecycleOwner = try { viewLifecycleOwner } catch (_: IllegalStateException) { return }
        lifecycleOwner.lifecycleScope.launch {
            try {
                val allTransactions = withContext(Dispatchers.IO) {
                    DeclareDatabase.transactionsTable.select {
                        filter { if (groupId != null) eq("group_id", groupId) }
                    }.decodeList<TransactionFull>()
                }
                val allSplits = withContext(Dispatchers.IO) {
                    DeclareDatabase.transactionSplitsTable.select().decodeList<TransactionSplitTable>()
                }
                val userSplitsByTx = allSplits.filter { it.userId == userId }.groupBy { it.transactionId }
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val dailySpends = mutableMapOf<Int, Double>()

                for (tx in allTransactions) {
                    val txId = tx.id ?: continue
                    if (txId !in userSplitsByTx) continue
                    val timestamp = try { sdf.parse(tx.createdAt ?: "")?.time ?: 0L } catch (e: Exception) { 0L }
                    if (timestamp !in startOfMonth.timeInMillis..endOfMonth.timeInMillis) continue
                    val day = Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.DAY_OF_MONTH)
                    dailySpends[day] = (dailySpends[day] ?: 0.0) + (userSplitsByTx[txId]?.sumOf { it.amount } ?: 0.0)
                }

                val daysWithData = dailySpends.entries.filter { it.value > 0 }.sortedBy { it.key }
                val entries = daysWithData.mapIndexed { index, (_, amount) -> Entry(index.toFloat(), amount.toFloat()) }
                val labels = daysWithData.map { it.key.toString() }
                cachedMonthlyEntries = entries
                cachedMonthlyLabels = labels
                withContext(Dispatchers.Main) { setupLineChart(entries, labels) }
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
        val dataSet = LineDataSet(entries, "Daily Spending").apply {
            color = Color.parseColor("#FFBA08")
            valueTextColor = Color.WHITE
            lineWidth = 2f
            setCircleColor(Color.parseColor("#FFBA08"))
            circleRadius = 4f
            setDrawCircleHole(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = Color.parseColor("#FFBA08")
            fillAlpha = 50
        }
        chart.data = LineData(dataSet)
        chart.description?.isEnabled = false
        chart.setDrawGridBackground(false)
        chart.setDrawBorders(false)
        chart.legend?.isEnabled = false
        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            textColor = Color.WHITE
            setDrawGridLines(false)
            setDrawAxisLine(false)
            valueFormatter = IndexAxisValueFormatter(labels)
            granularity = 1f
            labelCount = labels.size
            labelRotationAngle = -45f
            textSize = 10f
        }
        chart.axisLeft.apply {
            textColor = Color.parseColor("#adb5bd")
            setDrawGridLines(true)
            gridColor = Color.parseColor("#3A3D4E")
            setDrawAxisLine(false)
            textSize = 10f
        }
        chart.axisRight?.isEnabled = false
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)
        chart.animateX(500)
        chart.invalidate()
    }

    private fun showLoading() {
        if (!hasLoadedOnce && recentTransactionList.isEmpty()) {
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
        }
    }

    // Inner classes for ViewPager2
    private inner class CardStackAdapter : RecyclerView.Adapter<CardStackAdapter.ViewHolder>() {
        private var items: List<HomeGroupState> = emptyList()

        fun submitList(newItems: List<HomeGroupState>) {
            items = newItems
            notifyDataSetChanged()
        }

        fun updateDataOnly(newItems: List<HomeGroupState>) {
            items = newItems
            // We don't call notifyDataSetChanged here because we'll update holders directly 
            // if they are visible, or they'll be updated on rebind.
            // For ViewPager2, it's often better to notify specifically if we know what changed.
            notifyDataSetChanged() 
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_spending_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            if (items.isEmpty()) return
            holder.bind(items[position % items.size])
            
            holder.itemView.post {
                holder.itemView.requestLayout()
            }
        }

        override fun getItemCount() = if (items.isEmpty()) 0 else 2000

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val groupName: TextView = view.findViewById(R.id.groupName)
            val totalMonthSpends: TextView = view.findViewById(R.id.totalMonthSpends)
            val monthChangeText: TextView = view.findViewById(R.id.monthChangeText)

            fun bind(state: HomeGroupState) {
                groupName.text = state.groupName
                
                // Always set defaults or update based on state
                totalMonthSpends.text = CurrencyUtils.formatAmountWithCurrency(state.homeData?.totalMonthSpends ?: 0.0)
                updateMonthChangeTextInternal(
                    monthChangeText, 
                    state.homeData?.totalMonthSpends ?: 0.0, 
                    state.homeData?.lastMonthTotal ?: 0.0
                )
                
                // Ensure visibility
                monthChangeText.visibility = View.VISIBLE
            }

            private fun updateMonthChangeTextInternal(view: TextView, currentTotal: Double, lastMonthTotal: Double) {
                val (label, arrowColor, arrow) = when {
                    lastMonthTotal == 0.0 && currentTotal == 0.0 -> Triple("● No data yet", "#FFFFFF", "●")
                    lastMonthTotal == 0.0 -> Triple("● No data from last month", "#FFFFFF", "●")
                    currentTotal == lastMonthTotal -> Triple("● No change from last month", "#FFFFFF", "●")
                    else -> {
                        val diff = currentTotal - lastMonthTotal
                        val pct = (Math.abs(diff) / lastMonthTotal * 100).toInt()
                        if (diff > 0)
                            Triple("↑ +$pct% from last month", "#FF4444", "↑")
                        else
                            Triple("↓ -$pct% from last month", "#00CC66", "↓")
                    }
                }
                val spannable = android.text.SpannableString(label)
                spannable.setSpan(android.text.style.ForegroundColorSpan(Color.parseColor(arrowColor)), 0, arrow.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(android.text.style.ForegroundColorSpan(Color.parseColor("#FFFFFF")), arrow.length, label.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                view.text = spannable
            }
        }
    }

    private inner class StackedPageTransformer : ViewPager2.PageTransformer {
        override fun transformPage(page: View, position: Float) {
            val density = resources.displayMetrics.density
            val maxVisibleCards = 2f 
            
            // If width is not yet measured, we must still hide or stack pages to prevent "side display"
            val width = if (page.width > 0) page.width.toFloat() else resources.displayMetrics.widthPixels.toFloat()

            when {
                position < -1 -> { 
                    page.alpha = 0f
                }
                position <= 0 -> { 
                    page.alpha = 1f + position 
                    page.translationX = 0f
                    page.scaleX = 1f
                    page.scaleY = 1f
                    page.translationY = 0f
                    page.elevation = 10f
                }
                position <= maxVisibleCards -> { 
                    page.alpha = 1f
                    val scaleFactor = 1.0f - (0.05f * position)
                    page.scaleX = scaleFactor
                    page.scaleY = scaleFactor
                    page.elevation = 10f - position
                    page.translationX = -width * position
                    val offsetDp = 12 * position * density
                    page.translationY = -offsetDp 
                    page.translationX += offsetDp 
                }
                else -> { 
                    page.alpha = 0f
                    page.translationX = -width * position
                }
            }
        }
    }
}
