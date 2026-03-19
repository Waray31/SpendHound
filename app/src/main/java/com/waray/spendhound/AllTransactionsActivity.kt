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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AllTransactionsActivity : AppCompatActivity() {
    private var recyclerView: RecyclerView? = null
    private var adapter: RecentTransactionAdapter? = null
    private var transactionList: ArrayList<RecentTransaction>? = null
    private var datePickerButton: Button? = null
    private var currentMonthTextView: TextView? = null
    private var transactionCountTextView: TextView? = null
    private var loadingProgressBar: ProgressBar? = null
    private var emptyStateLayout: LinearLayout? = null
    private var mAuth: Auth? = null
    private var currentNickname: String? = ""
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
        transactionList = ArrayList()

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
        val userId = mAuth?.currentUserOrNull()?.id ?: return
        
        lifecycleScope.launch {
            try {
                val user = DeclareDatabase.usersTable.select {
                    filter { eq("id", userId) }
                }.decodeSingleOrNull<User>()
                
                currentNickname = user?.username ?: ""
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
        transactionList?.clear()
        adapter?.notifyDataSetChanged()

        lifecycleScope.launch {
            try {
                val transactions = DeclareDatabase.transactionsTable.select {
                    filter {
                        eq("monthYear", monthYear)
                    }
                }.decodeList<Transaction>()

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

                        val transactionType = transaction.getTransactionType()
                        val details = transaction.getMultilineStr()
                        val paymentAmount = transaction.getPaymentAmount()
                        val paymentAmountStr = CurrencyUtils.formatAmountWithCurrency(paymentAmount)
                        val iconResource = getIconForTransactionType(transactionType)

                        var payorsList = transaction.getPayorsDisplayNames()
                        if (payorsList.isNullOrEmpty()) {
                            payorsList = transaction.getPayorsList()
                        }
                        val payorUids = transaction.getPayorsList()
                        val amountsPaidList = transaction.getAmountsPaidList()
                        val totalIndividualPayment = transaction.getTotalIndividualPayment()

                        var createdBy = transaction.getPosterDisplayName()
                        if (createdBy.isNullOrEmpty()) {
                            createdBy = transaction.getUsernamePost()
                        }
                        val createdByUid = transaction.getUsernamePost()

                        val recentTrans = RecentTransaction(
                            displayDate, transactionType, details, paymentAmountStr,
                            iconResource, sortDateTime, payorsList, payorUids,
                            amountsPaidList, totalIndividualPayment, fullDateWithYear,
                            createdBy, createdByUid, monthYear, day, timeKey
                        )
                        transactionList?.add(recentTrans)
                    }
                }

                transactionList?.sortWith { t1, t2 ->
                    val dateTime1 = t1?.getSortDateTime()
                    val dateTime2 = t2?.getSortDateTime()
                    if (dateTime1 != null && dateTime2 != null) {
                        dateTime2.compareTo(dateTime1)
                    } else 0
                }

                adapter?.notifyDataSetChanged()
                adapter?.preloadAllImages(this@AllTransactionsActivity)

                val count = transactionList?.size ?: 0
                transactionCountTextView?.text = "$count ${if (count == 1) "transaction" else "transactions"}"

                if (transactionList.isNullOrEmpty()) {
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

        val paidAmounts = transaction.getAmountsPaidList()
        val totalToPay = transaction.getTotalIndividualPayment()

        if (paidAmounts.isNullOrEmpty()) {
            return "Unpaid".equals(statusFilter, ignoreCase = true)
        }

        var allPaid = true
        var allUnpaid = true

        for (paid in paidAmounts) {
            if (paid!! < totalToPay) {
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
        usernameOrUid: String?
    ): Boolean {
        if (transaction == null || usernameOrUid.isNullOrEmpty()) return false
        val currentUid = mAuth?.currentUserOrNull()?.id
        if (transaction.isUserInvolvedByUid(currentUid)) return true
        if (usernameOrUid == transaction.getUsernamePost()) return true
        if (usernameOrUid == transaction.getPosterDisplayName()) return true
        val payorsList = transaction.getPayorsList()
        if (payorsList != null && payorsList.contains(usernameOrUid)) return true
        val payorsDisplayNames = transaction.getPayorsDisplayNames()
        if (payorsDisplayNames != null && payorsDisplayNames.contains(usernameOrUid)) return true
        return false
    }
}
