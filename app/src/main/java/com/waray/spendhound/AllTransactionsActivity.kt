package com.waray.spendhound

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.MaterialDatePicker
import androidx.core.util.Pair
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AllTransactionsActivity : AppCompatActivity() {

    private var recyclerView: RecyclerView? = null
    private var adapter: RecentTransactionAdapter? = null
    private var transactionList: ArrayList<RecentTransaction> = ArrayList()
    private var dateRangeSpinner: android.widget.Spinner? = null
    private var transactionCountTextView: TextView? = null
    private var loadingProgressBar: ProgressBar? = null
    private var emptyStateLayout: LinearLayout? = null
    private var mAuth: Auth? = null
    private var currentNickname: String? = ""
    private var currentUserNumericId: Long? = null
    
    private var startDate: Long = 0L
    private var endDate: Long = 0L

    // Status Tabs
    private var allTabTV: TextView? = null
    private var paidTabTV: TextView? = null
    private var unpaidTabTV: TextView? = null
    private var pendingTabTV: TextView? = null
    private var selectedStatusTab = "All"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_transactions)

        mAuth = DeclareDatabase.auth
        
        initViews()
        getCurrentNickname()
        setupDateRangeSpinner()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.allTransactionsRecyclerView)
        dateRangeSpinner = findViewById(R.id.dateRangeSpinner)
        transactionCountTextView = findViewById(R.id.transactionCountTextView)
        loadingProgressBar = findViewById(R.id.rvSkeletonTransactions)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)

        allTabTV = findViewById(R.id.allTabTV)
        paidTabTV = findViewById(R.id.paidTabTV)
        unpaidTabTV = findViewById(R.id.unpaidTabTV)
        pendingTabTV = findViewById(R.id.pendingTabTV)

        setupStatusTabs()

        adapter = RecentTransactionAdapter(
            transactionList,
            null,
            RecentTransactionAdapter.OnTransactionClickListener { transaction ->
                onTransactionTap(transaction)
            })
        recyclerView?.layoutManager = LinearLayoutManager(this)
        recyclerView?.adapter = adapter
    }

    private fun setupStatusTabs() {
        allTabTV?.let { setStatusTabSelected(it) }

        allTabTV?.setOnClickListener {
            selectedStatusTab = "All"
            allTabTV?.let { setStatusTabSelected(it) }
            refreshTransactions()
        }

        paidTabTV?.setOnClickListener {
            selectedStatusTab = "Paid"
            paidTabTV?.let { setStatusTabSelected(it) }
            refreshTransactions()
        }

        unpaidTabTV?.setOnClickListener {
            selectedStatusTab = "Unpaid"
            unpaidTabTV?.let { setStatusTabSelected(it) }
            refreshTransactions()
        }

        pendingTabTV?.setOnClickListener {
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
        selectedTab.setBackgroundResource(R.drawable.spinner_border_grey)
    }

    private fun refreshTransactions() {
        fetchTransactionsInRange(startDate, endDate)
    }

    private fun onTransactionTap(transaction: RecentTransaction?) {
        // Handle tap if needed
    }

    private fun getCurrentNickname() {
        val user = mAuth?.currentUserOrNull() ?: return
        val authId = user.id
        
        lifecycleScope.launch {
            try {
                val userDetails = DeclareDatabase.usersTable.select {
                    filter { eq("auth_id", authId) }
                }.decodeSingleOrNull<User>()
                
                currentNickname = userDetails?.username ?: ""
                currentUserNumericId = userDetails?.id
                
                // Refresh transactions once we have the numeric ID
                refreshTransactions()
            } catch (e: Exception) {
                Log.e("AllTransactions", "Error getting nickname: " + e.message)
            }
        }
    }

    private fun setupDateRangeSpinner() {
        val options = mutableListOf<String?>("This Month", "Last Month", "All", "Custom Date")
        val spinnerAdapter = SpinnerItemMonths(this, options)
        dateRangeSpinner?.adapter = spinnerAdapter
        setThisMonth()
        refreshTransactions()

        dateRangeSpinner?.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    0 -> { setThisMonth(); refreshTransactions() }
                    1 -> { setLastMonth(); refreshTransactions() }
                    2 -> { setAllTime(); refreshTransactions() }
                    3 -> showDateRangePickerDialog(spinnerAdapter, options)
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
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

    private fun showDateRangePickerDialog(spinnerAdapter: SpinnerItemMonths, options: MutableList<String?>) {
        val safeStart = if (startDate == 0L || startDate == Long.MAX_VALUE)
            Calendar.getInstance().also { it.set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
        else startDate
        val safeEnd = if (endDate == Long.MAX_VALUE) Calendar.getInstance().timeInMillis else endDate

        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select Date Range")
            .setSelection(Pair(safeStart, safeEnd))
            .build()
        picker.show(supportFragmentManager, "DATE_RANGE_PICKER")
        picker.addOnPositiveButtonClickListener { selection ->
            startDate = selection.first ?: safeStart
            endDate = (selection.second ?: selection.first ?: safeEnd) + 86400000 - 1
            val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
            options[3] = "${sdf.format(startDate)} - ${sdf.format(endDate)}"
            spinnerAdapter.notifyDataSetChanged()
            dateRangeSpinner?.setSelection(3)
            refreshTransactions()
        }
        picker.addOnCancelListener { dateRangeSpinner?.setSelection(0) }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun fetchTransactionsInRange(start: Long, end: Long) {
        if (currentUserNumericId == null && mAuth?.currentUserOrNull() != null) {
            // Wait for user details to load
            return
        }

        loadingProgressBar?.visibility = View.VISIBLE
        emptyStateLayout?.visibility = View.GONE
        transactionList.clear()
        adapter?.notifyDataSetChanged()

        lifecycleScope.launch {
            try {
                val transactions = DeclareDatabase.transactionsTable.select().decodeList<Transaction>()

                for (transaction in transactions) {
                    val timestamp = parseCreatedAt(transaction.createdAt)
                    
                    if (timestamp in start..end && isUserInvolved(transaction, currentNickname)) {
                        if (!matchesStatusFilter(transaction, selectedStatusTab)) {
                            continue
                        }

                        val cal = Calendar.getInstance()
                        cal.timeInMillis = timestamp
                        val monthName = cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())
                        val year = cal.get(Calendar.YEAR).toString()
                        val day = cal.get(Calendar.DAY_OF_MONTH).toString()
                        val monthYear = "$monthName-$year"

                        val displayDate = "$monthName - $day"
                        val fullDateWithYear = "$monthName $day, $year"
                        val timeKey = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(cal.time)
                        val sortDateTime = "$year-$monthName-$day $timeKey"

                        val transactionType = transaction.transactionType
                        val details = transaction.transactionDetail
                        val paymentAmount = transaction.paymentAmount
                        val paymentAmountStr = CurrencyUtils.formatAmountWithCurrency(paymentAmount)
                        val iconResource = getIconForTransactionType(transactionType)

                        val payorsList = (transaction.payorsDisplayNames ?: transaction.contributors)?.map { it as String? }?.toMutableList()
                        val payorUserIds = transaction.contributors?.map { it as String? }?.toMutableList()
                        val amountsPaidList = transaction.amountPaidList?.map { it as Double? }?.toMutableList()
                        val totalIndividualPayment = transaction.individualPayment

                        var createdBy = transaction.posterDisplayName
                        if (createdBy.isNullOrEmpty()) {
                            createdBy = transaction.usernamePost
                        }
                        val createdByUserId = transaction.usernamePost

                        val recentTrans = RecentTransaction(
                            transaction.id,
                            displayDate, transactionType, details, paymentAmountStr,
                            iconResource, sortDateTime, timestamp, payorsList, payorUserIds,
                            amountsPaidList, totalIndividualPayment, fullDateWithYear,
                            createdBy, createdByUserId, monthYear, day, timeKey
                        )
                        transactionList.add(recentTrans)
                    }
                }

                transactionList.sortWith { t1, t2 ->
                    t2.timestamp.compareTo(t1.timestamp)
                }

                adapter?.notifyDataSetChanged()
                adapter?.preloadAllImages(this@AllTransactionsActivity)

                val count = transactionList.size
                transactionCountTextView?.text = "$count ${if (count == 1) "transaction" else "transactions"}"

                if (transactionList.isEmpty()) {
                    emptyStateLayout?.visibility = View.VISIBLE
                    recyclerView?.visibility = View.GONE
                } else {
                    emptyStateLayout?.visibility = View.GONE
                    recyclerView?.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Log.e("AllTransactions", "Error loading transactions: " + e.message)
            } finally {
                loadingProgressBar?.visibility = View.GONE
            }
        }
    }

    private fun parseCreatedAt(createdAt: String?): Long {
        if (createdAt == null) return 0L
        val formats = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.getDefault())
                val date = sdf.parse(createdAt)
                if (date != null) return date.time
            } catch (e: Exception) {
                // Try next format
            }
        }
        return 0L
    }

    private fun matchesStatusFilter(
        transaction: Transaction,
        statusFilter: String?
    ): Boolean {
        if ("All".equals(statusFilter, ignoreCase = true)) return true

        val paidAmounts = transaction.amountPaidList
        val totalToPay = transaction.individualPayment

        if (paidAmounts.isNullOrEmpty()) {
            return "Unpaid".equals(statusFilter, ignoreCase = true)
        }

        var allPaid = true
        var allUnpaid = true

        for (paid in paidAmounts) {
            if (paid < totalToPay) {
                allPaid = false
            }
            if (paid > 0) {
                allUnpaid = false
            }
        }

        val status = if (allPaid) "Paid" else if (allUnpaid) "Unpaid" else "Pending"
        return status.equals(statusFilter, ignoreCase = true)
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

    private fun isUserInvolved(
        transaction: Transaction?,
        usernameOrUserId: String?
    ): Boolean {
        if (transaction == null) return false
        
        // 1. Check by numeric ID (Best for consistency with storage/other models)
        if (currentUserNumericId != null) {
            if (transaction.creatorId == currentUserNumericId) return true
            if (transaction.contributors?.contains(currentUserNumericId.toString()) == true) return true
        }
        
        // 2. Check by Auth UID (Fallback for legacy data)
        val currentAuthId = mAuth?.currentUserOrNull()?.id
        if (currentAuthId != null) {
            if (transaction.usernamePost == currentAuthId) return true
            if (transaction.contributors?.contains(currentAuthId) == true) return true
        }
        
        // 3. Check by Nickname (Fallback for names stored in contributors)
        if (usernameOrUserId != null) {
            if (usernameOrUserId == transaction.posterDisplayName) return true
            if (transaction.payorsDisplayNames?.contains(usernameOrUserId) == true) return true
            if (transaction.contributors?.contains(usernameOrUserId) == true) return true
        }

        return false
    }
}
