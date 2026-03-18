package com.waray.spendhound

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Collections
import java.util.Comparator
import java.util.HashSet
import java.util.Locale

class AllTransactionsActivity : AppCompatActivity() {
    private var recyclerView: RecyclerView? = null
    private var adapter: RecentTransactionAdapter? = null
    private var transactionList: ArrayList<RecentTransaction?>? = null
    private var monthSpinner: Spinner? = null
    private var currentMonthTextView: TextView? = null
    private var transactionCountTextView: TextView? = null
    private var loadingProgressBar: ProgressBar? = null
    private var emptyStateLayout: LinearLayout? = null
    private var mAuth: Auth? = null
    private var currentNickname: String? = ""
    private var availableMonths: MutableList<String>? = null
    private var selectedMonth: String? = null

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
        availableMonths = ArrayList()

        initViews()
        getCurrentNickname()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.allTransactionsRecyclerView)
        monthSpinner = findViewById(R.id.monthSpinner)
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
            transactionList!!,
            OnTransactionClickListener { transaction ->
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
        val userId = mAuth?.currentUserOrNull()?.id ?: return run { loadAvailableMonths() }
        val userRef = DeclareDatabase.getDatabaseReference().child(userId)

        userRef.addListenerForSingleValueEvent(object : ValueEventListener() {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    currentNickname = snapshot.child("username").getValue(String::class.java) ?: ""
                }
                loadAvailableMonths()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("AllTransactions", "Error getting nickname: " + error.message)
                loadAvailableMonths()
            }
        })
    }

    private fun loadAvailableMonths() {
        loadingProgressBar?.visibility = View.VISIBLE

        val transRef = DeclareDatabase.getDBRefTransaction()
        transRef.addListenerForSingleValueEvent(object : ValueEventListener() {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val uniqueMonths = HashSet<String>()

                for (monthSnapshot in dataSnapshot.children) {
                    val monthYear = monthSnapshot.key
                    if (!monthYear.isNullOrEmpty()) {
                        for (daySnapshot in monthSnapshot.children) {
                            for (timeSnapshot in daySnapshot.children) {
                                val transaction = timeSnapshot.getValue(Transaction::class.java)
                                if (transaction != null && isUserInvolved(transaction, currentNickname)) {
                                    uniqueMonths.add(monthYear)
                                    break
                                }
                            }
                            if (uniqueMonths.contains(monthYear)) break
                        }
                    }
                }

                availableMonths = ArrayList(uniqueMonths)
                Collections.sort(availableMonths!!) { m1, m2 ->
                    try {
                        val sdf = SimpleDateFormat("MMMM-yyyy", Locale.getDefault())
                        return@sort sdf.parse(m2)!!.compareTo(sdf.parse(m1))
                    } catch (e: Exception) {
                        return@sort m2.compareTo(m1)
                    }
                }

                setupMonthSpinner()
                loadingProgressBar?.visibility = View.GONE
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("AllTransactions", "Error loading months: " + error.message)
                loadingProgressBar?.visibility = View.GONE
            }
        })
    }

    private fun setupMonthSpinner() {
        if (availableMonths.isNullOrEmpty()) {
            val calendar = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("MMMM-yyyy", Locale.getDefault())
            availableMonths = arrayListOf(dateFormat.format(calendar.time))
        }

        val spinnerAdapter = SpinnerItemMonths(this, availableMonths!!)
        monthSpinner?.adapter = spinnerAdapter

        val calendar = Calendar.getInstance()
        val currentMonth = SimpleDateFormat("MMMM-yyyy", Locale.getDefault()).format(calendar.time)

        var defaultPosition = 0
        for (i in availableMonths!!.indices) {
            if (availableMonths!![i] == currentMonth) {
                defaultPosition = i
                break
            }
        }

        monthSpinner?.setSelection(defaultPosition)
        selectedMonth = availableMonths!![defaultPosition]
        selectedMonth?.let {
            updateMonthDisplay(it)
            fetchTransactionsForMonth(it)
        }

        monthSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val month = availableMonths!![position]
                if (month != selectedMonth) {
                    selectedMonth = month
                    selectedMonth?.let {
                        updateMonthDisplay(it)
                        fetchTransactionsForMonth(it)
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
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

        val monthRef = DeclareDatabase.getDBRefTransaction().child(monthYear)

        monthRef.addListenerForSingleValueEvent(object : ValueEventListener() {
            @SuppressLint("NotifyDataSetChanged")
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (daySnapshot in dataSnapshot.children) {
                    val day = daySnapshot.key

                    for (timeSnapshot in daySnapshot.children) {
                        val transaction = timeSnapshot.getValue(Transaction::class.java)
                        val timeKey = timeSnapshot.key

                        if (transaction != null && isUserInvolved(transaction, currentNickname)) {
                            if (!matchesStatusFilter(transaction, selectedStatusTab)) {
                                continue
                            }

                            val parts = monthYear.split("-").toTypedArray()
                            val month = parts[0]
                            val year = if (parts.size > 1) parts[1] else ""
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
                                createdBy, createdByUid, monthYear, day!!, timeKey
                            )
                            transactionList?.add(recentTrans)
                        }
                    }
                }

                Collections.sort(transactionList!!) { t1, t2 ->
                    val dateTime1 = t1?.getSortDateTime()
                    val dateTime2 = t2?.getSortDateTime()
                    if (dateTime1 != null && dateTime2 != null) {
                        return@sort dateTime2.compareTo(dateTime1)
                    }
                    0
                }

                adapter?.notifyDataSetChanged()
                adapter?.preloadAllImages(this@AllTransactionsActivity)

                loadingProgressBar?.visibility = View.GONE

                val count = transactionList?.size ?: 0
                transactionCountTextView?.text = "$count ${if (count == 1) "transaction" else "transactions"}"

                if (transactionList.isNullOrEmpty()) {
                    emptyStateLayout?.visibility = View.VISIBLE
                    recyclerView?.visibility = View.GONE
                } else {
                    emptyStateLayout?.visibility = View.GONE
                    recyclerView?.visibility = View.VISIBLE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("AllTransactions", "Error loading transactions: " + error.message)
                loadingProgressBar?.visibility = View.GONE
            }
        })
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
