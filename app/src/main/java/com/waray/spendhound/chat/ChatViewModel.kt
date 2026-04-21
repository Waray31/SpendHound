package com.waray.spendhound.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.GroupMessage
import com.waray.spendhound.GroupMessageInsert
import com.waray.spendhound.GroupMessageReaction
import com.waray.spendhound.MessageRead
import com.waray.spendhound.MessageReadInsert
import com.waray.spendhound.User
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeOldRecord
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReactionInsert(
    @SerialName("message_id") val messageId: Long,
    @SerialName("user_id") val userId: Long,
    @SerialName("emoji") val emoji: String
)

data class ChatUiState(
    val messages: List<GroupMessage> = emptyList(),
    val reactions: Map<Long, List<GroupMessageReaction>> = emptyMap(),
    val readReceipts: Map<Long, Set<Long>> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class ChatViewModel : ViewModel() {

    companion object { private const val TAG = "ChatViewModel" }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var messagesChannel: RealtimeChannel? = null
    private var reactionsChannel: RealtimeChannel? = null

    // Fetch all non-deleted messages for a group ordered by created_at ascending
    fun loadMessages(groupId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                Log.d(TAG, "loadMessages: fetching groupId=$groupId")
                val raw = DeclareDatabase.groupMessagesTable.select {
                    filter { eq("group_id", groupId) }
                    order("created_at", Order.ASCENDING)
                }.decodeList<GroupMessage>()

                Log.d(TAG, "loadMessages: raw count=${raw.size}")

                // Filter deleted client-side to avoid boolean serialization issues
                val msgs = raw.filter { !it.isDeleted }
                Log.d(TAG, "loadMessages: non-deleted count=${msgs.size}")

                val users = DeclareDatabase.usersTable.select().decodeList<User>()
                Log.d(TAG, "loadMessages: users count=${users.size}")

                val enriched = msgs.map { msg ->
                    val sender = users.firstOrNull { it.id == msg.userId }
                    msg.senderName = sender?.username
                    msg.senderProfileImage = sender?.id?.let { "${it}/${it}.jpg" }
                    msg
                }

                val msgIds = enriched.mapNotNull { it.id }
                val reactions = if (msgIds.isNotEmpty()) loadReactionsForMessages(msgIds) else emptyMap()
                val reads = if (msgIds.isNotEmpty()) loadReadReceipts(msgIds) else emptyMap()

                Log.d(TAG, "loadMessages: updating state with ${enriched.size} messages")
                _uiState.update {
                    it.copy(messages = enriched, reactions = reactions, readReceipts = reads, isLoading = false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadMessages failed: ${e.javaClass.simpleName}: ${e.message}", e)
                _uiState.update { it.copy(isLoading = false, error = "Load failed: ${e.message}") }
            }
        }
    }

    // Insert a new row into group_messages, then reload to ensure display
    fun sendMessage(groupId: Long, userId: Long, content: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "sendMessage: groupId=$groupId userId=$userId content=$content")
                DeclareDatabase.groupMessagesTable.insert(
                    GroupMessageInsert(groupId = groupId, userId = userId, message = content)
                )
                Log.d(TAG, "sendMessage: insert succeeded")
                _uiState.update { it.copy(error = null) }
            } catch (e: Exception) {
                Log.e(TAG, "sendMessage failed: ${e.javaClass.simpleName}: ${e.message}", e)
                _uiState.update { it.copy(error = "Send failed: ${e.message}") }
            }
        }
    }

    // Soft delete by setting is_deleted = true
    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            try {
                DeclareDatabase.groupMessagesTable.update({ set("is_deleted", true) }) {
                    filter { eq("id", messageId) }
                }
                _uiState.update { state ->
                    state.copy(messages = state.messages.map {
                        if (it.id == messageId) it.copy(isDeleted = true) else it
                    })
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "deleteMessage failed", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    // Update message content and updated_at timestamp
    fun editMessage(messageId: Long, newContent: String) {
        viewModelScope.launch {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).also { it.timeZone = TimeZone.getTimeZone("UTC") }
                val now = sdf.format(Date())
                DeclareDatabase.groupMessagesTable.update({
                    set("message", newContent)
                    set("updated_at", now)
                }) {
                    filter { eq("id", messageId) }
                }
                _uiState.update { state ->
                    state.copy(messages = state.messages.map {
                        if (it.id == messageId) it.copy(message = newContent, updatedAt = now) else it
                    })
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "editMessage failed", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    // Insert a row into message_reads, skipping duplicates
    fun markAsRead(messageId: Long, userId: Long) {
        viewModelScope.launch {
            try {
                val existing = DeclareDatabase.messageReadsTable.select {
                    filter { eq("message_id", messageId); eq("user_id", userId) }
                }.decodeList<MessageRead>()
                if (existing.isEmpty()) {
                    DeclareDatabase.messageReadsTable.insert(
                        MessageReadInsert(messageId = messageId, userId = userId)
                    )
                    _uiState.update { state ->
                        val updated = state.readReceipts.toMutableMap()
                        updated[messageId] = (updated[messageId] ?: emptySet()) + userId
                        state.copy(readReceipts = updated)
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "markAsRead failed", e)
            }
        }
    }

    // Insert a row into group_message_reactions
    fun addReaction(messageId: Long, userId: Long, emoji: String) {
        viewModelScope.launch {
            try {
                DeclareDatabase.groupMessageReactionsTable.insert(
                    ReactionInsert(messageId = messageId, userId = userId, emoji = emoji)
                )
                val newReaction = GroupMessageReaction(messageId = messageId, userId = userId, emoji = emoji)
                _uiState.update { state ->
                    val updated = state.reactions.toMutableMap()
                    updated[messageId] = (updated[messageId] ?: emptyList()) + newReaction
                    state.copy(reactions = updated)
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "addReaction failed", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    // Delete matching row from group_message_reactions
    fun removeReaction(messageId: Long, userId: Long, emoji: String) {
        viewModelScope.launch {
            try {
                DeclareDatabase.groupMessageReactionsTable.delete {
                    filter { eq("message_id", messageId); eq("user_id", userId); eq("emoji", emoji) }
                }
                _uiState.update { state ->
                    val updated = state.reactions.toMutableMap()
                    updated[messageId] = (updated[messageId] ?: emptyList()).filter {
                        !(it.userId == userId && it.emoji == emoji)
                    }
                    state.copy(reactions = updated)
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "removeReaction failed", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    // Open Realtime channel for INSERT events on group_messages filtered by group_id
    fun subscribeToMessages(groupId: Long) {
        viewModelScope.launch {
            try {
                messagesChannel?.let { DeclareDatabase.realtime.removeChannel(it) }
                val channel = DeclareDatabase.realtime.channel("chat_messages_$groupId")
                messagesChannel = channel

                channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = "group_messages"
                    filter("group_id", FilterOperator.EQ, groupId)
                }.onEach { action ->
                    try {
                        val msg = action.decodeRecord<GroupMessage>()
                        if (msg.isDeleted) return@onEach
                        if (_uiState.value.messages.any { it.id == msg.id }) return@onEach

                        val users = DeclareDatabase.usersTable.select().decodeList<User>()
                        val sender = users.firstOrNull { it.id == msg.userId }
                        msg.senderName = sender?.username
                        msg.senderProfileImage = sender?.id?.let { "${it}/${it}.jpg" }

                        _uiState.update { state -> state.copy(messages = state.messages + msg) }
                    } catch (e: Exception) {
                        Log.e("ChatViewModel", "Realtime message insert error", e)
                    }
                }.launchIn(viewModelScope)

                channel.subscribe(blockUntilSubscribed = true)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "subscribeToMessages failed", e)
            }
        }
    }

    // Listen for INSERT and DELETE events on group_message_reactions and update reaction state
    fun subscribeToReactions(groupId: Long) {
        viewModelScope.launch {
            try {
                reactionsChannel?.let { DeclareDatabase.realtime.removeChannel(it) }
                val channel = DeclareDatabase.realtime.channel("chat_reactions_$groupId")
                reactionsChannel = channel

                channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = "group_message_reactions"
                }.onEach { action ->
                    try {
                        val reaction = action.decodeRecord<GroupMessageReaction>()
                        val msgId = reaction.messageId ?: return@onEach
                        if (_uiState.value.messages.none { it.id == msgId }) return@onEach
                        _uiState.update { state ->
                            val updated = state.reactions.toMutableMap()
                            val existing = updated[msgId] ?: emptyList()
                            if (existing.none { it.userId == reaction.userId && it.emoji == reaction.emoji }) {
                                updated[msgId] = existing + reaction
                            }
                            state.copy(reactions = updated)
                        }
                    } catch (e: Exception) {
                        Log.e("ChatViewModel", "Realtime reaction insert error", e)
                    }
                }.launchIn(viewModelScope)

                channel.postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
                    table = "group_message_reactions"
                }.onEach { action ->
                    try {
                        val reaction = action.decodeOldRecord<GroupMessageReaction>()
                        val msgId = reaction.messageId ?: return@onEach
                        _uiState.update { state ->
                            val updated = state.reactions.toMutableMap()
                            updated[msgId] = (updated[msgId] ?: emptyList()).filter {
                                !(it.userId == reaction.userId && it.emoji == reaction.emoji)
                            }
                            state.copy(reactions = updated)
                        }
                    } catch (e: Exception) {
                        Log.e("ChatViewModel", "Realtime reaction delete error", e)
                    }
                }.launchIn(viewModelScope)

                channel.subscribe(blockUntilSubscribed = true)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "subscribeToReactions failed", e)
            }
        }
    }

    // Disconnect Realtime channels on ViewModel destruction
    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            try {
                messagesChannel?.let { DeclareDatabase.realtime.removeChannel(it) }
                reactionsChannel?.let { DeclareDatabase.realtime.removeChannel(it) }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "onCleared channel removal failed", e)
            }
        }
    }

    private suspend fun loadReactionsForMessages(messageIds: List<Long>): Map<Long, List<GroupMessageReaction>> {
        return try {
            DeclareDatabase.groupMessageReactionsTable.select {
                filter { isIn("message_id", messageIds) }
            }.decodeList<GroupMessageReaction>().groupBy { it.messageId ?: -1L }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "loadReactions failed", e)
            emptyMap()
        }
    }

    private suspend fun loadReadReceipts(messageIds: List<Long>): Map<Long, Set<Long>> {
        return try {
            DeclareDatabase.messageReadsTable.select {
                filter { isIn("message_id", messageIds) }
            }.decodeList<MessageRead>()
                .groupBy { it.messageId ?: -1L }
                .mapValues { (_, reads) -> reads.mapNotNull { it.userId }.toSet() }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "loadReadReceipts failed", e)
            emptyMap()
        }
    }
}
