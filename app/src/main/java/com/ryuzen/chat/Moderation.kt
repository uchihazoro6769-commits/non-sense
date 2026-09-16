package com.ryuzen.chat

enum class ReportReason {
    SPAM, HARASSMENT, VIOLENCE, NUDITY, FAKE_ACCOUNT, ILLEGAL_CONTENT, OTHER
}

data class Report(
    val id: String,
    val reporterId: String,
    val targetUserId: String? = null,
    val targetMessageId: String? = null,
    val targetConversationId: String? = null,
    val reason: ReportReason,
    val comment: String? = null,
    val createdAt: Long,
    val status: ReportStatus = ReportStatus.PENDING
)

enum class ReportStatus {
    PENDING, REVIEWED, ACTION_TAKEN, DISMISSED
}

enum class ModerationAction {
    WARN, MUTE, KICK, BAN, DELETE_MESSAGE
}

data class ModerationLogEntry(
    val id: String,
    val moderatorId: String,
    val targetUserId: String,
    val action: ModerationAction,
    val reason: String? = null,
    val durationMinutes: Int? = null, // MUTE/BAN uchun, null = doimiy
    val timestamp: Long
)

/**
 * Anti-spam qoidalari (backendda tekshiriladi, UI faqat holatni ko'rsatadi)
 */
data class AntiSpamRule(
    val maxMessagesPerMinute: Int = 15,
    val maxDuplicateMessages: Int = 3,
    val linkPostingAllowed: Boolean = true,
    val newUserRestrictionMinutes: Int = 10, // yangi a'zo uchun cheklov vaqti
    val bannedWords: List<String> = emptyList()
)

enum class SpamFlagType {
    FLOOD, DUPLICATE_MESSAGE, BANNED_WORD, SUSPICIOUS_LINK, NEW_ACCOUNT_ACTIVITY
}

data class SpamFlag(
    val messageId: String,
    val userId: String,
    val type: SpamFlagType,
    val autoActionTaken: ModerationAction? = null
)
