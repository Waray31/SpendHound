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
                listener(onError = { _, _ ->
                    holder.avatar.setImageResource(R.drawable.placeholder_profile_image)
                    holder.avatar.imageTintList = null
                })
            }
        } else {
            holder.avatar.setImageResource(R.drawable.placeholder_profile_image)
            holder.avatar.imageTintList = null
        }

        holder.inviteBtn.setOnClickListener { onInvite(user) }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<User>) {
        items = newItems
        notifyDataSetChanged()
    }
}
