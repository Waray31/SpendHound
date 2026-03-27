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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class CreateGroupActivity : AppCompatActivity() {

    private lateinit var etSearch: EditText
    private lateinit var selectedMembersScroll: View
    private lateinit var selectedMembersContainer: LinearLayout
    private lateinit var rvUsers: RecyclerView
    private lateinit var btnNext: TextView
    private lateinit var loadingOverlay: View

    private var currentUserId: Long? = null
    private var currentUser: User? = null
    private var allUsers: List<User> = emptyList()
    private var filteredUsers: List<User> = emptyList()
    private val selectedUsers = mutableSetOf<Long>() // user IDs selected (excludes current user)

    private lateinit var userAdapter: UserSelectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_group)

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

        rvUsers.layoutManager = LinearLayoutManager(this)

        btnNext.setOnClickListener { showGroupNameDialog() }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { filterUsers(s.toString()) }
        })

        fetchUsers()
    }

    private fun fetchUsers() {
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

                filteredUsers = allUsers
                runOnUiThread { setupAdapter() }
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
        if (id in selectedUsers) {
            selectedUsers.remove(id)
        } else {
            selectedUsers.add(id)
        }
        userAdapter.notifyDataSetChanged()
        updateSelectedStrip()
        btnNext.visibility = if (selectedUsers.isNotEmpty()) View.VISIBLE else View.GONE
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

        // Current user chip (always shown, no remove button)
        currentUser?.let { addMemberChip(it, removable = false) }

        // Selected users chips
        selected.forEach { user -> addMemberChip(user, removable = true) }

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
                btnNext.visibility = if (selectedUsers.isNotEmpty()) View.VISIBLE else View.GONE
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
                Glide.with(this@CreateGroupActivity)
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

    private fun showGroupNameDialog() {
        val input = EditText(this).apply {
            hint = "Enter group name"
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("Group Name")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) { toast("Enter a group name"); return@setPositiveButton }
                createGroup(name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createGroup(name: String) {
        val creatorId = currentUserId ?: return
        showLoading()
        lifecycleScope.launch {
            try {
                val inserted = DeclareDatabase.groupsTable.insert(
                    GroupInsert(groupName = name, createdById = creatorId)
                ) { select() }.decodeSingle<PayerGroup>()

                val groupId = inserted.groupId ?: throw Exception("No group ID returned")

                val memberIds = (listOf(creatorId) + selectedUsers.toList()).distinct()
                val records = memberIds.map { GroupMemberInsert(groupId = groupId, userId = it) }
                DeclareDatabase.groupMembersTable.insert(records)

                toast("Group \"$name\" created!")
                finish()
            } catch (e: Exception) {
                toast("Failed to create group: ${e.message}")
            } finally {
                hideLoading()
            }
        }
    }

    private fun showLoading() { loadingOverlay.visibility = View.VISIBLE }
    private fun hideLoading() { loadingOverlay.visibility = View.GONE }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ─── Adapter ──────────────────────────────────────────────────────────────

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

        @SuppressLint("NotifyDataSetChanged")
        override fun onBindViewHolder(holder: VH, position: Int) {
            val user = users[position]
            val isCurrentUser = user.id == currentUserId
            val isSelected = user.id in selectedIds || isCurrentUser

            holder.tvUsername.text = if (isCurrentUser) "${user.username ?: "You"} (You)" else user.username ?: "Unknown"
            holder.ivCheck.setImageResource(if (isSelected) R.drawable.ic_circle_checked else R.drawable.ic_circle_unchecked)
            holder.ivCheck.alpha = if (isCurrentUser) 0.5f else 1f
            holder.itemView.alpha = if (isCurrentUser) 0.6f else 1f

            loadAvatar(holder.ivAvatar, user.id)

            holder.itemView.setOnClickListener {
                if (!isCurrentUser) onToggle(user)
            }
        }

        fun updateList(newList: List<User>) {
            users = newList
            notifyDataSetChanged()
        }
    }
}
