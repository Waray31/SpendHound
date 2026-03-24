package com.waray.spendhound.ui.transactions

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.MaterialDatePicker
import androidx.core.util.Pair
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
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

class TransactionsFragment : Fragment() {
    private var recyclerView: RecyclerView? = null
    private var adapter: RecentTransactionAdapter? = null
    private var transactionList: ArrayList<RecentTransaction> = ArrayList()
    private var datePickerButton: Button? = null
    private var groupSpinner: Spinner? = null
    private var currentMonthTextView: TextView? = null
    private var transactionCountTextView: TextView? = null
    private var loadingProgressBar: ProgressBar? = null
    private var emptyStateLayout: LinearLayout? = null
    private var mAuth: Auth? = null
    private var currentNickname: String? = ""
    private var currentUserNumericId: Long? = null
    
    private var startDate: Long = 0L
    private var endDate: Long = 0L

    private var groupNames: MutableList<String?>? = null
    private var groupIds: MutableList<Long?>? = null
    private var selectedGroupId: Long? = -1L // -1 for All
    private var groupAdapter: SpinnerItemMonths? = null

    // Status Tabs
    private var allTabTV: TextView? = null
    private var paidTabTV: TextView? = null
    private var unpaidTabTV: TextView? = null
    private var pendingTabTV: TextView? = null
    private var selectedStatusTab = "All"

    private var pendingLoads = 0

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
        setupDateRangePicker()

        return root
    }

    private fun initViews(root: View) {
        recyclerView = root.findViewById(R.id.allTransactionsRecyclerView)
        datePickerButton = root.findViewById(R.id.datePickerButton)
        groupSpinner = root.findViewById(R.id.groupSpinner)
        transactionCountTextView = root.findViewById(R.id.transactionCountTextView)
        loadingProgressBar = root.findViewById(R.id.loadingProgressBar)
        emptyStateLayout = root.findViewById(R.id.emptyStateLayout)

        allTabTV = root.findViewById(R.id.allTabTV)
        paidTabTV = root.findViewById(R.id.paidTabTV)
        unpaidTabTV = root.findViewById(R.id.unpaidTabTV)
        pendingTabTV = root.findViewById(R.id.pendingTabTV)

        setupStatusTabs()

        adapter = RecentTransactionAdapter(transactionList) { transaction ->
            if (transaction?.isExpanded == true) {
                (activity as? MainActivity)?.hideNavigation()
            } else {
                (activity as? MainActivity)?.unhideNavigation()
            }
        }
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
        fetchTransactionsInRange(startDate, endDate)
    }

    private fun getCurrentUserAndGroups() {
        val authUserId = mAuth?.currentUserOrNull()?.id ?: return
        
        showLoading()
        lifecycleScope.launch {
            try {
                val user = DeclareDatabase.usersTable.select {
                    filter {
                        eq("auth_id", authUserId)
                    }
                }.decodeSingleOrNull<User>()
                
                if (user != null) {
                    currentNickname = user.username ?: ""
                    currentUserNumericId = user.id
                    loadUserGroups()
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
                val groups = DeclareDatabase.groupsTable.select().decodeList<PayerGroup>()
                
                groupNames?.clear()
                groupIds?.clear()

                groupNames?.add("All group")
                groupIds?.add(-1L)

                for (group in groups) {
                    if (group.members?.contains(currentUserIdLong) == true) {
                        groupNames?.add(group.groupName ?: "")
                        groupIds?.add(group.groupId)
                    }
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
        picker.show(childFragmentManager, "DATE_RANGE_PICKER")

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
        showLoading()
        emptyStateLayout?.visibility = View.GONE
        transactionList.clear()
        adapter?.notifyDataSetChanged()

        lifecycleScope.launch {
            try {
                val transactions = DeclareDatabase.client.from("transactions").select().decodeList<Transaction>()

                val mainActivity = activity as? MainActivity
                for (transaction in transactions) {
                    val timestamp = parseCreatedAt(transaction.createdAt)
                    
                    if (timestamp in start..end && mainActivity?.isUserInvolved(transaction, currentNickname) == true) {
                        if (selectedGroupId != -1L && transaction.groupId != selectedGroupId) continue
                        if (!matchesStatusFilter(transaction, selectedStatusTab)) continue

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
                        if (createdBy.isNullOrEmpty()) createdBy = transaction.usernamePost
                        
                        val createdByUserId = transaction.usernamePost

                        transactionList.add(RecentTransaction(
                            transaction.id,
                            displayDate, transactionType, details, paymentAmountStr,
                            iconResource, sortDateTime, payorsList, payorUserIds,
                            amountsPaidList, totalIndividualPayment, fullDateWithYear,
                            createdBy, createdByUserId, monthYear, day, timeKey
                        ))
                    }
                }

                transactionList.sortWith { t1, t2 ->
                    val dateTime1 = t1.sortDateTime
                    val dateTime2 = t2.sortDateTime
                    if (dateTime1 != null && dateTime2 != null) dateTime2.compareTo(dateTime1) else 0
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
            // ISO 8601 format usually returned by Supabase
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            sdf.parse(createdAt)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun matchesStatusFilter(transaction: Transaction, statusFilter: String?): Boolean {
        if ("All".equals(statusFilter, ignoreCase = true)) return true
        val paidAmounts = transaction.amountPaidList
        val totalToPay = transaction.individualPayment
        if (paidAmounts.isNullOrEmpty()) return "Unpaid".equals(statusFilter, ignoreCase = true)

        var allPaid = true
        var allUnpaid = true
        for (paid in paidAmounts) {
            if (paid < totalToPay) allPaid = false
            if (paid > 0) allUnpaid = false
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

    private fun showLoading() {
        pendingLoads++
        loadingProgressBar?.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        pendingLoads = max(0, pendingLoads - 1)
        if (pendingLoads == 0) {
            loadingProgressBar?.visibility = View.GONE
        }
    }
}
