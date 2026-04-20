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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.waray.spendhound.CurrencyUtils
import kotlinx.coroutines.launch

class GroupsActivity : AppCompatActivity() {

    private lateinit var rvGroups: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var loadingOverlay: View
    private lateinit var fabCreateGroup: FloatingActionButton
    private lateinit var tvGroupCount: TextView

    private var currentUserId: Long? = null
    private var allUsers: List<User> = emptyList()
    private val groups = mutableListOf<Pair<PayerGroup, List<User>>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_groups)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        tvGroupCount = findViewById(R.id.tvGroupCount)

        rvGroups = findViewById(R.id.rvGroups)
        emptyState = findViewById(R.id.emptyState)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        fabCreateGroup = findViewById(R.id.fabCreateGroup)

        rvGroups.layoutManager = LinearLayoutManager(this)
        fabCreateGroup.setOnClickListener {
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
        lifecycleScope.launch {
            try {
                val user = DeclareDatabase.usersTable.select {
                    filter { eq("auth_id", authId) }
                }.decodeSingleOrNull<User>()
                currentUserId = user?.id
                allUsers = DeclareDatabase.usersTable.select().decodeList<User>()
                loadGroups()
            } catch (e: Exception) {
                toast("Failed to load user: ${e.message}")
                hideLoading()
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadGroups() {
        val userId = currentUserId ?: return
        showLoading()
        lifecycleScope.launch {
            try {
                val myGroupIds = DeclareDatabase.groupMembersTable.select {
                    filter { eq("user_id", userId) }
                }.decodeList<GroupMember>().mapNotNull { it.groupId }.toSet()

                val allGroups = DeclareDatabase.groupsTable.select().decodeList<PayerGroup>()
                    .filter { it.groupId in myGroupIds }

                val allMembers = DeclareDatabase.groupMembersTable.select().decodeList<GroupMember>()
                val membersByGroup = allMembers.groupBy { it.groupId }

                groups.clear()
                for (group in allGroups) {
                    val memberIds = membersByGroup[group.groupId]?.mapNotNull { it.userId } ?: emptyList()
                    val members = allUsers.filter { it.id in memberIds }
                    groups.add(Pair(group, members))
                }

                runOnUiThread {
                    val isEmpty = groups.isEmpty()
                    emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
                    rvGroups.visibility = if (isEmpty) View.GONE else View.VISIBLE
                    tvGroupCount.text = "${groups.size} group${if (groups.size != 1) "s" else ""}"
                    if (!isEmpty) {
                        rvGroups.adapter = GroupAdapter(groups)
                    }
                    hideLoading()
                }
            } catch (e: Exception) {
                toast("Failed to load groups: ${e.message}")
                hideLoading()
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

    private fun showLoading() { loadingOverlay.visibility = View.VISIBLE }
    private fun hideLoading() { loadingOverlay.visibility = View.GONE }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private inner class GroupAdapter(
        private val items: List<Pair<PayerGroup, List<User>>>
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
                val intent = android.content.Intent(this@GroupsActivity, GroupDetailActivity::class.java).apply {
                    putExtra(GroupDetailActivity.EXTRA_GROUP_ID, group.groupId ?: return@setOnClickListener)
                }
                startActivity(intent)
            }

            if (!group.groupImageUrl.isNullOrBlank()) {
                holder.ivGroupIcon.imageTintList = null
                Glide.with(this@GroupsActivity)
                    .load(group.groupImageUrl)
                    .transform(com.bumptech.glide.load.resource.bitmap.CenterCrop(), com.bumptech.glide.load.resource.bitmap.RoundedCorners(48))
                    .into(holder.ivGroupIcon)
            } else {
                holder.ivGroupIcon.setImageResource(R.drawable.add_group)
                holder.ivGroupIcon.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#7B2FBE"))
            }

            val groupId = group.groupId ?: return
            lifecycleScope.launch {
                try {
                    val transactions = DeclareDatabase.transactionsTable.select {
                        filter { eq("group_id", groupId) }
                    }.decodeList<com.waray.spendhound.ui.multi_transaction.TransactionFull>()

                    val totalExpenses = transactions.sumOf { it.totalAmount }
                    val activeCount = transactions.count { (it.status ?: 0) == 2 }

                    val txIds = transactions.mapNotNull { it.id }
                    val settledAmount = if (txIds.isNotEmpty()) {
                        DeclareDatabase.transactionPayorsTable.select {
                            filter { isIn("transaction_id", txIds) }
                        }.decodeList<com.waray.spendhound.ui.multi_transaction.TransactionPayorTable>()
                            .filter { it.status == 1 }
                            .sumOf { it.currentAmountPaid }
                    } else 0.0

                    // Unread messages count
                    val uid = currentUserId
                    val unreadCount = if (uid != null) {
                        val allMessages = DeclareDatabase.groupMessagesTable.select {
                            filter {
                                eq("group_id", groupId)
                                eq("is_deleted", false)
                            }
                        }.decodeList<GroupMessage>().filter { it.userId != uid }
                        val readIds = DeclareDatabase.messageReadsTable.select {
                            filter { eq("user_id", uid) }
                        }.decodeList<MessageRead>().mapNotNull { it.messageId }.toSet()
                        allMessages.count { it.id != null && it.id !in readIds }
                    } else 0

                    runOnUiThread {
                        holder.tvTotalExpenses.text = CurrencyUtils.formatAmountWithCurrency(totalExpenses)
                        holder.tvActiveTransactions.text = activeCount.toString()
                        
                        holder.tvSettledRatio.text = "${CurrencyUtils.formatAmountWithCurrency(settledAmount)} / ${CurrencyUtils.formatAmountWithCurrency(totalExpenses)}"
                        val progress = if (totalExpenses > 0) ((settledAmount / totalExpenses) * 100).toInt() else 0
                        holder.settledProgressBar.progress = progress

                        if (unreadCount > 0) {
                            holder.tvUnreadBadge.visibility = View.VISIBLE
                            holder.tvUnreadBadge.text = unreadCount.toString()
                        } else {
                            holder.tvUnreadBadge.visibility = View.GONE
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        holder.tvTotalExpenses.text = CurrencyUtils.formatAmountWithCurrency(0.0)
                        holder.tvActiveTransactions.text = "0"
                        holder.tvSettledRatio.text = "₱ 0 / ₱ 0"
                        holder.settledProgressBar.progress = 0
                        holder.tvUnreadBadge.visibility = View.GONE
                    }
                }
            }
        }
    }
}
