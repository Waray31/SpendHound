package com.waray.spendhound

import android.os.Bundle
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

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvDmRecipientName).text = recipientName

        val avatarView = findViewById<ImageView>(R.id.dmRecipientAvatar)
        if (!recipientAvatar.isNullOrBlank() && recipientAvatar != "placeholder_profile_image") {
            avatarView.load(recipientAvatar) {
                crossfade(true)
                transformations(CircleCropTransformation())
            }
        }

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

                // Check DM eligibility
                val canDm = withContext(Dispatchers.IO) { repo.canSendDm(currentUserId, recipientId) }
                if (!canDm) {
                    layoutInput.visibility = View.GONE
                    layoutBlocked.visibility = View.VISIBLE
                } else {
                    layoutInput.visibility = View.VISIBLE
                    layoutBlocked.visibility = View.GONE
                    setupSendButton()
                }

                setupMessageList()
                loadMessages()
            } catch (e: Exception) {
                Toast.makeText(this@DirectMessageActivity, "Failed to load messages.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupMessageList() {
        dmAdapter = DirectMessageAdapter(currentUserId)
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
            btnSend.isEnabled = false
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) { repo.sendDirectMessage(currentUserId, recipientId, content) }
                    etMessage.setText("")
                    loadMessages()
                } catch (e: Exception) {
                    Toast.makeText(this@DirectMessageActivity, "Failed to send message.", Toast.LENGTH_SHORT).show()
                } finally {
                    btnSend.isEnabled = true
                }
            }
        }
    }
}
