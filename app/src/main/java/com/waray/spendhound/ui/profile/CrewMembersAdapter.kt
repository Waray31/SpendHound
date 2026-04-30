package com.waray.spendhound.ui.profile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.waray.spendhound.CrewMember
import com.waray.spendhound.R
import com.waray.spendhound.User

class CrewMembersAdapter(
    private var items: List<Pair<CrewMember, User>> = emptyList(),
    private val currentUserId: Long,
    private val onMessage: (User, CrewMember) -> Unit,
    private val onRemove: (CrewMember) -> Unit
) : RecyclerView.Adapter<CrewMembersAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatarCard: CardView = view.findViewById(R.id.crewAvatarCard)
        val avatarImage: ImageView = view.findViewById(R.id.crewAvatarImage)
        val username: TextView = view.findViewById(R.id.crewUsername)
        val guestBadge: TextView = view.findViewById(R.id.crewGuestBadge)
        val statusText: TextView = view.findViewById(R.id.crewStatusText)
        val messageBtn: ImageButton = view.findViewById(R.id.crewMessageBtn)
        val removeBtn: ImageButton = view.findViewById(R.id.crewRemoveBtn)
        val divider: View = view.findViewById(R.id.crewDivider)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_crew_member, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (crew, user) = items[position]
        holder.username.text = user.username ?: "Unknown"

        val isGuest = user.userType == 2
        holder.guestBadge.visibility = if (isGuest) View.VISIBLE else View.GONE

        val isOwner = crew.ownerUserId == currentUserId
        holder.statusText.text = if (isOwner) "You invited" else "In your crew"

        // Disable DM button for guests
        if (isGuest) {
            holder.messageBtn.alpha = 0.4f
            holder.messageBtn.isEnabled = false
        } else {
            holder.messageBtn.alpha = 1f
            holder.messageBtn.isEnabled = true
            holder.messageBtn.setOnClickListener { onMessage(user, crew) }
        }

        holder.removeBtn.setOnClickListener { onRemove(crew) }

        // Load avatar
        val profileUrl = user.profileImageUrl
        if (!profileUrl.isNullOrBlank() && profileUrl != "placeholder_profile_image") {
            holder.avatarImage.load(profileUrl) {
                crossfade(true)
                transformations(CircleCropTransformation())
                listener(
                    onSuccess = { _, _ ->
                        holder.avatarImage.imageTintList = null
                        holder.avatarImage.setPadding(0, 0, 0, 0)
                    },
                    onError = { _, _ -> setPlaceholderAvatar(holder) }
                )
            }
        } else {
            setPlaceholderAvatar(holder)
        }

        holder.divider.visibility = if (position == items.size - 1) View.GONE else View.VISIBLE
    }

    private fun setPlaceholderAvatar(holder: ViewHolder) {
        holder.avatarImage.setImageResource(R.drawable.placeholder_profile_image)
        holder.avatarImage.imageTintList = null
        val pad = (8 * holder.itemView.resources.displayMetrics.density).toInt()
        holder.avatarImage.setPadding(pad, pad, pad, pad)
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<Pair<CrewMember, User>>) {
        items = newItems
        notifyDataSetChanged()
    }
}
