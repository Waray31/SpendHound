package com.waray.spendhound.ui.profile

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.waray.spendhound.GroupDetailActivity
import com.waray.spendhound.PayerGroup
import com.waray.spendhound.R

data class ProfileGroupItem(
    val group: PayerGroup,
    val memberCount: Int,
    val unreadTransactions: Int,
    val unreadMessages: Int
)

class ProfileGroupsAdapter(
    private var items: List<ProfileGroupItem> = emptyList()
) : RecyclerView.Adapter<ProfileGroupsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val groupItemContainer: LinearLayout = view.findViewById(R.id.groupItemContainer)
        val groupIcon: ImageView = view.findViewById(R.id.groupIcon)
        val groupName: TextView = view.findViewById(R.id.groupName)
        val groupMembers: TextView = view.findViewById(R.id.groupMembers)
        val transactionNotif: LinearLayout = view.findViewById(R.id.transactionNotif)
        val bubbleTransactionCount: TextView = view.findViewById(R.id.bubble_transactionCount)
        val messageNotif: LinearLayout = view.findViewById(R.id.messageNotif)
        val bubbleMessageCount: TextView = view.findViewById(R.id.bubble_messageCount)
        val divider: View = view.findViewById(R.id.divider)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_profile_group, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.groupName.text = item.group.groupName
        holder.groupMembers.text = "${item.memberCount} members"

        // Get CardView reference
        val cardView = holder.itemView.findViewById<androidx.cardview.widget.CardView>(R.id.groupProfileCardView)

        // Load group icon
        if (!item.group.groupImageUrl.isNullOrBlank()) {
            holder.groupIcon.load(item.group.groupImageUrl) {
                crossfade(true)
                placeholder(R.drawable.add_group)
                error(R.drawable.add_group)
                transformations(CircleCropTransformation())
                listener(
                    onSuccess = { _, _ ->
                        // Successfully loaded image - remove tint, remove padding, and set orange background
                        holder.groupIcon.imageTintList = null
                        holder.groupIcon.setPadding(0, 0, 0, 0)
                        cardView?.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.orange))
                    },
                    onError = { _, _ ->
                        // Error loading image - remove tint, add padding, and set orange background
                        holder.groupIcon.imageTintList = null
                        holder.groupIcon.setPadding(4.dpToPx(holder.itemView.context), 4.dpToPx(holder.itemView.context), 4.dpToPx(holder.itemView.context), 4.dpToPx(holder.itemView.context))
                        cardView?.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.orange))
                    }
                )
            }
        } else {
            // No group image URL - remove tint, add padding, and set orange background
            holder.groupIcon.setImageResource(R.drawable.add_group)
            holder.groupIcon.imageTintList = null
            holder.groupIcon.setPadding(4.dpToPx(holder.itemView.context), 4.dpToPx(holder.itemView.context), 4.dpToPx(holder.itemView.context), 4.dpToPx(holder.itemView.context))
            cardView?.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.orange))
        }

        if (item.unreadTransactions > 0) {
            holder.transactionNotif.visibility = View.VISIBLE
            holder.bubbleTransactionCount.text = if (item.unreadTransactions > 99) "99+" else item.unreadTransactions.toString()
        } else {
            holder.transactionNotif.visibility = View.GONE
        }

        if (item.unreadMessages > 0) {
            holder.messageNotif.visibility = View.VISIBLE
            holder.bubbleMessageCount.text = if (item.unreadMessages > 99) "99+" else item.unreadMessages.toString()
        } else {
            holder.messageNotif.visibility = View.GONE
        }

        holder.divider.visibility = if (position == items.size - 1) View.GONE else View.VISIBLE

        holder.groupItemContainer.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, GroupDetailActivity::class.java).apply {
                putExtra(GroupDetailActivity.EXTRA_GROUP_ID, item.group.groupId)
            }
            context.startActivity(intent)
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<ProfileGroupItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}

private fun Int.dpToPx(context: android.content.Context): Int {
    return (this * context.resources.displayMetrics.density).toInt()
}
