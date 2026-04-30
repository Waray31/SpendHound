package com.waray.spendhound

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DirectMessageAdapter(
    private val currentUserId: Long,
    private var items: List<DirectMessage> = emptyList()
) : RecyclerView.Adapter<DirectMessageAdapter.ViewHolder>() {

    companion object {
        private const val VIEW_MINE = 0
        private const val VIEW_THEIRS = 1
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val content: TextView = view.findViewById(R.id.tvDmContent)
        val time: TextView = view.findViewById(R.id.tvDmTime)
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position].senderId == currentUserId) VIEW_MINE else VIEW_THEIRS
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = if (viewType == VIEW_MINE) R.layout.item_dm_mine else R.layout.item_dm_theirs
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val msg = items[position]
        holder.content.text = msg.content ?: ""
        holder.time.text = formatTime(msg.sentAt)
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<DirectMessage>) {
        items = newItems
        notifyDataSetChanged()
    }

    private fun formatTime(iso: String?): String {
        if (iso == null) return ""
        return try {
            val parts = iso.split("T")
            val timePart = parts.getOrNull(1)?.substring(0, 5) ?: ""
            val datePart = parts.getOrNull(0) ?: ""
            "$datePart $timePart"
        } catch (e: Exception) { "" }
    }
}
