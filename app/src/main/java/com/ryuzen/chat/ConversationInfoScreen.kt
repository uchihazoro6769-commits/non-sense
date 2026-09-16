package com.ryuzen.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ryuzen.chat.*

/**
 * Guruh/Kanal ma'lumot ekrani: a'zolar ro'yxati, rollar va moderatsiya amallari.
 * Kanal uchun (type == CHANNEL) a'zolar o'rniga "subscribers" ko'rsatiladi,
 * va faqat admin/owner post qila oladi (onlyAdminsCanPost).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationInfoScreen(
    conversation: Conversation,
    currentUserRole: MemberRole,
    onMuteMember: (GroupMember) -> Unit,
    onKickMember: (GroupMember) -> Unit,
    onBanMember: (GroupMember) -> Unit,
    onPromoteToModerator: (GroupMember) -> Unit,
    onReportConversation: () -> Unit
) {
    val isChannel = conversation.type == ConversationType.CHANNEL
    val canModerate = currentUserRole == MemberRole.OWNER ||
            currentUserRole == MemberRole.ADMIN ||
            currentUserRole == MemberRole.MODERATOR

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (isChannel) "Kanal ma'lumoti" else "Guruh ma'lumoti") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                ConversationHeader(conversation = conversation, isChannel = isChannel)
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
            }

            item {
                ListItem(
                    headlineContent = { Text("Shikoyat qilish") },
                    leadingContent = {
                        Icon(Icons.Filled.ReportProblem, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onReportConversation)
                )
                HorizontalDivider()
            }

            if (!isChannel || canModerate) {
                item {
                    Text(
                        text = if (isChannel) "Adminlar" else "A'zolar (${conversation.membersCount})",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                items(conversation.members) { member ->
                    MemberRow(
                        member = member,
                        canModerate = canModerate && member.role != MemberRole.OWNER,
                        onMute = { onMuteMember(member) },
                        onKick = { onKickMember(member) },
                        onBan = { onBanMember(member) },
                        onPromote = { onPromoteToModerator(member) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationHeader(conversation: Conversation, isChannel: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = conversation.avatarUrl,
            contentDescription = conversation.title,
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
        )
        Spacer(Modifier.height(8.dp))
        Text(conversation.title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = if (isChannel)
                "${conversation.subscribersCount} obunachi"
            else
                "${conversation.membersCount} a'zo",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        conversation.description?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        if (isChannel && conversation.onlyAdminsCanPost) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Faqat adminlar post qila oladi",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun MemberRow(
    member: GroupMember,
    canModerate: Boolean,
    onMute: () -> Unit,
    onKick: () -> Unit,
    onBan: () -> Unit,
    onPromote: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    ListItem(
        leadingContent = {
            AsyncImage(
                model = member.avatarUrl,
                contentDescription = member.userName,
                modifier = Modifier.size(40.dp).clip(CircleShape)
            )
        },
        headlineContent = { Text(member.userName) },
        supportingContent = {
            Text(
                text = when (member.role) {
                    MemberRole.OWNER -> "Egasi"
                    MemberRole.ADMIN -> "Admin"
                    MemberRole.MODERATOR -> "Moderator"
                    MemberRole.MEMBER -> if (member.isMuted) "Ovozi o'chirilgan" else "A'zo"
                }
            )
        },
        trailingContent = {
            if (canModerate) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Amallar")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Moderator qilish") },
                            onClick = { showMenu = false; onPromote() }
                        )
                        DropdownMenuItem(
                            text = { Text(if (member.isMuted) "Ovozini yoqish" else "Ovozini o'chirish") },
                            onClick = { showMenu = false; onMute() }
                        )
                        DropdownMenuItem(
                            text = { Text("Guruhdan chiqarish") },
                            onClick = { showMenu = false; onKick() }
                        )
                        DropdownMenuItem(
                            text = { Text("Bloklash (ban)") },
                            onClick = { showMenu = false; onBan() }
                        )
                    }
                }
            }
        }
    )
}

