package com.waray.spendhound.ui.profile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.waray.spendhound.R
import com.waray.spendhound.User
import com.google.android.material.button.MaterialButton

class UserSearchAdapter(
    private var items: List<User> = emptyList(),
    private val onInvite: (User) -> Unit
) : RecyclerView.Adapter<UserSearchAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: ImageView = view.findViewById(R.id.searchResultAvatar)
        val username: TextView = view.findViewById(R.id.searchResultUsername)
        val inviteBtn: MaterialButton = view.findViewById(R.id.btnSendInvite)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_search_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = items[position]
        holder.username.text = user.username ?: "Unknown"

        val profileUrl = user.profileImageUrl
        if (!profileUrl.isNullOrBlank() && profileUrl != "placeholder_profile_image") {
            holder.avatar.load(profileUrl) {
                crossfade(true)
                transformations(CircleCropTransformation())
                listener(
                    onSuccess = { _, _ ->
                        // Remove tint and orange background for real images
                        holder.avatar.imageTintList = null
                        val cardView = holder.itemView.findViewById<androidx.cardview.widget.CardView>(R.id.searchResultCardView)
                        cardView?.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
                    },
                    onError = { _, _ ->
                        // Set placeholder with orange background and white tint
                        holder.avatar.setImageResource(R.drawable.ic_profile_silhouette)
                        holder.avatar.imageTintList = android.content.res.ColorStateList.valueOf(
                            androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.white)
                        )
                        val cardView = holder.itemView.findViewById<androidx.cardview.widget.CardView>(R.id.searchResultCardView)
                        cardView?.setCardBackgroundColor(
                            androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.orange)
                        )
                    }
                )
            }
        } else {
            // Set placeholder with orange background and white tint
            holder.avatar.setImageResource(R.drawable.ic_profile_silhouette)
            holder.avatar.imageTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.white)
            )
            val cardView = holder.itemView.findViewById<androidx.cardview.widget.CardView>(R.id.searchResultCardView)
            cardView?.setCardBackgroundColor(
                androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.orange)
            )
        }

        holder.inviteBtn.setOnClickListener { onInvite(user) }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<User>) {
        items = newItems
        notifyDataSetChanged()
    }
}
