package com.waray.spendhound

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.waray.spendhound.ui.group.GroupDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GroupDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GROUP_ID = "extra_group_id"
    }

    var groupId: Long = -1
    var currentUserId: Long? = null
    var isAdmin: Boolean = false
    var groupMembers: List<Pair<GroupMember, User>> = emptyList()
    private var currentGroup: PayerGroup? = null

    private val viewModel: GroupDetailViewModel by viewModels()
    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_detail)

        groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1)
        if (groupId == -1L) { finish(); return }

        viewPager = findViewById(R.id.viewPager)
        bottomNav = findViewById(R.id.bottomNav)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnEditGroup).setOnClickListener { launchEditGroup() }

        viewPager.adapter = GroupPagerAdapter()
        viewPager.offscreenPageLimit = 2
        viewPager.isUserInputEnabled = false

        bottomNav.setOnItemSelectedListener { item ->
            viewPager.currentItem = when (item.itemId) {
                R.id.tab_expenses -> 0
                R.id.tab_chat -> 1
                R.id.tab_members -> 2
                else -> 0
            }
            true
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                bottomNav.selectedItemId = when (position) {
                    0 -> R.id.tab_expenses
                    1 -> R.id.tab_chat
                    2 -> R.id.tab_members
                    else -> R.id.tab_expenses
                }
            }
        })

        // Observe preloaded data — renders immediately if already cached from GroupsActivity tap
        lifecycleScope.launch {
            viewModel.groupData.collect { data ->
                data ?: return@collect
                currentGroup = data.group
                groupMembers = data.members
                isAdmin = data.isAdmin
                updateHeader(data.group, data.members.size)
            }
        }

        resolveCurrentUser()
    }

    override fun onResume() {
        super.onResume()
        currentUserId?.let { viewModel.preloadGroup(groupId, it) }
    }

    private fun resolveCurrentUser() {
        val authId = DeclareDatabase.auth.currentUserOrNull()?.id ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val user = DeclareDatabase.usersTable.select {
                    filter { eq("auth_id", authId) }
                }.decodeSingleOrNull<User>() ?: return@launch
                currentUserId = user.id
                user.id?.let { viewModel.preloadGroup(groupId, it) }
            } catch (_: Exception) {}
        }
    }

    private fun updateHeader(group: PayerGroup, memberCount: Int) {
        findViewById<TextView>(R.id.tvGroupName).text = group.groupName ?: "Group"
        findViewById<TextView>(R.id.tvMemberCount).text =
            "$memberCount member${if (memberCount != 1) "s" else ""}"

        val iv = findViewById<ImageView>(R.id.ivGroupIcon)
        if (!group.groupImageUrl.isNullOrBlank()) {
            iv.load(group.groupImageUrl) {
                crossfade(true)
                placeholder(R.drawable.add_group)
                error(R.drawable.add_group)
                transformations(RoundedCornersTransformation(48f))
                listener(
                    onSuccess = { _, _ ->
                        // Successfully loaded image - remove tint and remove padding
                        iv.imageTintList = null
                        iv.setPadding(0, 0, 0, 0)
                        // Background is already set in XML as white with circular background
                    },
                    onError = { _, _ ->
                        // Error loading image - remove tint and add padding
                        iv.imageTintList = null
                        iv.setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
                        // Background is already set in XML as white with circular background
                    }
                )
            }
        } else {
            // No group image URL - remove tint and add padding
            iv.setImageResource(R.drawable.add_group)
            iv.imageTintList = null
            iv.setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
            // Background is already set in XML as white with circular background
        }
    }

    private fun launchEditGroup() {
        val group = currentGroup ?: return
        startActivity(Intent(this, EditGroupActivity::class.java).apply {
            putExtra(EditGroupActivity.EXTRA_GROUP_ID, groupId)
            putExtra(EditGroupActivity.EXTRA_GROUP_NAME, group.groupName ?: "")
            putExtra(EditGroupActivity.EXTRA_GROUP_IMAGE, group.groupImageUrl)
        })
    }

    private inner class GroupPagerAdapter : FragmentStateAdapter(this) {
        override fun getItemCount() = 3
        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> GroupExpensesFragment.newInstance(groupId)
            1 -> GroupChatFragment.newInstance(groupId)
            else -> GroupMembersFragment.newInstance(groupId)
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}
