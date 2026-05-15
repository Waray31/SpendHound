package com.waray.spendhound

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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.waray.spendhound.data.repository.CrewRepository
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DirectMessageActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RECIPIENT_ID = "recipient_id"
        const val EXTRA_RECIPIENT_NAME = "recipient_name"
        const val EXTRA_RECIPIENT_AVATAR = "recipient_avatar"
        const val EXTRA_CURRENT_USER_ID = "current_user_id"
    }

    private val repo = CrewRepository()
    private var currentUserId: Long = -1L
    private var currentUser: User? = null
    private var recipientId: Long = -1L

    private lateinit var rvMessages: RecyclerView
    private lateinit var rvSkeleton: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var layoutInput: LinearLayout
    private lateinit var layoutBlocked: LinearLayout
    private lateinit var emojiPopup: LinearLayout
    private lateinit var popupOverlay: View
    private lateinit var dmAdapter: DirectMessageAdapter
    private var messagesChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_direct_message)
        supportActionBar?.hide()

        recipientId = intent.getLongExtra(EXTRA_RECIPIENT_ID, -1L)
        val recipientName = intent.getStringExtra(EXTRA_RECIPIENT_NAME) ?: ""
        val recipientAvatar = intent.getStringExtra(EXTRA_RECIPIENT_AVATAR)

        rvMessages = findViewById(R.id.rvDirectMessages)
        rvSkeleton = findViewById(R.id.rvDmSkeleton)
        etMessage = findViewById(R.id.etDmMessage)
        btnSend = findViewById(R.id.btnSendDm)
        layoutInput = findViewById(R.id.layoutDmInput)
        layoutBlocked = findViewById(R.id.layoutGuestBlocked)
        emojiPopup = findViewById(R.id.emojiPopup)
        popupOverlay = findViewById(R.id.popupOverlay)

        rvSkeleton.layoutManager = LinearLayoutManager(this)
        rvSkeleton.adapter = SkeletonAdapter(R.layout.item_skeleton_chat, 8)
        rvSkeleton.visibility = View.VISIBLE
        rvMessages.visibility = View.GONE

        popupOverlay.setOnClickListener { dismissPopup() }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvDmRecipientName).text = recipientName

        val avatarView = findViewById<ImageView>(R.id.dmRecipientAvatar)
        if (!recipientAvatar.isNullOrBlank() && recipientAvatar != "placeholder_profile_image") {
            avatarView.load(recipientAvatar) {
                crossfade(true)
                transformations(CircleCropTransformation())
            }
        }

        // Dismiss emoji popup on outside tap
        findViewById<View>(android.R.id.content).setOnClickListener { dismissPopup() }

        currentUserId = intent.getLongExtra(EXTRA_CURRENT_USER_ID, -1L)
        if (currentUserId != -1L) {
            val cached = CrewRepository.getCachedMessages(currentUserId, recipientId)
            if (cached != null) {
                setupMessageList()
                dmAdapter.updateItems(cached)
                setupSendButton()
                rvSkeleton.visibility = View.GONE
                rvMessages.visibility = View.VISIBLE
                rvMessages.scrollToPosition(cached.size - 1)
            }
        }

        resolveUserAndInit()
    }

    private fun resolveUserAndInit() {
        lifecycleScope.launch {
            try {
                if (currentUserId == -1L) {
                    val authId = DeclareDatabase.auth.currentUserOrNull()?.id ?: return@launch
                    val user = withContext(Dispatchers.IO) {
                        DeclareDatabase.usersTable.select(Columns.list("user_id", "username", "profile_image_url")) {
                            filter { eq("auth_id", authId) }
                        }.decodeSingleOrNull<User>()
                    }
                    currentUserId = user?.id ?: return@launch
                    currentUser = user
                }

                val canDm = withContext(Dispatchers.IO) { repo.canSendDm(currentUserId, recipientId) }
                if (!canDm) {
                    layoutInput.visibility = View.GONE
                    layoutBlocked.visibility = View.VISIBLE
                } else {
                    layoutInput.visibility = View.VISIBLE
                    layoutBlocked.visibility = View.GONE
                }

                if (::dmAdapter.isInitialized) {
                    loadMessages()
                } else {
                    setupMessageList()
                    loadMessages()
                    setupSendButton()
                }
                subscribeRealtime()

                rvSkeleton.visibility = View.GONE
                rvMessages.visibility = View.VISIBLE
            } catch (e: Exception) {
                Toast.makeText(this@DirectMessageActivity, "Failed to load messages.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val channel = messagesChannel
        if (channel != null) {
            lifecycleScope.launch {
                try {
                    DeclareDatabase.realtime.removeChannel(channel)
                } catch (e: Exception) {
                    Log.e("DMActivity", "Error removing channel", e)
                }
            }
        }
    }

    private fun subscribeRealtime() {
        val uid = currentUserId
        if (uid == -1L) return
        
        try {
            val channelId = if (uid < recipientId) "dm_${uid}_$recipientId" else "dm_${recipientId}_$uid"
            val channel = DeclareDatabase.realtime.channel(channelId)
            messagesChannel = channel

            channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                this.table = "direct_messages"
            }.onEach { action ->
                val msg = action.decodeRecord<DirectMessage>()
                // Only handle messages between these two users
                val isRelevant = (msg.senderId == uid && msg.recipientId == recipientId) ||
                                 (msg.senderId == recipientId && msg.recipientId == uid)
                
                if (isRelevant) {
                    // Update local list
                    loadMessages()
                    // Invalidate crew list cache and notify global state
                    withContext(Dispatchers.IO) {
                        repo.invalidateCrew(uid)
                        repo.invalidateCrew(recipientId)
                    }
                    CrewState.notifyChange()
                }
            }.launchIn(lifecycleScope)

            lifecycleScope.launch {
                try {
                    channel.subscribe(blockUntilSubscribed = true)
                } catch (e: Exception) {
                    Log.e("DMActivity", "Subscription error", e)
                }
            }
        } catch (e: Exception) {
            Log.e("DMActivity", "Failed to subscribe realtime", e)
        }
    }

    private fun setupMessageList() {
        dmAdapter = DirectMessageAdapter(
            currentUserId = currentUserId,
            onLongPress = { msg, bubble -> showEmojiPopup(msg, bubble) },
            onReactionClick = { msg, reactions -> showReactionDetails(msg, reactions) },
            onReactionAction = { msgId, emoji, isAdd ->
                handleReaction(msgId, emoji, isAdd)
            },
            recipientAvatarUrl = intent.getStringExtra(EXTRA_RECIPIENT_AVATAR)
        )
        rvMessages.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        rvMessages.adapter = dmAdapter
    }

    private fun handleReaction(messageId: Long, emoji: String, isAdd: Boolean) {
        lifecycleScope.launch {
            try {
                if (isAdd) {
                    Log.d("DMActivity", "handleReaction: Adding reaction msgId=$messageId, emoji=$emoji")
                    DeclareDatabase.messageReactionsTable.insert(buildJsonObject {
                        put("direct_message_id", messageId)
                        put("user_id", currentUserId)
                        put("emoji", emoji)
                        put("message_type", MessageType.DIRECT)
                    })
                    Log.d("DMActivity", "handleReaction: Insert successful")
                } else {
                    DeclareDatabase.messageReactionsTable.delete {
                        filter {
                            eq("direct_message_id", messageId)
                            eq("user_id", currentUserId)
                            eq("emoji", emoji)
                            eq("message_type", MessageType.DIRECT)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DMActivity", "Failed to handle reaction: ${e.message}")
            }
        }
    }

    private fun showReactionDetails(msg: DirectMessage, msgReactions: List<MessageReaction>) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_reactions, null)
        dialog.setContentView(view)

        val rv = view.findViewById<RecyclerView>(R.id.rvReactionDetails)
        val btnClose = view.findViewById<ImageButton>(R.id.btnDetailClose)

        btnClose.setOnClickListener { dialog.dismiss() }

        val recipientName = intent.getStringExtra(EXTRA_RECIPIENT_NAME)
        val recipientAvatar = intent.getStringExtra(EXTRA_RECIPIENT_AVATAR)

        val allItems = msgReactions.map { r ->
            val isMine = r.userId == currentUserId
            val avatarPath = if (isMine) currentUser?.profileImageUrl else recipientAvatar
            val fullAvatarUrl = if (!avatarPath.isNullOrBlank() && avatarPath != "placeholder_profile_image") {
                if (avatarPath.startsWith("http")) avatarPath 
                else DeclareDatabase.profileImagesBucket.publicUrl(avatarPath)
            } else null

            ReactionItem(
                userId = r.userId ?: -1L,
                username = if (isMine) currentUser?.username ?: "You" else recipientName,
                avatarUrl = fullAvatarUrl,
                emoji = r.emoji ?: "",
                isMine = isMine
            )
        }

        val detailAdapter = ReactionDetailAdapter(allItems) { emoji ->
            dmAdapter.removeReaction(msg.id ?: -1L, currentUserId, emoji)
            dialog.dismiss()
        }
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = detailAdapter

        // Filters
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
            detailAdapter.updateItems(allItems)
        }

        val emojisCount = msgReactions.groupBy { it.emoji }.mapValues { it.value.size }
        emojiTabs.forEach { (emoji, tab) ->
            val count = emojisCount[emoji] ?: 0
            if (count > 0) {
                tab.visibility = View.VISIBLE
                tab.text = "$emoji $count"
                tab.setOnClickListener {
                    updateFilterUI(it)
                    detailAdapter.updateItems(allItems.filter { it.emoji == emoji })
                }
            } else {
                tab.visibility = View.GONE
            }
        }

        dialog.show()
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    private suspend fun loadMessages() {
        try {
            val messages = withContext(Dispatchers.IO) {
                val msgs = repo.getDirectMessages(currentUserId, recipientId)
                val msgIds = msgs.mapNotNull { it.id }
                if (msgIds.isNotEmpty()) {
                    try {
                        val rx = DeclareDatabase.messageReactionsTable.select {
                            filter { isIn("direct_message_id", msgIds); eq("message_type", MessageType.DIRECT) }
                        }.decodeList<MessageReaction>()
                        val rxMap = rx.groupBy { it.directMessageId ?: -1L }
                        msgs.forEach { m ->
                            m.reactions = rxMap[m.id ?: -1L]?.toMutableList() ?: mutableListOf()
                        }
                    } catch (e: Exception) {
                        Log.e("DMActivity", "Failed to load reactions", e)
                    }
                }
                msgs
            }
            dmAdapter.updateItems(messages)
            if (messages.isNotEmpty()) rvMessages.scrollToPosition(messages.size - 1)
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@DirectMessageActivity, "Failed to load messages.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSendButton() {
        btnSend.setOnClickListener {
            val content = etMessage.text.toString().trim()
            if (content.isBlank()) return@setOnClickListener
            sendMessage(content)
            etMessage.setText("")
        }
    }

    private fun sendMessage(content: String) {
        val tempId = -(System.currentTimeMillis())
        val optimistic = DirectMessage(
            id = tempId,
            senderId = currentUserId,
            recipientId = recipientId,
            content = content,
            sentAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(java.util.Date())
        )
        dmAdapter.pendingTempId = tempId
        val currentItems = dmAdapter.getItems().toMutableList()
        currentItems.add(optimistic)
        dmAdapter.updateItems(currentItems)
        rvMessages.scrollToPosition(currentItems.size - 1)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                repo.sendDirectMessage(currentUserId, recipientId, content)
                val messages = repo.getDirectMessages(currentUserId, recipientId)
                withContext(Dispatchers.Main) {
                    dmAdapter.pendingTempId = null
                    dmAdapter.updateItems(messages)
                    if (messages.isNotEmpty()) rvMessages.scrollToPosition(messages.size - 1)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    dmAdapter.pendingTempId = null
                    val withoutTemp = currentItems.filter { it.id != tempId }
                    dmAdapter.updateItems(withoutTemp)
                    Toast.makeText(this@DirectMessageActivity, "Failed to send message.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showEmojiPopup(msg: DirectMessage, bubbleView: View) {
        val uid = currentUserId
        emojiPopup.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val emojiLayout = inflater.inflate(R.layout.item_emoji_reaction, emojiPopup, false)
        emojiPopup.addView(emojiLayout)

        val userReacted = dmAdapter.getReactionsForUser(msg.id ?: -1L, uid)

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
                    if (emoji in userReacted) {
                        dmAdapter.removeReaction(msgId, uid, emoji)
                    } else {
                        dmAdapter.addReaction(msgId, uid, emoji)
                    }
                    dismissPopup()
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
            val location = IntArray(2)
            bubbleView.getLocationOnScreen(location)
            val bubbleY = location[1]

            val rootLocation = IntArray(2)
            findViewById<View>(R.id.dmRootLayout).getLocationOnScreen(rootLocation)
            val rootY = rootLocation[1]

            val margin = 8 // dp
            val density = resources.displayMetrics.density
            val marginPx = (margin * density).toInt()

            // Position above bubble
            emojiPopup.y = (bubbleY - rootY - emojiPopup.height - marginPx).toFloat()
            // Center horizontally to screen
            val rootWidth = findViewById<View>(R.id.dmRootLayout).width
            emojiPopup.x = (rootWidth - emojiPopup.width) / 2f
        }
    }

    private fun dismissPopup() {
        emojiPopup.visibility = View.GONE
        popupOverlay.visibility = View.GONE
    }
}

data class ReactionItem(
    val userId: Long,
    val username: String?,
    val avatarUrl: String?,
    val emoji: String,
    val isMine: Boolean
)

class ReactionDetailAdapter(
    private var items: List<ReactionItem>,
    private val onRemove: (String) -> Unit
) : RecyclerView.Adapter<ReactionDetailAdapter.VH>() {
    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val ivAvatar: ImageView = view.findViewById(R.id.ivReactorAvatar)
        val tvName: TextView = view.findViewById(R.id.tvReactorName)
        val tvRemove: TextView = view.findViewById(R.id.tvRemoveAction)
        val tvEmoji: TextView = view.findViewById(R.id.tvReactedEmoji)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_reaction_detail, parent, false))
    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvName.text = item.username ?: "Unknown"
        holder.tvEmoji.text = item.emoji
        if (item.isMine) {
            holder.tvRemove.visibility = View.VISIBLE
            holder.itemView.setOnClickListener { onRemove(item.emoji) }
        } else {
            holder.tvRemove.visibility = View.GONE
            holder.itemView.setOnClickListener(null)
        }
        
        val avatarUrl = item.avatarUrl
        if (!avatarUrl.isNullOrBlank() && avatarUrl != "placeholder_profile_image") {
            holder.ivAvatar.load(avatarUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_profile_silhouette)
                error(R.drawable.ic_profile_silhouette)
                transformations(CircleCropTransformation())
            }
        } else {
            holder.ivAvatar.setImageResource(R.drawable.ic_profile_silhouette)
        }
    }
    override fun getItemCount() = items.size
    @android.annotation.SuppressLint("NotifyDataSetChanged")
    fun updateItems(newItems: List<ReactionItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
