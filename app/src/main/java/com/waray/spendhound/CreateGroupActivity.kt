package com.waray.spendhound

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.waray.spendhound.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private val selectedUsers = mutableSetOf<Long>()

    private lateinit var userAdapter: UserSelectAdapter

    private var selectedImageUri: Uri? = null
    private var dialogImageView: ImageView? = null

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        selectedImageUri = uri
        dialogImageView?.let {
            it.imageTintList = null
            Glide.with(this).load(uri).centerCrop().into(it)
        }
    }

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
        if (id in selectedUsers) selectedUsers.remove(id) else selectedUsers.add(id)
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
        currentUser?.let { addMemberChip(it, removable = false) }
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
                    .placeholder(R.drawable.ic_profile_silhouette)
                    .error(R.drawable.ic_profile_silhouette)
                    .into(iv)
            } catch (e: Exception) {
                iv.setImageResource(R.drawable.ic_profile_silhouette)
            }
        }
    }

    private fun showGroupNameDialog() {
        selectedImageUri = null
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_group_name, null)
        val ivGroupImage = dialogView.findViewById<ImageView>(R.id.ivGroupImage)
        val ivContainer = dialogView.findViewById<FrameLayout>(R.id.ivGroupImageContainer)
        val etGroupName = dialogView.findViewById<TextInputEditText>(R.id.etGroupName)

        dialogImageView = ivGroupImage

        ivContainer.setOnClickListener { imagePickerLauncher.launch("image/*") }

        MaterialAlertDialogBuilder(this, R.style.AppDialog)
            .setTitle("New Group")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val name = etGroupName.text.toString().trim()
                if (name.isBlank()) { toast("Enter a group name"); return@setPositiveButton }
                createGroup(name)
            }
            .setNegativeButton("Cancel") { _, _ -> selectedImageUri = null }
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

                val uri = selectedImageUri
                if (uri != null) {
                    val imageUrl = uploadGroupImage(uri, groupId)
                    if (imageUrl != null) {
                        DeclareDatabase.groupsTable.update(GroupNameUpdate(groupName = name, groupImageUrl = imageUrl)) {
                            filter { eq("group_id", groupId) }
                        }
                    }
                }

                val memberIds = (listOf(creatorId) + selectedUsers.toList()).distinct()
                DeclareDatabase.groupMembersTable.insert(memberIds.map { GroupMemberInsert(groupId, it) })

                toast("Group \"$name\" created!")
                finish()
            } catch (e: Exception) {
                toast("Failed to create group: ${e.message}")
            } finally {
                hideLoading()
            }
        }
    }

    private suspend fun uploadGroupImage(uri: Uri, groupId: Long): String? = withContext(Dispatchers.IO) {
        try {
            val bytes = ImageUtils.compressImage(contentResolver, uri) ?: return@withContext null
            val path = "$groupId.jpg"
            DeclareDatabase.groupImagesBucket.upload(path, bytes, upsert = true)
            DeclareDatabase.groupImagesBucket.publicUrl(path)
        } catch (e: Exception) {
            null
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
