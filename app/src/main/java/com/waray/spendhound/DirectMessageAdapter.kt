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
    private val reactions: MutableMap<Long, MutableList<MessageReaction>> = mutableMapOf(),
    var pendingTempId: Long? = null,
    var onLongPress: ((msg: DirectMessage, bubble: View) -> Unit)? = null,
    var onReactionClick: ((msg: DirectMessage, reactions: List<MessageReaction>) -> Unit)? = null,
    var onReactionAction: ((msgId: Long, emoji: String, isAdd: Boolean) -> Unit)? = null,
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
                        placeholder(R.drawable.ic_profile_silhouette)
                        error(R.drawable.ic_profile_silhouette)
                        transformations(coil.transform.CircleCropTransformation())
                    }
                } else {
                    iv.setImageResource(R.drawable.ic_profile_silhouette)
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
            holder.reactionsRow.setOnClickListener { onReactionClick?.invoke(msg, msgReactions) }
            
            val isMyReaction = msgReactions.any { it.userId == currentUserId }
            val distinctEmojis = msgReactions.map { it.emoji }.distinct()
            
            val container = LinearLayout(holder.itemView.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                
                if (msgReactions.size == 1) {
                    setPadding(dpToPx(holder.itemView, 4), 0, dpToPx(holder.itemView, 4), 0)
                } else {
                    setPadding(dpToPx(holder.itemView, 8), dpToPx(holder.itemView, 4), dpToPx(holder.itemView, 8), dpToPx(holder.itemView, 4))
                    if (isMyReaction) {
                        setBackgroundResource(R.drawable.bg_profile_card)
                    } else {
                        setBackgroundResource(R.drawable.bg_light_card_outline)
                    }
                }
            }
            
            if (msgReactions.size == 1) {
                // Only one reaction: just display the emoji, no background
                val tv = TextView(holder.itemView.context).apply {
                    text = msgReactions.first().emoji
                    setTextColor(android.graphics.Color.BLACK)
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f) // slightly larger if alone
                }
                container.addView(tv)
            } else {
                // Multiple reactions: display emojis + count sharing one background
                distinctEmojis.forEach { emoji ->
                    val tv = TextView(holder.itemView.context).apply {
                        text = emoji
                        setTextColor(android.graphics.Color.BLACK)
                        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                    }
                    container.addView(tv)
                }
                val tvCount = TextView(holder.itemView.context).apply {
                    text = "${msgReactions.size}"
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
                    setPadding(dpToPx(holder.itemView, 4), 0, 0, 0)
                    setTextColor(if (isMyReaction) android.graphics.Color.BLACK else android.graphics.Color.DKGRAY)
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                container.addView(tvCount)
            }
            
            holder.reactionsRow.addView(container)
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

    fun updateReactions(newReactions: Map<Long, MutableList<MessageReaction>>) {
        reactions.clear()
        reactions.putAll(newReactions)
        notifyDataSetChanged()
    }

    fun addReaction(messageId: Long, userId: Long, emoji: String) {
        val list = reactions.getOrPut(messageId) { mutableListOf() }
        // Limit every user to 1 reaction per message
        val removed = list.filter { it.userId == userId }
        removed.forEach { onReactionAction?.invoke(messageId, it.emoji ?: "", false) }
        list.removeAll { it.userId == userId }

        list.add(MessageReaction(directMessageId = messageId, userId = userId, emoji = emoji, messageType = MessageType.DIRECT))
        onReactionAction?.invoke(messageId, emoji, true)

        val idx = items.indexOfFirst { it.id == messageId }
        if (idx >= 0) notifyItemChanged(idx)
    }

    fun removeReaction(messageId: Long, userId: Long, emoji: String) {
        reactions[messageId]?.removeAll { it.userId == userId && it.emoji == emoji }
        onReactionAction?.invoke(messageId, emoji, false)
        val idx = items.indexOfFirst { it.id == messageId }
        if (idx >= 0) notifyItemChanged(idx)
    }

    fun getReactionsForUser(messageId: Long, userId: Long): Set<String> =
        (reactions[messageId] ?: emptyList()).filter { it.userId == userId }.mapNotNull { it.emoji }.toSet()

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

    private fun spToPx(context: android.content.Context, sp: Float): Float =
        android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics)
}

// Remove DmReaction data class as we use MessageReaction from GroupMessage.kt
