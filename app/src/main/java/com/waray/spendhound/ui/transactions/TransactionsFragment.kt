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
import com.waray.spendhound.CurrencyUtils
import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.MainActivity
import com.waray.spendhound.PayerGroup
import com.waray.spendhound.R
import com.waray.spendhound.RecentTransaction
import com.waray.spendhound.RecentTransactionAdapter
import com.waray.spendhound.SpinnerItemMonths
import com.waray.spendhound.Transaction
import com.waray.spendhound.User
import io.github.jan.supabase.gotrue.Auth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Collections
import java.util.HashSet
import java.util.Locale

class TransactionsFragment : Fragment() {
    private var recyclerView: RecyclerView? = null
    private var adapter: RecentTransactionAdapter? = null
    private var transactionList: ArrayList<RecentTransaction>? = null
    private var monthSpinner: Spinner? = null
    private var groupSpinner: Spinner? = null
    private var currentMonthTextView: TextView? = null
    private var transactionCountTextView: TextView? = null
    private var loadingProgressBar: ProgressBar? = null
    private var emptyStateLayout: LinearLayout? = null
    private var mAuth: Auth? = null
    private var currentNickname: String? = ""
    private var availableMonths: MutableList<String?>? = null
    private var selectedMonth: String? = null

    private var groupNames: MutableList<String?>? = null
    private var groupIds: MutableList<String?>? = null
    private var selectedGroupId: String? = "All"
    private var groupAdapter: SpinnerItemMonths? = null

    // Status Tabs
    private var allTabTV: TextView? = null
    private var paidTabTV: TextView? = null
    private var unpaidTabTV: TextView? = null
    private var pendingTabTV: TextView? = null
    private var selectedStatusTab = "All"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root: View = inflater.inflate(R.layout.fragment_transactions, container, false)

        mAuth = DeclareDatabase.auth
        transactionList = ArrayList()
        availableMonths = ArrayList()
        groupNames = ArrayList()
        groupIds = ArrayList()

        initViews(root)

        groupNames?.add("All group")
        groupIds?.add("All")
        setupGroupSpinner()

        getCurrentNickname()
        loadUserGroups()

        return root
    }

    private fun initViews(root: View) {
        recyclerView = root.findViewById(R.id.allTransactionsRecyclerView)
        monthSpinner = root.findViewById(R.id.monthSpinner)
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

        adapter = RecentTransactionAdapter(
            transactionList,
            RecentTransactionAdapter.OnTransactionClickListener { transaction ->
                if (transaction?.isExpanded == false) {
                    (activity as? MainActivity)?.unhideNavigation()
                }
            })
        recyclerView?.layoutManager = LinearLayoutManager(context)
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

    private fun getCurrentNickname() {
        val userId = mAuth?.currentUserOrNull()?.id ?: return run { loadAvailableMonths() }
        
        lifecycleScope.launch {
            try {
                val user = DeclareDatabase.usersTable.select {
                    filter {
                        eq("id", userId)
                    }
                }.decodeSingleOrNull<User>()
                
                if (user != null) {
                    currentNickname = user.username ?: ""
                }
                loadAvailableMonths()
            } catch (e: Exception) {
                Log.e("TransactionsFragment", "Error getting nickname", e)
                loadAvailableMonths()
            }
        }
    }

    private fun loadUserGroups() {
        val currentUid = mAuth?.currentUserOrNull()?.id ?: return
        
        lifecycleScope.launch {
            try {
                val groups = DeclareDatabase.groupsTable.select().decodeList<PayerGroup>()
                
                groupNames?.clear()
                groupIds?.clear()

                groupNames?.add("All group")
                groupIds?.add("All")

                for (group in groups) {
                    if (group.members?.contains(currentUid) == true) {
                        groupNames?.add(group.groupName ?: "")
                        groupIds?.add(group.groupId)
                    }
                }
                groupAdapter?.notifyDataSetChanged()
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
                selectedGroupId = groupIds?.get(position)
                refreshTransactions()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadAvailableMonths() {
        loadingProgressBar?.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val transactions = DeclareDatabase.transactionsTable.select().decodeList<Transaction>()
                val uniqueMonths = HashSet<String>()

                for (transaction in transactions) {
                    if (isUserInvolved(transaction, currentNickname)) {
                        transaction.monthYear?.let { uniqueMonths.add(it) }
                    }
                }

                availableMonths = ArrayList(uniqueMonths)
                availableMonths?.let { months ->
                    Collections.sort(months) { m1, m2 ->
                        try {
                            val sdf = SimpleDateFormat("MMMM-yyyy", Locale.getDefault())
                            return@sort sdf.parse(m2!!)!!.compareTo(sdf.parse(m1!!))
                        } catch (e: Exception) {
                            return@sort m2?.compareTo(m1 ?: "") ?: 0
                        }
                    }
                }

                setupMonthSpinner()
                loadingProgressBar?.visibility = View.GONE
            } catch (e: Exception) {
                Log.e("TransactionsFragment", "Error loading months", e)
                loadingProgressBar?.visibility = View.GONE
            }
        }
    }

    private fun setupMonthSpinner() {
        if (context == null || monthSpinner == null) return

        if (availableMonths.isNullOrEmpty()) {
            val calendar = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("MMMM-yyyy", Locale.getDefault())
            availableMonths = arrayListOf(dateFormat.format(calendar.time))
        }

        val spinnerAdapter = SpinnerItemMonths(requireContext(), availableMonths!!)
        monthSpinner?.adapter = spinnerAdapter

        val calendar = Calendar.getInstance()
        val currentMonth = SimpleDateFormat("MMMM-yyyy", Locale.getDefault()).format(calendar.time)

        var defaultPosition = 0
        availableMonths?.let { months ->
            for (i in months.indices) {
                if (months[i] == currentMonth) {
                    defaultPosition = i
                    break
                }
            }
        }

        monthSpinner?.setSelection(defaultPosition)
        selectedMonth = availableMonths?.get(defaultPosition)
        selectedMonth?.let {
            updateMonthDisplay(it)
            fetchTransactionsForMonth(it)
        }

        monthSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val month = availableMonths?.get(position)
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

        lifecycleScope.launch {
            try {
                val transactions = DeclareDatabase.transactionsTable.select {
                    filter {
                        eq("monthYear", monthYear)
                    }
                }.decodeList<Transaction>()

                for (transaction in transactions) {
                    val day = transaction.day
                    val timeKey = transaction.timeKey

                    if (isUserInvolved(transaction, currentNickname)) {
                        if (selectedGroupId != "All" && transaction.groupId != selectedGroupId) continue
                        if (!matchesStatusFilter(transaction, selectedStatusTab)) continue

                        val parts = monthYear.split("-").toTypedArray()
                        val monthName = parts[0]
                        val year = if (parts.size > 1) parts[1] else ""
                        val displayDate = "$monthName - $day"
                        val fullDateWithYear = "$monthName $day, $year"
                        val sortDateTime = "$year-$monthName-$day $timeKey"

                        val transactionType = transaction.transactionType
                        val details = transaction.multilineStr
                        val paymentAmount = transaction.paymentAmount
                        val paymentAmountStr = CurrencyUtils.formatAmountWithCurrency(paymentAmount)
                        val iconResource = getIconForTransactionType(transactionType)

                        var payorsList = transaction.payorsDisplayNames
                        if (payorsList.isNullOrEmpty()) payorsList = transaction.payorsList
                        
                        val payorUids = transaction.payorsList
                        val amountsPaidList = transaction.amountsPaidList
                        val totalIndividualPayment = transaction.totalIndividualPayment

                        var createdBy = transaction.posterDisplayName
                        if (createdBy.isNullOrEmpty()) createdBy = transaction.usernamePost
                        
                        val createdByUid = transaction.usernamePost

                        transactionList?.add(RecentTransaction(
                            displayDate, transactionType, details, paymentAmountStr,
                            iconResource, sortDateTime, payorsList, payorUids,
                            amountsPaidList, totalIndividualPayment, fullDateWithYear,
                            createdBy, createdByUid, monthYear, day ?: "", timeKey ?: ""
                        ))
                    }
                }

                transactionList?.let { list ->
                    Collections.sort(list) { t1, t2 ->
                        val dateTime1 = t1?.getSortDateTime()
                        val dateTime2 = t2?.getSortDateTime()
                        if (dateTime1 != null && dateTime2 != null) return@sort dateTime2.compareTo(dateTime1)
                        0
                    }
                }

                adapter?.notifyDataSetChanged()
                context?.let { adapter?.preloadAllImages(it) }

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
            } catch (e: Exception) {
                Log.e("TransactionsFragment", "Error fetching transactions", e)
                loadingProgressBar?.visibility = View.GONE
            }
        }
    }

    private fun matchesStatusFilter(transaction: Transaction, statusFilter: String?): Boolean {
        if ("All".equals(statusFilter, ignoreCase = true)) return true
        val paidAmounts = transaction.amountsPaidList
        val totalToPay = transaction.totalIndividualPayment
        if (paidAmounts.isNullOrEmpty()) return "Unpaid".equals(statusFilter, ignoreCase = true)

        var allPaid = true
        var allUnpaid = true
        for (paid in paidAmounts) {
            if (paid == null || paid < totalToPay) allPaid = false
            if (paid != null && paid > 0) allUnpaid = false
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

    private fun isUserInvolved(transaction: Transaction?, usernameOrUid: String?): Boolean {
        if (transaction == null || usernameOrUid.isNullOrEmpty()) return false
        val currentUid = mAuth?.currentUserOrNull()?.id
        if (transaction.isUserInvolvedByUid(currentUid)) return true
        if (usernameOrUid == transaction.usernamePost) return true
        if (usernameOrUid == transaction.posterDisplayName) return true
        val payorsList = transaction.payorsList
        if (payorsList != null && payorsList.contains(usernameOrUid)) return true
        val payorsDisplayNames = transaction.payorsDisplayNames
        return payorsDisplayNames != null && payorsDisplayNames.contains(usernameOrUid)
    }
}
