package com.waray.spendhound

import android.util.Log
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.waray.spendhound.data.local.CachedTransaction
import com.waray.spendhound.data.repository.GroupRepository
import com.waray.spendhound.ui.group.MemberWithUser
import com.waray.spendhound.ui.multi_transaction.TransactionItemFull
import com.waray.spendhound.ui.multi_transaction.TransactionPayorInsert
import com.waray.spendhound.ui.multi_transaction.TransactionPayorTable
import com.waray.spendhound.ui.multi_transaction.TransactionSplitTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.jan.supabase.postgrest.query.Order
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class SettlementActivity : AppCompatActivity() {

    private lateinit var rvMembers: RecyclerView
    private lateinit var rvTransactions: RecyclerView
    private lateinit var summaryContainer: View
    private lateinit var tvBalanceTitle: TextView
    private lateinit var tvReceivableAmount: TextView
    private lateinit var tvDebtAmount: TextView
    private lateinit var tvTransactionsHeader: TextView
    private lateinit var btnSelectAll: TextView
    private lateinit var amountInputContainer: View
    private lateinit var tvComputedTotal: TextView
    private lateinit var tvTotalSummaryNote: TextView
    private lateinit var tvReceivableFooterAmount: TextView
    private lateinit var btnSettle: View
    private lateinit var loadingLayout: View
    private lateinit var btnBack: View
    private lateinit var settleTabLayout: com.google.android.material.tabs.TabLayout
    private lateinit var transactionsTabContainer: View
    private lateinit var personTabContainer: View
    private lateinit var rvAllSettlableTransactions: RecyclerView

    private var groupId: Long = -1
    private var fullTransactions: List<RecentTransaction> = emptyList()
    private var members: List<MemberWithUser> = emptyList()
    
    private var selectedMember: MemberWithUser? = null
    private var currentUserId: Long? = null
    private var memberBalances: Map<Long, Double> = emptyMap()
    private var memberReceivableTotal: Double = 0.0
    private var memberDebtTotal: Double = 0.0
    
    private val selectedTransactions = mutableSetOf<RecentTransaction>()
    private var transactionListForMember = mutableListOf<TransactionWithBalance>()
    private var displayList = mutableListOf<Any>()

    private val epsilon = 0.01

    data class TransactionWithBalance(
        val transaction: RecentTransaction,
        val balanceWithMember: Double,
        val isCurrentUserOwed: Boolean
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settlement)

        groupId = intent.getLongExtra("group_id", -1)

        initViews()
        setupToolbar()
        setupRecyclerViews()
        setupTabs()
        
        loadData()
        loadAllSettlableTransactions()

        btnSettle.setOnClickListener { showSettlementConfirmation() }
        btnSelectAll.setOnClickListener { toggleSelectAll() }
    }

    private fun showSettlementConfirmation() {
        val count = selectedTransactions.size
        val memberName = selectedMember?.user?.username ?: getString(R.string.placeholder_name)
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.AppDialog)
            .setTitle(getString(R.string.dialog_confirm_settlement_title))
            .setMessage(getString(R.string.dialog_confirm_settlement_message, count, memberName))
            .setPositiveButton(getString(R.string.btn_settle_now)) { _, _ ->
                performBatchSettlement()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        rvMembers = findViewById(R.id.rvMembers)
        rvTransactions = findViewById(R.id.rvTransactions)
        summaryContainer = findViewById(R.id.summaryContainer)
        tvBalanceTitle = findViewById(R.id.tvBalanceTitle)
        tvReceivableAmount = findViewById(R.id.tvReceivableAmount)
        tvDebtAmount = findViewById(R.id.tvDebtAmount)
        tvTransactionsHeader = findViewById(R.id.tvTransactionsHeader)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        findViewById<View>(R.id.settleHandle)?.visibility = View.GONE
        amountInputContainer = findViewById(R.id.amountInputContainer)
        tvReceivableFooterAmount = findViewById(R.id.tvReceivableFooterAmount)
        tvComputedTotal = findViewById(R.id.tvComputedTotal)
        tvTotalSummaryNote = findViewById(R.id.tvTotalSummaryNote)
        btnSettle = findViewById(R.id.btnSettle)
        loadingLayout = findViewById(R.id.loadingLayout)
        settleTabLayout = findViewById(R.id.settleTabLayout)
        transactionsTabContainer = findViewById(R.id.transactionsTabContainer)
        personTabContainer = findViewById(R.id.personTabContainer)
        rvAllSettlableTransactions = findViewById(R.id.rvAllSettlableTransactions)
    }

    private fun setupToolbar() {
        btnBack.setOnClickListener { finish() }
    }

    private fun setupRecyclerViews() {
        rvMembers.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvMembers.adapter = MemberSelectionAdapter(emptyList()) { selectMember(it) }

        rvTransactions.layoutManager = LinearLayoutManager(this)
        rvTransactions.adapter = TransactionAdapter(emptyList()) { tx, isSelected ->
            if (isSelected) selectedTransactions.add(tx) else selectedTransactions.remove(tx)
            updateTotalSelected()
        }

        rvAllSettlableTransactions.layoutManager = LinearLayoutManager(this)
        rvAllSettlableTransactions.adapter = RecentTransactionAdapter(ArrayList(), {
            loadAllSettlableTransactions()
        }, { tx ->
            if (tx == null) return@RecentTransactionAdapter
            tx.isExpanded = !tx.isExpanded
            val pos = (rvAllSettlableTransactions.adapter as RecentTransactionAdapter).recentTransactionList?.indexOf(tx) ?: -1
            if (pos != -1) (rvAllSettlableTransactions.adapter as RecentTransactionAdapter).notifyItemChanged(pos)
        })
    }

    private fun setupTabs() {
        settleTabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        transactionsTabContainer.visibility = View.VISIBLE
                        personTabContainer.visibility = View.GONE
                        amountInputContainer.visibility = View.GONE
                        btnSettle.visibility = View.GONE
                    }
                    1 -> {
                        transactionsTabContainer.visibility = View.GONE
                        personTabContainer.visibility = View.VISIBLE
                        if (transactionListForMember.isNotEmpty()) {
                            amountInputContainer.visibility = View.VISIBLE
                            btnSettle.visibility = View.VISIBLE
                        }
                    }
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
        
        // Default to Person if groupId is provided, else Transactions
        if (groupId == -1L) {
            settleTabLayout.getTabAt(0)?.select()
        } else {
            settleTabLayout.getTabAt(1)?.select()
        }
    }

    private fun loadAllSettlableTransactions() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val authId = DeclareDatabase.auth.currentUserOrNull()?.id
                val user = DeclareDatabase.usersTable.select { filter { eq("auth_id", authId ?: "") } }.decodeSingleOrNull<User>()
                val myId = user?.id ?: return@launch

                val groupIdsToSearch = if (groupId != -1L) {
                    listOf(groupId)
                } else {
                    DeclareDatabase.groupMembersTable.select { filter { eq("user_id", myId) } }
                        .decodeList<GroupMember>().mapNotNull { it.groupId }
                }

                if (groupIdsToSearch.isEmpty()) return@launch

                val cached = DeclareDatabase.transactionsTable.select {
                    filter { 
                        isIn("group_id", groupIdsToSearch)
                        neq("status", 3) // Not Settled
                    }
                    order("created_at", Order.DESCENDING)
                }.decodeList<CachedTransaction>()

                val built = buildTransactionsExtra(cached, myId)
                withContext(Dispatchers.Main) {
                    (rvAllSettlableTransactions.adapter as RecentTransactionAdapter).recentTransactionList?.clear()
                    (rvAllSettlableTransactions.adapter as RecentTransactionAdapter).recentTransactionList?.addAll(built)
                    (rvAllSettlableTransactions.adapter as RecentTransactionAdapter).notifyDataSetChanged()
                }
            } catch (e: Exception) {
                Log.e("SettlementActivity", "Error loading settlable transactions", e)
            }
        }
    }

    private suspend fun buildTransactionsExtra(cached: List<CachedTransaction>, myId: Long): List<RecentTransaction> {
        if (cached.isEmpty()) return emptyList()
        val txIds = cached.mapNotNull { it.id }
        val allPayors = DeclareDatabase.transactionPayorsTable.select { filter { isIn("transaction_id", txIds) } }.decodeList<TransactionPayorTable>()
        val allSplits = DeclareDatabase.transactionSplitsTable.select { filter { isIn("transaction_id", txIds) } }.decodeList<TransactionSplitTable>()
        val allItems = DeclareDatabase.transactionItemsTable.select { filter { isIn("transaction_id", txIds) } }.decodeList<TransactionItemFull>()
        
        val allUserIds = (allPayors.map { it.userId } + allSplits.map { it.userId }).distinct()
        val usersById = DeclareDatabase.usersTable.select { filter { isIn("user_id", allUserIds) } }.decodeList<User>().associateBy { it.id }

        val gIds = cached.mapNotNull { it.groupId }.distinct()
        val groupsById = DeclareDatabase.groupsTable.select { filter { isIn("group_id", gIds) } }.decodeList<PayerGroup>().associateBy { it.groupId }

        val payorsByTx = allPayors.groupBy { it.transactionId }
        val splitsByTx = allSplits.groupBy { it.transactionId }
        val itemsByTx = allItems.groupBy { it.transactionId }

        return cached.mapNotNull { tx ->
            val txId = tx.id
            val payors = payorsByTx[txId] ?: emptyList()
            val splits = splitsByTx[txId] ?: emptyList()
            val items = itemsByTx[txId] ?: emptyList()
            
            val timestamp = try { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(tx.createdAt?.take(19) ?: "")?.time ?: 0L } catch (_: Exception) { 0L }
            
            val payorNames = payors.map { usersById[it.userId]?.username ?: "Unknown" }.toMutableList()
            val payorIds = payors.map { it.userId.toString() }.toMutableList()
            val amountsPaid = payors.map { it.currentAmountPaid as Double? }.toMutableList()

            RecentTransaction().apply {
                this.transactionId = txId
                this.timestamp = timestamp
                this.transactionItems = items
                this.rawPayorRows = payors
                this.rawSplitRows = splits
                this.mostRecentTransactionType = items.maxByOrNull { it.amount }?.category
                this.mostRecentDetails = tx.description ?: mostRecentTransactionType
                this.mostRecentPaymentAmountStr = CurrencyUtils.formatAmountWithCurrency(tx.totalAmount)
                this.transactionStatus = if (splits.isNotEmpty() && splits.all { s -> payors.filter { it.userId == s.userId }.sumOf { it.currentAmountPaid } >= s.amount - epsilon }) "Settled" else "Pending"
                this.mostRecentDate = tx.createdAt?.let { 
                    try { 
                        val d = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(it.take(19))!!
                        SimpleDateFormat("MMM - d", Locale.getDefault()).format(d) 
                    } catch (_: Exception) { "" } 
                } ?: ""
                this.payorsList = payorNames as MutableList<String?>
                this.payorUserIds = payorIds as MutableList<String?>
                this.amountsPaidList = amountsPaid
                this.groupName = groupsById[tx.groupId]?.groupName
                this.createdBy = usersById[tx.createdBy]?.username
                this.creatorNumericId = tx.createdBy
                
                this.itemPayorMap = items.associate { item ->
                    val itemId = item.id ?: 0L
                    val names = payors.filter { it.transactionItemsId == itemId }
                        .map { usersById[it.userId]?.username ?: "Unknown" }
                        .joinToString(", ").ifEmpty { "-" }
                    itemId to names
                }
            }
        }.sortedByDescending { it.timestamp }
    }

    private fun loadData() {
        loadingLayout.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val authId = DeclareDatabase.auth.currentUserOrNull()?.id
                val currentUser = if (authId != null) {
                    DeclareDatabase.usersTable.select {
                        filter { eq("auth_id", authId) }
                    }.decodeSingleOrNull<User>()
                } else null
                currentUserId = currentUser?.id

                val memberResult = DeclareDatabase.groupMembersTable.select {
                    filter { eq("group_id", groupId) }
                }.decodeList<GroupMember>()
                
                val userIds = memberResult.mapNotNull { it.userId }
                val users = DeclareDatabase.usersTable.select {
                    filter { isIn("user_id", userIds) }
                }.decodeList<User>().associateBy { it.id }
                
                members = memberResult.mapNotNull { m ->
                    users[m.userId]?.let { u -> MemberWithUser(m, u) }
                }.filter { it.user.id != currentUserId }

                val repo = GroupRepository((application as SpendHoundApplication).database)
                repo.getTransactions(groupId).collect { cached ->
                    val built = buildTransactions(cached)
                    withContext(Dispatchers.Main) {
                        fullTransactions = built
                        calculateAllMemberBalances()
                        (rvMembers.adapter as MemberSelectionAdapter).updateMembers(members)
                        val firstWithBalance = members.firstOrNull { 
                            kotlin.math.abs(memberBalances[it.user.id] ?: 0.0) > epsilon 
                        }
                        selectMember(firstWithBalance ?: members.firstOrNull() ?: return@withContext)
                        loadingLayout.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingLayout.visibility = View.GONE
                    Toast.makeText(this@SettlementActivity, "Error loading data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun calculateAllMemberBalances() {
        val balances = mutableMapOf<Long, Double>()
        val myId = currentUserId ?: return
        val pendingTransactions = fullTransactions.filter { it.transactionStatus != "Settled" }

        for (tx in pendingTransactions) {
            val payors = tx.rawPayorRows
            val splits = tx.rawSplitRows
            val myPaid = payors.filter { it.userId == myId }.sumOf { it.currentAmountPaid }
            val myShare = splits.filter { it.userId == myId }.sumOf { it.amount }
            val totalDeficit = splits.sumOf { s ->
                val p = payors.filter { it.userId == s.userId }.sumOf { it.currentAmountPaid }
                if (s.amount > p) s.amount - p else 0.0
            }
            val totalSurplus = payors.sumOf { p ->
                val s = splits.filter { it.userId == p.userId }.sumOf { it.amount }
                if (p.currentAmountPaid > s) p.currentAmountPaid - s else 0.0
            }

            if (myPaid > myShare) {
                val mySurplus = myPaid - myShare
                if (totalDeficit > 0) {
                    splits.forEach { s ->
                        val p = payors.filter { it.userId == s.userId }.sumOf { it.currentAmountPaid }
                        if (s.amount > p) {
                            val deficit = s.amount - p
                            val owesToMe = (deficit / totalDeficit) * mySurplus
                            balances[s.userId] = (balances[s.userId] ?: 0.0) + owesToMe
                        }
                    }
                }
            } else if (myShare > myPaid) {
                val myDeficit = myShare - myPaid
                if (totalSurplus > 0) {
                    payors.map { it.userId }.distinct().forEach { uid ->
                        val p = payors.filter { it.userId == uid }.sumOf { it.currentAmountPaid }
                        val s = splits.filter { it.userId == uid }.sumOf { it.amount }
                        if (p > s) {
                            val surplus = p - s
                            val iOweThem = (surplus / totalSurplus) * myDeficit
                            balances[uid] = (balances[uid] ?: 0.0) - iOweThem
                        }
                    }
                }
            }
        }
        memberBalances = balances
    }

    private fun selectMember(member: MemberWithUser) {
        selectedMember = member
        (rvMembers.adapter as MemberSelectionAdapter).setSelected(member)
        tvBalanceTitle.text = getString(R.string.balance_with_format, member.user.username)
        loadTransactionsForMember(member)
    }

    private fun loadTransactionsForMember(member: MemberWithUser) {
        val myId = currentUserId ?: return
        val memberId = member.user.id ?: return
        transactionListForMember.clear()
        selectedTransactions.clear()
        var receivableTotal = 0.0
        var debtTotal = 0.0
        val pendingTransactions = fullTransactions.filter { it.transactionStatus != "Settled" }
        
        for (tx in pendingTransactions) {
            val payors = tx.rawPayorRows
            val splits = tx.rawSplitRows
            val myPaid = payors.filter { it.userId == myId }.sumOf { it.currentAmountPaid }
            val myShare = splits.filter { it.userId == myId }.sumOf { it.amount }
            val memberPaid = payors.filter { it.userId == memberId }.sumOf { it.currentAmountPaid }
            val memberShare = splits.filter { it.userId == memberId }.sumOf { it.amount }

            if (myPaid > myShare) {
                val mySurplus = myPaid - myShare
                val totalDeficit = splits.sumOf { s ->
                    val p = payors.filter { it.userId == s.userId }.sumOf { it.currentAmountPaid }
                    if (s.amount > p) s.amount - p else 0.0
                }
                if (totalDeficit > 0) {
                    val memberDeficit = if (memberShare > memberPaid) memberShare - memberPaid else 0.0
                    if (memberDeficit > epsilon) {
                        val owesToMe = (memberDeficit / totalDeficit) * mySurplus
                        if (owesToMe > epsilon) {
                            transactionListForMember.add(TransactionWithBalance(tx, owesToMe, true))
                            receivableTotal += owesToMe
                        }
                    }
                }
            } else if (memberPaid > memberShare) {
                val memberSurplus = memberPaid - memberShare
                val totalDeficit = splits.sumOf { s ->
                    val p = payors.filter { it.userId == s.userId }.sumOf { it.currentAmountPaid }
                    if (s.amount > p) s.amount - p else 0.0
                }
                if (totalDeficit > 0) {
                    val myDeficit = if (myShare > myPaid) myShare - myPaid else 0.0
                    if (myDeficit > epsilon) {
                        val owesToMember = (myDeficit / totalDeficit) * memberSurplus
                        if (owesToMember > epsilon) {
                            transactionListForMember.add(TransactionWithBalance(tx, -owesToMember, false))
                            debtTotal += owesToMember
                        }
                    }
                }
            }
        }
        
        memberReceivableTotal = receivableTotal
        memberDebtTotal = debtTotal
        tvReceivableAmount.text = CurrencyUtils.formatAmountWithCurrency(receivableTotal)
        tvDebtAmount.text = CurrencyUtils.formatAmountWithCurrency(debtTotal)
        transactionListForMember.sortByDescending { it.transaction.timestamp }
        prepareDisplayList()
        
        if (transactionListForMember.isNotEmpty()) {
            summaryContainer.visibility = View.VISIBLE
            tvTransactionsHeader.visibility = View.VISIBLE
            btnSelectAll.visibility = View.VISIBLE
            rvTransactions.visibility = View.VISIBLE
            amountInputContainer.visibility = View.VISIBLE
            btnSettle.visibility = View.VISIBLE
            selectedTransactions.addAll(transactionListForMember.map { it.transaction })
            updateTotalSelected()
        } else {
            summaryContainer.visibility = View.GONE
            tvTransactionsHeader.visibility = View.GONE
            btnSelectAll.visibility = View.GONE
            rvTransactions.visibility = View.GONE
            amountInputContainer.visibility = View.GONE
            btnSettle.visibility = View.GONE
        }
    }

    private fun prepareDisplayList() {
        displayList.clear()
        val (receivables, debts) = transactionListForMember.partition { it.isCurrentUserOwed }
        
        if (receivables.isNotEmpty()) {
            displayList.add(getString(R.string.label_member_owes_you))
            displayList.addAll(receivables)
        }
        if (debts.isNotEmpty()) {
            displayList.add(getString(R.string.label_you_owe_member))
            displayList.addAll(debts)
        }
        (rvTransactions.adapter as? TransactionAdapter)?.updateItems(displayList)
    }

    private fun toggleSelectAll() {
        if (selectedTransactions.size == transactionListForMember.size) {
            selectedTransactions.clear()
        } else {
            selectedTransactions.addAll(transactionListForMember.map { it.transaction })
        }
        (rvTransactions.adapter as? TransactionAdapter)?.notifyDataSetChanged()
        updateTotalSelected()
    }

    private fun updateTotalSelected() {
        val totalReceivable = transactionListForMember.filter { it.isCurrentUserOwed && selectedTransactions.contains(it.transaction) }
            .sumOf { it.balanceWithMember }
        val totalDebt = transactionListForMember.filter { !it.isCurrentUserOwed && selectedTransactions.contains(it.transaction) }
            .sumOf { kotlin.math.abs(it.balanceWithMember) }
        
        tvReceivableFooterAmount.text = CurrencyUtils.formatAmountWithCurrency(totalReceivable)
        tvComputedTotal.text = CurrencyUtils.formatAmountWithCurrency(totalDebt)
        
        val count = selectedTransactions.size
        
        // Update "Select all" text based on selection state
        if (transactionListForMember.isNotEmpty() && count == transactionListForMember.size) {
            btnSelectAll.text = getString(R.string.action_unselect_all)
        } else {
            btnSelectAll.text = getString(R.string.action_select_all)
        }

        tvTotalSummaryNote.text = getString(R.string.transactions_selected_format, count)
        
        btnSettle.isEnabled = (totalDebt > epsilon && selectedTransactions.isNotEmpty())
        btnSettle.alpha = if (btnSettle.isEnabled) 1.0f else 0.5f
        
        // Ensure backgrounds update by notifying adapter
        (rvTransactions.adapter as? TransactionAdapter)?.notifyDataSetChanged()
    }

    private fun performBatchSettlement() {
        val myId = currentUserId ?: return
        val memberId = selectedMember?.user?.id ?: return
        loadingLayout.visibility = View.VISIBLE
        btnSettle.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sortedSelections = transactionListForMember
                    .filter { selectedTransactions.contains(it.transaction) }
                    .sortedBy { it.transaction.timestamp }

                for (item in sortedSelections) {
                    val tx = item.transaction
                    val txId = tx.transactionId ?: continue
                    val amountToApply = kotlin.math.abs(item.balanceWithMember)
                    val debtorId = if (item.balanceWithMember > 0) memberId else myId
                    val creditorId = if (item.balanceWithMember > 0) myId else memberId
                    val userOwedMap = tx.rawSplitRows.groupBy { it.userId }.mapValues { it.value.sumOf { s -> s.amount } }
                    val totalOwedByDebtor = userOwedMap[debtorId] ?: 0.0
                    val existingDebtorRow = tx.rawPayorRows.firstOrNull { it.userId == debtorId }
                    val newPaid = (existingDebtorRow?.currentAmountPaid ?: 0.0) + amountToApply
                    val excess = if (newPaid > totalOwedByDebtor + epsilon) newPaid - totalOwedByDebtor else 0.0
                    val status = when {
                        newPaid <= epsilon -> 0
                        newPaid >= totalOwedByDebtor - epsilon -> 1
                        else -> 2
                    }
                    if (existingDebtorRow != null) {
                        DeclareDatabase.transactionPayorsTable.update({
                            set("current_amount_paid", newPaid)
                            set("excess_amount", excess)
                            set<Int>("status", status)
                            set<Long>("paid_to", creditorId)
                        }) { filter { eq("transaction_id", txId); eq("user_id", debtorId) } }
                    } else {
                        DeclareDatabase.transactionPayorsTable.insert(TransactionPayorInsert(txId, debtorId, 0.0, newPaid, excess, null, status, creditorId))
                    }
                    val existingCreditorRow = tx.rawPayorRows.firstOrNull { it.userId == creditorId }
                    if (existingCreditorRow != null && existingCreditorRow.excessAmount > epsilon) {
                        val newExcess = kotlin.math.max(0.0, existingCreditorRow.excessAmount - amountToApply)
                        DeclareDatabase.transactionPayorsTable.update({ set("excess_amount", newExcess) }) { filter { eq("transaction_id", txId); eq("user_id", creditorId) } }
                    }
                    val allPayorsForTx = DeclareDatabase.transactionPayorsTable.select { filter { eq("transaction_id", txId) } }.decodeList<TransactionPayorTable>()
                    val allSettled = tx.rawSplitRows.all { s -> allPayorsForTx.filter { it.userId == s.userId }.sumOf { it.currentAmountPaid } >= s.amount - epsilon }
                    DeclareDatabase.transactionsTable.update({ set<Int>("status", if (allSettled) 3 else 2) }) { filter { eq("id", txId) } }
                }
                BalanceHelper.refreshUserBalance(myId)
                BalanceHelper.refreshUserBalance(memberId)
                withContext(Dispatchers.Main) {
                    loadingLayout.visibility = View.GONE
                    Toast.makeText(this@SettlementActivity, "Settlement successful", Toast.LENGTH_SHORT).show()
                    TransactionState.notifyChange()
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingLayout.visibility = View.GONE; btnSettle.isEnabled = true
                    Toast.makeText(this@SettlementActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun buildTransactions(cached: List<CachedTransaction>): List<RecentTransaction> {
        if (cached.isEmpty()) return emptyList()
        val txIds = cached.mapNotNull { it.id }
        val allPayors = DeclareDatabase.transactionPayorsTable.select { filter { isIn("transaction_id", txIds) } }.decodeList<TransactionPayorTable>()
        val allSplits = DeclareDatabase.transactionSplitsTable.select { filter { isIn("transaction_id", txIds) } }.decodeList<TransactionSplitTable>()
        val allItems = DeclareDatabase.transactionItemsTable.select { filter { isIn("transaction_id", txIds) } }.decodeList<TransactionItemFull>()
        val payorsByTx = allPayors.groupBy { it.transactionId }
        val splitsByTx = allSplits.groupBy { it.transactionId }
        val itemsByTx = allItems.groupBy { it.transactionId }
        return cached.mapNotNull { tx ->
            val txId = tx.id
            val payors = payorsByTx[txId] ?: emptyList()
            val splits = splitsByTx[txId] ?: emptyList()
            val items = itemsByTx[txId] ?: emptyList()
            val timestamp = try { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(tx.createdAt?.take(19) ?: "")?.time ?: 0L } catch (_: Exception) { 0L }
            RecentTransaction().apply {
                this.transactionId = txId
                this.timestamp = timestamp
                this.transactionItems = items
                this.rawPayorRows = payors
                this.rawSplitRows = splits
                this.mostRecentDetails = if (tx.description.isNullOrBlank() || items.size == 1) {
                    items.firstOrNull()?.category ?: tx.description
                } else {
                    tx.description
                }
                this.transactionStatus = if (splits.isNotEmpty() && splits.all { s -> payors.filter { it.userId == s.userId }.sumOf { it.currentAmountPaid } >= s.amount - epsilon }) "Settled" else "Pending"
                this.mostRecentDate = tx.createdAt?.let { 
                    try { 
                        val d = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(it.take(19))!!
                        SimpleDateFormat("MMM - d", Locale.getDefault()).format(d) 
                    } catch (_: Exception) { "" } 
                } ?: ""
            }
        }.sortedByDescending { it.timestamp }
    }

    private inner class MemberSelectionAdapter(private var items: List<MemberWithUser>, private val onMemberSelected: (MemberWithUser) -> Unit) : RecyclerView.Adapter<MemberSelectionAdapter.VH>() {
        private var selectedId: Long? = null
        fun updateMembers(newMembers: List<MemberWithUser>) { items = newMembers; notifyDataSetChanged() }
        fun setSelected(member: MemberWithUser) { selectedId = member.user.id; notifyDataSetChanged() }
        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.ivMemberIcon); val name: TextView = view.findViewById(R.id.tvMemberName)
            val card: androidx.cardview.widget.CardView = view.findViewById(R.id.profileCardView)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(LayoutInflater.from(parent.context).inflate(R.layout.item_member_selection, parent, false))
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.name.text = item.user.username
            
            if (item.user.profileImageUrl.isNullOrEmpty()) {
                holder.icon.setImageDrawable(null)
                holder.card.setCardBackgroundColor(ContextCompat.getColor(this@SettlementActivity, R.color.orange))
            } else {
                holder.icon.setPadding(0, 0, 0, 0)
                holder.card.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
                holder.icon.load(item.user.profileImageUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_profile_silhouette)
                    error(R.drawable.ic_profile_silhouette)
                    transformations(CircleCropTransformation())
                }
            }

            val isSelected = item.user.id == selectedId
            holder.itemView.alpha = if (isSelected) 1.0f else 0.5f
            holder.name.setTypeface(null, if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            holder.itemView.setOnClickListener { onMemberSelected(item) }
        }
        override fun getItemCount() = items.size
    }

    private inner class TransactionAdapter(private var items: List<Any>, private val onToggled: (RecentTransaction, Boolean) -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val TYPE_HEADER = 0; private val TYPE_ITEM = 1
        fun updateItems(newItems: List<Any>) { this.items = newItems; notifyDataSetChanged() }
        override fun getItemViewType(position: Int): Int {
            return if (items[position] is String) TYPE_HEADER else TYPE_ITEM
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == TYPE_HEADER) {
                HeaderVH(LayoutInflater.from(parent.context).inflate(R.layout.item_settlement_header, parent, false))
            } else {
                ItemVH(LayoutInflater.from(parent.context).inflate(R.layout.item_multi_settle_transaction, parent, false))
            }
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val data = items[position]
            if (holder is HeaderVH && data is String) {
                holder.label.text = data
                val isReceivable = data.contains(getString(R.string.label_member_owes_you), ignoreCase = true)
                holder.label.setTextColor(ContextCompat.getColor(this@SettlementActivity, if (isReceivable) R.color.green else R.color.red))
                holder.label.backgroundTintList = ContextCompat.getColorStateList(this@SettlementActivity, if (isReceivable) R.color.paid_bg else R.color.unpaid_bg)
            } else if (holder is ItemVH && data is TransactionWithBalance) {
                val tx = data.transaction; holder.tvTitle.text = tx.mostRecentDetails; holder.tvDate.text = tx.mostRecentDate; val amount = kotlin.math.abs(data.balanceWithMember)
                holder.tvAmount.text = CurrencyUtils.formatAmountWithCurrency(amount)
                if (data.isCurrentUserOwed) {
                    holder.tvAmount.text = "+${holder.tvAmount.text}"; holder.tvAmount.setTextColor(ContextCompat.getColor(this@SettlementActivity, R.color.green))
                } else {
                    holder.tvAmount.setTextColor(ContextCompat.getColor(this@SettlementActivity, R.color.red))
                }
                holder.cbSelected.buttonTintList = ContextCompat.getColorStateList(this@SettlementActivity, R.color.orange)
                holder.cbSelected.setOnCheckedChangeListener(null); val isSelected = selectedTransactions.contains(tx); holder.cbSelected.isChecked = isSelected
                
                holder.itemView.setBackgroundResource(if (isSelected) R.drawable.orange_rounded_background else R.drawable.transaction_rounded_background)
                
                holder.cbSelected.setOnCheckedChangeListener { _, isChecked -> 
                    onToggled(tx, isChecked)
                    updateTotalSelected() // Trigger footer update
                }
                holder.itemView.setOnClickListener { holder.cbSelected.isChecked = !holder.cbSelected.isChecked }
            }
        }
        override fun getItemCount() = items.size
        inner class HeaderVH(view: View) : RecyclerView.ViewHolder(view) { val label: TextView = view.findViewById(R.id.tvHeaderLabel) }
        inner class ItemVH(view: View) : RecyclerView.ViewHolder(view) {
            val cbSelected: CheckBox = view.findViewById(R.id.cbSelected); val tvTitle: TextView = view.findViewById(R.id.tvTransactionTitle); val tvDate: TextView = view.findViewById(R.id.tvTransactionDate); val tvAmount: TextView = view.findViewById(R.id.tvTransactionAmount)
        }
    }
}
