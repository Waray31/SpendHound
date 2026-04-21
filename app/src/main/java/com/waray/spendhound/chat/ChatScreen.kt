package com.waray.spendhound.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.waray.spendhound.GroupMessage
import com.waray.spendhound.R
import kotlinx.coroutines.launch

/**
 * Full chat screen composable.
 * Loads messages and subscribes to Realtime on first composition.
 * Supports send, long-press actions (react, edit, delete), and read receipts.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    groupId: Long,
    currentUserId: Long,
    chatViewModel: ChatViewModel = viewModel()
) {
    val uiState by chatViewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var selectedMessage by remember { mutableStateOf<GroupMessage?>(null) }
    var emojiTargetMessage by remember { mutableStateOf<GroupMessage?>(null) }
    var editingMessage by remember { mutableStateOf<GroupMessage?>(null) }
    var editText by remember { mutableStateOf("") }
    var inputText by remember { mutableStateOf("") }

    // Load messages and subscribe to Realtime on first composition
    LaunchedEffect(groupId) {
        chatViewModel.loadMessages(groupId)
        chatViewModel.subscribeToMessages(groupId)
        chatViewModel.subscribeToReactions(groupId)
    }

    // Auto-scroll to latest message whenever the list grows
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(uiState.messages.size - 1) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            uiState.error != null && uiState.messages.isEmpty() -> Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) { Text("Failed to load messages.", color = Color.Red) }

            uiState.messages.isEmpty() -> Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) { Text("No messages yet. Say hello!", color = Color.Gray) }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f)
            ) {
                items(uiState.messages, key = { it.id ?: it.hashCode() }) { message ->
                    val isOwn = message.userId == currentUserId
                    val reactions = uiState.reactions[message.id ?: -1L] ?: emptyList()
                    val readByUserIds = uiState.readReceipts[message.id ?: -1L] ?: emptySet()

                    // Mark each visible message as read
                    LaunchedEffect(message.id) {
                        if (!isOwn && message.id != null) {
                            chatViewModel.markAsRead(message.id, currentUserId)
                        }
                    }

                    Box(
                        modifier = Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = { selectedMessage = message }
                        )
                    ) {
                        MessageBubble(
                            message = message,
                            isOwn = isOwn,
                            reactions = reactions,
                            readByUserIds = readByUserIds,
                            currentUserId = currentUserId
                        )
                    }
                }
            }
        }

        if (uiState.error != null) {
            Text(
                text = uiState.error!!,
                color = Color.Red,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        // Bottom input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Type a message\u2026") },
                modifier = Modifier.weight(1f),
                maxLines = 4
            )
            IconButton(onClick = {
                val text = inputText.trim()
                if (text.isNotEmpty()) {
                    chatViewModel.sendMessage(groupId, currentUserId, text)
                    inputText = ""
                    // Reload after send as fallback if Realtime hasn't delivered yet
                    scope.launch { chatViewModel.loadMessages(groupId) }
                }
            }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_send),
                    contentDescription = "Send"
                )
            }
        }
    }

    // Long-press action bottom sheet
    selectedMessage?.let { msg ->
        val isOwn = msg.userId == currentUserId
        ModalBottomSheet(
            onDismissRequest = { selectedMessage = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                TextButton(onClick = {
                    emojiTargetMessage = msg
                    selectedMessage = null
                }) { Text("React") }

                if (isOwn && !msg.isDeleted) {
                    TextButton(onClick = {
                        editingMessage = msg
                        editText = msg.message ?: ""
                        selectedMessage = null
                    }) { Text("Edit") }

                    TextButton(onClick = {
                        msg.id?.let { chatViewModel.deleteMessage(it) }
                        selectedMessage = null
                    }) { Text("Delete", color = Color.Red) }
                }
            }
        }
    }

    // Emoji picker sheet
    emojiTargetMessage?.let { msg ->
        val userReactedEmojis = remember(uiState.reactions, msg.id) {
            (uiState.reactions[msg.id ?: -1L] ?: emptyList())
                .filter { it.userId == currentUserId }
                .mapNotNull { it.emoji }
                .toSet()
        }
        EmojiPickerSheet(
            messageId = msg.id ?: -1L,
            userId = currentUserId,
            userReactedEmojis = userReactedEmojis,
            onAddReaction = chatViewModel::addReaction,
            onRemoveReaction = chatViewModel::removeReaction,
            onDismiss = { emojiTargetMessage = null }
        )
    }

    // Edit message dialog
    editingMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("Edit Message") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    msg.id?.let { chatViewModel.editMessage(it, editText.trim()) }
                    editingMessage = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) { Text("Cancel") }
            }
        )
    }
}
