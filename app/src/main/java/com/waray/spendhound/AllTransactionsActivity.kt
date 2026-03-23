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
    private var datePickerButton: Button? = null
    private var currentMonthTextView: TextView? = null
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
        setContentView(R.layout.activity_all_transactions)

        mAuth = DeclareDatabase.auth
        
        initViews()
        getCurrentNickname()
        setupDateRangePicker()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.allTransactionsRecyclerView)
        datePickerButton = findViewById(R.id.datePickerButton)
        currentMonthTextView = findViewById(R.id.currentMonthTextView)
        transactionCountTextView = findViewById(R.id.transactionCountTextView)
        loadingProgressBar = findViewById(R.id.loadingProgressBar)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)

        allTabTV = findViewById(R.id.allTabTV)
        paidTabTV = findViewById(R.id.paidTabTV)
        unpaidTabTV = findViewById(R.id.unpaidTabTV)
        pendingTabTV = findViewById(R.id.pendingTabTV)

        setupStatusTabs()

        adapter = RecentTransactionAdapter(
            transactionList,
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
        selectedTab.setBackgroundResource(R.drawable.bg_status_tab_selected)
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
            } catch (e: Exception) {
                Log.e("AllTransactions", "Error getting nickname: " + e.message)
            }
        }
    }

    private fun setupDateRangePicker() {
        // Default to current month
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        startDate = cal.timeInMillis
        
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        endDate = cal.timeInMillis

        updateDatePickerButtonText()
        fetchTransactionsInRange(startDate, endDate)

        datePickerButton?.setOnClickListener {
            showDateRangePickerDialog()
        }
    }

    private fun showDateRangePickerDialog() {
        val builder = MaterialDatePicker.Builder.dateRangePicker()
        builder.setTitleText("Select Date Range")
        builder.setSelection(Pair(startDate, endDate))

        val picker = builder.build()
        picker.show(supportFragmentManager, "DATE_RANGE_PICKER")

        picker.addOnPositiveButtonClickListener { selection ->
            startDate = selection.first ?: startDate
            endDate = (selection.second ?: selection.first ?: endDate) + 86400000 - 1
            updateDatePickerButtonText()
            fetchTransactionsInRange(startDate, endDate)
        }
    }

    private fun updateDatePickerButtonText() {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val startStr = sdf.format(startDate)
        val endStr = sdf.format(endDate)
        val displayStr = "$startStr - $endStr"
        datePickerButton?.text = displayStr
        currentMonthTextView?.text = displayStr
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun fetchTransactionsInRange(start: Long, end: Long) {
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
                        val paymentAmountStr = CurrencyUtils.formatAmountWithCurrency(paymentAmount.toString())
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
                            iconResource, sortDateTime, payorsList, payorUserIds,
                            amountsPaidList, totalIndividualPayment, fullDateWithYear,
                            createdBy, createdByUserId, monthYear, day, timeKey
                        )
                        transactionList.add(recentTrans)
                    }
                }

                transactionList.sortWith { t1, t2 ->
                    val dateTime1 = t1.sortDateTime
                    val dateTime2 = t2.sortDateTime
                    if (dateTime1 != null && dateTime2 != null) {
                        dateTime2.compareTo(dateTime1)
                    } else 0
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
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            sdf.parse(createdAt)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
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
        val currentUserId = mAuth?.currentUserOrNull()?.id
        
        if (transaction.isUserInvolvedByUserId(currentUserId)) return true
        if (transaction.creatorId == currentUserNumericId) return true
        
        if (usernameOrUserId != null) {
            if (usernameOrUserId == transaction.usernamePost) return true
            if (usernameOrUserId == transaction.posterDisplayName) return true
            if (transaction.payorsDisplayNames?.contains(usernameOrUserId) == true) return true
        }
        return false
    }
}
