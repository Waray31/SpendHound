package com.waray.spendhound

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class GroupsActivity : AppCompatActivity() {

    private lateinit var rvGroups: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var loadingOverlay: View
    private lateinit var fabCreateGroup: FloatingActionButton

    private var currentUserId: Long? = null
    private var allUsers: List<User> = emptyList()
    private val groups = mutableListOf<Pair<PayerGroup, List<User>>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_groups)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

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
                    if (!isEmpty) {
                        rvGroups.adapter = GroupAdapter(groups,
                            onEdit = { pos -> launchEditGroup(pos) },
                            onDelete = { pos -> confirmDelete(pos) }
                        )
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
        }
        startActivity(intent)
    }

    private fun confirmDelete(position: Int) {
        AlertDialog.Builder(this)
            .setTitle("Remove Group")
            .setMessage("Remove this group from your list? This will not delete the group data.")
            .setPositiveButton("Remove") { _, _ ->
                groups.removeAt(position)
                rvGroups.adapter?.notifyItemRemoved(position)
                val isEmpty = groups.isEmpty()
                emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
                rvGroups.visibility = if (isEmpty) View.GONE else View.VISIBLE
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLoading() { loadingOverlay.visibility = View.VISIBLE }
    private fun hideLoading() { loadingOverlay.visibility = View.GONE }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private inner class GroupAdapter(
        private val items: List<Pair<PayerGroup, List<User>>>,
        private val onEdit: (Int) -> Unit,
        private val onDelete: (Int) -> Unit
    ) : RecyclerView.Adapter<GroupAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.groupName)
            val members: TextView = view.findViewById(R.id.groupMembers)
            val editBtn: Button = view.findViewById(R.id.editGroupBtn)
            val deleteBtn: Button = view.findViewById(R.id.removeGroupBtn)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(layoutInflater.inflate(R.layout.item_group, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (group, members) = items[position]
            holder.name.text = group.groupName ?: "Unnamed"
            holder.members.text = if (members.isEmpty()) "No members"
            else "Members: ${members.joinToString(", ") { it.username ?: "?" }}"
            holder.editBtn.setOnClickListener { onEdit(position) }
            holder.deleteBtn.setOnClickListener { onDelete(position) }
        }
    }
}
