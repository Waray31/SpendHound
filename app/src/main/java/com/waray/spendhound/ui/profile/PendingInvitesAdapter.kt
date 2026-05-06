package com.waray.spendhound.ui.profile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.button.MaterialButton
import com.waray.spendhound.CrewMember
import com.waray.spendhound.R
import com.waray.spendhound.User

class PendingInvitesAdapter(
    private var items: List<Pair<CrewMember, User>> = emptyList(),
    private val onAccept: (CrewMember) -> Unit,
    private val onDecline: (CrewMember) -> Unit
) : RecyclerView.Adapter<PendingInvitesAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: ImageView = view.findViewById(R.id.pendingAvatarImage)
        val username: TextView = view.findViewById(R.id.pendingUsername)
        val acceptBtn: TextView = view.findViewById(R.id.btnAcceptInvite)
        val declineBtn: TextView = view.findViewById(R.id.btnDeclineInvite)
        val divider: View = view.findViewById(R.id.pendingDivider)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pending_invite, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (crew, user) = items[position]
        holder.username.text = user.username ?: "Unknown"

        val profileUrl = user.profileImageUrl
        if (!profileUrl.isNullOrBlank() && profileUrl != "placeholder_profile_image") {
            holder.avatar.load(profileUrl) {
                crossfade(true)
                transformations(CircleCropTransformation())
                listener(onError = { _, _ ->
                    holder.avatar.setImageResource(R.drawable.ic_profile_silhouette)
                    holder.avatar.imageTintList = null
                })
            }
        } else {
            holder.avatar.setImageResource(R.drawable.ic_profile_silhouette)
            holder.avatar.imageTintList = null
        }

        holder.acceptBtn.setOnClickListener { onAccept(crew) }
        holder.declineBtn.setOnClickListener { onDecline(crew) }
        holder.divider.visibility = if (position == items.size - 1) View.GONE else View.VISIBLE
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<Pair<CrewMember, User>>) {
        items = newItems
        notifyDataSetChanged()
    }
}
