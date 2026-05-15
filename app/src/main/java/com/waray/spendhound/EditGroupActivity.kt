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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.waray.spendhound.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditGroupActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GROUP_ID = "extra_group_id"
        const val EXTRA_GROUP_NAME = "extra_group_name"
        const val EXTRA_GROUP_IMAGE = "extra_group_image"
    }

    private lateinit var etSearch: EditText
    private lateinit var selectedMembersScroll: View
    private lateinit var selectedMembersContainer: LinearLayout
    private lateinit var rvUsers: RecyclerView
    private lateinit var btnNext: TextView
    private lateinit var loadingOverlay: View

    private val repo = com.waray.spendhound.data.repository.CrewRepository()
    private var groupId: Long = -1
    private var groupName: String = ""
    private var existingGroupImage: String? = null
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
            it.load(uri) {
                crossfade(true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_group)

        groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1)
        groupName = intent.getStringExtra(EXTRA_GROUP_NAME) ?: ""
        existingGroupImage = intent.getStringExtra(EXTRA_GROUP_IMAGE)

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

        findViewById<TextView>(R.id.tvTitle).text = "Edit Group"
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
                currentUserId = currentUser?.id ?: return@launch

                // 1. Get Crew Members
                val crewPairs = repo.getCrewList(currentUserId!!)
                val crewUsers = crewPairs.map { it.second }.sortedBy { it.username?.lowercase() }
                val crewUserIds = crewUsers.mapNotNull { it.id }.toSet()

                // 2. Get All Registered Users (Type 1)
                val allFetched = DeclareDatabase.usersTable.select().decodeList<User>()
                    .filter { it.id != currentUserId && it.userType == 1 }

                // 3. Filter out crew from all users to get suggested/other users
                val otherUsers = allFetched.filter { it.id !in crewUserIds }
                    .sortedBy { it.username?.lowercase() }

                // 4. Combine: Crew first, then suggested
                allUsers = crewUsers + otherUsers

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
        loadAvatar(ivAvatar, user)

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

    private fun loadAvatar(iv: ImageView, user: User) {
        val userId = user.id ?: return
        
        // Get CardView reference
        val cardView = (iv.parent as? androidx.cardview.widget.CardView)
        
        lifecycleScope.launch {
            try {
                // Try to use profileImageUrl from DB first, fallback to standard bucket path
                val url = if (!user.profileImageUrl.isNullOrBlank() && user.profileImageUrl != "placeholder_profile_image") {
                    user.profileImageUrl
                } else {
                    withContext(Dispatchers.IO) {
                        DeclareDatabase.profileImagesBucket.publicUrl("$userId/$userId.jpg")
                    }
                }

                iv.load(url) {
                    placeholder(R.drawable.ic_profile_silhouette)
                    error(R.drawable.ic_profile_silhouette)
                    transformations(CircleCropTransformation())
                    listener(
                        onSuccess = { _, _ ->
                            // Remove tint and orange background for real images
                            iv.imageTintList = null
                            iv.clearColorFilter()
                            cardView?.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
                        },
                        onError = { _, _ ->
                            // Set placeholder with orange background and white tint
                            iv.setImageResource(R.drawable.ic_profile_silhouette)
                            iv.imageTintList = android.content.res.ColorStateList.valueOf(
                                androidx.core.content.ContextCompat.getColor(this@EditGroupActivity, R.color.white)
                            )
                            cardView?.setCardBackgroundColor(
                                androidx.core.content.ContextCompat.getColor(this@EditGroupActivity, R.color.orange)
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                // Set placeholder with orange background and white tint
                iv.setImageResource(R.drawable.ic_profile_silhouette)
                iv.imageTintList = android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(this@EditGroupActivity, R.color.white)
                )
                cardView?.setCardBackgroundColor(
                    androidx.core.content.ContextCompat.getColor(this@EditGroupActivity, R.color.orange)
                )
            }
        }
    }

    private fun showConfirmDialog() {
        selectedImageUri = null
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_group_name, null)
        val ivGroupImage = dialogView.findViewById<ImageView>(R.id.ivGroupImage)
        val btnChangeGroupPhoto = dialogView.findViewById<View>(R.id.btnChangeGroupPhoto)
        val etGroupName = dialogView.findViewById<TextInputEditText>(R.id.etGroupName)

        dialogImageView = ivGroupImage
        etGroupName.setText(groupName)

        if (!existingGroupImage.isNullOrBlank()) {
            ivGroupImage.imageTintList = null
            ivGroupImage.load(existingGroupImage) {
                placeholder(R.drawable.add_group)
            }
        }

        btnChangeGroupPhoto.setOnClickListener { imagePickerLauncher.launch("image/*") }

        MaterialAlertDialogBuilder(this, R.style.AppDialog)
            .setTitle("Edit Group")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = etGroupName.text.toString().trim()
                if (name.isBlank()) { toast("Enter a group name"); return@setPositiveButton }
                saveGroup(name)
            }
            .setNegativeButton("Cancel") { _, _ -> selectedImageUri = null }
            .show()
    }

    private fun saveGroup(newName: String) {
        val creatorId = currentUserId ?: return
        showLoading()
        lifecycleScope.launch {
            try {
                var imageUrl = existingGroupImage
                val uri = selectedImageUri
                if (uri != null) {
                    imageUrl = uploadGroupImage(uri, groupId)
                }

                DeclareDatabase.groupsTable.update(GroupNameUpdate(groupName = newName, groupImageUrl = imageUrl)) {
                    filter { eq("group_id", groupId) }
                }

                DeclareDatabase.groupMembersTable.delete {
                    filter { eq("group_id", groupId) }
                }

                val memberIds = (listOf(creatorId) + selectedUsers.toList()).distinct()
                DeclareDatabase.groupMembersTable.insert(memberIds.map { GroupMemberInsert(groupId, it) })

                GroupsState.notifyChange()
                toast("Group updated!")
                finish()
            } catch (e: Exception) {
                toast("Failed to update group: ${e.message}")
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

            // Load avatar using the common activity helper
            loadAvatar(holder.ivAvatar, user)
            
            holder.itemView.setOnClickListener { if (!isCurrentUser) onToggle(user) }
        }

        @SuppressLint("NotifyDataSetChanged")
        fun updateList(newList: List<User>) {
            users = newList
            notifyDataSetChanged()
        }
    }
}
