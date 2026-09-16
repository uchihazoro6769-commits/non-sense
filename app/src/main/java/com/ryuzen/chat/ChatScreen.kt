package com.ryuzen.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ryuzen.chat.ChatMessage
import com.ryuzen.chat.MessageStatus
import kotlinx.coroutines.launch

/**
 * Chat ekrani. ViewModel orqali WebSocket va REST (Go backend) bilan bog'lanadi.
 * Bu yerda faqat UI qismi ko'rsatilgan; tarmoq qatlami (WebSocketClient, ChatViewModel)
 * alohida qo'shiladi.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    userName: String,
    isOnline: Boolean,
    messages: List<ChatMessage>,
    isTyping: Boolean = false,
    onSendText: (String) -> Unit,
    onSendImage: () -> Unit,
    onSendFile: () -> Unit,
    onEditMessage: (ChatMessage) -> Unit,
    onDeleteMessage: (ChatMessage) -> Unit,
    onBlockUser: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var selectedMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editText by remember { mutableStateOf("") }
    var showUserMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Yangi xabar kelganda ro'yxatni pastga suramiz
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(messages.size - 1) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(userName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (isTyping) "yozayotgan..." else if (isOnline) "online" else "oxirgi marta ko'rilgan",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showUserMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menyu")
                    }
                    DropdownMenu(expanded = showUserMenu, onDismissRequest = { showUserMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Bloklash") },
                            leadingIcon = { Icon(Icons.Filled.Block, contentDescription = null) },
                            onClick = {
                                showUserMenu = false
                                onBlockUser()
                            }
                        )
                    }
                }
            )
        },
        bottomBar = {
            ChatInputBar(
                text = inputText,
                onTextChange = { inputText = it },
                onSendClick = {
                    if (inputText.isNotBlank()) {
                        onSendText(inputText.trim())
                        inputText = ""
                    }
                },
                onImageClick = onSendImage,
                onFileClick = onSendFile
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            itemsIndexed(messages, key = { _, m -> m.id }) { _, message ->
                Box(
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = {
                            if (message.isMine) selectedMessage = message
                        }
                    )
                ) {
                    MessageBubble(message = message)
                }
            }
        }
    }

    // Edit / Delete tanlash menyusi (uzoq bosilganda chiqadi)
    selectedMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { selectedMessage = null },
            title = { Text("Xabar bilan ishlash") },
            text = { Text(msg.text ?: "") },
            confirmButton = {
                TextButton(onClick = {
                    editingMessage = msg
                    editText = msg.text ?: ""
                    selectedMessage = null
                }) { Text("Tahrirlash") }
            },
            dismissButton = {
                TextButton(onClick = {
                    onDeleteMessage(msg)
                    selectedMessage = null
                }) { Text("O'chirish") }
            }
        )
    }

    editingMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("Xabarni tahrirlash") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editText.isNotBlank()) {
                        onEditMessage(msg.copy(text = editText.trim(), isEdited = true))
                    }
                    editingMessage = null
                }) { Text("Saqlash") }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) { Text("Bekor qilish") }
            }
        )
    }
}

@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onImageClick: () -> Unit,
    onFileClick: () -> Unit
) {
    Surface(shadowElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onFileClick) {
                Icon(Icons.Filled.AttachFile, contentDescription = "Fayl biriktirish")
            }
            IconButton(onClick = onImageClick) {
                Icon(Icons.Filled.Image, contentDescription = "Rasm yuborish")
            }

            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                placeholder = { Text("Xabar yozing...") },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp)
            )

            IconButton(
                onClick = onSendClick,
                enabled = text.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = "Yuborish",
                    tint = if (text.isNotBlank())
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}
