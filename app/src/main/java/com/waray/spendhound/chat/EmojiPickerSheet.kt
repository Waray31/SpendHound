package com.waray.spendhound.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val COMMON_EMOJIS = listOf("👍", "❤️", "😂", "😮", "😢", "🔥")

/**
 * Bottom sheet showing a row of common emojis.
 * Tapping an emoji calls addReaction or removeReaction depending on whether the user already reacted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiPickerSheet(
    messageId: Long,
    userId: Long,
    // Set of emojis the current user has already reacted with on this message
    userReactedEmojis: Set<String>,
    onAddReaction: (messageId: Long, userId: Long, emoji: String) -> Unit,
    onRemoveReaction: (messageId: Long, userId: Long, emoji: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            COMMON_EMOJIS.forEach { emoji ->
                val alreadyReacted = emoji in userReactedEmojis
                Text(
                    text = emoji,
                    fontSize = 30.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(4.dp)
                        .clickable {
                            if (alreadyReacted) {
                                onRemoveReaction(messageId, userId, emoji)
                            } else {
                                onAddReaction(messageId, userId, emoji)
                            }
                            onDismiss()
                        }
                )
            }
        }
    }
}
