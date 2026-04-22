package com.waray.spendhound

import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.annotation.SuppressLint
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeOldRecord
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Serializable
data class ReactionInsert(
    @SerialName("message_id") val messageId: Long,
    @SerialName("user_id") val userId: Long,
    @SerialName("emoji") val emoji: String
)

class GroupChatFragment : Fragment() {

    companion object {
        private const val TAG = "GroupChatFragment"
        private val COMMON_EMOJIS = listOf("👍", "❤️", "😂", "😮", "😢", "🔥")
        fun newInstance(groupId: Long) = GroupChatFragment().apply {
            arguments = Bundle().also { it.putLong("group_id", groupId) }
        }
    }

    private var groupId: Long = -1
    private var currentUserId: Long? = null
    private var currentUserName: String? = null
    private var currentUserProfileImage: String? = null
    private val messages = mutableListOf<GroupMessage>()
    private val reactions = mutableMapOf<Long, MutableList<GroupMessageReaction>>()
    private val readReceipts = mutableMapOf<Long, MutableSet<Long>>()
    private var pendingTempId: Long? = null
    private lateinit var adapter: ChatAdapter
    private lateinit var rvMessages: RecyclerView
    private lateinit var loadingOverlay: View
    private lateinit var emojiPopup: LinearLayout
    private lateinit var actionsPopup: LinearLayout
    private var visibleTimeId: Long? = null
    private var messagesChannel: RealtimeChannel? = null
    private var reactionsChannel: RealtimeChannel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        groupId = arguments?.getLong("group_id") ?: -1
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        inflater.inflate(R.layout.fragment_group_chat, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        rvMessages = view.findViewById(R.id.rvMessages)
        loadingOverlay = view.findViewById(R.id.loadingOverlay)
        emojiPopup = view.findViewById(R.id.emojiPopup)
        actionsPopup = view.findViewById(R.id.actionsPopup)
        adapter = ChatAdapter()
        rvMessages.layoutManager = LinearLayoutManager(requireContext()).also { it.stackFromEnd = true }
        rvMessages.adapter = adapter

        // Dismiss popups when tapping outside
        view.setOnClickListener { dismissPopups() }

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            if (insets.getInsets(WindowInsetsCompat.Type.ime()).bottom > 0 && messages.isNotEmpty()) {
                rvMessages.post { rvMessages.scrollToPosition(messages.size - 1) }
            }
            insets
        }

        view.findViewById<ImageButton>(R.id.btnSend).setOnClickListener {
            val et = view.findViewById<EditText>(R.id.etMessage)
            val text = et.text.toString().trim()
            if (text.isNotEmpty()) { sendMessage(text); et.setText("") }
        }

        lifecycleScope.launch {
            loadingOverlay.visibility = View.VISIBLE
            resolveCurrentUser()
            loadMessages()
            requireActivity().runOnUiThread { loadingOverlay.visibility = View.GONE }
            subscribeRealtime()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        lifecycleScope.launch {
            try {
                messagesChannel?.let { DeclareDatabase.realtime.removeChannel(it) }
                reactionsChannel?.let { DeclareDatabase.realtime.removeChannel(it) }
                messagesChannel = null
                reactionsChannel = null
            } catch (e: Exception) {
                Log.e(TAG, "Error removing channels", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        markMessagesRead()
    }

    private suspend fun resolveCurrentUser() {
        val authId = DeclareDatabase.auth.currentUserOrNull()?.id ?: return
        try {
            val user = DeclareDatabase.usersTable.select {
                filter { eq("auth_id", authId) }
            }.decodeSingleOrNull<User>() ?: return
            currentUserId = user.id
            currentUserName = user.username
            currentUserProfileImage = user.id?.let { "$it/$it.jpg" }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve user", e)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private suspend fun loadMessages() {
        try {
            val raw = DeclareDatabase.groupMessagesTable.select {
                filter { eq("group_id", groupId) }
                order("created_at", Order.ASCENDING)
            }.decodeList<GroupMessage>().filter { !it.isDeleted }

            val allUsers = DeclareDatabase.usersTable.select().decodeList<User>()
            val enriched = enrichMessages(raw, allUsers)

            val msgIds = enriched.mapNotNull { it.id }
            if (msgIds.isNotEmpty()) {
                DeclareDatabase.groupMessageReactionsTable.select {
                    filter { isIn("message_id", msgIds) }
                }.decodeList<GroupMessageReaction>().forEach { r ->
                    val mid = r.messageId ?: return@forEach
                    reactions.getOrPut(mid) { mutableListOf() }.add(r)
                }

                DeclareDatabase.messageReadsTable.select {
                    filter { isIn("message_id", msgIds) }
                }.decodeList<MessageRead>().forEach { r ->
                    val mid = r.messageId ?: return@forEach
                    val uid = r.userId ?: return@forEach
                    readReceipts.getOrPut(mid) { mutableSetOf() }.add(uid)
                }
            }

            messages.clear()
            messages.addAll(enriched)

            requireActivity().runOnUiThread {
                adapter.notifyDataSetChanged()
                if (messages.isNotEmpty()) rvMessages.scrollToPosition(messages.size - 1)
            }
            markMessagesRead()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load messages", e)
        }
    }

    private suspend fun enrichMessages(raw: List<GroupMessage>, users: List<User>): List<GroupMessage> {
        return raw.map { msg ->
            val sender = users.firstOrNull { it.id == msg.userId }
            msg.senderName = sender?.username
            msg.senderProfileImage = sender?.id?.let { "$it/$it.jpg" }
            if (msg.transactionId != null) {
                try {
                    val tx = DeclareDatabase.transactionsTable.select {
                        filter { eq("id", msg.transactionId) }
                    }.decodeSingleOrNull<com.waray.spendhound.ui.multi_transaction.TransactionFull>()
                    msg.transactionTitle = tx?.description ?: "Transaction"
                    msg.transactionAmount = tx?.totalAmount ?: 0.0
                    msg.transactionStatus = when (tx?.status) {
                        1 -> "Settled"; 2 -> "Active"; else -> "Pending"
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to enrich transaction", e)
                }
            }
            msg
        }
    }

    private fun sendMessage(text: String) {
        val uid = currentUserId ?: return
        val tempId = -(System.currentTimeMillis())
        val optimistic = GroupMessage(id = tempId, groupId = groupId, userId = uid, message = text, isDeleted = false).also {
            it.senderName = currentUserName
            it.senderProfileImage = currentUserProfileImage
        }
        pendingTempId = tempId
        messages.add(optimistic)
        requireActivity().runOnUiThread {
            adapter.notifyItemInserted(messages.size - 1)
            rvMessages.scrollToPosition(messages.size - 1)
        }

        lifecycleScope.launch {
            try {
                DeclareDatabase.groupMessagesTable.insert(
                    GroupMessageInsert(groupId = groupId, userId = uid, message = text)
                )
                // Reload to replace optimistic with real row
                val raw = DeclareDatabase.groupMessagesTable.select {
                    filter { eq("group_id", groupId) }
                    order("created_at", Order.ASCENDING)
                }.decodeList<GroupMessage>().filter { !it.isDeleted }
                val users = DeclareDatabase.usersTable.select().decodeList<User>()
                val enriched = enrichMessages(raw, users)
                messages.clear()
                messages.addAll(enriched)
                pendingTempId = null
                requireActivity().runOnUiThread {
                    @Suppress("NotifyDataSetChanged")
                    adapter.notifyDataSetChanged()
                    rvMessages.scrollToPosition(messages.size - 1)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message", e)
                messages.removeAll { it.id == tempId }
                pendingTempId = null
                requireActivity().runOnUiThread { adapter.notifyDataSetChanged() }
            }
        }
    }

    private fun deleteMessage(messageId: Long) {
        lifecycleScope.launch {
            try {
                DeclareDatabase.groupMessagesTable.update({ set("is_deleted", true) }) {
                    filter { eq("id", messageId) }
                }
                val idx = messages.indexOfFirst { it.id == messageId }
                if (idx >= 0) {
                    messages[idx] = messages[idx].copy(isDeleted = true)
                    requireActivity().runOnUiThread { adapter.notifyItemChanged(idx) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete message", e)
            }
        }
    }

    private fun editMessage(messageId: Long, newContent: String) {
        lifecycleScope.launch {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).also {
                    it.timeZone = TimeZone.getTimeZone("UTC")
                }
                val now = sdf.format(Date())
                DeclareDatabase.groupMessagesTable.update({
                    set("message", newContent)
                    set("updated_at", now)
                }) { filter { eq("id", messageId) } }
                val idx = messages.indexOfFirst { it.id == messageId }
                if (idx >= 0) {
                    messages[idx] = messages[idx].copy(message = newContent, updatedAt = now)
                    requireActivity().runOnUiThread { adapter.notifyItemChanged(idx) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to edit message", e)
            }
        }
    }

    private fun addReaction(messageId: Long, emoji: String) {
        val uid = currentUserId ?: return
        lifecycleScope.launch {
            try {
                DeclareDatabase.groupMessageReactionsTable.insert(
                    ReactionInsert(messageId = messageId, userId = uid, emoji = emoji)
                )
                val newReaction = GroupMessageReaction(messageId = messageId, userId = uid, emoji = emoji)
                reactions.getOrPut(messageId) { mutableListOf() }.add(newReaction)
                val idx = messages.indexOfFirst { it.id == messageId }
                if (idx >= 0) requireActivity().runOnUiThread { adapter.notifyItemChanged(idx) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add reaction", e)
            }
        }
    }

    private fun removeReaction(messageId: Long, emoji: String) {
        val uid = currentUserId ?: return
        lifecycleScope.launch {
            try {
                DeclareDatabase.groupMessageReactionsTable.delete {
                    filter { eq("message_id", messageId); eq("user_id", uid); eq("emoji", emoji) }
                }
                reactions[messageId]?.removeAll { it.userId == uid && it.emoji == emoji }
                val idx = messages.indexOfFirst { it.id == messageId }
                if (idx >= 0) requireActivity().runOnUiThread { adapter.notifyItemChanged(idx) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove reaction", e)
            }
        }
    }

    private fun markMessagesRead() {
        val uid = currentUserId ?: return
        lifecycleScope.launch {
            try {
                val readIds = DeclareDatabase.messageReadsTable.select {
                    filter { eq("user_id", uid) }
                }.decodeList<MessageRead>().mapNotNull { it.messageId }.toSet()

                val unread = messages.filter { it.id != null && it.userId != uid && it.id !in readIds }
                if (unread.isEmpty()) return@launch

                DeclareDatabase.messageReadsTable.insert(
                    unread.map { MessageReadInsert(messageId = it.id!!, userId = uid) }
                )
                unread.forEach { msg ->
                    readReceipts.getOrPut(msg.id!!) { mutableSetOf() }.add(uid)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mark messages read", e)
            }
        }
    }

    private fun dismissPopups() {
        emojiPopup.visibility = View.GONE
        actionsPopup.visibility = View.GONE
    }

    private fun showInlinePopup(msg: GroupMessage, bubbleView: View) {
        val uid = currentUserId ?: return
        val isOwn = msg.userId == uid

        // Get bubble position on screen
        val loc = IntArray(2)
        bubbleView.getLocationOnScreen(loc)
        val fragLoc = IntArray(2)
        requireView().getLocationOnScreen(fragLoc)
        val bubbleTop = loc[1] - fragLoc[1]
        val bubbleBottom = bubbleTop + bubbleView.height

        // Build emoji row
        emojiPopup.removeAllViews()
        val userReacted = (reactions[msg.id ?: -1L] ?: emptyList())
            .filter { it.userId == uid }.mapNotNull { it.emoji }.toSet()
        COMMON_EMOJIS.forEach { emoji ->
            val tv = TextView(requireContext()).apply {
                text = emoji
                textSize = 24f
                setPadding(12, 8, 12, 8)
                alpha = if (emoji in userReacted) 0.4f else 1f
                setOnClickListener {
                    dismissPopups()
                    val msgId = msg.id ?: return@setOnClickListener
                    if (emoji in userReacted) removeReaction(msgId, emoji) else addReaction(msgId, emoji)
                }
            }
            emojiPopup.addView(tv)
        }

        // Build actions row (Edit / Delete) — only for own non-deleted messages
        actionsPopup.removeAllViews()
        if (isOwn && !msg.isDeleted) {
            listOf("Edit" to false, "Delete" to true).forEach { (label, isDelete) ->
                val tv = TextView(requireContext()).apply {
                    text = label
                    textSize = 13f
                    setPadding(20, 10, 20, 10)
                    setTextColor(requireContext().getColor(if (isDelete) android.R.color.holo_red_dark else R.color.black))
                    setOnClickListener {
                        dismissPopups()
                        if (isDelete) msg.id?.let { deleteMessage(it) }
                        else showEditDialog(msg)
                    }
                }
                actionsPopup.addView(tv)
            }
            actionsPopup.visibility = View.VISIBLE
        } else {
            actionsPopup.visibility = View.GONE
        }

        // Position popups — measure first
        emojiPopup.visibility = View.INVISIBLE
        emojiPopup.post {
            val popupH = emojiPopup.height
            val margin = 8
            val emojiY = (bubbleTop - popupH - margin).coerceAtLeast(0).toFloat()
            emojiPopup.y = emojiY
            emojiPopup.visibility = View.VISIBLE

            if (actionsPopup.visibility == View.VISIBLE) {
                actionsPopup.post {
                    actionsPopup.y = (bubbleBottom + margin).toFloat()
                }
            }
        }
    }

    private fun showEditDialog(msg: GroupMessage) {
        val et = EditText(requireContext()).apply {
            setText(msg.message)
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Edit Message")
            .setView(et)
            .setPositiveButton("Save") { _, _ ->
                val newText = et.text.toString().trim()
                if (newText.isNotEmpty()) msg.id?.let { editMessage(it, newText) }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private suspend fun subscribeRealtime() {
        try {
            messagesChannel?.let { DeclareDatabase.realtime.removeChannel(it) }
            val msgChannel = DeclareDatabase.realtime.channel("group_messages_$groupId")
            messagesChannel = msgChannel

            msgChannel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "group_messages"
                filter("group_id", FilterOperator.EQ, groupId)
            }.onEach { action ->
                try {
                    val msg = action.decodeRecord<GroupMessage>()
                    if (msg.isDeleted || messages.any { it.id == msg.id }) return@onEach
                    val users = DeclareDatabase.usersTable.select().decodeList<User>()
                    val enriched = enrichMessages(listOf(msg), users).first()
                    messages.add(enriched)
                    requireActivity().runOnUiThread {
                        adapter.notifyItemInserted(messages.size - 1)
                        rvMessages.scrollToPosition(messages.size - 1)
                    }
                    markMessagesRead()
                } catch (e: Exception) {
                    Log.e(TAG, "Realtime message insert error", e)
                }
            }.launchIn(lifecycleScope)

            msgChannel.subscribe(blockUntilSubscribed = true)

            // Reactions channel
            reactionsChannel?.let { DeclareDatabase.realtime.removeChannel(it) }
            val rxChannel = DeclareDatabase.realtime.channel("chat_reactions_$groupId")
            reactionsChannel = rxChannel

            rxChannel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "group_message_reactions"
            }.onEach { action ->
                try {
                    val r = action.decodeRecord<GroupMessageReaction>()
                    val mid = r.messageId ?: return@onEach
                    if (messages.none { it.id == mid }) return@onEach
                    val list = reactions.getOrPut(mid) { mutableListOf() }
                    if (list.none { it.userId == r.userId && it.emoji == r.emoji }) list.add(r)
                    val idx = messages.indexOfFirst { it.id == mid }
                    if (idx >= 0) requireActivity().runOnUiThread { adapter.notifyItemChanged(idx) }
                } catch (e: Exception) {
                    Log.e(TAG, "Realtime reaction insert error", e)
                }
            }.launchIn(lifecycleScope)

            rxChannel.postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
                table = "group_message_reactions"
            }.onEach { action ->
                try {
                    val r = action.decodeOldRecord<GroupMessageReaction>()
                    val mid = r.messageId ?: return@onEach
                    reactions[mid]?.removeAll { it.userId == r.userId && it.emoji == r.emoji }
                    val idx = messages.indexOfFirst { it.id == mid }
                    if (idx >= 0) requireActivity().runOnUiThread { adapter.notifyItemChanged(idx) }
                } catch (e: Exception) {
                    Log.e(TAG, "Realtime reaction delete error", e)
                }
            }.launchIn(lifecycleScope)

            rxChannel.subscribe(blockUntilSubscribed = true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe realtime", e)
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

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
                diffDays <= 6 -> {
                    val day = SimpleDateFormat("EEE", Locale.getDefault()).format(date).uppercase()
                    "$day AT $timeFmt"
                }
                sameYear -> {
                    val monthDay = SimpleDateFormat("MMM d", Locale.getDefault()).format(date).uppercase()
                    "$monthDay AT $timeFmt"
                }
                else -> {
                    val full = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date).uppercase()
                    "$full AT $timeFmt"
                }
            }
        } catch (_: Exception) { "" }
    }

    inner class ChatAdapter : RecyclerView.Adapter<ChatAdapter.VH>() {

        private val VIEW_TYPE_MINE = 1
        private val VIEW_TYPE_OTHER = 2

        inner class VH(view: View, val isMine: Boolean) : RecyclerView.ViewHolder(view) {
            val tvSenderName: TextView? = view.findViewById(R.id.tvSenderName)
            val ivAvatar: ImageView? = view.findViewById(R.id.ivAvatar)
            val tvMessageTime: TextView = view.findViewById(R.id.tvMessageTime)
            val bubble: LinearLayout = view.findViewById(R.id.bubble)
            val tvMessage: TextView = view.findViewById(R.id.tvMessage)
            val reactionsRow: LinearLayout = view.findViewById(R.id.reactionsRow)
            val tvSendStatus: TextView? = view.findViewById(R.id.tvSendStatus)
            val transactionCard: LinearLayout = view.findViewById(R.id.transactionCard)
            val tvTransactionTitle: TextView = view.findViewById(R.id.tvTransactionTitle)
            val tvTransactionAmount: TextView = view.findViewById(R.id.tvTransactionAmount)
            val tvTransactionStatus: TextView = view.findViewById(R.id.tvTransactionStatus)
        }

        override fun getItemViewType(position: Int) =
            if (messages[position].userId == currentUserId) VIEW_TYPE_MINE else VIEW_TYPE_OTHER

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val layout = if (viewType == VIEW_TYPE_MINE) R.layout.item_chat_message_mine
                         else R.layout.item_chat_message_other
            return VH(LayoutInflater.from(parent.context).inflate(layout, parent, false), viewType == VIEW_TYPE_MINE)
        }

        override fun getItemCount() = messages.size

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: VH, position: Int) {
            val msg = messages[position]

            // Deleted: strike-through
            if (msg.isDeleted) {
                holder.tvMessage.paintFlags = holder.tvMessage.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                holder.tvMessage.alpha = 0.5f
            } else {
                holder.tvMessage.paintFlags = holder.tvMessage.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                holder.tvMessage.alpha = 1f
            }

            // Determine bubble position in consecutive group
            val prevMsg = if (position > 0) messages[position - 1] else null
            val nextMsg = if (position < messages.size - 1) messages[position + 1] else null
            val isFirstInGroup = prevMsg?.userId != msg.userId
            val isLastInGroup = nextMsg?.userId != msg.userId
            val isMiddle = !isFirstInGroup && !isLastInGroup

            // Set bubble background and margin based on position in group
            val bubbleLp = holder.bubble.layoutParams as? LinearLayout.LayoutParams
                ?: LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            if (holder.isMine) {
                when {
                    isFirstInGroup && isLastInGroup -> {
                        holder.bubble.setBackgroundResource(R.drawable.bg_bubble_mine)
                        bubbleLp.topMargin = dpToPx(2); bubbleLp.bottomMargin = dpToPx(2)
                    }
                    isFirstInGroup -> {
                        holder.bubble.setBackgroundResource(R.drawable.bg_bubble_mine)
                        bubbleLp.topMargin = dpToPx(2); bubbleLp.bottomMargin = 0
                    }
                    isMiddle -> {
                        holder.bubble.setBackgroundResource(R.drawable.bg_bubble_mine_middle)
                        bubbleLp.topMargin = 0; bubbleLp.bottomMargin = 0
                    }
                    else -> {
                        holder.bubble.setBackgroundResource(R.drawable.bg_bubble_mine_bottom)
                        bubbleLp.topMargin = 0; bubbleLp.bottomMargin = dpToPx(2)
                    }
                }
            } else {
                when {
                    isFirstInGroup && isLastInGroup -> {
                        holder.bubble.setBackgroundResource(R.drawable.bg_bubble_others)
                        bubbleLp.topMargin = dpToPx(2); bubbleLp.bottomMargin = dpToPx(2)
                    }
                    isFirstInGroup -> {
                        holder.bubble.setBackgroundResource(R.drawable.bg_bubble_others)
                        bubbleLp.topMargin = dpToPx(2); bubbleLp.bottomMargin = 0
                    }
                    isMiddle -> {
                        holder.bubble.setBackgroundResource(R.drawable.bg_bubble_theirs_middle)
                        bubbleLp.topMargin = 0; bubbleLp.bottomMargin = 0
                    }
                    else -> {
                        holder.bubble.setBackgroundResource(R.drawable.bg_bubble_theirs_bottom)
                        bubbleLp.topMargin = 0; bubbleLp.bottomMargin = dpToPx(2)
                    }
                }
            }
            holder.bubble.layoutParams = bubbleLp

            // Others: show sender name only on first message of a consecutive group
            if (!holder.isMine) {
                holder.tvSenderName?.let {
                    it.text = msg.senderName ?: "Unknown"
                    it.visibility = if (isFirstInGroup) View.VISIBLE else View.GONE
                }
                holder.ivAvatar?.let { iv ->
                    val avatarPath = msg.senderProfileImage
                    if (isLastInGroup) {
                        iv.visibility = View.VISIBLE
                        if (!avatarPath.isNullOrBlank()) {
                            Glide.with(requireContext())
                                .load(DeclareDatabase.profileImagesBucket.publicUrl(avatarPath))
                                .circleCrop()
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .placeholder(R.drawable.placeholder_profile_image)
                                .error(R.drawable.placeholder_profile_image)
                                .into(iv)
                        }
                    } else {
                        iv.visibility = View.INVISIBLE
                    }
                }
            }

            holder.tvMessage.text = msg.message ?: ""

            // Send status (latest mine only)
            holder.tvSendStatus?.let {
                val isLatestOwn = msg.id == messages.lastOrNull { m -> m.userId == currentUserId }?.id
                if (isLatestOwn && (pendingTempId != null || (msg.id != null && msg.id > 0))) {
                    it.visibility = View.VISIBLE
                    it.text = if (pendingTempId != null) "sending..." else "sent"
                } else {
                    it.visibility = View.GONE
                }
            }

            // Hide time label when recycled
            holder.tvMessageTime.visibility = if (msg.id == visibleTimeId) View.VISIBLE else View.GONE
            if (msg.id == visibleTimeId) holder.tvMessageTime.text = formatTime(msg.createdAt)

            // Tap bubble to show/hide time label above this bubble
            holder.bubble.setOnClickListener {
                val msgId = msg.id ?: return@setOnClickListener
                if (visibleTimeId == msgId) {
                    visibleTimeId = null
                    holder.tvMessageTime.visibility = View.GONE
                } else {
                    visibleTimeId = msgId
                    holder.tvMessageTime.text = formatTime(msg.createdAt)
                    holder.tvMessageTime.visibility = View.VISIBLE
                }
            }

            // Reactions
            holder.reactionsRow.removeAllViews()
            val msgReactions = reactions[msg.id ?: -1L]
            if (!msgReactions.isNullOrEmpty()) {
                holder.reactionsRow.visibility = View.VISIBLE
                msgReactions.groupBy { it.emoji ?: "" }.filter { it.key.isNotBlank() }.forEach { (emoji, list) ->
                    val tv = TextView(requireContext()).apply {
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

            // Transaction card
            if (msg.transactionId != null && !msg.transactionTitle.isNullOrBlank()) {
                holder.transactionCard.visibility = View.VISIBLE
                holder.tvTransactionTitle.text = msg.transactionTitle
                holder.tvTransactionAmount.text = CurrencyUtils.formatAmountWithCurrency(msg.transactionAmount)
                holder.tvTransactionStatus.text = msg.transactionStatus ?: ""
                holder.transactionCard.setOnClickListener {
                    startActivity(
                        Intent(requireContext(), TransactionDetailActivity::class.java)
                            .putExtra(TransactionDetailActivity.EXTRA_TRANSACTION_ID, msg.transactionId)
                    )
                }
            } else {
                holder.transactionCard.visibility = View.GONE
            }

            // Long press on bubble — show inline emoji + actions popup, no ripple
            holder.bubble.setOnLongClickListener {
                dismissPopups()
                showInlinePopup(msg, holder.bubble)
                true
            }
            holder.bubble.foreground = null
        }
    }
}
