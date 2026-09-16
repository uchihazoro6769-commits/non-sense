package com.ryuzen.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ryuzen.chat.Conversation
import com.ryuzen.chat.ConversationType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    conversations: List<Conversation>,
    onOpenConversation: (Conversation) -> Unit,
    onCreateGroup: () -> Unit,
    onCreateChannel: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Suhbatlar") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateGroup,
                icon = { Icon(Icons.Filled.Groups, contentDescription = null) },
                text = { Text("Guruh") }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(conversations, key = { it.id }) { conv ->
                ConversationRow(conv, onClick = { onOpenConversation(conv) })
            }
        }
    }
}

@Composable
private fun ConversationRow(conversation: Conversation, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Box {
                AsyncImage(
                    model = conversation.avatarUrl,
                    contentDescription = conversation.title,
                    modifier = Modifier.size(48.dp).clip(CircleShape)
                )
                if (conversation.type == ConversationType.CHANNEL) {
                    Icon(
                        imageVector = Icons.Filled.Campaign,
                        contentDescription = "Kanal",
                        modifier = Modifier.size(16.dp).align(Alignment.BottomEnd)
                    )
                } else if (conversation.type == ConversationType.GROUP) {
                    Icon(
                        imageVector = Icons.Filled.Groups,
                        contentDescription = "Guruh",
                        modifier = Modifier.size(16.dp).align(Alignment.BottomEnd)
                    )
                }
            }
        },
        headlineContent = { Text(conversation.title) },
        supportingContent = {
            Text(
                text = conversation.lastMessage ?: when (conversation.type) {
                    ConversationType.CHANNEL -> "${conversation.subscribersCount} obunachi"
                    ConversationType.GROUP -> "${conversation.membersCount} a'zo"
                    ConversationType.PRIVATE -> ""
                },
                maxLines = 1
            )
        },
        trailingContent = {
            if (conversation.unreadCount > 0) {
                Badge { Text(conversation.unreadCount.toString()) }
            }
        }
    )
}
