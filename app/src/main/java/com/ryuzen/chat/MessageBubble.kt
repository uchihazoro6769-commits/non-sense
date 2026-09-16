package com.ryuzen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ryuzen.chat.ChatMessage
import com.ryuzen.chat.MessageStatus
import com.ryuzen.chat.MessageType
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MessageBubble(message: ChatMessage) {
    val bubbleColor = if (message.isMine)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.surfaceVariant

    val textColor = if (message.isMine)
        MaterialTheme.colorScheme.onPrimary
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    val alignment = if (message.isMine) Alignment.End else Alignment.Start
    val shape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (message.isMine) 16.dp else 4.dp,
        bottomEnd = if (message.isMine) 4.dp else 16.dp
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(bubbleColor)
                .padding(10.dp)
        ) {
            when (message.type) {
                MessageType.IMAGE -> {
                    AsyncImage(
                        model = message.imageUrl,
                        contentDescription = "Yuborilgan rasm",
                        modifier = Modifier
                            .widthIn(max = 240.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
                MessageType.AUDIO -> {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.PlayArrow, "Audio", tint = textColor); Spacer(Modifier.width(6.dp)); Text("Audio ${message.audioDurationMs / 1000}s", color = textColor) }
                }
                MessageType.FILE -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.InsertDriveFile,
                            contentDescription = "Fayl",
                            tint = textColor
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = message.fileName ?: "Fayl",
                            color = textColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                MessageType.TEXT -> {
                    Text(
                        text = message.text ?: "",
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (message.isEdited) {
                    Text(
                        text = "tahrirlangan",
                        color = textColor.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall,
                        fontStyle = FontStyle.Italic
                    )
                    Spacer(Modifier.width(4.dp))
                }

                Text(
                    text = formatTime(message.timestamp),
                    color = textColor.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall
                )

                if (message.isMine) {
                    Spacer(Modifier.width(4.dp))
                    MessageStatusIcon(status = message.status)
                }
            }
        }
    }
}

@Composable
private fun MessageStatusIcon(status: MessageStatus) {
    when (status) {
        MessageStatus.SENDING -> {
            // Kichik yuklanish indikatori o'rnini bosadi - oddiy nuqta
            Text(text = "…", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
        }
        MessageStatus.SENT -> {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Yuborildi",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(14.dp)
            )
        }
        MessageStatus.DELIVERED -> {
            Icon(
                imageVector = Icons.Filled.DoneAll,
                contentDescription = "Yetkazildi",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(14.dp)
            )
        }
        MessageStatus.READ -> {
            Icon(
                imageVector = Icons.Filled.DoneAll,
                contentDescription = "O'qildi",
                tint = Color(0xFF34B7F1), // ko'k rang - Telegram/WhatsApp uslubi
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
