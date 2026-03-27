package com.waray.spendhound

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditGroupActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GROUP_ID = "extra_group_id"
        const val EXTRA_GROUP_NAME = "extra_group_name"
    }

    private lateinit var etSearch: EditText
    private lateinit var selectedMembersScroll: View
    private lateinit var selectedMembersContainer: LinearLayout
    private lateinit var rvUsers: RecyclerView
    private lateinit var btnNext: TextView
    private lateinit var loadingOverlay: View

    private var groupId: Long = -1
    private var groupName: String = ""
    private var currentUserId: Long? = null
    private var currentUser: User? = null
    private var allUsers: List<User> = emptyList()
    private var filteredUsers: List<User> = emptyList()
    private val selectedUsers = mutableSetOf<Long>()

    private lateinit var userAdapter: UserSelectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_group) // reuse same layout

        groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1)
        groupName = intent.getStringExtra(EXTRA_GROUP_NAME) ?: ""

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }

        etSearch = findViewById(R.id.etSearch)
        selectedMembersScroll = findViewById(R.id.selectedMembersScroll)
        selectedMembersContainer = findViewById(R.id.selectedMembersContainer)
        rvUsers = findViewById(R.id.rvUsers)
        btnNext = findViewById(R.id.btnNext)
        loadingOverlay = findViewById(R.id.loadingOverlay)

        btnNext.text = "Save"
        btnNext.visibility = View.VISIBLE

        rvUsers.layoutManager = LinearLayoutManager(this)
        btnNext.setOnClickListener { showConfirmDialog() }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { filterUsers(s.toString()) }
        })

        fetchUsersAndMembers()
    }

    private fun fetchUsersAndMembers() {
        val authId = DeclareDatabase.auth.currentUserOrNull()?.id ?: return
        showLoading()
        lifecycleScope.launch {
            try {
                currentUser = DeclareDatabase.usersTable.select {
                    filter { eq("auth_id", authId) }
                }.decodeSingleOrNull<User>()
                currentUserId = currentUser?.id

                allUsers = DeclareDatabase.usersTable.select().decodeList<User>()
                    .filter { it.id != currentUserId }

                val existingMemberIds = DeclareDatabase.groupMembersTable.select {
                    filter { eq("group_id", groupId) }
                }.decodeList<GroupMember>().mapNotNull { it.userId }

                selectedUsers.clear()
                selectedUsers.addAll(existingMemberIds.filter { it != currentUserId })

                filteredUsers = allUsers
                runOnUiThread {
                    setupAdapter()
                    updateSelectedStrip()
                }
            } catch (e: Exception) {
                toast("Failed to load users: ${e.message}")
            } finally {
                hideLoading()
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setupAdapter() {
        userAdapter = UserSelectAdapter(
            users = filteredUsers,
            selectedIds = selectedUsers,
            currentUserId = currentUserId,
            onToggle = { user -> toggleSelection(user) }
        )
        rvUsers.adapter = userAdapter
    }

    private fun toggleSelection(user: User) {
        val id = user.id ?: return
        if (id in selectedUsers) selectedUsers.remove(id) else selectedUsers.add(id)
        userAdapter.notifyDataSetChanged()
        updateSelectedStrip()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun filterUsers(query: String) {
        filteredUsers = if (query.isBlank()) allUsers
        else allUsers.filter { it.username?.contains(query, ignoreCase = true) == true }
        userAdapter.updateList(filteredUsers)
    }

    private fun updateSelectedStrip() {
        selectedMembersContainer.removeAllViews()
        val selected = allUsers.filter { it.id in selectedUsers }
        currentUser?.let { addMemberChip(it, removable = false) }
        selected.forEach { addMemberChip(it, removable = true) }
        selectedMembersScroll.visibility = if (selected.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun addMemberChip(user: User, removable: Boolean) {
        val chip = LayoutInflater.from(this).inflate(R.layout.item_selected_member, selectedMembersContainer, false)
        val ivAvatar = chip.findViewById<ImageView>(R.id.ivSelectedAvatar)
        val tvName = chip.findViewById<TextView>(R.id.tvSelectedName)
        val ivRemove = chip.findViewById<ImageView>(R.id.ivRemove)

        tvName.text = user.username ?: "?"
        loadAvatar(ivAvatar, user.id)

        if (removable) {
            ivRemove.visibility = View.VISIBLE
            ivRemove.setOnClickListener {
                user.id?.let { selectedUsers.remove(it) }
                userAdapter.notifyDataSetChanged()
                updateSelectedStrip()
            }
        }
        selectedMembersContainer.addView(chip)
    }

    private fun loadAvatar(iv: ImageView, userId: Long?) {
        if (userId == null) return
        lifecycleScope.launch {
            try {
                val url = withContext(Dispatchers.IO) {
                    DeclareDatabase.profileImagesBucket.publicUrl("$userId/$userId.jpg")
                }
                Glide.with(this@EditGroupActivity)
                    .load(url)
                    .circleCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.placeholder_profile_image)
                    .error(R.drawable.placeholder_profile_image)
                    .into(iv)
            } catch (e: Exception) {
                iv.setImageResource(R.drawable.placeholder_profile_image)
            }
        }
    }

    private fun showConfirmDialog() {
        val input = EditText(this).apply {
            setText(groupName)
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("Save Changes")
            .setMessage("Edit group name and save changes?")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) { toast("Enter a group name"); return@setPositiveButton }
                saveGroup(name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveGroup(newName: String) {
        val creatorId = currentUserId ?: return
        showLoading()
        lifecycleScope.launch {
            try {
                DeclareDatabase.groupsTable.update(GroupNameUpdate(groupName = newName)) {
                    filter { eq("group_id", groupId) }
                }

                DeclareDatabase.groupMembersTable.delete {
                    filter { eq("group_id", groupId) }
                }

                val memberIds = (listOf(creatorId) + selectedUsers.toList()).distinct()
                val records = memberIds.map { GroupMemberInsert(groupId = groupId, userId = it) }
                DeclareDatabase.groupMembersTable.insert(records)

                toast("Group updated!")
                finish()
            } catch (e: Exception) {
                toast("Failed to update group: ${e.message}")
            } finally {
                hideLoading()
            }
        }
    }

    private fun showLoading() { loadingOverlay.visibility = View.VISIBLE }
    private fun hideLoading() { loadingOverlay.visibility = View.GONE }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    inner class UserSelectAdapter(
        private var users: List<User>,
        private val selectedIds: MutableSet<Long>,
        private val currentUserId: Long?,
        private val onToggle: (User) -> Unit
    ) : RecyclerView.Adapter<UserSelectAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val ivAvatar: ImageView = view.findViewById(R.id.ivAvatar)
            val tvUsername: TextView = view.findViewById(R.id.tvUsername)
            val ivCheck: ImageView = view.findViewById(R.id.ivCheck)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_user_select, parent, false))

        override fun getItemCount() = users.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val user = users[position]
            val isCurrentUser = user.id == currentUserId
            val isSelected = user.id in selectedIds || isCurrentUser

            holder.tvUsername.text = if (isCurrentUser) "${user.username ?: "You"} (You)" else user.username ?: "Unknown"
            holder.ivCheck.setImageResource(if (isSelected) R.drawable.ic_circle_checked else R.drawable.ic_circle_unchecked)
            holder.ivCheck.alpha = if (isCurrentUser) 0.5f else 1f
            holder.itemView.alpha = if (isCurrentUser) 0.6f else 1f

            loadAvatar(holder.ivAvatar, user.id)

            holder.itemView.setOnClickListener { if (!isCurrentUser) onToggle(user) }
        }

        fun updateList(newList: List<User>) {
            users = newList
            notifyDataSetChanged()
        }
    }
}
