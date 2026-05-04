package com.waray.spendhound.ui.profile

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import coil.transform.CircleCropTransformation
import com.waray.spendhound.CrewMember
import com.waray.spendhound.R
import com.waray.spendhound.User

class CrewMembersAdapter(
    private var items: List<Pair<CrewMember, User>> = emptyList(),
    private var currentUserId: Long,
    private val onMessage: (User, CrewMember) -> Unit,
    private val onRemove: (CrewMember) -> Unit
) : RecyclerView.Adapter<CrewMembersAdapter.ViewHolder>() {

    fun updateCurrentUserId(id: Long) { currentUserId = id }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatarCard: CardView = view.findViewById(R.id.crewAvatarCard)
        val avatarImage: ImageView = view.findViewById(R.id.crewAvatarImage)
        val username: TextView = view.findViewById(R.id.crewUsername)
        val guestBadge: TextView = view.findViewById(R.id.crewGuestBadge)
        val statusText: TextView = view.findViewById(R.id.crewStatusText)
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

        if (isGuest) {
            // Guest: show badge below name, hide message preview
            holder.guestBadge.visibility = View.VISIBLE
            holder.statusText.visibility = View.GONE
            holder.itemView.setOnClickListener(null)
            holder.itemView.isClickable = false
        } else {
            // Registered: hide badge, show last message preview
            holder.guestBadge.visibility = View.GONE
            val unread = crew.unreadCount
            val last = crew.lastMessage
            when {
                unread > 1 -> {
                    holder.statusText.visibility = View.VISIBLE
                    holder.statusText.text = "$unread unread messages"
                    holder.statusText.setTypeface(null, Typeface.BOLD)
                    holder.statusText.setTextColor(
                        holder.itemView.context.getColor(R.color.black)
                    )
                }
                unread == 1 -> {
                    holder.statusText.visibility = View.VISIBLE
                    holder.statusText.text = last ?: "1 unread message"
                    holder.statusText.setTypeface(null, Typeface.BOLD)
                    holder.statusText.setTextColor(
                        holder.itemView.context.getColor(R.color.black)
                    )
                }
                !last.isNullOrBlank() -> {
                    holder.statusText.visibility = View.VISIBLE
                    holder.statusText.text = last
                    holder.statusText.setTypeface(null, Typeface.NORMAL)
                    holder.statusText.setTextColor(
                        holder.itemView.context.getColor(R.color.grey)
                    )
                }
                else -> {
                    holder.statusText.visibility = View.GONE
                }
            }
            holder.itemView.setOnClickListener { onMessage(user, crew) }
        }

        // Load avatar
        val profileUrl = user.profileImageUrl
        if (!profileUrl.isNullOrBlank() && profileUrl != "placeholder_profile_image") {
            holder.avatarImage.load(profileUrl) {
                crossfade(true)
                transformations(CircleCropTransformation())
                memoryCachePolicy(CachePolicy.ENABLED)
                diskCachePolicy(CachePolicy.ENABLED)
                memoryCacheKey(profileUrl)
                diskCacheKey(profileUrl)
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
