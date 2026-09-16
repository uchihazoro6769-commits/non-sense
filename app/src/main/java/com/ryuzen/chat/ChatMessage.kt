package com.ryuzen.chat

/**
 * Xabar holati (status): yuborilgan -> yetkazilgan -> o'qilgan
 */
enum class MessageStatus {
    SENDING,    // hali serverga yubormoqda
    SENT,       // ✓ serverga yetdi
    DELIVERED,  // ✓✓ qabul qiluvchi qurilmasiga yetdi
    READ        // ✓✓ (ko'k) o'qildi
}

enum class MessageType {
    TEXT, IMAGE, FILE, AUDIO
}

data class ChatMessage(
    val id: String,
    val senderId: String,
    val text: String? = null,
    val imageUrl: String? = null,
    val fileUrl: String? = null,
    val fileName: String? = null,
    val audioUrl: String? = null,
    val audioDurationMs: Long = 0L,
    val type: MessageType = MessageType.TEXT,
    val timestamp: Long,
    val status: MessageStatus = MessageStatus.SENDING,
    val isMine: Boolean = false,
    val isEdited: Boolean = false
)
