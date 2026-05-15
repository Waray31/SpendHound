package com.waray.spendhound.ui.transactions

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.waray.spendhound.utils.PullInterceptLayout
import com.waray.spendhound.utils.PullToRefreshHelper
import com.google.android.material.datepicker.MaterialDatePicker
import com.waray.spendhound.CurrencyUtils
import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.GroupMember
import com.waray.spendhound.MainActivity
import com.waray.spendhound.PayerGroup
import com.waray.spendhound.R
import com.waray.spendhound.RecentTransaction
import com.waray.spendhound.RecentTransactionAdapter
import com.waray.spendhound.SpinnerItemMonths
import com.waray.spendhound.TransactionRead
import com.waray.spendhound.TransactionReadInsert
import com.waray.spendhound.User
import com.waray.spendhound.ui.multi_transaction.MultiTransactionActivity
import com.waray.spendhound.ui.multi_transaction.TransactionFull
import io.github.jan.supabase.postgrest.query.Columns
import com.waray.spendhound.ui.multi_transaction.TransactionItemFull
import com.waray.spendhound.ui.multi_transaction.TransactionPayorTable
import com.waray.spendhound.ui.multi_transaction.TransactionSplitTable
import com.waray.spendhound.SkeletonAdapter
import com.waray.spendhound.utils.LoadingManager
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TransactionsFragment : Fragment() {
    private var recyclerView: RecyclerView? = null
    private var adapter: RecentTransactionAdapter? = null
    private var transactionList: ArrayList<RecentTransaction> = ArrayList()
    private var dateRangeSpinner: Spinner? = null
    private var groupSpinner: Spinner? = null
    private var currentMonthTextView: TextView? = null
    private var transactionCountTextView: TextView? = null
    private var loadingProgressBar: View? = null
    private var emptyStateLayout: LinearLayout? = null
    private var mAuth: Auth? = null
    private var currentUserNumericId: Long? = null

    private var startDate: Long = 0L
    private var endDate: Long = 0L

    private var groupNames: MutableList<String?>? = null
    private var groupIds: MutableList<Long?>? = null
    private var selectedGroupId: Long? = -1L
    private var groupAdapter: SpinnerItemMonths? = null

    private var allTabTV: TextView? = null
    private var paidTabTV: TextView? = null
    private var unpaidTabTV: TextView? = null
    private var selectedStatusTab = "All"

    private var isTabClickEnabled = true
    private var isLoading = false
    private var pullToRefreshHelper: PullToRefreshHelper? = null
    private var rvSkeletonTransactions: RecyclerView? = null
    private var fullTransactions: List<RecentTransaction> = emptyList()
    private var archivedTransactions: List<RecentTransaction> = emptyList()
    private var transactionActionsPopup: View? = null
    private var popupOverlay: View? = null
    private var showArchivedSection: LinearLayout? = null
    private var showArchivedToggle: TextView? = null
    private var archivedRecyclerView: RecyclerView? = null
    private var archivedAdapter: RecentTransactionAdapter? = null
    private var isArchivedExpanded = false
    private var lastSeenUpdate: Long = 0L

    private val viewModel: TransactionsViewModel by viewModels()

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
        setupDateRangeSpinner()

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lastSeenUpdate = com.waray.spendhound.TransactionState.lastUpdateTimestamp
        resolveUserThenLoad()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        if (com.waray.spendhound.TransactionState.lastUpdateTimestamp > lastSeenUpdate) {
            lastSeenUpdate = com.waray.spendhound.TransactionState.lastUpdateTimestamp
            refreshTransactions()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.transactions.collectLatest { list ->
                if (list.isEmpty() && fullTransactions.isEmpty()) return@collectLatest
                fullTransactions = list
                applyStatusFilter()
                hideLoading()
                pullToRefreshHelper?.stopRefreshing()
            }
        }
    }

    private fun resolveUserThenLoad() {
        val authUserId = mAuth?.currentUserOrNull()?.id ?: return
        if (currentUserNumericId != null) {
            viewModel.load(currentUserNumericId!!)
            loadUserGroups()
            return
        }
        showLoading(true)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val user = DeclareDatabase.usersTable.select {
                    filter { eq("auth_id", authUserId) }
                }.decodeSingleOrNull<User>()
                withContext(Dispatchers.Main) {
                    if (user?.id != null) {
                        currentUserNumericId = user.id
                        viewModel.load(user.id)
                        loadUserGroups()
                    } else {
                        hideLoading()
                    }
                }
            } catch (e: Exception) {
                Log.e("TransactionsFragment", "Error getting user info", e)
                withContext(Dispatchers.Main) { hideLoading() }
            }
        }
    }

    private fun initViews(root: View) {
        recyclerView = root.findViewById(R.id.allTransactionsRecyclerView)
        dateRangeSpinner = root.findViewById(R.id.dateRangeSpinner)
        groupSpinner = root.findViewById(R.id.groupSpinner)
        currentMonthTextView = root.findViewById(R.id.currentMonthTextView)
        transactionCountTextView = root.findViewById(R.id.transactionCountTextView)
        emptyStateLayout = root.findViewById(R.id.emptyStateLayout)
        rvSkeletonTransactions = root.findViewById(R.id.rvSkeletonTransactions)
        transactionActionsPopup = root.findViewById(R.id.transactionActionsPopup)
        popupOverlay = root.findViewById(R.id.popupOverlay)
        showArchivedSection = root.findViewById(R.id.showArchivedSection)
        showArchivedToggle = root.findViewById(R.id.showArchivedToggle)
        archivedRecyclerView = root.findViewById(R.id.archivedTransactionsRecyclerView)

        allTabTV = root.findViewById(R.id.allTabTV)
        paidTabTV = root.findViewById(R.id.paidTabTV)
        unpaidTabTV = root.findViewById(R.id.unpaidTabTV)

        setupStatusTabs()

        val scrollView = root.findViewById<androidx.core.widget.NestedScrollView>(R.id.transactionsNestedScrollView)
        val indicator = root.findViewById<View>(R.id.pullRefreshIndicator_transactions)
        val rootLayout = root as PullInterceptLayout
        pullToRefreshHelper = PullToRefreshHelper(scrollView, indicator, { refreshTransactions() }, rootLayout)
        rootLayout.onInterceptCallback = { event -> pullToRefreshHelper?.onInterceptTouch(event) ?: false }

        rvSkeletonTransactions?.layoutManager = LinearLayoutManager(context)
        rvSkeletonTransactions?.adapter = SkeletonAdapter(R.layout.item_skeleton_transaction)

        adapter = RecentTransactionAdapter(transactionList, { refreshTransactions() }, { tx ->
            if (tx == null) return@RecentTransactionAdapter
            tx.isExpanded = !tx.isExpanded
            val pos = transactionList.indexOf(tx)
            if (tx.isUnread && currentUserNumericId != null) {
                markTransactionsRead(listOf(tx), currentUserNumericId!!)
            }
            if (pos != -1) adapter?.notifyItemChanged(pos)
        }) { transaction, view ->
            showTransactionPopup(transaction, view)
        }
        
        archivedAdapter = RecentTransactionAdapter(arrayListOf(), { refreshTransactions() }, { tx ->
            if (tx == null) return@RecentTransactionAdapter
            tx.isExpanded = !tx.isExpanded
            val pos = archivedTransactions.indexOf(tx)
            if (pos != -1) archivedAdapter?.notifyItemChanged(pos)
        }) { transaction, view ->
            showArchivedTransactionPopup(transaction, view)
        }
        
        recyclerView?.layoutManager = LinearLayoutManager(context)
        recyclerView?.adapter = adapter
        
        archivedRecyclerView?.layoutManager = LinearLayoutManager(context)
        archivedRecyclerView?.adapter = archivedAdapter
        
        setupArchivedSection()
        
        popupOverlay?.setOnClickListener { dismissPopup() }
        
        // Dismiss popup when clicking outside
        view?.setOnClickListener { dismissPopup() }
    }

    private fun setupStatusTabs() {
        allTabTV?.let { setStatusTabSelected(it) }
        allTabTV?.setOnClickListener {
            if (!isTabClickEnabled) return@setOnClickListener
            selectedStatusTab = "All"; allTabTV?.let { setStatusTabSelected(it) }; applyStatusFilter()
        }
        paidTabTV?.setOnClickListener {
            if (!isTabClickEnabled) return@setOnClickListener
            selectedStatusTab = "Settled"; paidTabTV?.let { setStatusTabSelected(it) }; applyStatusFilter()
        }
        unpaidTabTV?.setOnClickListener {
            if (!isTabClickEnabled) return@setOnClickListener
            selectedStatusTab = "Pending"; unpaidTabTV?.let { setStatusTabSelected(it) }; applyStatusFilter()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun applyStatusFilter() {
        val (active, archived) = fullTransactions.partition { !it.isArchived }
        
        val filtered = active.filter { tx ->
            val statusOk = selectedStatusTab == "All" || tx.transactionStatus.equals(selectedStatusTab, ignoreCase = true)
            val dateOk = tx.timestamp in startDate..endDate
            val groupOk = selectedGroupId == -1L || tx.groupId == selectedGroupId
            statusOk && dateOk && groupOk
        }
        
        archivedTransactions = archived.filter { tx ->
            val dateOk = tx.timestamp in startDate..endDate
            val groupOk = selectedGroupId == -1L || tx.groupId == selectedGroupId
            dateOk && groupOk
        }
        
        transactionList.clear()
        transactionList.addAll(filtered)
        adapter?.notifyDataSetChanged()
        
        updateArchivedSection()
        
        val count = transactionList.size
        transactionCountTextView?.text = String.format(Locale.getDefault(), "%d %s", count, if (count == 1) "transaction" else "transactions")
        if (transactionList.isEmpty() && !isLoading) {
            emptyStateLayout?.visibility = View.VISIBLE
            recyclerView?.visibility = View.GONE
        } else {
            emptyStateLayout?.visibility = View.GONE
            recyclerView?.visibility = View.VISIBLE
        }
    }

    private fun setStatusTabSelected(selectedTab: TextView) {
        allTabTV?.setBackgroundResource(0)
        paidTabTV?.setBackgroundResource(0)
        unpaidTabTV?.setBackgroundResource(0)
        selectedTab.setBackgroundResource(R.drawable.spinner_border_grey)
    }

    internal fun refreshTransactions(forceSkeleton: Boolean = false) {
        val userId = currentUserNumericId ?: return
        if (forceSkeleton) showLoading(true)
        viewModel.invalidate(userId)
    }

    private fun loadUserGroups() {
        val currentUserIdLong = currentUserNumericId ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val myGroupIds = DeclareDatabase.groupMembersTable.select {
                    filter { eq("user_id", currentUserIdLong) }
                }.decodeList<GroupMember>().mapNotNull { it.groupId }.toSet()
                val allGroups = DeclareDatabase.groupsTable.select {
                    filter { isIn("group_id", myGroupIds.toList()) }
                }.decodeList<PayerGroup>()
                withContext(Dispatchers.Main) {
                    groupNames?.clear(); groupIds?.clear()
                    groupNames?.add("All group"); groupIds?.add(-1L)
                    allGroups.forEach { groupNames?.add(it.groupName ?: ""); groupIds?.add(it.groupId) }
                    groupAdapter?.notifyDataSetChanged()
                }
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
                val newGroupId = groupIds?.get(position)
                if (selectedGroupId != newGroupId) { selectedGroupId = newGroupId; applyStatusFilter() }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private var customDateActive = false

    private fun setupDateRangeSpinner() {
        val options = mutableListOf<String?>("This Month", "Last Month", "All", "Custom Date")
        val spinnerAdapter = SpinnerItemMonths(requireContext(), options)
        dateRangeSpinner?.adapter = spinnerAdapter
        setThisMonth(); updateCurrentMonthText()
        currentMonthTextView?.setOnClickListener { if (customDateActive) showDateRangePickerDialog() }
        dateRangeSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    0 -> { customDateActive = false; setThisMonth(); updateCurrentMonthText(); applyStatusFilter() }
                    1 -> { customDateActive = false; setLastMonth(); updateCurrentMonthText(); applyStatusFilter() }
                    2 -> { customDateActive = false; setAllTime(); updateCurrentMonthText(); applyStatusFilter() }
                    3 -> showDateRangePickerDialog()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setThisMonth() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        startDate = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH)); cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
        endDate = cal.timeInMillis
    }

    private fun setLastMonth() {
        val cal = Calendar.getInstance(); cal.add(Calendar.MONTH, -1)
        cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        startDate = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH)); cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
        endDate = cal.timeInMillis
    }

    private fun setAllTime() { startDate = 0L; endDate = Long.MAX_VALUE }

    private fun showDateRangePickerDialog() {
        val safeStart = if (startDate == 0L || startDate == Long.MAX_VALUE) Calendar.getInstance().also { it.set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis else startDate
        val safeEnd = if (endDate == Long.MAX_VALUE) Calendar.getInstance().timeInMillis else endDate
        val picker = MaterialDatePicker.Builder.dateRangePicker().setTitleText("Select Date Range").setSelection(androidx.core.util.Pair(safeStart, safeEnd)).build()
        picker.show(childFragmentManager, "DATE_RANGE_PICKER")
        picker.addOnPositiveButtonClickListener { selection ->
            startDate = selection.first ?: safeStart
            val selectedEnd = selection.second ?: selection.first ?: safeEnd
            endDate = selectedEnd + 86400000 - 1
            val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
            customDateActive = true
            updateCurrentMonthText(customLabel = "${sdf.format(startDate)} - ${sdf.format(selectedEnd)}")
            applyStatusFilter()
        }
        picker.addOnCancelListener { if (!customDateActive) dateRangeSpinner?.setSelection(0) }
    }

    private fun updateCurrentMonthText(customLabel: String? = null) {
        val text = customLabel ?: when {
            startDate == 0L && endDate == Long.MAX_VALUE -> "All Time"
            else -> {
                val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                val start = sdf.format(startDate); val end = sdf.format(endDate)
                if (start == end) start else "$start - $end"
            }
        }
        currentMonthTextView?.text = text
    }

    private fun markTransactionsRead(transactions: List<RecentTransaction>, userId: Long) {
        val unreadTxIds = transactions.filter { it.isUnread }.mapNotNull { (it.transactionId to it.groupId) }
        if (unreadTxIds.isEmpty()) return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault()).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                val now = sdf.format(java.util.Date())
                unreadTxIds.forEach { (txId, gid) ->
                    if (txId == null || gid == null) return@forEach
                    val existing = DeclareDatabase.transactionReadsTable.select {
                        filter { eq("user_id", userId); eq("transaction_id", txId) }; limit(1)
                    }.decodeSingleOrNull<TransactionRead>()
                    if (existing == null) {
                        DeclareDatabase.transactionReadsTable.insert(TransactionReadInsert(transactionId = txId, userId = userId, groupId = gid, readAt = now))
                    }
                }
                withContext(Dispatchers.Main) {
                    transactions.forEach { it.isUnread = false }
                    transactions.forEach { tx -> val index = transactionList.indexOf(tx); if (index != -1) adapter?.notifyItemChanged(index) }
                }
            } catch (e: Exception) {
                Log.e("TransactionsFragment", "Error marking transactions as read", e)
            }
        }
    }

    private fun showLoading(force: Boolean = false) {
        if (force || transactionList.isEmpty()) {
            rvSkeletonTransactions?.visibility = View.VISIBLE
            recyclerView?.visibility = View.GONE
            emptyStateLayout?.visibility = View.GONE
        }
        isLoading = true
        isTabClickEnabled = false
    }

    private fun hideLoading() {
        isLoading = false
        rvSkeletonTransactions?.visibility = View.GONE
        isTabClickEnabled = true
        if (transactionList.isNotEmpty()) {
            recyclerView?.visibility = View.VISIBLE
            emptyStateLayout?.visibility = View.GONE
        }
    }
    
    private fun setupArchivedSection() {
        showArchivedToggle?.setOnClickListener {
            isArchivedExpanded = !isArchivedExpanded
            archivedRecyclerView?.visibility = if (isArchivedExpanded) View.VISIBLE else View.GONE
            updateArchivedToggleText()
        }
    }
    
    private fun updateArchivedToggleText() {
        val count = archivedTransactions.size
        val action = if (isArchivedExpanded) "Hide" else "Show"
        showArchivedToggle?.text = "🗂 $count archived transactions — $action"
    }
    
    private fun showTransactionPopup(transaction: RecentTransaction, anchorView: View) {
        transactionActionsPopup?.let { popup ->
            // Position popup
            val location = IntArray(2)
            anchorView.getLocationOnScreen(location)
            val rootLocation = IntArray(2)
            view?.getLocationOnScreen(rootLocation)
            
            popup.x = (location[0] - rootLocation[0]).toFloat()
            popup.y = (location[1] - rootLocation[1] + anchorView.height + 8).toFloat()
            
            // Setup click listeners
            popup.findViewById<TextView>(R.id.tvEdit)?.setOnClickListener {
                editTransaction(transaction)
                dismissPopup()
            }
            
            popup.findViewById<TextView>(R.id.tvArchive)?.setOnClickListener {
                archiveTransaction(transaction)
                dismissPopup()
            }
            
            popup.findViewById<TextView>(R.id.tvCancel)?.setOnClickListener {
                dismissPopup()
            }
            
            popupOverlay?.visibility = View.VISIBLE
            popup.visibility = View.VISIBLE
        }
    }
    
    private fun showArchivedTransactionPopup(transaction: RecentTransaction, anchorView: View) {
        transactionActionsPopup?.let { popup ->
            // Position popup
            val location = IntArray(2)
            anchorView.getLocationOnScreen(location)
            val rootLocation = IntArray(2)
            view?.getLocationOnScreen(rootLocation)
            
            popup.x = (location[0] - rootLocation[0]).toFloat()
            popup.y = (location[1] - rootLocation[1] + anchorView.height + 8).toFloat()
            
            // Setup click listeners for archived transaction (only unarchive)
            popup.findViewById<TextView>(R.id.tvEdit)?.visibility = View.GONE
            popup.findViewById<TextView>(R.id.tvArchive)?.apply {
                text = "Unarchive"
                visibility = View.VISIBLE
                setOnClickListener {
                    unarchiveTransaction(transaction)
                    dismissPopup()
                }
            }
            
            popup.findViewById<TextView>(R.id.tvCancel)?.setOnClickListener {
                dismissPopup()
            }
            
            popupOverlay?.visibility = View.VISIBLE
            popup.visibility = View.VISIBLE
        }
    }
    
    private fun dismissPopup() {
        transactionActionsPopup?.visibility = View.GONE
        popupOverlay?.visibility = View.GONE
        // Reset archive button text and visibility
        transactionActionsPopup?.findViewById<TextView>(R.id.tvEdit)?.visibility = View.VISIBLE
        transactionActionsPopup?.findViewById<TextView>(R.id.tvArchive)?.text = "Archive"
    }
    
    private fun editTransaction(transaction: RecentTransaction) {
        val intent = Intent(requireContext(), MultiTransactionActivity::class.java)
        intent.putExtra("TRANSACTION_ID", transaction.transactionId)
        intent.putExtra("EDIT_MODE", true)
        startActivity(intent)
    }
    
    private fun archiveTransaction(transaction: RecentTransaction) {
        val txId = transaction.transactionId ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                DeclareDatabase.transactionsTable.update({ set("is_archived", true) }) {
                    filter { eq("id", txId) }
                }
                withContext(Dispatchers.Main) {
                    transaction.isArchived = true
                    moveToArchived(transaction)
                    com.waray.spendhound.TransactionState.notifyChange()
                    lastSeenUpdate = com.waray.spendhound.TransactionState.lastUpdateTimestamp
                    refreshTransactions()
                }
            } catch (e: Exception) {
                Log.e("TransactionsFragment", "Error archiving transaction", e)
            }
        }
    }
    
    private fun unarchiveTransaction(transaction: RecentTransaction) {
        val txId = transaction.transactionId ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                DeclareDatabase.transactionsTable.update({ set("is_archived", false) }) {
                    filter { eq("id", txId) }
                }
                withContext(Dispatchers.Main) {
                    transaction.isArchived = false
                    moveFromArchived(transaction)
                    com.waray.spendhound.TransactionState.notifyChange()
                    lastSeenUpdate = com.waray.spendhound.TransactionState.lastUpdateTimestamp
                    refreshTransactions()
                }
            } catch (e: Exception) {
                Log.e("TransactionsFragment", "Error unarchiving transaction", e)
            }
        }
    }
    
    private fun moveToArchived(transaction: RecentTransaction) {
        transactionList.remove(transaction)
        adapter?.notifyDataSetChanged()
        archivedTransactions = archivedTransactions + transaction
        updateArchivedSection()
    }
    
    private fun moveFromArchived(transaction: RecentTransaction) {
        archivedTransactions = archivedTransactions.filter { it.transactionId != transaction.transactionId }
        updateArchivedSection()
        applyStatusFilter() // This will add it back to main list
    }
    
    private fun updateArchivedSection() {
        val hasArchived = archivedTransactions.isNotEmpty()
        showArchivedSection?.visibility = if (hasArchived && selectedStatusTab == "All") View.VISIBLE else View.GONE
        
        if (hasArchived) {
            updateArchivedToggleText()
            (archivedAdapter?.recentTransactionList as? ArrayList)?.let { list ->
                list.clear()
                list.addAll(archivedTransactions)
                archivedAdapter?.notifyDataSetChanged()
            }
        }
    }
}
