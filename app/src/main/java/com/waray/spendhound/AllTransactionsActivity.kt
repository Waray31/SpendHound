package com.waray.spendhound

import android.annotation.SuppressLint
import android.app.DatePickerDialog
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
    private var selectedMonth: String? = null
    private val selectedCalendar: Calendar = Calendar.getInstance()

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
        setupDatePicker()
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
        selectedMonth?.let { fetchTransactionsForMonth(it) }
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

    private fun setupDatePicker() {
        val year = selectedCalendar.get(Calendar.YEAR)
        val month = selectedCalendar.get(Calendar.MONTH)
        selectedMonth = formatMonthYear(year, month)
        
        updateDatePickerButtonText()
        selectedMonth?.let { 
            updateMonthDisplay(it)
            fetchTransactionsForMonth(it) 
        }

        datePickerButton?.setOnClickListener {
            showDatePickerDialog()
        }
    }

    private fun showDatePickerDialog() {
        val year = selectedCalendar.get(Calendar.YEAR)
        val month = selectedCalendar.get(Calendar.MONTH)
        val day = selectedCalendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            selectedCalendar.set(selectedYear, selectedMonth, selectedDay)
            val monthYear = formatMonthYear(selectedYear, selectedMonth)
            this.selectedMonth = monthYear
            updateDatePickerButtonText()
            updateMonthDisplay(monthYear)
            fetchTransactionsForMonth(monthYear)
        }, year, month, day).show()
    }

    private fun formatMonthYear(year: Int, month: Int): String {
        val cal = Calendar.getInstance()
        cal.set(year, month, 1)
        val sdf = SimpleDateFormat("MMMM-yyyy", Locale.getDefault())
        return sdf.format(cal.time)
    }

    private fun updateDatePickerButtonText() {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        datePickerButton?.text = sdf.format(selectedCalendar.time)
    }

    private fun updateMonthDisplay(monthYear: String) {
        val displayMonth = monthYear.replace("-", " ")
        currentMonthTextView?.text = displayMonth
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun fetchTransactionsForMonth(monthYear: String) {
        loadingProgressBar?.visibility = View.VISIBLE
        emptyStateLayout?.visibility = View.GONE
        transactionList.clear()
        adapter?.notifyDataSetChanged()

        lifecycleScope.launch {
            try {
                // Fetch all and filter locally because monthYear is @Transient
                val transactions = DeclareDatabase.transactionsTable.select().decodeList<Transaction>()

                for (transaction in transactions) {
                    if (isUserInvolved(transaction, currentNickname)) {
                        if (!matchesStatusFilter(transaction, selectedStatusTab)) {
                            continue
                        }

                        val parts = monthYear.split("-").toTypedArray()
                        val month = parts[0]
                        val year = if (parts.size > 1) parts[1] else ""
                        val day = transaction.day ?: ""
                        val timeKey = transaction.timeKey ?: ""

                        val displayDate = "$month - $day"
                        val fullDateWithYear = "$month $day, $year"
                        val sortDateTime = "$year-$month-$day $timeKey"

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
