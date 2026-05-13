package com.waray.spendhound

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DirectMessageActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RECIPIENT_ID = "recipient_id"
        const val EXTRA_RECIPIENT_NAME = "recipient_name"
        const val EXTRA_RECIPIENT_AVATAR = "recipient_avatar"
    }

    private val repo = CrewRepository()
    private var currentUserId: Long = -1L
    private var recipientId: Long = -1L

    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var layoutInput: LinearLayout
    private lateinit var layoutBlocked: LinearLayout
    private lateinit var emojiPopup: LinearLayout
    private lateinit var popupOverlay: View
    private lateinit var dmAdapter: DirectMessageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_direct_message)
        supportActionBar?.hide()

        recipientId = intent.getLongExtra(EXTRA_RECIPIENT_ID, -1L)
        val recipientName = intent.getStringExtra(EXTRA_RECIPIENT_NAME) ?: ""
        val recipientAvatar = intent.getStringExtra(EXTRA_RECIPIENT_AVATAR)

        rvMessages = findViewById(R.id.rvDirectMessages)
        etMessage = findViewById(R.id.etDmMessage)
        btnSend = findViewById(R.id.btnSendDm)
        layoutInput = findViewById(R.id.layoutDmInput)
        layoutBlocked = findViewById(R.id.layoutGuestBlocked)
        emojiPopup = findViewById(R.id.emojiPopup)
        popupOverlay = findViewById(R.id.popupOverlay)

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

        resolveUserAndInit()
    }

    private fun resolveUserAndInit() {
        lifecycleScope.launch {
            try {
                val authId = DeclareDatabase.auth.currentUserOrNull()?.id ?: return@launch
                val user = withContext(Dispatchers.IO) {
                    DeclareDatabase.usersTable.select(Columns.list("user_id")) {
                        filter { eq("auth_id", authId) }
                    }.decodeSingleOrNull<User>()
                }
                currentUserId = user?.id ?: return@launch

                val canDm = withContext(Dispatchers.IO) { repo.canSendDm(currentUserId, recipientId) }
                if (!canDm) {
                    layoutInput.visibility = View.GONE
                    layoutBlocked.visibility = View.VISIBLE
                } else {
                    layoutInput.visibility = View.VISIBLE
                    layoutBlocked.visibility = View.GONE
                }

                setupMessageList()
                loadMessages()
                setupSendButton()
            } catch (e: Exception) {
                Toast.makeText(this@DirectMessageActivity, "Failed to load messages.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupMessageList() {
        dmAdapter = DirectMessageAdapter(
            currentUserId = currentUserId,
            onLongPress = { msg, bubble -> showEmojiPopup(msg, bubble) },
            recipientAvatarUrl = intent.getStringExtra(EXTRA_RECIPIENT_AVATAR)
        )
        rvMessages.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        rvMessages.adapter = dmAdapter
    }

    private fun loadMessages() {
        lifecycleScope.launch {
            try {
                val messages = withContext(Dispatchers.IO) {
                    repo.getDirectMessages(currentUserId, recipientId)
                }
                dmAdapter.updateItems(messages)
                if (messages.isNotEmpty()) rvMessages.scrollToPosition(messages.size - 1)
            } catch (e: Exception) {
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

        val emojiMap = mapOf(
            R.id.tvEmojiLike to "👍",
            R.id.tvEmojiLove to "❤️",
            R.id.tvEmojiHaha to "😂",
            R.id.tvEmojiWow  to "😮",
            R.id.tvEmojiSad  to "😢",
            R.id.tvEmojiFire to "🔥"
        )

        emojiMap.forEach { (viewId, emoji) ->
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
            }
        }

        popupOverlay.visibility = View.VISIBLE
        emojiPopup.visibility = View.VISIBLE

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
