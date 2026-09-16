package com.ryuzen.chat

enum class AccountType {
    PERSONAL, BUSINESS, BOT
}

data class BusinessProfile(
    val userId: String,
    val companyName: String,
    val category: String? = null,       // "Restoran", "Do'kon" va h.k.
    val website: String? = null,
    val workingHours: String? = null,
    val isVerified: Boolean = false,     // ko'k belgi
    val autoReplyMessage: String? = null,
    val catalogEnabled: Boolean = false
)

enum class BotPermission {
    READ_MESSAGES, SEND_MESSAGES, MANAGE_GROUP, ACCESS_PAYMENTS
}

data class BotAccount(
    val botId: String,
    val name: String,
    val username: String,        // masalan @weather_bot
    val ownerId: String,
    val apiToken: String,        // faqat owner ko'radi, UI'da hech qachon to'liq ko'rsatilmaydi
    val webhookUrl: String? = null,
    val permissions: List<BotPermission> = emptyList(),
    val isActive: Boolean = true
)

enum class CloudFileType { IMAGE, VIDEO, DOCUMENT, AUDIO, OTHER }

data class CloudFile(
    val id: String,
    val fileName: String,
    val type: CloudFileType,
    val sizeBytes: Long,
    val uploadedAt: Long,
    val downloadUrl: String,
    val isSharedInChat: Boolean = false
)

data class CloudStorageQuota(
    val usedBytes: Long,
    val totalBytes: Long
)

enum class PaymentStatus { PENDING, COMPLETED, FAILED, REFUNDED }

enum class PaymentMethod { CARD, WALLET, BANK_TRANSFER }

data class Payment(
    val id: String,
    val fromUserId: String,
    val toUserId: String,
    val amount: Double,
    val currency: String = "UZS",
    val method: PaymentMethod,
    val status: PaymentStatus,
    val relatedConversationId: String? = null,
    val comment: String? = null,
    val createdAt: Long
)
