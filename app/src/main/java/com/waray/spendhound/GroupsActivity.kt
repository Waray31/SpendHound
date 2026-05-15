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
import com.waray.spendhound.utils.PullInterceptLayout
import com.waray.spendhound.utils.PullToRefreshHelper
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.waray.spendhound.data.repository.GroupListItem
import com.waray.spendhound.ui.group.GroupDetailViewModel
import com.waray.spendhound.ui.group.GroupsListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GroupsActivity : AppCompatActivity() {

    private val groupDetailViewModel: GroupDetailViewModel by viewModels()
    private val groupsListViewModel: GroupsListViewModel by viewModels()

    private lateinit var rvGroups: RecyclerView
    private lateinit var rvSkeleton: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var fabCreateGroup: FloatingActionButton
    private lateinit var tvGroupCount: TextView
    private var pullToRefreshHelper: PullToRefreshHelper? = null

    private var currentUserId: Long? = null
    private var lastSeenGroupsUpdate: Long = 0L
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
        fabCreateGroup.visibility = View.VISIBLE
        fabCreateGroup.setOnClickListener {
            startActivity(android.content.Intent(this, CreateGroupActivity::class.java))
        }

        rvSkeleton.layoutManager = LinearLayoutManager(this)
        rvSkeleton.adapter = SkeletonAdapter(R.layout.item_skeleton_group)
        rvGroups.layoutManager = LinearLayoutManager(this)

        findViewById<TextView>(R.id.tvAddNewGroup).setOnClickListener {
            startActivity(android.content.Intent(this, CreateGroupActivity::class.java))
        }

        val rootLayout = findViewById<PullInterceptLayout>(R.id.groupsRootLayout)
        val indicator = findViewById<View>(R.id.pullRefreshIndicator_groups)
        val scrollView = findViewById<androidx.core.widget.NestedScrollView>(R.id.groupsNestedScrollView)
        pullToRefreshHelper = PullToRefreshHelper(scrollView, indicator, {
            val uid = currentUserId ?: return@PullToRefreshHelper
            groupsListViewModel.forceRefresh(uid)
        }, rootLayout)
        rootLayout.onInterceptCallback = { event -> pullToRefreshHelper!!.onInterceptTouch(event) }

        observeViewModel()
        lastSeenGroupsUpdate = GroupsState.lastUpdateTimestamp
        fetchCurrentUser()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            groupsListViewModel.groups.collectLatest { items ->
                if (items == null) {
                    showLoading()
                    return@collectLatest
                }
                
                val cardDataMap = items.associate { it.group.groupId!! to it.cardData }
                groups.clear()
                items.forEach { groups.add(Pair(it.group, it.members)) }

                runOnUiThread {
                    val isEmpty = groups.isEmpty()
                    emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
                    rvGroups.visibility = if (isEmpty) View.GONE else View.VISIBLE
                    tvGroupCount.text = "${groups.size} group${if (groups.size != 1) "s" else ""}"
                    if (!isEmpty) {
                        rvGroups.adapter = GroupAdapter(groups, cardDataMap)
                    }
                    hideLoading()
                    pullToRefreshHelper?.stopRefreshing()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (GroupsState.lastUpdateTimestamp > lastSeenGroupsUpdate) {
            lastSeenGroupsUpdate = GroupsState.lastUpdateTimestamp
            currentUserId?.let { groupsListViewModel.invalidate(it) }
        } else {
            currentUserId?.let { groupsListViewModel.load(it) }
        }
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
                android.util.Log.d("GroupsActivity", "Resolved currentUserId: $currentUserId")
                val uid = currentUserId ?: return@launch
                runOnUiThread { groupsListViewModel.load(uid) }
            } catch (e: Exception) {
                runOnUiThread { hideLoading(); toast("Failed to load user: ${e.message}") }
            }
        }
    }

    fun invalidateAndReload() {
        val uid = currentUserId ?: return
        groupsListViewModel.invalidate(uid)
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

    private fun showLoading() {
        rvSkeleton.visibility = View.VISIBLE
        rvGroups.visibility = View.GONE
        emptyState.visibility = View.GONE
    }

    private fun hideLoading() {
        rvSkeleton.visibility = View.GONE
    }

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
                currentUserId?.let { uid -> groupDetailViewModel.preloadGroup(gid, uid) }
                val intent = android.content.Intent(this@GroupsActivity, GroupDetailActivity::class.java).apply {
                    putExtra(GroupDetailActivity.EXTRA_GROUP_ID, gid)
                }
                startActivity(intent)
            }

            val cardView = holder.itemView.findViewById<androidx.cardview.widget.CardView>(R.id.groupListCardView)

            if (!group.groupImageUrl.isNullOrBlank()) {
                holder.ivGroupIcon.load(group.groupImageUrl) {
                    crossfade(true)
                    placeholder(R.drawable.add_group)
                    error(R.drawable.add_group)
                    transformations(CircleCropTransformation())
                    listener(
                        onSuccess = { _, _ ->
                            holder.ivGroupIcon.imageTintList = null
                            holder.ivGroupIcon.setPadding(0, 0, 0, 0)
                            cardView?.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.orange))
                        },
                        onError = { _, _ ->
                            holder.ivGroupIcon.imageTintList = null
                            holder.ivGroupIcon.setPadding(12.dpToPx(holder.itemView.context), 12.dpToPx(holder.itemView.context), 12.dpToPx(holder.itemView.context), 12.dpToPx(holder.itemView.context))
                            cardView?.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.orange))
                        }
                    )
                }
            } else {
                holder.ivGroupIcon.setImageResource(R.drawable.add_group)
                holder.ivGroupIcon.imageTintList = null
                holder.ivGroupIcon.setPadding(12.dpToPx(holder.itemView.context), 12.dpToPx(holder.itemView.context), 12.dpToPx(holder.itemView.context), 12.dpToPx(holder.itemView.context))
                cardView?.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.orange))
            }

            val data = cardDataMap[group.groupId] ?: GroupCardData(0.0, 0, 0.0, 0)
            holder.tvTotalExpenses.text = CurrencyUtils.formatAmountWithCurrency(data.totalExpenses)
            holder.tvActiveTransactions.text = data.activeCount.toString()
            holder.tvSettledRatio.text = "${CurrencyUtils.formatAmountWithCurrency(data.settledAmount)} / ${CurrencyUtils.formatAmountWithCurrency(data.totalExpenses)}"
            val progress = if (data.totalExpenses > 0) ((data.settledAmount / data.totalExpenses) * 100).toInt() else 0
            holder.settledProgressBar.progress = progress
            if (data.unreadCount > 0) {
                holder.tvUnreadBadge.visibility = View.VISIBLE
                holder.tvUnreadBadge.text = if (data.unreadCount > 99) "99+" else data.unreadCount.toString()
            } else {
                holder.tvUnreadBadge.visibility = View.GONE
            }
        }
    }
}

private fun Int.dpToPx(context: android.content.Context): Int {
    return (this * context.resources.displayMetrics.density).toInt()
}
