package com.waray.spendhound

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.waray.spendhound.ui.group.MemberWithUser
import com.waray.spendhound.ui.multi_transaction.TransactionPayorInsert
import com.waray.spendhound.ui.multi_transaction.TransactionPayorTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MultiSettleBottomSheet(
    private val members: List<MemberWithUser>,
    private val transactions: List<RecentTransaction>
) : BottomSheetDialogFragment() {

    private lateinit var rvMembers: RecyclerView
    private lateinit var rvTransactions: RecyclerView
    private lateinit var tvSummary: TextView
    private lateinit var summaryContainer: View
    private lateinit var tvTransactionsHeader: TextView
    private lateinit var amountInputContainer: View
    private lateinit var etSettleAmount: TextInputEditText
    private lateinit var tvTotalSelected: TextView
    private lateinit var btnSettle: View
    private lateinit var loadingLayout: View

    private var selectedMember: MemberWithUser? = null
    private var currentUserId: Long? = null
    private var memberBalances: Map<Long, Double> = emptyMap()
    private val selectedTransactions = mutableSetOf<RecentTransaction>()
    private var transactionListForMember = mutableListOf<TransactionWithBalance>()

    private val epsilon = 0.01

    data class TransactionWithBalance(
        val transaction: RecentTransaction,
        val balanceWithMember: Double, // Positive if member owes current user, negative if current user owes member
        val isCurrentUserOwed: Boolean
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_multi_settle, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvMembers = view.findViewById(R.id.rvMembers)
        rvTransactions = view.findViewById(R.id.rvTransactions)
        tvSummary = view.findViewById(R.id.tvSummary)
        summaryContainer = view.findViewById(R.id.summaryContainer)
        tvTransactionsHeader = view.findViewById(R.id.tvTransactionsHeader)
        amountInputContainer = view.findViewById(R.id.amountInputContainer)
        etSettleAmount = view.findViewById(R.id.etSettleAmount)
        tvTotalSelected = view.findViewById(R.id.tvTotalSelected)
        btnSettle = view.findViewById(R.id.btnSettle)
        loadingLayout = view.findViewById(R.id.loadingLayout)

        view.findViewById<View>(R.id.btnClose).setOnClickListener { dismiss() }

        setupMembersList()
        resolveCurrentUserAndBalances()

        btnSettle.setOnClickListener { performBatchSettlement() }

        etSettleAmount.addTextChangedListener {
            validateAndEnableSettle()
        }
    }

    private fun resolveCurrentUserAndBalances() {
        val authId = DeclareDatabase.auth.currentUserOrNull()?.id
        if (authId != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val user = DeclareDatabase.usersTable.select {
                        filter { eq("auth_id", authId) }
                    }.decodeSingleOrNull<User>()
                    currentUserId = user?.id
                    
                    if (currentUserId != null) {
                        calculateAllMemberBalances()
                        withContext(Dispatchers.Main) {
                            val membersWithBalance = members.filter { 
                                val balance = memberBalances[it.user.id] ?: 0.0
                                kotlin.math.abs(balance) > epsilon
                            }
                            (rvMembers.adapter as MemberSelectionAdapter).updateMembers(membersWithBalance)
                            if (membersWithBalance.isNotEmpty()) {
                                selectMember(membersWithBalance[0])
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun calculateAllMemberBalances() {
        val balances = mutableMapOf<Long, Double>()
        val myId = currentUserId ?: return
        
        // Filter pending transactions
        val pendingTransactions = transactions.filter { it.transactionStatus != "Settled" }

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
                // I have a surplus, others owe me
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
                // I have a deficit, I owe others
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

    private fun setupMembersList() {
        rvMembers.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvMembers.adapter = MemberSelectionAdapter(emptyList()) { member ->
            selectMember(member)
        }
    }

    private fun selectMember(member: MemberWithUser) {
        selectedMember = member
        (rvMembers.adapter as MemberSelectionAdapter).setSelected(member)
        
        val balance = memberBalances[member.user.id] ?: 0.0
        summaryContainer.visibility = View.VISIBLE
        val amountStr = CurrencyUtils.formatAmountWithCurrency(kotlin.math.abs(balance))
        val memberName = member.user.username ?: "This member"
        
        if (balance > epsilon) {
            tvSummary.text = String.format(Locale.getDefault(), "%s owes you a total of %s", memberName, amountStr)
            tvSummary.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
        } else if (balance < -epsilon) {
            tvSummary.text = String.format(Locale.getDefault(), "You owe %s a total of %s", memberName, amountStr)
            tvSummary.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
        } else {
            tvSummary.text = String.format(Locale.getDefault(), "You and %s are all settled", memberName)
            tvSummary.setTextColor(ContextCompat.getColor(requireContext(), R.color.darkBlue))
        }
        
        loadTransactionsForMember(member)
    }

    private fun loadTransactionsForMember(member: MemberWithUser) {
        val myId = currentUserId ?: return
        val memberId = member.user.id ?: return
        
        transactionListForMember.clear()
        selectedTransactions.clear()
        
        val pendingTransactions = transactions.filter { it.transactionStatus != "Settled" }
        
        for (tx in pendingTransactions) {
            val payors = tx.rawPayorRows
            val splits = tx.rawSplitRows
            
            val myPaid = payors.filter { it.userId == myId }.sumOf { it.currentAmountPaid }
            val myShare = splits.filter { it.userId == myId }.sumOf { it.amount }
            
            val memberPaid = payors.filter { it.userId == memberId }.sumOf { it.currentAmountPaid }
            val memberShare = splits.filter { it.userId == memberId }.sumOf { it.amount }

            if (myPaid > myShare) {
                // I have surplus
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
                        }
                    }
                }
            } else if (memberPaid > memberShare) {
                // Member has surplus
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
                        }
                    }
                }
            }
        }
        
        // Sort by date (oldest first for FIFO)
        transactionListForMember.sortBy { it.transaction.timestamp }
        
        if (transactionListForMember.isNotEmpty()) {
            tvTransactionsHeader.visibility = View.VISIBLE
            rvTransactions.visibility = View.VISIBLE
            amountInputContainer.visibility = View.VISIBLE
            
            setupTransactionList()
            // Select all by default
            selectedTransactions.addAll(transactionListForMember.map { it.transaction })
            updateTotalSelected()
        } else {
            tvTransactionsHeader.visibility = View.GONE
            rvTransactions.visibility = View.GONE
            amountInputContainer.visibility = View.GONE
        }
    }

    private fun setupTransactionList() {
        rvTransactions.layoutManager = LinearLayoutManager(requireContext())
        rvTransactions.adapter = TransactionAdapter(transactionListForMember) { tx, isSelected ->
            if (isSelected) selectedTransactions.add(tx) else selectedTransactions.remove(tx)
            updateTotalSelected()
        }
    }

    private fun updateTotalSelected() {
        val total = transactionListForMember.filter { selectedTransactions.contains(it.transaction) }
            .sumOf { kotlin.math.abs(it.balanceWithMember) }
        
        tvTotalSelected.text = String.format(Locale.getDefault(), "Total selected: %s", CurrencyUtils.formatAmountWithCurrency(total))
        etSettleAmount.setText(String.format(Locale.getDefault(), "%.2f", total))
        validateAndEnableSettle()
    }

    private fun validateAndEnableSettle() {
        val amount = etSettleAmount.text.toString().toDoubleOrNull() ?: 0.0
        btnSettle.isEnabled = amount > epsilon && selectedTransactions.isNotEmpty()
        btnSettle.alpha = if (btnSettle.isEnabled) 1.0f else 0.5f
    }

    private fun performBatchSettlement() {
        val totalToSettle = etSettleAmount.text.toString().toDoubleOrNull() ?: 0.0
        if (totalToSettle <= epsilon) return

        val myId = currentUserId ?: return
        val memberId = selectedMember?.user?.id ?: return
        
        loadingLayout.visibility = View.VISIBLE
        btnSettle.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var remainingAmount = totalToSettle
                
                // Process selected transactions in FIFO order
                val sortedSelections = transactionListForMember
                    .filter { selectedTransactions.contains(it.transaction) }
                    .sortedBy { it.transaction.timestamp }

                for (item in sortedSelections) {
                    if (remainingAmount <= epsilon) break
                    
                    val tx = item.transaction
                    val txId = tx.transactionId ?: continue
                    val absBalance = kotlin.math.abs(item.balanceWithMember)
                    val amountToApply = kotlin.math.min(remainingAmount, absBalance)
                    
                    // We need to update the payer row for whoever is the "debtor" in this transaction context
                    // If balanceWithMember > 0, member owes me. If < 0, I owe member.
                    val debtorId = if (item.balanceWithMember > 0) memberId else myId
                    val creditorId = if (item.balanceWithMember > 0) myId else memberId
                    
                    val userOwedMap = tx.rawSplitRows.groupBy { it.userId }.mapValues { it.value.sumOf { s -> s.amount } }
                    val totalOwedByDebtor = userOwedMap[debtorId] ?: 0.0
                    
                    val existingDebtorRow = tx.rawPayorRows.firstOrNull { it.userId == debtorId }
                    val currentPaid = existingDebtorRow?.currentAmountPaid ?: 0.0
                    val newPaid = currentPaid + amountToApply
                    
                    val excess = if (newPaid > totalOwedByDebtor + epsilon) newPaid - totalOwedByDebtor else 0.0
                    val status = when {
                        newPaid <= epsilon -> 0
                        newPaid >= totalOwedByDebtor - epsilon -> 1
                        else -> 2
                    }

                    // Update database
                    if (existingDebtorRow != null) {
                        DeclareDatabase.transactionPayorsTable.update({
                            set("current_amount_paid", newPaid)
                            set("excess_amount", excess)
                            set("status", status)
                            set("paid_to", creditorId)
                        }) { filter { eq("transaction_id", txId); eq("user_id", debtorId) } }
                    } else {
                        DeclareDatabase.transactionPayorsTable.insert(
                            TransactionPayorInsert(
                                transactionId = txId,
                                userId = debtorId,
                                initialAmountPaid = 0.0,
                                currentAmountPaid = newPaid,
                                excessAmount = excess,
                                transactionItemsId = null,
                                status = status,
                                paidTo = creditorId
                            )
                        )
                    }
                    
                    // If the creditor was already a payor with excess, reduce their excess
                    val existingCreditorRow = tx.rawPayorRows.firstOrNull { it.userId == creditorId }
                    if (existingCreditorRow != null && existingCreditorRow.excessAmount > epsilon) {
                        val newExcess = kotlin.math.max(0.0, existingCreditorRow.excessAmount - amountToApply)
                        DeclareDatabase.transactionPayorsTable.update({
                            set("excess_amount", newExcess)
                        }) { filter { eq("transaction_id", txId); eq("user_id", creditorId) } }
                    }

                    // Re-calculate transaction status
                    val allPayorsForTx = DeclareDatabase.transactionPayorsTable.select {
                        filter { eq("transaction_id", txId) }
                    }.decodeList<TransactionPayorTable>()
                    
                    val allSettled = tx.rawSplitRows.all { s ->
                        val p = allPayorsForTx.filter { it.userId == s.userId }.sumOf { it.currentAmountPaid }
                        p >= s.amount - epsilon
                    }
                    
                    if (allSettled) {
                        DeclareDatabase.transactionsTable.update({
                            set<Int>("status", 3) // Settled
                        }) { filter { eq("id", txId) } }
                    } else {
                        DeclareDatabase.transactionsTable.update({
                            set<Int>("status", 2) // Pending
                        }) { filter { eq("id", txId) } }
                    }

                    remainingAmount -= amountToApply
                }

                BalanceHelper.refreshUserBalance(myId)
                BalanceHelper.refreshUserBalance(memberId)

                withContext(Dispatchers.Main) {
                    loadingLayout.visibility = View.GONE
                    Toast.makeText(requireContext(), "Settlement successful", Toast.LENGTH_SHORT).show()
                    TransactionState.notifyChange()
                    dismiss()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingLayout.visibility = View.GONE
                    btnSettle.isEnabled = true
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private inner class MemberSelectionAdapter(
        private var items: List<MemberWithUser>,
        private val onMemberSelected: (MemberWithUser) -> Unit
    ) : RecyclerView.Adapter<MemberSelectionAdapter.VH>() {

        private var selectedId: Long? = null

        fun updateMembers(newMembers: List<MemberWithUser>) {
            items = newMembers
            notifyDataSetChanged()
        }

        fun setSelected(member: MemberWithUser) {
            selectedId = member.user.id
            notifyDataSetChanged()
        }

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.ivMemberIcon)
            val name: TextView = view.findViewById(R.id.tvMemberName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_member_selection, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.name.text = item.user.username
            holder.icon.load(item.user.profileImageUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_profile_silhouette)
                error(R.drawable.ic_profile_silhouette)
                transformations(CircleCropTransformation())
            }

            val isSelected = item.user.id == selectedId
            holder.itemView.alpha = if (isSelected) 1.0f else 0.5f
            holder.name.setTypeface(null, if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)

            holder.itemView.setOnClickListener { onMemberSelected(item) }
        }

        override fun getItemCount() = items.size
    }

    private inner class TransactionAdapter(
        private val items: List<TransactionWithBalance>,
        private val onToggled: (RecentTransaction, Boolean) -> Unit
    ) : RecyclerView.Adapter<TransactionAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val cbSelected: CheckBox = view.findViewById(R.id.cbSelected)
            val tvTitle: TextView = view.findViewById(R.id.tvTransactionTitle)
            val tvDate: TextView = view.findViewById(R.id.tvTransactionDate)
            val tvAmount: TextView = view.findViewById(R.id.tvTransactionAmount)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_multi_settle_transaction, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvTitle.text = item.transaction.mostRecentDetails
            holder.tvDate.text = item.transaction.mostRecentDate
            holder.tvAmount.text = CurrencyUtils.formatAmountWithCurrency(kotlin.math.abs(item.balanceWithMember))
            holder.tvAmount.setTextColor(ContextCompat.getColor(requireContext(), if (item.isCurrentUserOwed) R.color.green else R.color.red))
            
            holder.cbSelected.setOnCheckedChangeListener(null)
            holder.cbSelected.isChecked = selectedTransactions.contains(item.transaction)
            holder.cbSelected.setOnCheckedChangeListener { _, isChecked ->
                onToggled(item.transaction, isChecked)
            }
            
            holder.itemView.setOnClickListener {
                holder.cbSelected.isChecked = !holder.cbSelected.isChecked
            }
        }

        override fun getItemCount() = items.size
    }
}
