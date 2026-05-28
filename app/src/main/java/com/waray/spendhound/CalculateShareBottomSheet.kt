package com.waray.spendhound

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.waray.spendhound.ui.group.MemberWithUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CalculateShareBottomSheet(
    private val members: List<MemberWithUser>,
    private val transactions: List<RecentTransaction>
) : BottomSheetDialogFragment() {

    private lateinit var rvMembers: RecyclerView
    private lateinit var rvReceivables: RecyclerView
    private lateinit var rvDebts: RecyclerView
    private lateinit var tvEmptyReceivables: TextView
    private lateinit var tvEmptyDebts: TextView
    private lateinit var summaryContainer: View
    private lateinit var tvSummary: TextView
    private lateinit var tvSelectedMemberName: TextView
    private lateinit var tvNetTotal: TextView

    private var selectedMember: MemberWithUser? = null
    private var currentUserId: Long? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_calculate_share, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvMembers = view.findViewById(R.id.rvMembers)
        rvReceivables = view.findViewById(R.id.rvReceivables)
        rvDebts = view.findViewById(R.id.rvDebts)
        tvEmptyReceivables = view.findViewById(R.id.tvEmptyReceivables)
        tvEmptyDebts = view.findViewById(R.id.tvEmptyDebts)
        summaryContainer = view.findViewById(R.id.summaryContainer)
        tvSummary = view.findViewById(R.id.tvSummary)
        tvSelectedMemberName = view.findViewById(R.id.tvSelectedMemberName)
        tvNetTotal = view.findViewById(R.id.tvNetTotal)

        view.findViewById<View>(R.id.btnClose).setOnClickListener { dismiss() }

        setupMembersList()
        resolveCurrentUser()
        
        // Select first member by default
        if (members.isNotEmpty()) {
            selectMember(members[0])
        }
    }

    private fun resolveCurrentUser() {
        val authId = DeclareDatabase.auth.currentUserOrNull()?.id
        if (authId != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val user = DeclareDatabase.usersTable.select {
                        filter { eq("auth_id", authId) }
                    }.decodeSingleOrNull<User>()
                    currentUserId = user?.id
                    selectedMember?.let { member ->
                        withContext(Dispatchers.Main) { calculateAndDisplayShares(member) }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun setupMembersList() {
        rvMembers.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvMembers.adapter = MemberSelectionAdapter(members) { member ->
            selectMember(member)
        }
    }

    private fun selectMember(member: MemberWithUser) {
        selectedMember = member
        tvSelectedMemberName.text = member.user.username
        (rvMembers.adapter as MemberSelectionAdapter).setSelected(member)
        calculateAndDisplayShares(member)
    }

    private fun calculateAndDisplayShares(member: MemberWithUser) {
        val userId = member.user.id ?: return
        val receivables = mutableListOf<ShareItem>()
        val debts = mutableListOf<ShareItem>()
        var netTotal = 0.0
        var netWithMe = 0.0

        val memberNames = members.associate { it.user.id to (it.user.username ?: "Unknown") }

        // Filter pending transactions
        val pendingTransactions = transactions.filter { it.transactionStatus != "Settled" }

        for (tx in pendingTransactions) {
            val paidByMe = tx.rawPayorRows.filter { it.userId == userId }.sumOf { it.currentAmountPaid }
            val myShare = tx.rawSplitRows.filter { it.userId == userId }.sumOf { it.amount }

            if (paidByMe > myShare) {
                // I paid more than my share, I'm owed the difference
                val diff = paidByMe - myShare
                
                // Who owes me? Those who paid less than their share.
                val owers = tx.rawSplitRows.map { it.userId }.distinct()
                    .filter { uid -> 
                        val paid = tx.rawPayorRows.filter { it.userId == uid }.sumOf { it.currentAmountPaid }
                        val share = tx.rawSplitRows.filter { it.userId == uid }.sumOf { it.amount }
                        share > paid && uid != userId
                    }
                
                val owerNames = owers.map { memberNames[it] ?: "Unknown" }
                val subtitle = if (owerNames.isNotEmpty()) "Owed by: ${owerNames.joinToString(", ")}" else "Owed by others"
                receivables.add(ShareItem(tx.mostRecentDetails ?: "Transaction", subtitle, diff))
                netTotal += diff

                // Specifically calculate how much they owe "me" (current user)
                if (currentUserId != null && currentUserId != userId && owers.contains(currentUserId)) {
                    // This is complex because diff is shared among all owers. 
                    // Simple approach: if I'm an ower, the surplus from 'member' covers some of my deficit.
                    val totalDeficitInTx = tx.rawSplitRows.map { it.userId }.distinct().sumOf { uid ->
                        val paid = tx.rawPayorRows.filter { it.userId == uid }.sumOf { it.currentAmountPaid }
                        val share = tx.rawSplitRows.filter { it.userId == uid }.sumOf { it.amount }
                        if (share > paid) share - paid else 0.0
                    }
                    val myDeficit = tx.rawSplitRows.filter { it.userId == currentUserId }.sumOf { it.amount } - 
                                   tx.rawPayorRows.filter { it.userId == currentUserId }.sumOf { it.currentAmountPaid }
                    
                    if (totalDeficitInTx > 0) {
                        val shareOfSurplusToMe = (myDeficit / totalDeficitInTx) * diff
                        netWithMe -= shareOfSurplusToMe // I owe them
                    }
                }
            } else if (myShare > paidByMe) {
                // I paid less than my share, I owe the difference
                val diff = myShare - paidByMe
                
                // Who do I owe? Those who paid more than their share.
                val owedToList = tx.rawPayorRows.map { it.userId }.distinct()
                    .filter { uid ->
                        val paid = tx.rawPayorRows.filter { it.userId == uid }.sumOf { it.currentAmountPaid }
                        val share = tx.rawSplitRows.filter { it.userId == uid }.sumOf { it.amount }
                        paid > share && uid != userId
                    }
                
                val owedToNames = owedToList.map { memberNames[it] ?: "Unknown" }
                val subtitle = if (owedToNames.isNotEmpty()) "Owed to: ${owedToNames.joinToString(", ")}" else "Owed to others"
                debts.add(ShareItem(tx.mostRecentDetails ?: "Transaction", subtitle, diff))
                netTotal -= diff

                // Specifically calculate how much they are owed by "me" (current user)
                if (currentUserId != null && currentUserId != userId && owedToList.contains(currentUserId)) {
                    val totalSurplusInTx = tx.rawPayorRows.map { it.userId }.distinct().sumOf { uid ->
                        val paid = tx.rawPayorRows.filter { it.userId == uid }.sumOf { it.currentAmountPaid }
                        val share = tx.rawSplitRows.filter { it.userId == uid }.sumOf { it.amount }
                        if (paid > share) paid - share else 0.0
                    }
                    val mySurplus = tx.rawPayorRows.filter { it.userId == currentUserId }.sumOf { it.currentAmountPaid } - 
                                    tx.rawSplitRows.filter { it.userId == currentUserId }.sumOf { it.amount }
                    
                    if (totalSurplusInTx > 0) {
                        val shareOfDeficitToMe = (mySurplus / totalSurplusInTx) * diff
                        netWithMe += shareOfDeficitToMe // They owe me
                    }
                }
            }
        }

        rvReceivables.layoutManager = LinearLayoutManager(requireContext())
        rvReceivables.adapter = ShareBreakdownAdapter(receivables, resources.getColor(R.color.green, null))
        rvReceivables.visibility = if (receivables.isEmpty()) View.GONE else View.VISIBLE
        tvEmptyReceivables.visibility = if (receivables.isEmpty()) View.VISIBLE else View.GONE

        rvDebts.layoutManager = LinearLayoutManager(requireContext())
        rvDebts.adapter = ShareBreakdownAdapter(debts, resources.getColor(R.color.red, null))
        rvDebts.visibility = if (debts.isEmpty()) View.GONE else View.VISIBLE
        tvEmptyDebts.visibility = if (debts.isEmpty()) View.VISIBLE else View.GONE

        // Update Summary
        if (currentUserId != null && currentUserId != userId) {
            summaryContainer.visibility = View.VISIBLE
            val amountStr = CurrencyUtils.formatAmountWithCurrency(kotlin.math.abs(netWithMe))
            val memberName = member.user.username ?: "This member"
            if (netWithMe > 0.01) {
                tvSummary.text = "$memberName owes you a total of $amountStr"
                tvSummary.setTextColor(resources.getColor(R.color.green, null))
            } else if (netWithMe < -0.01) {
                tvSummary.text = "You owe $memberName a total of $amountStr"
                tvSummary.setTextColor(resources.getColor(R.color.red, null))
            } else {
                tvSummary.text = "You and $memberName are all settled"
                tvSummary.setTextColor(resources.getColor(R.color.darkBlue, null))
            }
        } else {
            summaryContainer.visibility = View.GONE
        }

        tvNetTotal.text = CurrencyUtils.formatAmountWithCurrency(netTotal)
        if (netTotal > 0.01) {
            tvNetTotal.setTextColor(resources.getColor(R.color.green, null))
        } else if (netTotal < -0.01) {
            tvNetTotal.setTextColor(resources.getColor(R.color.red, null))
        } else {
            tvNetTotal.setTextColor(resources.getColor(R.color.darkBlue, null))
        }
    }

    data class ShareItem(val description: String, val subtitle: String, val amount: Double)

    private inner class MemberSelectionAdapter(
        private val items: List<MemberWithUser>,
        private val onMemberSelected: (MemberWithUser) -> Unit
    ) : RecyclerView.Adapter<MemberSelectionAdapter.VH>() {

        private var selectedId: Long? = null

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

    private inner class ShareBreakdownAdapter(
        private val items: List<ShareItem>,
        private val amountColor: Int? = null
    ) : RecyclerView.Adapter<ShareBreakdownAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val description: TextView = view.findViewById(R.id.tvDescription)
            val subtitle: TextView = view.findViewById(R.id.tvSubtitle)
            val amount: TextView = view.findViewById(R.id.tvAmount)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_share_breakdown, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.description.text = item.description
            holder.subtitle.text = item.subtitle
            holder.amount.text = CurrencyUtils.formatAmountWithCurrency(item.amount)
            amountColor?.let { holder.amount.setTextColor(it) }
        }

        override fun getItemCount() = items.size
    }
}
