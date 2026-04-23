package com.waray.spendhound

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.waray.spendhound.ui.group.GroupDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GroupsActivity : AppCompatActivity() {

    private val groupDetailViewModel: GroupDetailViewModel by viewModels()

    private lateinit var rvGroups: RecyclerView
    private lateinit var rvSkeleton: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var fabCreateGroup: FloatingActionButton
    private lateinit var tvGroupCount: TextView

    private var currentUserId: Long? = null
    private var allUsers: List<User> = emptyList()
    private val groups = mutableListOf<Pair<PayerGroup, List<User>>>()

    data class GroupCardData(
        val totalExpenses: Double,
        val activeCount: Int,
        val settledAmount: Double,
        val unreadCount: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_groups)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        tvGroupCount = findViewById(R.id.tvGroupCount)

        rvGroups = findViewById(R.id.rvGroups)
        rvSkeleton = findViewById(R.id.rvSkeleton)
        emptyState = findViewById(R.id.emptyState)
        fabCreateGroup = findViewById(R.id.fabCreateGroup)
        fabCreateGroup.visibility = View.GONE

        rvSkeleton.layoutManager = LinearLayoutManager(this)
        rvSkeleton.adapter = SkeletonAdapter(R.layout.item_skeleton_group)
        rvGroups.layoutManager = LinearLayoutManager(this)
        findViewById<TextView>(R.id.tvAddNewGroup).setOnClickListener {
            startActivity(android.content.Intent(this, CreateGroupActivity::class.java))
        }

        fetchCurrentUser()
    }

    override fun onResume() {
        super.onResume()
        if (currentUserId != null) loadGroups()
    }

    private fun fetchCurrentUser() {
        val authId = DeclareDatabase.auth.currentUserOrNull()?.id ?: return
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val user = DeclareDatabase.usersTable.select {
                    filter { eq("auth_id", authId) }
                }.decodeSingleOrNull<User>()
                currentUserId = user?.id
                allUsers = DeclareDatabase.usersTable.select {
                    // select only needed columns
                }.decodeList<User>()
                loadGroups()
            } catch (e: Exception) {
                runOnUiThread { hideLoading() }
                toast("Failed to load user: ${e.message}")
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadGroups() {
        val userId = currentUserId ?: return
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val myGroupIds = DeclareDatabase.groupMembersTable.select {
                    filter { eq("user_id", userId) }
                }.decodeList<GroupMember>().mapNotNull { it.groupId }.toSet()

                val allGroups = DeclareDatabase.groupsTable.select().decodeList<PayerGroup>()
                    .filter { it.groupId in myGroupIds }

                val allMembers = DeclareDatabase.groupMembersTable.select {
                    filter { isIn("group_id", myGroupIds.toList()) }
                }.decodeList<GroupMember>()
                val membersByGroup = allMembers.groupBy { it.groupId }

                groups.clear()
                for (group in allGroups) {
                    val memberIds = membersByGroup[group.groupId]?.mapNotNull { it.userId } ?: emptyList()
                    val members = allUsers.filter { it.id in memberIds }
                    groups.add(Pair(group, members))
                }

                // Pre-fetch all card data before showing the list
                val groupIds = allGroups.mapNotNull { it.groupId }
                val allTransactions = if (groupIds.isNotEmpty()) DeclareDatabase.transactionsTable.select {
                    filter { isIn("group_id", groupIds) }
                    order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                }.decodeList<com.waray.spendhound.ui.multi_transaction.TransactionFull>() else emptyList()
                val allTxIds = allTransactions.mapNotNull { it.id }
                val allPayors = if (allTxIds.isNotEmpty()) DeclareDatabase.transactionPayorsTable.select {
                    filter { isIn("transaction_id", allTxIds) }
                }.decodeList<com.waray.spendhound.ui.multi_transaction.TransactionPayorTable>() else emptyList()

                val uid = currentUserId
                val allMessages = if (uid != null && groupIds.isNotEmpty()) DeclareDatabase.groupMessagesTable.select {
                    filter { isIn("group_id", groupIds); eq("is_deleted", false) }
                    order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    limit(500)
                }.decodeList<GroupMessage>().filter { it.userId != uid } else emptyList()
                val readIds = if (uid != null) DeclareDatabase.messageReadsTable.select {
                    filter { eq("user_id", uid) }
                }.decodeList<MessageRead>().mapNotNull { it.messageId }.toSet() else emptySet()

                val cardDataMap = mutableMapOf<Long, GroupCardData>()
                for (group in allGroups) {
                    val gid = group.groupId ?: continue
                    val txs = allTransactions.filter { it.groupId == gid }
                    val txIds = txs.mapNotNull { it.id }
                    val totalExpenses = txs.sumOf { it.totalAmount }
                    val activeCount = txs.count { (it.status ?: 0) == 2 }
                    val settledAmount = allPayors.filter { it.transactionId in txIds && it.status == 1 }.sumOf { it.currentAmountPaid }
                    val unreadCount = allMessages.count { it.groupId == gid && it.id != null && it.id !in readIds }
                    cardDataMap[gid] = GroupCardData(totalExpenses, activeCount, settledAmount, unreadCount)
                }

                runOnUiThread {
                    val isEmpty = groups.isEmpty()
                    emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
                    rvGroups.visibility = if (isEmpty) View.GONE else View.VISIBLE
                    tvGroupCount.text = "${groups.size} group${if (groups.size != 1) "s" else ""}"
                    if (!isEmpty) rvGroups.adapter = GroupAdapter(groups, cardDataMap)
                    hideLoading()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    toast("Failed to load groups: ${e.message}")
                    hideLoading()
                }
            }
        }
    }

    private fun launchEditGroup(position: Int) {
        val (group, _) = groups[position]
        val intent = android.content.Intent(this, EditGroupActivity::class.java).apply {
            putExtra(EditGroupActivity.EXTRA_GROUP_ID, group.groupId ?: return)
            putExtra(EditGroupActivity.EXTRA_GROUP_NAME, group.groupName ?: "")
            putExtra(EditGroupActivity.EXTRA_GROUP_IMAGE, group.groupImageUrl)
        }
        startActivity(intent)
    }

    private fun showLoading() { rvSkeleton.visibility = View.VISIBLE; rvGroups.visibility = View.GONE }
    private fun hideLoading() { rvSkeleton.visibility = View.GONE }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private inner class GroupAdapter(
        private val items: List<Pair<PayerGroup, List<User>>>,
        private val cardDataMap: Map<Long, GroupCardData>
    ) : RecyclerView.Adapter<GroupAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.groupName)
            val members: TextView = view.findViewById(R.id.groupMembers)
            val tvTotalExpenses: TextView = view.findViewById(R.id.tvTotalExpenses)
            val tvActiveTransactions: TextView = view.findViewById(R.id.tvActiveTransactions)
            val ivGroupIcon: ImageView = view.findViewById(R.id.ivGroupIcon)
            val settledProgressBar: android.widget.ProgressBar = view.findViewById(R.id.settledProgressBar)
            val tvSettledRatio: TextView = view.findViewById(R.id.tvSettledRatio)
            val tvUnreadBadge: TextView = view.findViewById(R.id.tvUnreadBadge)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(layoutInflater.inflate(R.layout.item_group, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (group, members) = items[position]
            holder.name.text = group.groupName ?: "Unnamed"
            holder.members.text = if (members.isEmpty()) "No members"
            else members.joinToString(", ") { it.username ?: "?" }

            holder.itemView.setOnClickListener {
                val gid = group.groupId ?: return@setOnClickListener
                // Preload group data before transition
                currentUserId?.let { uid -> groupDetailViewModel.preloadGroup(gid, uid) }
                val intent = android.content.Intent(this@GroupsActivity, GroupDetailActivity::class.java).apply {
                    putExtra(GroupDetailActivity.EXTRA_GROUP_ID, gid)
                }
                startActivity(intent)
            }

            if (!group.groupImageUrl.isNullOrBlank()) {
                holder.ivGroupIcon.imageTintList = null
                holder.ivGroupIcon.load(group.groupImageUrl) {
                    crossfade(true)
                    placeholder(R.drawable.skeleton_shape)
                    error(R.drawable.add_group)
                    transformations(RoundedCornersTransformation(48f))
                }
            } else {
                holder.ivGroupIcon.setImageResource(R.drawable.add_group)
                holder.ivGroupIcon.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#7B2FBE"))
            }

            val data = cardDataMap[group.groupId] ?: GroupCardData(0.0, 0, 0.0, 0)
            holder.tvTotalExpenses.text = CurrencyUtils.formatAmountWithCurrency(data.totalExpenses)
            holder.tvActiveTransactions.text = data.activeCount.toString()
            holder.tvSettledRatio.text = "${CurrencyUtils.formatAmountWithCurrency(data.settledAmount)} / ${CurrencyUtils.formatAmountWithCurrency(data.totalExpenses)}"
            val progress = if (data.totalExpenses > 0) ((data.settledAmount / data.totalExpenses) * 100).toInt() else 0
            holder.settledProgressBar.progress = progress
            if (data.unreadCount > 0) {
                holder.tvUnreadBadge.visibility = View.VISIBLE
                holder.tvUnreadBadge.text = data.unreadCount.toString()
            } else {
                holder.tvUnreadBadge.visibility = View.GONE
            }
        }
    }
}
