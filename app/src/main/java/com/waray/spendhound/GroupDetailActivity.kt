package com.waray.spendhound

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
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

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout

    private val tabTitles = arrayOf("Expenses", "Chat", "Members")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_detail)

        groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1)
        if (groupId == -1L) { finish(); return }

        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnEditGroup).setOnClickListener { launchEditGroup() }

        viewPager.adapter = GroupPagerAdapter()
        viewPager.offscreenPageLimit = 2

        TabLayoutMediator(tabLayout, viewPager) { tab, pos ->
            tab.text = tabTitles[pos]
        }.attach()

        loadHeader()
    }

    override fun onResume() {
        super.onResume()
        loadHeader()
    }

    private fun loadHeader() {
        val authId = DeclareDatabase.auth.currentUserOrNull()?.id ?: return
        lifecycleScope.launch {
            try {
                val user = DeclareDatabase.usersTable.select {
                    filter { eq("auth_id", authId) }
                }.decodeSingleOrNull<User>() ?: return@launch
                currentUserId = user.id

                val group = DeclareDatabase.groupsTable.select {
                    filter { eq("group_id", groupId) }
                }.decodeSingleOrNull<PayerGroup>() ?: return@launch
                currentGroup = group

                val members = DeclareDatabase.groupMembersTable.select {
                    filter { eq("group_id", groupId) }
                }.decodeList<GroupMember>()

                isAdmin = members.any { it.userId == user.id && it.admin }

                val allUsers = DeclareDatabase.usersTable.select().decodeList<User>()
                groupMembers = members.mapNotNull { m ->
                    val u = allUsers.firstOrNull { it.id == m.userId } ?: return@mapNotNull null
                    Pair(m, u)
                }

                runOnUiThread {
                    findViewById<TextView>(R.id.tvGroupName).text = group.groupName ?: "Group"
                    findViewById<TextView>(R.id.tvMemberCount).text =
                        "${members.size} member${if (members.size != 1) "s" else ""}"

                    val iv = findViewById<ImageView>(R.id.ivGroupIcon)
                    if (!group.groupImageUrl.isNullOrBlank()) {
                        iv.imageTintList = null
                        Glide.with(this@GroupDetailActivity)
                            .load(group.groupImageUrl)
                            .transform(CenterCrop(), RoundedCorners(48))
                            .into(iv)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun launchEditGroup() {
        val group = currentGroup ?: return
        val intent = Intent(this, EditGroupActivity::class.java).apply {
            putExtra(EditGroupActivity.EXTRA_GROUP_ID, groupId)
            putExtra(EditGroupActivity.EXTRA_GROUP_NAME, group.groupName ?: "")
            putExtra(EditGroupActivity.EXTRA_GROUP_IMAGE, group.groupImageUrl)
        }
        startActivity(intent)
    }

    private inner class GroupPagerAdapter : FragmentStateAdapter(this) {
        override fun getItemCount() = 3
        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> GroupExpensesFragment.newInstance(groupId)
            1 -> GroupChatFragment.newInstance(groupId)
            else -> GroupMembersFragment.newInstance(groupId)
        }
    }
}
