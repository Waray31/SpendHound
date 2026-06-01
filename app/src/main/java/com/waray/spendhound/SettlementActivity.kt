package com.waray.spendhound

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
    private lateinit var btnSettle: View
    private lateinit var loadingLayout: View
    private lateinit var btnBack: View

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
        if (groupId == -1L) {
            finish()
            return
        }

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
        amountInputContainer = findViewById(R.id.amountInputContainer)
        tvComputedTotal = findViewById(R.id.tvComputedTotal)
        tvTotalSummaryNote = findViewById(R.id.tvTotalSummaryNote)
        btnSettle = findViewById(R.id.btnSettle)
        loadingLayout = findViewById(R.id.loadingLayout)
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
                }

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
            displayList.add(getString(R.string.label_she_owes_you))
            displayList.addAll(receivables)
        }
        if (debts.isNotEmpty()) {
            displayList.add(getString(R.string.label_you_owe_her))
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
        val net = totalReceivable - totalDebt
        val absoluteNet = kotlin.math.abs(net)
        tvComputedTotal.text = CurrencyUtils.formatAmountWithCurrency(totalDebt + totalReceivable)
        val count = selectedTransactions.size
        
        // Update "Select all" text based on selection state
        if (transactionListForMember.isNotEmpty() && count == transactionListForMember.size) {
            btnSelectAll.text = getString(R.string.action_unselect_all)
        } else {
            btnSelectAll.text = getString(R.string.action_select_all)
        }

        val typeStr = if (net >= 0) getString(R.string.label_receivable) else getString(R.string.label_debt_lowercase)
        tvTotalSummaryNote.text = getString(R.string.transactions_selected_net_format, count, CurrencyUtils.formatAmountWithCurrency(absoluteNet), typeStr)
        btnSettle.isEnabled = (absoluteNet > epsilon && selectedTransactions.isNotEmpty())
        btnSettle.alpha = if (btnSettle.isEnabled) 1.0f else 0.5f
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
                this.transactionId = txId; this.timestamp = timestamp; this.transactionItems = items; this.rawPayorRows = payors; this.rawSplitRows = splits; this.mostRecentDetails = tx.description; this.transactionStatus = if (splits.isNotEmpty() && splits.all { s -> payors.filter { it.userId == s.userId }.sumOf { it.currentAmountPaid } >= s.amount - epsilon }) "Settled" else "Pending"; this.mostRecentDate = tx.createdAt?.let { try { val d = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(it.take(19))!!; SimpleDateFormat("MMM - d", Locale.getDefault()).format(d) } catch (_: Exception) { "" } } ?: ""
            }
        }.sortedByDescending { it.timestamp }
    }

    private inner class MemberSelectionAdapter(private var items: List<MemberWithUser>, private val onMemberSelected: (MemberWithUser) -> Unit) : RecyclerView.Adapter<MemberSelectionAdapter.VH>() {
        private var selectedId: Long? = null
        fun updateMembers(newMembers: List<MemberWithUser>) { items = newMembers; notifyDataSetChanged() }
        fun setSelected(member: MemberWithUser) { selectedId = member.user.id; notifyDataSetChanged() }
        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.ivMemberIcon); val name: TextView = view.findViewById(R.id.tvMemberName)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(LayoutInflater.from(parent.context).inflate(R.layout.item_member_selection, parent, false))
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.name.text = item.user.username
            
            if (item.user.profileImageUrl.isNullOrEmpty()) {
                holder.icon.setImageResource(R.drawable.ic_profile_silhouette)
                holder.icon.setBackgroundResource(R.drawable.circular_button_background)
                holder.icon.backgroundTintList = ContextCompat.getColorStateList(this@SettlementActivity, R.color.orange)
                holder.icon.setPadding(8, 8, 8, 8)
            } else {
                holder.icon.background = null
                holder.icon.setPadding(0, 0, 0, 0)
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
        override fun getItemViewType(position: Int) = if (items[position] is String) TYPE_HEADER else TYPE_ITEM
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = if (viewType == TYPE_HEADER) HeaderVH(LayoutInflater.from(parent.context).inflate(R.layout.item_settlement_header, parent, false)) else ItemVH(LayoutInflater.from(parent.context).inflate(R.layout.item_multi_settle_transaction, parent, false))
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val data = items[position]
            if (holder is HeaderVH && data is String) {
                holder.label.text = data
                val isReceivable = data.contains(getString(R.string.label_she_owes_you), ignoreCase = true)
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
                
                // Update background based on selection
                if (isSelected) {
                    holder.itemView.setBackgroundResource(R.drawable.orange_rounded_background)
                } else {
                    holder.itemView.setBackgroundResource(R.drawable.transaction_rounded_background)
                }

                holder.cbSelected.setOnCheckedChangeListener { _, isChecked -> onToggled(tx, isChecked) }
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
