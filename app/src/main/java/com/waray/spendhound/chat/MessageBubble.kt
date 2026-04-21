package com.waray.spendhound.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waray.spendhound.GroupMessage
import com.waray.spendhound.GroupMessageReaction
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val BlueBubble = Color(0xFF1E88E5)
private val GrayBubble = Color(0xFFEEEEEE)
private val BubbleTextBlue = Color.White
private val BubbleTextGray = Color(0xFF212121)
private val SubTextColor = Color(0xFF9E9E9E)

/**
 * Renders a single chat message bubble.
 * Own messages are right-aligned with a blue bubble; others are left-aligned with gray.
 */
@Composable
fun MessageBubble(
    message: GroupMessage,
    isOwn: Boolean,
    reactions: List<GroupMessageReaction>,
    readByUserIds: Set<Long>,
    currentUserId: Long
) {
    val alignment = if (isOwn) Alignment.End else Alignment.Start
    val bubbleColor = if (isOwn) BlueBubble else GrayBubble
    val textColor = if (isOwn) BubbleTextBlue else BubbleTextGray

    // Format timestamp from ISO string to hh:mm a
    val timeText = remember(message.createdAt) { formatTime(message.createdAt) }

    // Detect if message was edited (updated_at differs from created_at)
    val isEdited = remember(message.createdAt, message.updatedAt) {
        !message.updatedAt.isNullOrBlank() && message.updatedAt != message.createdAt
    }

    // Group reactions by emoji and count them
    val groupedReactions = remember(reactions) {
        reactions.groupBy { it.emoji ?: "" }.filter { it.key.isNotBlank() }
    }

    // Whether any other user has read this message
    val isRead = remember(readByUserIds, currentUserId) {
        readByUserIds.any { it != currentUserId }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = alignment
    ) {
        // Show sender name only for others' messages
        if (!isOwn && !message.senderName.isNullOrBlank()) {
            Text(
                text = message.senderName!!,
                fontSize = 11.sp,
                color = SubTextColor,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )
        }

        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(bubbleColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                // Message content — strike-through if deleted
                if (message.isDeleted) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                                append(message.message ?: "")
                            }
                        },
                        color = textColor.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                } else {
                    Text(text = message.message ?: "", color = textColor, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Timestamp row with edited label and read receipt
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    if (isEdited) {
                        Text(
                            text = "edited · ",
                            fontSize = 10.sp,
                            fontStyle = FontStyle.Italic,
                            color = textColor.copy(alpha = 0.6f)
                        )
                    }
                    Text(text = timeText, fontSize = 10.sp, color = textColor.copy(alpha = 0.7f))
                    // Read receipt indicator for own messages
                    if (isOwn && isRead) {
                        Text(
                            text = " ✓✓",
                            fontSize = 10.sp,
                            color = if (isOwn) Color(0xFFB3E5FC) else SubTextColor
                        )
                    }
                }
            }
        }

        // Emoji reactions grouped and counted below the bubble
        if (groupedReactions.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                groupedReactions.forEach { (emoji, list) ->
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFF5F5F5), RoundedCornerShape(12.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(text = "$emoji ${list.size}", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun formatTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            .also { it.timeZone = TimeZone.getTimeZone("UTC") }
        val date = parser.parse(iso.take(19)) ?: return ""
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date)
    } catch (_: Exception) { "" }
}
