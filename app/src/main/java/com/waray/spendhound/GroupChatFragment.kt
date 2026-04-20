package com.waray.spendhound

import android.annotation.SuppressLint
import android.content.Intent
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
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
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
    private val messages = mutableListOf<GroupMessage>()
    private lateinit var adapter: ChatAdapter
    private lateinit var rvMessages: RecyclerView
    private var realtimeChannel: RealtimeChannel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        groupId = arguments?.getLong("group_id") ?: -1
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        inflater.inflate(R.layout.fragment_group_chat, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        rvMessages = view.findViewById(R.id.rvMessages)
        adapter = ChatAdapter()
        rvMessages.layoutManager = LinearLayoutManager(requireContext()).also { it.stackFromEnd = true }
        rvMessages.adapter = adapter

        view.findViewById<ImageButton>(R.id.btnSend).setOnClickListener {
            val et = view.findViewById<EditText>(R.id.etMessage)
            val text = et.text.toString().trim()
            if (text.isNotEmpty()) { sendMessage(text); et.setText("") }
        }

        // Resolve user first, then load messages, then subscribe — in sequence
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
                realtimeChannel?.let { DeclareDatabase.realtime.removeChannel(it) }
                realtimeChannel = null
            } catch (e: Exception) {
                Log.e(TAG, "Error removing channel", e)
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
            currentUserId = DeclareDatabase.usersTable.select {
                filter { eq("auth_id", authId) }
            }.decodeSingleOrNull<User>()?.id
            Log.d(TAG, "Resolved currentUserId=$currentUserId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve user", e)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private suspend fun loadMessages() {
        try {
            val raw = DeclareDatabase.groupMessagesTable.select {
                filter {
                    eq("group_id", groupId)
                    eq("is_deleted", false)
                }
            }.decodeList<GroupMessage>()

            Log.d(TAG, "Loaded ${raw.size} messages")

            val allUsers = DeclareDatabase.usersTable.select().decodeList<User>()
            val enriched = enrichMessages(raw, allUsers)

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
        val uid = currentUserId ?: run {
            Log.w(TAG, "sendMessage called but currentUserId is null")
            return
        }
        lifecycleScope.launch {
            try {
                DeclareDatabase.groupMessagesTable.insert(
                    GroupMessageInsert(groupId = groupId, userId = uid, message = text)
                )
                Log.d(TAG, "Message sent")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message", e)
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
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mark messages read", e)
            }
        }
    }

    private suspend fun subscribeRealtime() {
        try {
            // Remove any existing channel first
            realtimeChannel?.let { DeclareDatabase.realtime.removeChannel(it) }

            val channel = DeclareDatabase.realtime.channel("group_messages_${groupId}")
            realtimeChannel = channel

            // MUST set up postgresChangeFlow BEFORE calling subscribe()
            channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "group_messages"
                filter("group_id", FilterOperator.EQ, groupId)
            }.onEach { action ->
                try {
                    val msg = action.decodeRecord<GroupMessage>()
                    Log.d(TAG, "Realtime insert received: id=${msg.id}")
                    if (msg.isDeleted) return@onEach
                    // Skip if already in list (loaded by loadMessages)
                    if (messages.any { it.id == msg.id }) return@onEach

                    val users = DeclareDatabase.usersTable.select().decodeList<User>()
                    val enriched = enrichMessages(listOf(msg), users).first()
                    messages.add(enriched)

                    requireActivity().runOnUiThread {
                        adapter.notifyItemInserted(messages.size - 1)
                        rvMessages.scrollToPosition(messages.size - 1)
                    }
                    markMessagesRead()
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling realtime insert", e)
                }
            }.launchIn(lifecycleScope)

            channel.subscribe(blockUntilSubscribed = true)
            Log.d(TAG, "Realtime channel subscribed for group $groupId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe realtime", e)
        }
    }

    private fun formatTime(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).also {
                it.timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = sdf.parse(iso.take(19)) ?: return ""
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
        } catch (_: Exception) { "" }
    }

    @SuppressLint("NotifyDataSetChanged")
    inner class ChatAdapter : RecyclerView.Adapter<ChatAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvSenderName: TextView = view.findViewById(R.id.tvSenderName)
            val bubbleRow: LinearLayout = view.findViewById(R.id.bubbleRow)
            val ivAvatar: ImageView = view.findViewById(R.id.ivAvatar)
            val bubble: LinearLayout = view.findViewById(R.id.bubble)
            val tvMessage: TextView = view.findViewById(R.id.tvMessage)
            val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
            val transactionCard: LinearLayout = view.findViewById(R.id.transactionCard)
            val tvTransactionTitle: TextView = view.findViewById(R.id.tvTransactionTitle)
            val tvTransactionAmount: TextView = view.findViewById(R.id.tvTransactionAmount)
            val tvTransactionStatus: TextView = view.findViewById(R.id.tvTransactionStatus)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false))

        override fun getItemCount() = messages.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val msg = messages[position]
            val isMine = msg.userId == currentUserId

            holder.bubbleRow.gravity = if (isMine) android.view.Gravity.END else android.view.Gravity.START
            holder.bubble.setBackgroundResource(
                if (isMine) R.drawable.glassy_background3 else R.drawable.bg_light_card_outline
            )
            holder.tvMessage.setTextColor(
                if (isMine) requireContext().getColor(R.color.whitest)
                else requireContext().getColor(R.color.black)
            )
            holder.tvTimestamp.setTextColor(
                if (isMine) requireContext().getColor(R.color.white_70)
                else requireContext().getColor(R.color.grey)
            )

            if (!isMine) {
                holder.tvSenderName.visibility = View.VISIBLE
                holder.tvSenderName.text = msg.senderName ?: "Unknown"
                holder.ivAvatar.visibility = View.VISIBLE
                val avatarPath = msg.senderProfileImage
                if (!avatarPath.isNullOrBlank()) {
                    Glide.with(requireContext())
                        .load(DeclareDatabase.profileImagesBucket.publicUrl(avatarPath))
                        .circleCrop()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .placeholder(R.drawable.placeholder_profile_image)
                        .error(R.drawable.placeholder_profile_image)
                        .into(holder.ivAvatar)
                }
            } else {
                holder.tvSenderName.visibility = View.GONE
                holder.ivAvatar.visibility = View.GONE
            }

            holder.tvMessage.text = msg.message ?: ""
            holder.tvTimestamp.text = formatTime(msg.createdAt)

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
        }
    }
}
