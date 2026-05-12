package com.waray.spendhound

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class GroupMembersFragment : Fragment() {

    companion object {
        fun newInstance(groupId: Long) = GroupMembersFragment().apply {
            arguments = Bundle().also { it.putLong("group_id", groupId) }
        }
    }

    private var groupId: Long = -1
    private val memberPairs = mutableListOf<Pair<GroupMember, User>>()
    private lateinit var adapter: MemberAdapter
    private lateinit var loadingOverlay: View
    private var allUsers: List<User> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        groupId = arguments?.getLong("group_id") ?: -1
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        inflater.inflate(R.layout.fragment_group_members, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rv = view.findViewById<RecyclerView>(R.id.rvMembers)
        loadingOverlay = view.findViewById(R.id.loadingOverlay)
        adapter = MemberAdapter()
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        view.findViewById<TextView>(R.id.btnLeaveGroup).setOnClickListener { confirmLeave() }
        view.findViewById<TextView>(R.id.btnAddMember).setOnClickListener { showAddMemberDialog() }

        loadMembers()
    }

    override fun onResume() {
        super.onResume()
        loadMembers()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadMembers() {
        if (memberPairs.isEmpty()) {
            view?.findViewById<View>(R.id.loadingOverlay)?.visibility = View.VISIBLE
        }
        lifecycleScope.launch {
            try {
                allUsers = DeclareDatabase.usersTable.select().decodeList<User>()
                val members = DeclareDatabase.groupMembersTable.select {
                    filter { eq("group_id", groupId) }
                }.decodeList<GroupMember>()

                memberPairs.clear()
                members.forEach { m ->
                    val u = allUsers.firstOrNull { it.id == m.userId } ?: return@forEach
                    memberPairs.add(Pair(m, u))
                }

                val activity = activity as? GroupDetailActivity
                requireActivity().runOnUiThread {
                    adapter.notifyDataSetChanged()
                    val isAdmin = activity?.isAdmin ?: false
                    view?.findViewById<TextView>(R.id.btnAddMember)?.visibility =
                        if (isAdmin) View.VISIBLE else View.GONE
                    loadingOverlay.visibility = View.GONE
                }
            } catch (_: Exception) {
                requireActivity().runOnUiThread { loadingOverlay.visibility = View.GONE }
            }
        }
    }

    private fun confirmLeave() {
        MaterialAlertDialogBuilder(requireContext(), R.style.AppDialog)
            .setTitle("Leave Group")
            .setMessage("Are you sure you want to leave this group?")
            .setPositiveButton("Leave") { _, _ -> leaveGroup() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun leaveGroup() {
        val uid = (activity as? GroupDetailActivity)?.currentUserId ?: return
        lifecycleScope.launch {
            try {
                DeclareDatabase.groupMembersTable.delete {
                    filter {
                        eq("group_id", groupId)
                        eq("user_id", uid)
                    }
                }
                requireActivity().finish()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to leave: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAddMemberDialog() {
        val existingIds = memberPairs.mapNotNull { it.second.id }.toSet()
        val candidates = allUsers.filter { it.id !in existingIds }
        if (candidates.isEmpty()) {
            Toast.makeText(requireContext(), "No users to add", Toast.LENGTH_SHORT).show()
            return
        }
        val names = candidates.map { it.username ?: "Unknown" }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext(), R.style.AppDialog)
            .setTitle("Add Member")
            .setItems(names) { _, idx ->
                addMember(candidates[idx])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun addMember(user: User) {
        val uid = user.id ?: return
        lifecycleScope.launch {
            try {
                DeclareDatabase.groupMembersTable.insert(
                    GroupMemberInsert(groupId = groupId, userId = uid, admin = false)
                )
                loadMembers()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to add: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun removeMember(member: GroupMember) {
        MaterialAlertDialogBuilder(requireContext(), R.style.AppDialog)
            .setTitle("Remove Member")
            .setMessage("Remove this member from the group?")
            .setPositiveButton("Remove") { _, _ ->
                lifecycleScope.launch {
                    try {
                        DeclareDatabase.groupMembersTable.delete {
                            filter {
                                eq("group_id", groupId)
                                eq("user_id", member.userId!!)
                            }
                        }
                        loadMembers()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadAvatar(iv: ImageView, userId: Long?) {
        if (userId == null) return
        
        // Get CardView reference
        val cardView = (iv.parent as? androidx.cardview.widget.CardView)
        
        lifecycleScope.launch {
            try {
                val url = DeclareDatabase.profileImagesBucket.publicUrl("$userId/$userId.jpg")
                iv.load(url) {
                    placeholder(R.drawable.ic_profile_silhouette)
                    error(R.drawable.ic_profile_silhouette)
                    transformations(CircleCropTransformation())
                    listener(
                        onSuccess = { _, _ ->
                            // Remove tint and orange background for real images
                            iv.imageTintList = null
                            cardView?.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
                        },
                        onError = { _, _ ->
                            // Set placeholder with orange background and white tint
                            iv.setImageResource(R.drawable.ic_profile_silhouette)
                            iv.imageTintList = android.content.res.ColorStateList.valueOf(
                                androidx.core.content.ContextCompat.getColor(requireContext(), R.color.white)
                            )
                            cardView?.setCardBackgroundColor(
                                androidx.core.content.ContextCompat.getColor(requireContext(), R.color.orange)
                            )
                        }
                    )
                }
            } catch (_: Exception) {
                // Set placeholder with orange background and white tint
                iv.setImageResource(R.drawable.ic_profile_silhouette)
                iv.imageTintList = android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(requireContext(), R.color.white)
                )
                cardView?.setCardBackgroundColor(
                    androidx.core.content.ContextCompat.getColor(requireContext(), R.color.orange)
                )
            }
        }
    }

    inner class MemberAdapter : RecyclerView.Adapter<MemberAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val memberAvatarCardView: androidx.cardview.widget.CardView = view.findViewById(R.id.memberAvatarCardView)
            val ivAvatar: ImageView = view.findViewById(R.id.ivMemberAvatar)
            val tvName: TextView = view.findViewById(R.id.tvMemberName)
            val tvAdmin: TextView = view.findViewById(R.id.tvAdminBadge)
            val btnRemove: ImageButton = view.findViewById(R.id.btnRemoveMember)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_group_member, parent, false))

        override fun getItemCount() = memberPairs.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (member, user) = memberPairs[position]
            val isAdmin = (activity as? GroupDetailActivity)?.isAdmin ?: false
            val currentUid = (activity as? GroupDetailActivity)?.currentUserId

            holder.tvName.text = if (user.id == currentUid) "${user.username} (You)" else user.username ?: "Unknown"
            holder.tvAdmin.visibility = if (member.admin) View.VISIBLE else View.GONE
            
            // Load avatar with proper CardView handling
            val profileUrl = user.profileImageUrl
            if (!profileUrl.isNullOrBlank() && profileUrl != "placeholder_profile_image") {
                holder.ivAvatar.load(profileUrl) {
                    crossfade(true)
                    transformations(CircleCropTransformation())
                    listener(
                        onSuccess = { _, _ ->
                            // Remove tint and orange background for real images
                            holder.ivAvatar.imageTintList = null
                            holder.memberAvatarCardView.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
                        },
                        onError = { _, _ ->
                            // Set placeholder with orange background and white tint
                            holder.ivAvatar.setImageResource(R.drawable.ic_profile_silhouette)
                            holder.ivAvatar.imageTintList = android.content.res.ColorStateList.valueOf(
                                androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.white)
                            )
                            holder.memberAvatarCardView.setCardBackgroundColor(
                                androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.orange)
                            )
                        }
                    )
                }
            } else {
                // Set placeholder with orange background and white tint
                holder.ivAvatar.setImageResource(R.drawable.ic_profile_silhouette)
                holder.ivAvatar.imageTintList = android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.white)
                )
                holder.memberAvatarCardView.setCardBackgroundColor(
                    androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.orange)
                )
            }

            // Admin can remove others (not themselves)
            if (isAdmin && user.id != currentUid) {
                holder.btnRemove.visibility = View.VISIBLE
                holder.btnRemove.setOnClickListener { removeMember(member) }
            } else {
                holder.btnRemove.visibility = View.GONE
            }
        }
    }
}
