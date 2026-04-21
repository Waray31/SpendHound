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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.GroupMessage
import com.waray.spendhound.GroupMessageReaction
import com.waray.spendhound.R
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val BlueBubble = Color(0xFF1E88E5)
private val GrayBubble = Color(0xFFEEEEEE)
private val BubbleTextBlue = Color.White
private val BubbleTextGray = Color(0xFF212121)
private val SubTextColor = Color(0xFF9E9E9E)

/**
 * Renders a single chat message bubble with avatar.
 * Own messages: avatar on the RIGHT, bubble right-aligned, blue background.
 * Others' messages: avatar on the LEFT, bubble left-aligned, gray background.
 */
@Composable
fun MessageBubble(
    message: GroupMessage,
    isOwn: Boolean,
    reactions: List<GroupMessageReaction>,
    readByUserIds: Set<Long>,
    currentUserId: Long,
    // Only relevant for the most recent own message
    isSending: Boolean = false,
    isSent: Boolean = false
) {
    val bubbleColor = if (isOwn) BlueBubble else GrayBubble
    val textColor = if (isOwn) BubbleTextBlue else BubbleTextGray

    val timeText = remember(message.createdAt) { formatTime(message.createdAt) }
    val isEdited = remember(message.createdAt, message.updatedAt) {
        !message.updatedAt.isNullOrBlank() && message.updatedAt != message.createdAt
    }
    val groupedReactions = remember(reactions) {
        reactions.groupBy { it.emoji ?: "" }.filter { it.key.isNotBlank() }
    }
    val isRead = remember(readByUserIds, currentUserId) {
        readByUserIds.any { it != currentUserId }
    }

    // Resolve avatar public URL from storage path
    val avatarUrl = remember(message.senderProfileImage) {
        message.senderProfileImage?.let {
            runCatching { DeclareDatabase.profileImagesBucket.publicUrl(it) }.getOrNull()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        // Avatar on the LEFT for others' messages
        if (!isOwn) {
            Avatar(avatarUrl = avatarUrl)
            Spacer(modifier = Modifier.width(6.dp))
        }

        // Bubble + reactions column
        Column(
            horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 260.dp)
        ) {
            // Sender name above bubble for others' messages
            if (!isOwn && !message.senderName.isNullOrBlank()) {
                Text(
                    text = message.senderName!!,
                    fontSize = 11.sp,
                    color = SubTextColor,
                    modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
                )
            }

            // Message bubble
            Box(
                modifier = Modifier
                    .background(bubbleColor, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column {
                    // Strike-through if deleted
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

                    // Timestamp + edited label + read receipt
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
                        Text(
                            text = timeText,
                            fontSize = 10.sp,
                            color = textColor.copy(alpha = 0.7f)
                        )
                        if (isOwn && isRead) {
                            Text(
                                text = " ✓✓",
                                fontSize = 10.sp,
                                color = Color(0xFFB3E5FC)
                            )
                        }
                    }
                }
            }

            // Emoji reactions below bubble
            if (groupedReactions.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 2.dp),
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

            // Sending / Sent status — only on the most recent own message
            if (isOwn && (isSending || isSent)) {
                Text(
                    text = if (isSending) "sending..." else "sent",
                    fontSize = 10.sp,
                    color = SubTextColor,
                    modifier = Modifier.padding(top = 2.dp, end = 2.dp)
                )
            }
        }

        // Avatar on the RIGHT for own messages
        if (isOwn) {
            Spacer(modifier = Modifier.width(6.dp))
            Avatar(avatarUrl = avatarUrl)
        }
    }
}

@Composable
private fun Avatar(avatarUrl: String?) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(avatarUrl)
            .crossfade(true)
            .build(),
        contentDescription = "Profile",
        contentScale = ContentScale.Crop,
        placeholder = painterResource(R.drawable.placeholder_profile_image),
        error = painterResource(R.drawable.placeholder_profile_image),
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
    )
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
