package com.waray.spendhound

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.waray.spendhound.data.local.CachedTransaction
import com.waray.spendhound.data.repository.GroupRepository
import com.waray.spendhound.ui.multi_transaction.MultiTransactionActivity
import com.waray.spendhound.ui.multi_transaction.TransactionFull
import com.waray.spendhound.ui.multi_transaction.TransactionItemFull
import com.waray.spendhound.ui.multi_transaction.TransactionPayorTable
import com.waray.spendhound.ui.multi_transaction.TransactionSplitTable
import com.waray.spendhound.utils.PullInterceptLayout
import com.waray.spendhound.utils.PullToRefreshHelper
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class GroupExpensesFragment : Fragment() {

    companion object {
        fun newInstance(groupId: Long) = GroupExpensesFragment().apply {
            arguments = Bundle().also { it.putLong("group_id", groupId) }
        }
    }

    private var groupId: Long = -1
    private val transactionList = ArrayList<RecentTransaction>()
    private lateinit var adapter: RecentTransactionAdapter
    private lateinit var rvSkeleton: RecyclerView
    private var pullToRefreshHelper: PullToRefreshHelper? = null
    private var fullTransactions: List<RecentTransaction> = emptyList()
    private var archivedTransactions: List<RecentTransaction> = emptyList()
    private var transactionActionsPopup: View? = null
    private var showArchivedSection: LinearLayout? = null
    private var showArchivedToggle: TextView? = null
    private var archivedRecyclerView: RecyclerView? = null
    private var archivedAdapter: RecentTransactionAdapter? = null
    private var isArchivedExpanded = false
    private lateinit var repo: GroupRepository

    private var selectedStatusTab = "All"
    private var isTabClickEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        groupId = arguments?.getLong("group_id") ?: -1
        repo = GroupRepository((requireActivity().application as SpendHoundApplication).database)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        inflater.inflate(R.layout.fragment_group_expenses, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rv = view.findViewById<RecyclerView>(R.id.rvExpenses)
        rvSkeleton = view.findViewById(R.id.rvSkeleton)
        transactionActionsPopup = view.findViewById(R.id.transactionActionsPopup)
        showArchivedSection = view.findViewById(R.id.showArchivedSection)
        showArchivedToggle = view.findViewById(R.id.showArchivedToggle)
        archivedRecyclerView = view.findViewById(R.id.archivedTransactionsRecyclerView)
        
        adapter = RecentTransactionAdapter(transactionList, { loadExpenses() }, { tx ->
            if (tx == null) return@RecentTransactionAdapter
            tx.isExpanded = !tx.isExpanded
            val pos = transactionList.indexOf(tx)
            if (tx.isUnread) {
                val authId = DeclareDatabase.auth.currentUserOrNull()?.id
                if (authId != null) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val user = DeclareDatabase.usersTable.select {
                                filter { eq("auth_id", authId) }
                            }.decodeSingleOrNull<User>()
                            if (user?.id != null) markTransactionsRead(listOf(tx), user.id)
                        } catch (_: Exception) {}
                    }
                }
            }
            if (pos != -1) adapter.notifyItemChanged(pos)
        }) { transaction, anchorView ->
            showTransactionPopup(transaction, anchorView)
        }
        
        archivedAdapter = RecentTransactionAdapter(arrayListOf(), { loadExpenses() }, { tx ->
            if (tx == null) return@RecentTransactionAdapter
            tx.isExpanded = !tx.isExpanded
            val pos = archivedTransactions.indexOf(tx)
            if (pos != -1) archivedAdapter?.notifyItemChanged(pos)
        }) { transaction, anchorView ->
            showArchivedTransactionPopup(transaction, anchorView)
        }
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter
        rvSkeleton.layoutManager = LinearLayoutManager(requireContext())
        rvSkeleton.adapter = SkeletonAdapter(R.layout.item_skeleton_transaction)
        
        archivedRecyclerView?.layoutManager = LinearLayoutManager(requireContext())
        archivedRecyclerView?.adapter = archivedAdapter
        
        setupArchivedSection()

        val scrollView = view.findViewById<NestedScrollView>(R.id.expensesScrollView)
        val indicator = view.findViewById<View>(R.id.pullRefreshIndicator_expenses)
        val rootLayout = view as PullInterceptLayout
        pullToRefreshHelper = PullToRefreshHelper(scrollView, indicator, {
            lifecycleScope.launch {
                repo.invalidateTransactions(groupId)
                loadExpenses()
            }
        }, rootLayout)
        rootLayout.onInterceptCallback = { event -> pullToRefreshHelper?.onInterceptTouch(event) ?: false }

        setupStatusTabs(view)
        loadExpenses()
        
        // Dismiss popup when clicking outside
        view.setOnClickListener { dismissPopup() }
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) loadExpenses()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadExpenses() {
        showLoading()
        lifecycleScope.launch {
            repo.getTransactions(groupId).collect { cached ->
                if (cached.isEmpty() && transactionList.isEmpty()) return@collect
                val result = buildTransactions(cached)
                withContext(Dispatchers.Main) {
                    fullTransactions = result
                    applyStatusFilter()
                    hideLoading()
                    pullToRefreshHelper?.stopRefreshing()
                }
            }
        }
    }

    private suspend fun buildTransactions(cached: List<CachedTransaction>): List<RecentTransaction> {
        if (cached.isEmpty()) return emptyList()

        val txIds = cached.mapNotNull { it.id }

        val authId = DeclareDatabase.auth.currentUserOrNull()?.id
        val currentUser = if (authId != null) {
            DeclareDatabase.usersTable.select {
                filter { eq("auth_id", authId) }
            }.decodeSingleOrNull<User>()
        } else null
        val currentUserId = currentUser?.id

        val readTxIds = if (currentUserId != null) {
            DeclareDatabase.transactionReadsTable.select(Columns.list("transaction_id")) {
                filter { eq("user_id", currentUserId); eq("group_id", groupId) }
            }.decodeList<TransactionRead>().mapNotNull { it.transactionId }.toSet()
        } else emptySet()

        val allPayors = DeclareDatabase.transactionPayorsTable.select {
            filter { isIn("transaction_id", txIds) }
        }.decodeList<TransactionPayorTable>()

        val allSplits = DeclareDatabase.transactionSplitsTable.select {
            filter { isIn("transaction_id", txIds) }
        }.decodeList<TransactionSplitTable>()

        val allItems = DeclareDatabase.transactionItemsTable.select {
            filter { isIn("transaction_id", txIds) }
        }.decodeList<TransactionItemFull>()

        val allUserIds = (allPayors.map { it.userId } + allSplits.map { it.userId }).distinct()
        val usersById: Map<Long, String> = if (allUserIds.isNotEmpty()) {
            DeclareDatabase.usersTable.select {
                filter { isIn("user_id", allUserIds) }
            }.decodeList<User>().associate { it.id!! to (it.username ?: "Unknown") }
        } else emptyMap()

        val payorsByTx = allPayors.groupBy { it.transactionId }
        val splitsByTx = allSplits.groupBy { it.transactionId }
        val itemsByTx = allItems.groupBy { it.transactionId }

        return cached.mapNotNull { tx ->
            val txId = tx.id
            val payors = payorsByTx[txId] ?: emptyList()
            val splits = splitsByTx[txId] ?: emptyList()
            val items = itemsByTx[txId] ?: emptyList()

            val timestamp = parseCreatedAt(tx.createdAt)
            val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
            val monthName = cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())
            val year = cal.get(Calendar.YEAR).toString()
            val day = cal.get(Calendar.DAY_OF_MONTH).toString()
            val timeKey = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(cal.time)

            val contributorIds = (payors.map { it.userId } + splits.map { it.userId }).distinct()
            val payorNames = contributorIds.map { usersById[it] ?: "Unknown" }.toMutableList<String?>()
            val payorUserIds = contributorIds.map { it.toString() }.toMutableList<String?>()
            val amountsPaid = contributorIds.map { uid ->
                payors.filter { it.userId == uid }.sumOf { it.currentAmountPaid } as Double?
            }.toMutableList()

            val individualPayment = splits.groupBy { it.userId }
                .values.firstOrNull()?.sumOf { it.amount } ?: 0.0

            val txStatus = computeStatus(payors, splits)

            val itemPayorMap = items.associate { item ->
                val itemId = item.id ?: 0L
                val names = allPayors.filter { it.transactionItemsId == itemId }
                    .map { usersById[it.userId] ?: "Unknown" }
                    .joinToString(", ").ifEmpty { "-" }
                itemId to names
            }

            val createdByName = tx.createdBy?.let { usersById[it] } ?: "Unknown"

            val rt = RecentTransaction(
                txId,
                formatSmartDate(tx.createdAt),
                tx.description,
                tx.description,
                CurrencyUtils.formatAmountWithCurrency(tx.totalAmount),
                getCategoryIcon(items.maxByOrNull { it.amount }?.category),
                "$year-$monthName-$day $timeKey",
                timestamp,
                payorNames,
                payorUserIds,
                amountsPaid,
                individualPayment,
                "$monthName $day, $year",
                createdByName,
                tx.createdBy?.toString(),
                "$monthName-$year",
                day,
                timeKey
            )
            rt.transactionItems = items
            rt.transactionStatus = txStatus
            rt.itemPayorMap = itemPayorMap
            rt.creatorNumericId = tx.createdBy
            rt.rawPayorRows = payors
            rt.rawSplitRows = splits
            rt.isUnread = txId !in readTxIds && tx.createdBy != currentUserId
            rt.groupId = tx.groupId
            rt.isArchived = tx.isArchived ?: false
            rt
        }.sortedByDescending { it.timestamp }
    }

    private fun markTransactionsRead(transactions: List<RecentTransaction>, userId: Long) {
        val unreadTxIds = transactions.filter { it.isUnread }.mapNotNull { it.transactionId }
        if (unreadTxIds.isEmpty()) return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val now = sdf.format(Date())

                unreadTxIds.forEach { txId ->
                    val existing = DeclareDatabase.transactionReadsTable.select {
                        filter { eq("user_id", userId); eq("transaction_id", txId) }
                        limit(1)
                    }.decodeSingleOrNull<TransactionRead>()

                    if (existing == null) {
                        DeclareDatabase.transactionReadsTable.insert(
                            TransactionReadInsert(
                                transactionId = txId,
                                userId = userId,
                                groupId = groupId,
                                readAt = now
                            )
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    transactions.forEach { it.isUnread = false }
                    transactions.forEach { tx ->
                        val index = transactionList.indexOf(tx)
                        if (index != -1) adapter.notifyItemChanged(index)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun applyStatusFilter() {
        val (active, archived) = fullTransactions.partition { !it.isArchived }
        
        val filtered = if (selectedStatusTab == "All") active
        else active.filter { it.transactionStatus.equals(selectedStatusTab, ignoreCase = true) }
        
        archivedTransactions = archived

        transactionList.clear()
        transactionList.addAll(filtered)
        adapter.notifyDataSetChanged()
        context?.let { adapter.preloadAllImages(it) }
        
        updateArchivedSection()

        if (transactionList.isEmpty()) showEmpty() else showList()
    }

    private fun formatSmartDate(createdAt: String?): String {
        if (createdAt.isNullOrBlank()) return ""
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).also {
                it.timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = parser.parse(createdAt.take(19)) ?: return ""
            SimpleDateFormat("MMM d", Locale.getDefault()).format(date).uppercase()
        } catch (_: Exception) { "" }
    }

    private fun computeStatus(payors: List<TransactionPayorTable>, splits: List<TransactionSplitTable>): String {
        if (splits.isEmpty()) return "Pending"
        val individualOwed = splits.groupBy { it.userId }.values.firstOrNull()?.sumOf { it.amount } ?: 0.0
        val paidByUser = payors.groupBy { it.userId }.mapValues { e -> e.value.sumOf { it.currentAmountPaid } }
        val allSettled = splits.map { it.userId }.distinct().all { (paidByUser[it] ?: 0.0) >= individualOwed }
        return if (allSettled) "Settled" else "Pending"
    }

    private fun parseCreatedAt(createdAt: String?): Long {
        if (createdAt == null) return 0L
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(createdAt)?.time ?: 0L
        } catch (_: Exception) { 0L }
    }

    private fun getCategoryIcon(category: String?): Int = when (category) {
        "Electricity"     -> R.drawable.lightning_bolt
        "Water"           -> R.drawable.faucet
        "Rent"            -> R.drawable.house
        "Internet"        -> R.drawable.internet
        "Online Shopping" -> R.drawable.online_shopping
        "Travel"          -> R.drawable.travel
        "Groceries"       -> R.drawable.groceries
        "Foods"           -> R.drawable.hamburger
        "House Necessity" -> R.drawable.necessities
        "Transportation"  -> R.drawable.vehicles
        else              -> R.drawable.others
    }

    private fun showEmpty() {
        view?.findViewById<RecyclerView>(R.id.rvExpenses)?.visibility = View.GONE
        view?.findViewById<View>(R.id.emptyExpenses)?.visibility = View.VISIBLE
        hideLoading()
    }

    private fun showList() {
        view?.findViewById<RecyclerView>(R.id.rvExpenses)?.visibility = View.VISIBLE
        view?.findViewById<View>(R.id.emptyExpenses)?.visibility = View.GONE
    }

    private fun showLoading() {
        if (transactionList.isEmpty()) {
            rvSkeleton.visibility = View.VISIBLE
            view?.findViewById<RecyclerView>(R.id.rvExpenses)?.visibility = View.GONE
            view?.findViewById<View>(R.id.emptyExpenses)?.visibility = View.GONE
        }
        isTabClickEnabled = false
    }

    private fun hideLoading() {
        rvSkeleton.visibility = View.GONE
        isTabClickEnabled = true
        if (transactionList.isNotEmpty()) showList()
    }

    private fun setupStatusTabs(view: View) {
        val allTab = view.findViewById<TextView>(R.id.allTabTV)
        val paidTab = view.findViewById<TextView>(R.id.paidTabTV)
        val unpaidTab = view.findViewById<TextView>(R.id.unpaidTabTV)

        setStatusTabSelected(allTab, allTab, paidTab, unpaidTab)

        allTab.setOnClickListener {
            if (!isTabClickEnabled) return@setOnClickListener
            selectedStatusTab = "All"
            setStatusTabSelected(allTab, allTab, paidTab, unpaidTab)
            applyStatusFilter()
        }
        paidTab.setOnClickListener {
            if (!isTabClickEnabled) return@setOnClickListener
            selectedStatusTab = "Settled"
            setStatusTabSelected(paidTab, allTab, paidTab, unpaidTab)
            applyStatusFilter()
        }
        unpaidTab.setOnClickListener {
            if (!isTabClickEnabled) return@setOnClickListener
            selectedStatusTab = "Pending"
            setStatusTabSelected(unpaidTab, allTab, paidTab, unpaidTab)
            applyStatusFilter()
        }
    }

    private fun setStatusTabSelected(selected: TextView, vararg all: TextView) {
        all.forEach { it.setBackgroundResource(0) }
        selected.setBackgroundResource(R.drawable.spinner_border_grey)
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
    
    private fun showTransactionPopup(transaction: RecentTransaction, anchorView: View) {
        transactionActionsPopup?.let { popup ->
            val location = IntArray(2)
            anchorView.getLocationOnScreen(location)
            val rootLocation = IntArray(2)
            view?.getLocationOnScreen(rootLocation)
            
            popup.x = (location[0] - rootLocation[0]).toFloat()
            popup.y = (location[1] - rootLocation[1] + anchorView.height + 8).toFloat()
            
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
            
            popup.visibility = View.VISIBLE
        }
    }
    
    private fun showArchivedTransactionPopup(transaction: RecentTransaction, anchorView: View) {
        transactionActionsPopup?.let { popup ->
            val location = IntArray(2)
            anchorView.getLocationOnScreen(location)
            val rootLocation = IntArray(2)
            view?.getLocationOnScreen(rootLocation)
            
            popup.x = (location[0] - rootLocation[0]).toFloat()
            popup.y = (location[1] - rootLocation[1] + anchorView.height + 8).toFloat()
            
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
            
            popup.visibility = View.VISIBLE
        }
    }
    
    private fun dismissPopup() {
        transactionActionsPopup?.visibility = View.GONE
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
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                DeclareDatabase.transactionsTable.update({ set("is_archived", true) }) {
                    filter { eq("id", txId) }
                }
                withContext(Dispatchers.Main) {
                    transaction.isArchived = true
                    moveToArchived(transaction)
                    loadExpenses()
                }
            } catch (e: Exception) {
                android.util.Log.e("GroupExpensesFragment", "Error archiving transaction", e)
            }
        }
    }
    
    private fun unarchiveTransaction(transaction: RecentTransaction) {
        val txId = transaction.transactionId ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                DeclareDatabase.transactionsTable.update({ set("is_archived", false) }) {
                    filter { eq("id", txId) }
                }
                withContext(Dispatchers.Main) {
                    transaction.isArchived = false
                    moveFromArchived(transaction)
                    loadExpenses()
                }
            } catch (e: Exception) {
                android.util.Log.e("GroupExpensesFragment", "Error unarchiving transaction", e)
            }
        }
    }
    
    private fun moveToArchived(transaction: RecentTransaction) {
        transactionList.remove(transaction)
        adapter.notifyDataSetChanged()
        archivedTransactions = archivedTransactions + transaction
        updateArchivedSection()
    }
    
    private fun moveFromArchived(transaction: RecentTransaction) {
        archivedTransactions = archivedTransactions.filter { it.transactionId != transaction.transactionId }
        updateArchivedSection()
        applyStatusFilter()
    }
}
