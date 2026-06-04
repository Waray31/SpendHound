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
import com.waray.spendhound.TransactionSettlementActivity
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import io.github.jan.supabase.postgrest.query.Order
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class SettlementActivity : AppCompatActivity() {

    private lateinit var rvGroups: RecyclerView
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
    private lateinit var btnHistory: View
    private lateinit var emptySettlementsLayout: View

    private var groupId: Long = -1
    private var groups: List<PayerGroup> = emptyList()
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
        
        loadData()

        btnSettle.setOnClickListener { showSettlementConfirmation() }
        btnSelectAll.setOnClickListener { toggleSelectAll() }
    }

    private fun showSettlementConfirmation() {
        val count = selectedTransactions.size
        val memberName = selectedMember?.user?.username ?: getString(R.string.placeholder_name)
        val totalToSettle = transactionListForMember
            .filter { selectedTransactions.contains(it.transaction) }
            .sumOf { kotlin.math.abs(it.balanceWithMember) }

        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_confirm_settlement)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val tvTitle = dialog.findViewById<TextView>(R.id.dialogTitle)
        val tvMessage = dialog.findViewById<TextView>(R.id.dialogMessage)
        val tvSummaryAmount = dialog.findViewById<TextView>(R.id.tvSummaryAmount)
        val tvSummaryCount = dialog.findViewById<TextView>(R.id.tvSummaryCount)
        val summaryLayout = dialog.findViewById<View>(R.id.settlementSummaryLayout)
        val btnConfirm = dialog.findViewById<android.widget.Button>(R.id.dialogConfirmBtn)
        val btnCancel = dialog.findViewById<android.widget.Button>(R.id.dialogCancelBtn)

        tvTitle.text = getString(R.string.dialog_confirm_settlement_title)
        tvMessage.text = getString(R.string.dialog_confirm_settlement_message, count, memberName)
        
        summaryLayout.visibility = View.VISIBLE
        tvSummaryAmount.text = CurrencyUtils.formatAmountWithCurrency(totalToSettle)
        tvSummaryCount.text = getString(R.string.transactions_selected_format, count)

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            performBatchSettlement()
        }
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        btnHistory = findViewById(R.id.btnHistory)
        rvGroups = findViewById(R.id.rvGroups)
        rvMembers = findViewById(R.id.rvMembers)
        rvTransactions = findViewById(R.id.rvTransactions)
        summaryContainer = findViewById(R.id.summaryContainer)
        tvBalanceTitle = findViewById(R.id.tvBalanceTitle)
        tvReceivableAmount = findViewById(R.id.tvReceivableAmount)
        tvDebtAmount = findViewById(R.id.tvDebtAmount)
        tvTransactionsHeader = findViewById(R.id.tvTransactionsHeader)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        amountInputContainer = findViewById(R.id.amountInputContainer)
        tvReceivableFooterAmount = findViewById(R.id.tvReceivableFooterAmount)
        tvComputedTotal = findViewById(R.id.tvComputedTotal)
        tvTotalSummaryNote = findViewById(R.id.tvTotalSummaryNote)
        btnSettle = findViewById(R.id.btnSettle)
        loadingLayout = findViewById(R.id.loadingLayout)
        emptySettlementsLayout = findViewById(R.id.emptySettlementsLayout)
    }

    private fun setupToolbar() {
        btnBack.setOnClickListener { finish() }
        btnHistory.setOnClickListener {
            val intent = android.content.Intent(this, SettlementHistoryActivity::class.java)
            intent.putExtra("group_id", groupId)
            startActivity(intent)
        }
    }

    private fun setupRecyclerViews() {
        rvGroups.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvGroups.adapter = GroupSelectionAdapter(emptyList()) { selectGroup(it) }

        rvMembers.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvMembers.adapter = MemberSelectionAdapter(emptyList()) { selectMember(it) }

        rvTransactions.layoutManager = LinearLayoutManager(this)
        rvTransactions.adapter = TransactionAdapter(emptyList(), { tx, isSelected ->
            if (isSelected) selectedTransactions.add(tx) else selectedTransactions.remove(tx)
            updateTotalSelected()
        }, { tx ->
            showSettleBottomSheet(tx)
        })
    }

    private fun showSettleBottomSheet(transaction: RecentTransaction) {
        val intent = Intent(this, TransactionSettlementActivity::class.java)
        intent.putExtra("EXTRA_TRANSACTION_JSON", Json.encodeToString(RecentTransaction.serializer(), transaction))
        startActivity(intent)
    }

    private fun loadData() {
        loadingLayout.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val authId = DeclareDatabase.auth.currentUserOrNull()?.id
                val user = if (authId != null) {
                    DeclareDatabase.usersTable.select {
                        filter { eq("auth_id", authId) }
                    }.decodeSingleOrNull<User>()
                } else null
                currentUserId = user?.id

                // 1. Load User's Groups
                val myId = currentUserId ?: return@launch
                val memberResult = DeclareDatabase.groupMembersTable.select {
                    filter { eq("user_id", myId) }
                }.decodeList<GroupMember>()
                
                val myGroupIds = memberResult.mapNotNull { it.groupId }.distinct()
                val allGroups = DeclareDatabase.groupsTable.select {
                    filter { isIn("group_id", myGroupIds) }
                }.decodeList<PayerGroup>()
                
                groups = allGroups

                withContext(Dispatchers.Main) {
                    (rvGroups.adapter as GroupSelectionAdapter).updateGroups(groups)
                    
                    // Auto-select group if passed via intent
                    val targetGroup = if (groupId != -1L) {
                        groups.find { it.groupId == groupId }
                    } else {
                        groups.firstOrNull()
                    }
                    
                    if (targetGroup != null) {
                        selectGroup(targetGroup)
                    } else {
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

    private fun selectGroup(group: PayerGroup) {
        groupId = group.groupId ?: -1
        (rvGroups.adapter as GroupSelectionAdapter).setSelected(group)
        loadGroupData()
    }

    private fun loadGroupData() {
        loadingLayout.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val currentGroupId = groupId
                if (currentGroupId == -1L) return@launch

                val memberResult = DeclareDatabase.groupMembersTable.select {
                    filter { eq("group_id", currentGroupId) }
                }.decodeList<GroupMember>()
                
                val userIds = memberResult.mapNotNull { it.userId }
                val users = DeclareDatabase.usersTable.select {
                    filter { isIn("user_id", userIds) }
                }.decodeList<User>().associateBy { it.id }
                
                val groupMembers = memberResult.mapNotNull { m ->
                    users[m.userId]?.let { u -> MemberWithUser(m, u) }
                }.filter { it.user.id != currentUserId }

                val repo = GroupRepository((application as SpendHoundApplication).database)
                repo.getTransactions(currentGroupId).collect { cached ->
                    val built = buildTransactions(cached)
                    withContext(Dispatchers.Main) {
                        if (groupId != currentGroupId) return@withContext
                        
                        members = groupMembers
                        fullTransactions = built
                        calculateAllMemberBalances()
                        (rvMembers.adapter as MemberSelectionAdapter).updateMembers(members)
                        
                        val firstWithBalance = members.firstOrNull { 
                            kotlin.math.abs(memberBalances[it.user.id] ?: 0.0) > epsilon 
                        }
                        
                        if (firstWithBalance != null) {
                            emptySettlementsLayout.visibility = View.GONE
                            selectMember(firstWithBalance)
                        } else {
                            // Check if anyone has balance
                            val anyBalance = members.any { m -> kotlin.math.abs(memberBalances[m.user.id] ?: 0.0) > epsilon }
                            if (anyBalance) {
                                emptySettlementsLayout.visibility = View.GONE
                                selectMember(members.first())
                            } else {
                                emptySettlementsLayout.visibility = View.VISIBLE
                                transactionListForMember.clear()
                                updateTotalSelected()
                                prepareDisplayList()
                                summaryContainer.visibility = View.GONE
                                tvTransactionsHeader.visibility = View.GONE
                                btnSelectAll.visibility = View.GONE
                                rvTransactions.visibility = View.GONE
                                amountInputContainer.visibility = View.GONE
                                btnSettle.visibility = View.GONE
                            }
                        }
                        
                        loadingLayout.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingLayout.visibility = View.GONE
                    Toast.makeText(this@SettlementActivity, "Error loading group data: ${e.message}", Toast.LENGTH_SHORT).show()
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

            val userTotalPaid = payors.groupBy { it.userId }.mapValues { it.value.sumOf { p -> p.currentAmountPaid } }
            val userTotalOwed = splits.groupBy { it.userId }.mapValues { it.value.sumOf { s -> s.amount } }

            val totalDeficit = userTotalOwed.keys.sumOf { uid ->
                val o = userTotalOwed[uid] ?: 0.0
                val p = userTotalPaid[uid] ?: 0.0
                if (o > p) o - p else 0.0
            }
            val totalSurplus = userTotalPaid.keys.sumOf { uid ->
                val p = userTotalPaid[uid] ?: 0.0
                val o = userTotalOwed[uid] ?: 0.0
                if (p > o) p - o else 0.0
            }

            if (myPaid > myShare) {
                val mySurplus = myPaid - myShare
                if (totalDeficit > 0) {
                    userTotalOwed.keys.forEach { uid ->
                        val o = userTotalOwed[uid] ?: 0.0
                        val p = userTotalPaid[uid] ?: 0.0
                        if (o > p) {
                            val deficit = o - p
                            val owesToMe = (deficit / totalDeficit) * mySurplus
                            balances[uid] = (balances[uid] ?: 0.0) + owesToMe
                        }
                    }
                }
            } else if (myShare > myPaid) {
                val myDeficit = myShare - myPaid
                if (totalSurplus > 0) {
                    userTotalPaid.keys.forEach { uid ->
                        val p = userTotalPaid[uid] ?: 0.0
                        val o = userTotalOwed[uid] ?: 0.0
                        if (p > o) {
                            val surplus = p - o
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
            
            val userTotalPaid = payors.groupBy { it.userId }.mapValues { it.value.sumOf { p -> p.currentAmountPaid } }
            val userTotalOwed = splits.groupBy { it.userId }.mapValues { it.value.sumOf { s -> s.amount } }
            
            val myPaid = userTotalPaid[myId] ?: 0.0
            val myShare = userTotalOwed[myId] ?: 0.0
            val memberPaid = userTotalPaid[memberId] ?: 0.0
            val memberShare = userTotalOwed[memberId] ?: 0.0

            if (myPaid > myShare) {
                val mySurplus = myPaid - myShare
                val totalDeficit = userTotalOwed.keys.sumOf { uid ->
                    val o = userTotalOwed[uid] ?: 0.0
                    val p = userTotalPaid[uid] ?: 0.0
                    if (o > p) o - p else 0.0
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
                val totalDeficit = userTotalOwed.keys.sumOf { uid ->
                    val o = userTotalOwed[uid] ?: 0.0
                    val p = userTotalPaid[uid] ?: 0.0
                    if (o > p) o - p else 0.0
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
        tvReceivableAmount.text = CurrencyUtils.formatAmountWithCurrency(kotlin.math.max(0.0, receivableTotal - debtTotal))
        tvDebtAmount.text = CurrencyUtils.formatAmountWithCurrency(kotlin.math.max(0.0, debtTotal - receivableTotal))
        transactionListForMember.sortByDescending { it.transaction.timestamp }
        prepareDisplayList()
        
        if (transactionListForMember.isNotEmpty()) {
            emptySettlementsLayout.visibility = View.GONE
            summaryContainer.visibility = View.VISIBLE
            tvTransactionsHeader.visibility = View.VISIBLE
            btnSelectAll.visibility = View.VISIBLE
            rvTransactions.visibility = View.VISIBLE
            amountInputContainer.visibility = View.VISIBLE
            btnSettle.visibility = View.VISIBLE
            selectedTransactions.addAll(transactionListForMember.map { it.transaction })
            updateTotalSelected()
        } else {
            emptySettlementsLayout.visibility = View.VISIBLE
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
        val totalReceivableSelected = transactionListForMember.filter { it.isCurrentUserOwed && selectedTransactions.contains(it.transaction) }
            .sumOf { it.balanceWithMember }
        val totalDebtSelected = transactionListForMember.filter { !it.isCurrentUserOwed && selectedTransactions.contains(it.transaction) }
            .sumOf { kotlin.math.abs(it.balanceWithMember) }
        
        // Net receivable is receivable - debt
        val netReceivable = totalReceivableSelected - totalDebtSelected
        
        tvReceivableFooterAmount.text = CurrencyUtils.formatAmountWithCurrency(kotlin.math.max(0.0, netReceivable))
        
        // Total to settle is total receivable + total debt (gross settlement volume)
        // OR as requested: total receivable + total debt. 
        // Note: The prompt says "total receivable is receivable - debt". 
        // And "total to settle should be total receivable + total debt".
        // This likely means: computedTotal = (receivable - debt) + debt = receivable? 
        // Or maybe it means (gross receivable) + (gross debt). 
        // Let's stick to the prompt's wording: total receivable + total debt.
        
        tvComputedTotal.text = CurrencyUtils.formatAmountWithCurrency(totalReceivableSelected + totalDebtSelected)
        
        val count = selectedTransactions.size
        
        // Update "Select all" text based on selection state
        if (transactionListForMember.isNotEmpty() && count == transactionListForMember.size) {
            btnSelectAll.text = getString(R.string.action_unselect_all)
        } else {
            btnSelectAll.text = getString(R.string.action_select_all)
        }

        tvTotalSummaryNote.text = getString(R.string.transactions_selected_format, count)
        
        btnSettle.isEnabled = ((totalReceivableSelected + totalDebtSelected) > epsilon && selectedTransactions.isNotEmpty())
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
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val batchTimestamp = sdf.format(java.util.Date())

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
                    val debtorRows = tx.rawPayorRows.filter { it.userId == debtorId }
                    val totalCurrentPaid = debtorRows.sumOf { it.currentAmountPaid }
                    val newPaid = totalCurrentPaid + amountToApply
                    val excess = if (newPaid > totalOwedByDebtor + epsilon) newPaid - totalOwedByDebtor else 0.0
                    val status = when {
                        newPaid <= epsilon -> 0
                        newPaid >= totalOwedByDebtor - epsilon -> 1
                        else -> 2
                    }
                    val leadDebtorRow = debtorRows.firstOrNull { it.transactionItemsId == null } ?: debtorRows.firstOrNull()
                    if (leadDebtorRow != null) {
                        val otherRowsPaid = debtorRows.filter { it.id != leadDebtorRow.id }.sumOf { it.currentAmountPaid }
                        val amountForLeadRow = newPaid - otherRowsPaid
                        DeclareDatabase.transactionPayorsTable.update({
                            set("current_amount_paid", amountForLeadRow)
                            set("excess_amount", excess)
                            set<Int>("status", status)
                            set<Long>("paid_to", creditorId)
                            set("updated_at", batchTimestamp)
                        }) { filter { eq("id", leadDebtorRow.id!!) } }

                        val otherRowIds = debtorRows.mapNotNull { it.id }.filter { it != leadDebtorRow.id }
                        if (otherRowIds.isNotEmpty()) {
                            DeclareDatabase.transactionPayorsTable.update({
                                set("excess_amount", 0.0)
                                set("updated_at", batchTimestamp)
                            }) { filter { isIn("id", otherRowIds) } }
                        }
                    } else {
                        DeclareDatabase.transactionPayorsTable.insert(TransactionPayorInsert(txId, debtorId, 0.0, newPaid, excess, null, status, creditorId, batchTimestamp))
                    }
                    val creditorRows = tx.rawPayorRows.filter { it.userId == creditorId }
                    val leadCreditorRow = creditorRows.firstOrNull { it.transactionItemsId == null } ?: creditorRows.firstOrNull()
                    if (leadCreditorRow != null) {
                        val totalExcess = creditorRows.map { it.excessAmount }.maxOrNull() ?: 0.0
                        if (totalExcess > epsilon) {
                            val newExcess = kotlin.math.max(0.0, totalExcess - amountToApply)
                            DeclareDatabase.transactionPayorsTable.update({ 
                                set("excess_amount", newExcess)
                                set("updated_at", batchTimestamp)
                            }) { filter { eq("id", leadCreditorRow.id!!) } }

                            val otherRowIds = creditorRows.mapNotNull { it.id }.filter { it != leadCreditorRow.id }
                            if (otherRowIds.isNotEmpty()) {
                                DeclareDatabase.transactionPayorsTable.update({
                                    set("excess_amount", 0.0)
                                    set("updated_at", batchTimestamp)
                                }) { filter { isIn("id", otherRowIds) } }
                            }
                        }
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
                    
                    // Clear selection and refresh list instead of finishing
                    selectedTransactions.clear()
                    loadGroupData()
                    updateTotalSelected()
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
        
        val allUserIds = (allPayors.map { it.userId } + allSplits.map { it.userId }).distinct()
        val usersById = DeclareDatabase.usersTable.select { filter { isIn("user_id", allUserIds) } }.decodeList<User>().associateBy { it.id }

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
                this.mostRecentDetails = if (tx.description.isNullOrBlank() || items.size == 1) {
                    items.firstOrNull()?.category ?: tx.description
                } else {
                    tx.description
                }
                this.mostRecentPaymentAmountStr = CurrencyUtils.formatAmountWithCurrency(tx.totalAmount)
                this.transactionStatus = if (splits.isNotEmpty() && splits.all { s -> payors.filter { it.userId == s.userId }.sumOf { it.currentAmountPaid } >= s.amount - epsilon }) "Settled" else "Pending"
                this.mostRecentDate = tx.createdAt?.let { 
                    try { 
                        val d = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(it.take(19))!!
                        SimpleDateFormat("MMM - d", Locale.getDefault()).format(d) 
                    } catch (_: Exception) { "" } 
                } ?: ""
                this.payorsList = payorNames.toMutableList() as MutableList<String?>
                this.payorUserIds = payorIds.toMutableList() as MutableList<String?>
                this.amountsPaidList = amountsPaid.toMutableList()
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

    private inner class GroupSelectionAdapter(private var items: List<PayerGroup>, private val onGroupSelected: (PayerGroup) -> Unit) : RecyclerView.Adapter<GroupSelectionAdapter.VH>() {
        private var selectedId: Long? = null
        fun updateGroups(newGroups: List<PayerGroup>) { items = newGroups; notifyDataSetChanged() }
        fun setSelected(group: PayerGroup) { selectedId = group.groupId; notifyDataSetChanged() }
        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.ivMemberIcon); val name: TextView = view.findViewById(R.id.tvMemberName)
            val card: androidx.cardview.widget.CardView = view.findViewById(R.id.profileCardView)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(LayoutInflater.from(parent.context).inflate(R.layout.item_member_selection, parent, false))
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.name.text = item.groupName
            
            val groupUrl = item.groupImageUrl
            if (groupUrl.isNullOrEmpty() || groupUrl == "placeholder_group_image") {
                holder.icon.setImageResource(R.drawable.add_group)
                holder.icon.setPadding(20, 20, 20, 20)
                holder.card.setCardBackgroundColor(ContextCompat.getColor(this@SettlementActivity, R.color.orange))
            } else {
                holder.icon.setPadding(0, 0, 0, 0)
                holder.card.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
                holder.icon.load(groupUrl) {
                    crossfade(true)
                    placeholder(R.drawable.add_group)
                    error(R.drawable.add_group)
                    transformations(CircleCropTransformation())
                }
            }

            val isSelected = item.groupId == selectedId
            holder.itemView.alpha = if (isSelected) 1.0f else 0.5f
            holder.name.setTypeface(null, if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            holder.itemView.setOnClickListener { onGroupSelected(item) }
        }
        override fun getItemCount() = items.size
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
            
            val profileUrl = item.user.profileImageUrl
            if (profileUrl.isNullOrEmpty() || profileUrl == "placeholder_profile_image") {
                holder.icon.setImageResource(R.drawable.ic_profile_silhouette)
                holder.icon.imageTintList = ContextCompat.getColorStateList(this@SettlementActivity, R.color.white)
                val padding = (8 * resources.displayMetrics.density).toInt()
                holder.icon.setPadding(padding, padding, padding, padding)
                holder.card.setCardBackgroundColor(ContextCompat.getColor(this@SettlementActivity, R.color.orange))
            } else {
                holder.icon.setPadding(0, 0, 0, 0)
                holder.icon.imageTintList = null
                holder.card.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
                holder.icon.load(profileUrl) {
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

    private inner class TransactionAdapter(private var items: List<Any>, private val onToggled: (RecentTransaction, Boolean) -> Unit, private val onLongClick: (RecentTransaction) -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
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
                
                holder.itemView.setBackgroundResource(if (isSelected) R.drawable.bg_unread_transaction else R.drawable.transaction_rounded_background)
                
                holder.cbSelected.setOnCheckedChangeListener { _, isChecked -> 
                    onToggled(tx, isChecked)
                    updateTotalSelected() // Trigger footer update
                }
                holder.itemView.setOnClickListener { holder.cbSelected.isChecked = !holder.cbSelected.isChecked }
                holder.itemView.setOnLongClickListener {
                    onLongClick(tx)
                    true
                }
            }
        }
        override fun getItemCount() = items.size
        inner class HeaderVH(view: View) : RecyclerView.ViewHolder(view) { val label: TextView = view.findViewById(R.id.tvHeaderLabel) }
        inner class ItemVH(view: View) : RecyclerView.ViewHolder(view) {
            val cbSelected: CheckBox = view.findViewById(R.id.cbSelected); val tvTitle: TextView = view.findViewById(R.id.tvTransactionTitle); val tvDate: TextView = view.findViewById(R.id.tvTransactionDate); val tvAmount: TextView = view.findViewById(R.id.tvTransactionAmount)
        }
    }
}
