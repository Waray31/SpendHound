package com.waray.spendhound

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class DirectMessageAdapter(
    private val currentUserId: Long,
    private var items: List<DirectMessage> = emptyList(),
    private val reactions: MutableMap<Long, MutableList<DmReaction>> = mutableMapOf(),
    var pendingTempId: Long? = null,
    var onLongPress: ((msg: DirectMessage, bubble: View) -> Unit)? = null,
    var recipientAvatarUrl: String? = null
) : RecyclerView.Adapter<DirectMessageAdapter.VH>() {

    companion object {
        private const val VIEW_MINE = 0
        private const val VIEW_THEIRS = 1
        private val COMMON_EMOJIS = listOf("👍", "❤️", "😂", "😮", "😢", "🔥")
    }

    private var visibleTimeId: Long? = null

    inner class VH(view: View, val isMine: Boolean) : RecyclerView.ViewHolder(view) {
        val bubble: LinearLayout = view.findViewById(R.id.bubble)
        val tvContent: TextView = view.findViewById(R.id.tvDmContent)
        val tvTime: TextView = view.findViewById(R.id.tvDmTime)
        val reactionsRow: LinearLayout = view.findViewById(R.id.reactionsRow)
        val tvSendStatus: TextView? = if (isMine) view.findViewById(R.id.tvSendStatus) else null
        val ivAvatar: android.widget.ImageView? = if (!isMine) view.findViewById(R.id.ivAvatar) else null
    }

    override fun getItemViewType(position: Int) =
        if (items[position].senderId == currentUserId) VIEW_MINE else VIEW_THEIRS

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = if (viewType == VIEW_MINE) R.layout.item_dm_mine else R.layout.item_dm_theirs
        return VH(LayoutInflater.from(parent.context).inflate(layout, parent, false), viewType == VIEW_MINE)
    }

    override fun getItemCount() = items.size

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: VH, position: Int) {
        val msg = items[position]
        holder.tvContent.text = msg.content ?: ""

        // Connected bubble shapes
        val prev = if (position > 0) items[position - 1] else null
        val next = if (position < items.size - 1) items[position + 1] else null
        val isFirst = prev?.senderId != msg.senderId
        val isLast = next?.senderId != msg.senderId
        val isMiddle = !isFirst && !isLast

        val bubbleLp = holder.bubble.layoutParams as? LinearLayout.LayoutParams
            ?: LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        if (holder.isMine) {
            holder.bubble.setBackgroundResource(when {
                isFirst && isLast -> R.drawable.bg_bubble_mine
                isFirst           -> R.drawable.bg_bubble_mine
                isMiddle          -> R.drawable.bg_bubble_mine_middle
                else              -> R.drawable.bg_bubble_mine_bottom
            })
        } else {
            holder.bubble.setBackgroundResource(when {
                isFirst && isLast -> R.drawable.bg_bubble_others
                isFirst           -> R.drawable.bg_bubble_others
                isMiddle          -> R.drawable.bg_bubble_theirs_middle
                else              -> R.drawable.bg_bubble_theirs_bottom
            })
        }
        bubbleLp.topMargin = if (isFirst) dpToPx(holder.itemView, 2) else 0
        bubbleLp.bottomMargin = if (isLast) dpToPx(holder.itemView, 2) else 0
        holder.bubble.layoutParams = bubbleLp

        // Avatar — show on last message of a consecutive group, hide otherwise
        holder.ivAvatar?.let { iv ->
            if (isLast) {
                iv.visibility = View.VISIBLE
                val url = recipientAvatarUrl
                if (!url.isNullOrBlank()) {
                    iv.load(url) {
                        crossfade(true)
                        placeholder(R.drawable.placeholder_profile_image)
                        error(R.drawable.placeholder_profile_image)
                        transformations(coil.transform.CircleCropTransformation())
                    }
                } else {
                    iv.setImageResource(R.drawable.placeholder_profile_image)
                }
            } else {
                iv.visibility = View.INVISIBLE
            }
        }

        // Time label — tap bubble to toggle
        val msgId = msg.id ?: -1L
        holder.tvTime.visibility = if (visibleTimeId == msgId) View.VISIBLE else View.GONE
        if (visibleTimeId == msgId) holder.tvTime.text = formatTime(msg.sentAt)

        holder.bubble.setOnClickListener {
            visibleTimeId = if (visibleTimeId == msgId) null else msgId
            notifyItemChanged(position)
        }

        // Send status — only on latest own message
        holder.tvSendStatus?.let {
            val isLatestOwn = msg.id == items.lastOrNull { m -> m.senderId == currentUserId }?.id
            if (isLatestOwn) {
                it.visibility = View.VISIBLE
                it.text = if (pendingTempId != null && msg.id == pendingTempId) "sending..." else "sent"
            } else {
                it.visibility = View.GONE
            }
        }

        // Reactions
        holder.reactionsRow.removeAllViews()
        val msgReactions = reactions[msgId]
        if (!msgReactions.isNullOrEmpty()) {
            holder.reactionsRow.visibility = View.VISIBLE
            msgReactions.groupBy { it.emoji }.filter { it.key.isNotBlank() }.forEach { (emoji, list) ->
                val tv = TextView(holder.itemView.context).apply {
                    text = "$emoji ${list.size}"
                    textSize = 12f
                    setPadding(12, 4, 12, 4)
                    setBackgroundResource(R.drawable.bg_light_card_outline)
                }
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginEnd = 8 }
                holder.reactionsRow.addView(tv, lp)
            }
        } else {
            holder.reactionsRow.visibility = View.GONE
        }

        // Long press → emoji popup via activity callback
        holder.bubble.setOnLongClickListener {
            onLongPress?.invoke(msg, holder.bubble)
            true
        }
        holder.bubble.foreground = null
    }

    fun getItems(): List<DirectMessage> = items

    @SuppressLint("NotifyDataSetChanged")
    fun updateItems(newItems: List<DirectMessage>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun addReaction(messageId: Long, userId: Long, emoji: String) {
        val list = reactions.getOrPut(messageId) { mutableListOf() }
        if (list.none { it.userId == userId && it.emoji == emoji }) {
            list.add(DmReaction(messageId = messageId, userId = userId, emoji = emoji))
        }
        val idx = items.indexOfFirst { it.id == messageId }
        if (idx >= 0) notifyItemChanged(idx)
    }

    fun removeReaction(messageId: Long, userId: Long, emoji: String) {
        reactions[messageId]?.removeAll { it.userId == userId && it.emoji == emoji }
        val idx = items.indexOfFirst { it.id == messageId }
        if (idx >= 0) notifyItemChanged(idx)
    }

    fun getReactionsForUser(messageId: Long, userId: Long): Set<String> =
        (reactions[messageId] ?: emptyList()).filter { it.userId == userId }.map { it.emoji }.toSet()

    private fun formatTime(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).also {
                it.timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = parser.parse(iso.take(19)) ?: return ""
            val now = Date()
            val diffMs = now.time - date.time
            val diffDays = (diffMs / (1000 * 60 * 60 * 24)).toInt()
            val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
            val nowCal = java.util.Calendar.getInstance()
            val msgCal = java.util.Calendar.getInstance().also { it.time = date }
            val sameDay = nowCal.get(java.util.Calendar.YEAR) == msgCal.get(java.util.Calendar.YEAR) &&
                          nowCal.get(java.util.Calendar.DAY_OF_YEAR) == msgCal.get(java.util.Calendar.DAY_OF_YEAR)
            val sameYear = nowCal.get(java.util.Calendar.YEAR) == msgCal.get(java.util.Calendar.YEAR)
            when {
                sameDay -> timeFmt
                diffDays <= 6 -> "${SimpleDateFormat("EEE", Locale.getDefault()).format(date).uppercase()} AT $timeFmt"
                sameYear -> "${SimpleDateFormat("MMM d", Locale.getDefault()).format(date).uppercase()} AT $timeFmt"
                else -> "${SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date).uppercase()} AT $timeFmt"
            }
        } catch (_: Exception) { "" }
    }

    private fun dpToPx(view: View, dp: Int): Int =
        (dp * view.resources.displayMetrics.density).toInt()
}

data class DmReaction(
    val messageId: Long,
    val userId: Long,
    val emoji: String
)
