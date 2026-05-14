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
import coil.load
import coil.transform.CircleCropTransformation
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeOldRecord
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class GroupChatFragment : Fragment() {

    companion object {
        private const val TAG = "GroupChatFragment"
        fun newInstance(groupId: Long) = GroupChatFragment().apply {
            arguments = Bundle().also { it.putLong("group_id", groupId) }
        }
    }

    private var groupId: Long = -1
    private var currentUserId: Long? = null
    private var currentUserName: String? = null
    private var currentUserProfileImage: String? = null
    private val messages = mutableListOf<GroupMessage>()
    private val reactions = mutableMapOf<Long, MutableList<MessageReaction>>()
    private val readReceipts = mutableMapOf<Long, MutableSet<Long>>()
    private var usersList = listOf<User>()
    private var pendingTempId: Long? = null
    private lateinit var adapter: ChatAdapter
    private lateinit var rvMessages: RecyclerView
    private lateinit var rvSkeleton: RecyclerView
    private lateinit var emojiPopup: LinearLayout
    private lateinit var actionsPopup: LinearLayout
    private lateinit var popupOverlay: View
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
        rvSkeleton = view.findViewById(R.id.rvSkeleton)
        emojiPopup = view.findViewById(R.id.emojiPopup)
        actionsPopup = view.findViewById(R.id.actionsPopup)
        popupOverlay = view.findViewById(R.id.popupOverlay)
        
        popupOverlay.setOnClickListener { dismissPopups() }
        
        adapter = ChatAdapter()
        rvMessages.layoutManager = LinearLayoutManager(requireContext()).also { it.stackFromEnd = true }
        rvMessages.adapter = adapter
        rvMessages.visibility = View.GONE
        rvSkeleton.layoutManager = LinearLayoutManager(requireContext())
        rvSkeleton.adapter = SkeletonAdapter(R.layout.item_skeleton_chat, 5)
        rvSkeleton.visibility = View.VISIBLE

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
            resolveCurrentUser()
            loadMessages()
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
            Log.d(TAG, "loadMessages: start groupId=$groupId")
            val raw = withContext(Dispatchers.IO) {
                DeclareDatabase.groupMessagesTable.select {
                    filter { eq("group_id", groupId) }
                    order("created_at", Order.ASCENDING)
                    limit(200)
                }.decodeList<GroupMessage>().filter { !it.isDeleted }
            }
            Log.d(TAG, "loadMessages: raw=${raw.size}")

            val userIds = raw.mapNotNull { it.userId }.distinct()
            val allUsers = withContext(Dispatchers.IO) {
                if (userIds.isNotEmpty()) DeclareDatabase.usersTable.select {
                    filter { isIn("user_id", userIds) }
                }.decodeList<User>() else emptyList()
            }
            usersList = allUsers
            val enriched = enrichMessages(raw, allUsers)
            Log.d(TAG, "loadMessages: enriched=${enriched.size}")

            val msgIds = enriched.mapNotNull { it.id }
            if (msgIds.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    try {
                        DeclareDatabase.messageReactionsTable.select {
                            filter { isIn("group_message_id", msgIds); eq("message_type", MessageType.GROUP) }
                        }.decodeList<MessageReaction>().forEach { r ->
                            val mid = r.groupMessageId ?: return@forEach
                            reactions.getOrPut(mid) { mutableListOf() }.add(r)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "loadMessages: reactions fetch failed: ${e.message}", e)
                    }

                    try {
                        DeclareDatabase.messageReadsTable.select {
                            filter { isIn("message_id", msgIds) }
                        }.decodeList<MessageRead>().forEach { r ->
                            val mid = r.messageId ?: return@forEach
                            val uid = r.userId ?: return@forEach
                            readReceipts.getOrPut(mid) { mutableSetOf() }.add(uid)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "loadMessages: reads fetch failed: ${e.message}", e)
                    }
                }
            }

            messages.clear()
            messages.addAll(enriched)
            Log.d(TAG, "loadMessages: messages list size=${messages.size}")

            requireActivity().runOnUiThread {
                rvSkeleton.visibility = View.GONE
                rvMessages.visibility = View.VISIBLE
                adapter.notifyDataSetChanged()
                if (messages.isNotEmpty()) rvMessages.scrollToPosition(messages.size - 1)
                Log.d(TAG, "loadMessages: UI updated")
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadMessages: FAILED: ${e.message}", e)
            requireActivity().runOnUiThread {
                rvSkeleton.visibility = View.GONE
                rvMessages.visibility = View.VISIBLE
            }
        }
        markMessagesRead()
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

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                DeclareDatabase.groupMessagesTable.insert(
                    GroupMessageInsert(groupId = groupId, userId = uid, message = text)
                )
                val raw = DeclareDatabase.groupMessagesTable.select {
                    filter { eq("group_id", groupId) }
                    order("created_at", Order.ASCENDING)
                    limit(200)
                }.decodeList<GroupMessage>().filter { !it.isDeleted }
                val userIds = raw.mapNotNull { it.userId }.distinct()
                val users = if (userIds.isNotEmpty()) DeclareDatabase.usersTable.select {
                    filter { isIn("user_id", userIds) }
                }.decodeList<User>() else emptyList()
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
                // Limit to 1 reaction: update local state first
                val existing = reactions[messageId]?.filter { it.userId == uid } ?: emptyList()
                existing.forEach { old ->
                    reactions[messageId]?.remove(old)
                    // Delete from DB - WAIT for it
                    DeclareDatabase.messageReactionsTable.delete {
                        filter {
                            eq("group_message_id", messageId)
                            eq("user_id", uid)
                            eq("emoji", old.emoji ?: "")
                            eq("message_type", MessageType.GROUP)
                        }
                    }
                }

                val newReaction = MessageReaction(
                    groupMessageId = messageId,
                    userId = uid,
                    emoji = emoji,
                    messageType = MessageType.GROUP
                )
                reactions.getOrPut(messageId) { mutableListOf() }.add(newReaction)

                val idx = messages.indexOfFirst { it.id == messageId }
                if (idx >= 0) requireActivity().runOnUiThread { adapter.notifyItemChanged(idx) }

                // Insert to DB
                Log.d(TAG, "addReaction: Inserting to DB: msgId=$messageId, uid=$uid, emoji=$emoji")
                DeclareDatabase.messageReactionsTable.insert(buildJsonObject {
                    put("group_message_id", messageId)
                    put("user_id", uid)
                    put("emoji", emoji)
                    put("message_type", MessageType.GROUP)
                })
                Log.d(TAG, "addReaction: Insert successful")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add reaction: ${e.message}", e)
            }
        }
    }

    private fun removeReaction(messageId: Long, emoji: String) {
        val uid = currentUserId ?: return
        lifecycleScope.launch {
            try {
                reactions[messageId]?.removeAll { it.userId == uid && it.emoji == emoji }
                val idx = messages.indexOfFirst { it.id == messageId }
                if (idx >= 0) requireActivity().runOnUiThread { adapter.notifyItemChanged(idx) }

                DeclareDatabase.messageReactionsTable.delete {
                    filter { eq("group_message_id", messageId); eq("user_id", uid); eq("emoji", emoji); eq("message_type", MessageType.GROUP) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove reaction", e)
            }
        }
    }

    private fun markMessagesRead() {
        val uid = currentUserId ?: return
        val maxMsgId = messages.mapNotNull { it.id }.maxOrNull() ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // High-water mark logic: everything up to maxMsgId is read
                val alreadyRead = DeclareDatabase.messageReadsTable.select(Columns.list("message_id")) {
                    filter {
                        eq("user_id", uid)
                        eq("group_id", groupId)
                        eq("message_type", MessageType.GROUP)
                        gte("message_id", maxMsgId)
                    }
                    limit(1)
                }.decodeSingleOrNull<MessageRead>()

                if (alreadyRead == null) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault()).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                    val now = sdf.format(Date())
                    DeclareDatabase.messageReadsTable.insert(
                        MessageReadInsert(messageId = maxMsgId, userId = uid, groupId = groupId, readAt = now)
                    )
                }
                
                // Update local receipts
                messages.filter { it.id != null && it.id!! <= maxMsgId }.forEach { msg ->
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
        popupOverlay.visibility = View.GONE
    }

    private fun showReactionDetails(msg: GroupMessage, msgReactions: List<MessageReaction>) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_reactions, null)
        dialog.setContentView(view)

        val rv = view.findViewById<RecyclerView>(R.id.rvReactionDetails)
        val btnClose = view.findViewById<ImageButton>(R.id.btnDetailClose)

        btnClose.setOnClickListener { dialog.dismiss() }

        val uid = currentUserId
        val reactionItems = msgReactions.mapNotNull { r ->
            val user = usersList.find { it.id == r.userId }
            ReactionItem(
                userId = r.userId ?: -1L,
                username = if (r.userId == uid) "You" else user?.username,
                avatarUrl = user?.profileImageUrl,
                emoji = r.emoji ?: "",
                isMine = r.userId == uid
            )
        }

        val detailAdapter = ReactionDetailAdapter(reactionItems) { emoji ->
            removeReaction(msg.id ?: -1L, emoji)
            dialog.dismiss()
        }
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = detailAdapter

        // Setup filters
        val emojiTabs = mapOf(
            "👍" to view.findViewById<TextView>(R.id.tabLike),
            "❤️" to view.findViewById<TextView>(R.id.tabLove),
            "😂" to view.findViewById<TextView>(R.id.tabHaha),
            "😮" to view.findViewById<TextView>(R.id.tabWow),
            "😢" to view.findViewById<TextView>(R.id.tabSad),
            "🔥" to view.findViewById<TextView>(R.id.tabFire)
        )
        val tabAll = view.findViewById<TextView>(R.id.tabAll)

        fun updateFilterUI(selectedView: View) {
            tabAll.setBackgroundResource(if (selectedView == tabAll) R.drawable.bg_profile_card else android.R.color.transparent)
            emojiTabs.values.forEach { tab ->
                tab.setBackgroundResource(if (tab == selectedView) R.drawable.bg_profile_card else android.R.color.transparent)
            }
        }

        tabAll.setOnClickListener {
            updateFilterUI(it)
            detailAdapter.updateItems(reactionItems)
        }

        val emojisCount = msgReactions.groupBy { it.emoji }.mapValues { it.value.size }
        emojiTabs.forEach { (emoji, tab) ->
            val count = emojisCount[emoji] ?: 0
            if (count > 0) {
                tab.visibility = View.VISIBLE
                tab.text = "$emoji $count"
                tab.setOnClickListener {
                    updateFilterUI(it)
                    detailAdapter.updateItems(reactionItems.filter { it.emoji == emoji })
                }
            } else {
                tab.visibility = View.GONE
            }
        }

        dialog.show()
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

        // Build emoji row from the predefined layout
        emojiPopup.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        val emojiLayout = inflater.inflate(R.layout.item_emoji_reaction, emojiPopup, false)
        emojiPopup.addView(emojiLayout)

        val userReacted = (reactions[msg.id ?: -1L] ?: emptyList())
            .filter { it.userId == uid }.mapNotNull { it.emoji }.toSet()

        val emojiMap = listOf(
            R.id.tvEmojiLike to "👍",
            R.id.tvEmojiLove to "❤️",
            R.id.tvEmojiHaha to "😂",
            R.id.tvEmojiWow  to "😮",
            R.id.tvEmojiSad  to "😢",
            R.id.tvEmojiFire to "🔥"
        )

        emojiMap.forEachIndexed { index, (viewId, emoji) ->
            emojiLayout.findViewById<TextView>(viewId)?.apply {
                val isSelected = emoji in userReacted
                if (isSelected) {
                    setBackgroundResource(R.drawable.bg_emoji_selected)
                    setTextColor(android.graphics.Color.BLACK)
                } else {
                    setBackgroundResource(R.drawable.bg_emoji_reaction)
                    setTextColor(android.graphics.Color.GRAY)
                }

                setOnClickListener {
                    val msgId = msg.id ?: return@setOnClickListener
                    if (emoji in userReacted) removeReaction(msgId, emoji) else addReaction(msgId, emoji)
                    dismissPopups()
                }

                // Individual emoji pop animation
                scaleX = 0.2f
                scaleY = 0.2f
                alpha = 0f
                animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setStartDelay(index * 80L)
                    .setDuration(350)
                    .setInterpolator(android.view.animation.OvershootInterpolator())
                    .start()
            }
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

        // Position actions row (Edit / Delete) — measure first
        popupOverlay.visibility = View.VISIBLE
        emojiPopup.visibility = View.VISIBLE
        
        // Pop up animation
        emojiPopup.scaleX = 0.7f
        emojiPopup.scaleY = 0.7f
        emojiPopup.alpha = 0f
        emojiPopup.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(200)
            .setInterpolator(android.view.animation.OvershootInterpolator())
            .start()

        emojiPopup.post {
            val margin = 8 // dp
            val density = resources.displayMetrics.density
            val marginPx = (margin * density).toInt()

            // Position above bubble
            emojiPopup.y = (bubbleTop - emojiPopup.height - marginPx).toFloat()
            // Center horizontally to screen
            val screenW = requireView().width
            emojiPopup.x = (screenW - emojiPopup.width) / 2f
        }

        if (actionsPopup.visibility == View.VISIBLE) {
            actionsPopup.post {
                val margin = 8
                actionsPopup.y = (bubbleBottom + margin).toFloat()
                
                // Horizontal center relative to bubble
                val bubbleCenterX = loc[0] - fragLoc[0] + (bubbleView.width / 2)
                val actW = actionsPopup.width
                val screenW = requireView().width
                var actX = (bubbleCenterX - (actW / 2)).toFloat()
                actX = actX.coerceIn(margin.toFloat(), (screenW - actW - margin).toFloat())
                actionsPopup.x = actX
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
                    val users = withContext(Dispatchers.IO) {
                        DeclareDatabase.usersTable.select().decodeList<User>()
                    }
                    usersList = users
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
                table = "message_reactions"
                filter("message_type", FilterOperator.EQ, MessageType.GROUP)
            }.onEach { action ->
                try {
                    val r = action.decodeRecord<MessageReaction>()
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
                table = "message_reactions"
                filter("message_type", FilterOperator.EQ, MessageType.GROUP)
            }.onEach { action ->
                try {
                    val r = action.decodeOldRecord<MessageReaction>()
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
                            iv.load(DeclareDatabase.profileImagesBucket.publicUrl(avatarPath)) {
                                crossfade(true)
                                placeholder(R.drawable.ic_profile_silhouette)
                                error(R.drawable.ic_profile_silhouette)
                                transformations(CircleCropTransformation())
                            }
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
                holder.reactionsRow.setOnClickListener { showReactionDetails(msg, msgReactions) }
                
                val isMyReaction = msgReactions.any { it.userId == currentUserId }
                val distinctEmojis = msgReactions.mapNotNull { it.emoji }.distinct()
                
                val container = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    
                    if (msgReactions.size == 1) {
                        setPadding(dpToPx(4), 0, dpToPx(4), 0)
                    } else {
                        setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
                        if (isMyReaction) {
                            setBackgroundResource(R.drawable.bg_profile_card)
                        } else {
                            setBackgroundResource(R.drawable.bg_light_card_outline)
                        }
                    }
                }
                
                if (msgReactions.size == 1) {
                    val tv = TextView(requireContext()).apply {
                        text = msgReactions.first().emoji
                        setTextColor(android.graphics.Color.BLACK)
                        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
                    }
                    container.addView(tv)
                } else {
                    distinctEmojis.forEach { emoji ->
                        val tv = TextView(requireContext()).apply {
                            text = emoji
                            setTextColor(android.graphics.Color.BLACK)
                            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                        }
                        container.addView(tv)
                    }
                    val tvCount = TextView(requireContext()).apply {
                        text = "${msgReactions.size}"
                        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
                        setPadding(dpToPx(4), 0, 0, 0)
                        setTextColor(if (isMyReaction) android.graphics.Color.BLACK else android.graphics.Color.DKGRAY)
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    container.addView(tvCount)
                }
                holder.reactionsRow.addView(container)
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
