package com.ryuzen.chat

enum class ConversationType {
    PRIVATE, GROUP, CHANNEL
}

enum class MemberRole {
    OWNER, ADMIN, MODERATOR, MEMBER
}

data class GroupMember(
    val userId: String,
    val userName: String,
    val avatarUrl: String? = null,
    val role: MemberRole = MemberRole.MEMBER,
    val isMuted: Boolean = false,
    val isBanned: Boolean = false
)

data class Conversation(
    val id: String,
    val title: String,
    val type: ConversationType,
    val avatarUrl: String? = null,
    val description: String? = null,
    val membersCount: Int = 0,
    val members: List<GroupMember> = emptyList(),
    val isPublic: Boolean = false,       // Channel uchun: public/private
    val subscribersCount: Int = 0,       // Channel uchun
    val onlyAdminsCanPost: Boolean = false, // Channel uchun odatda true
    val lastMessage: String? = null,
    val lastMessageTime: Long? = null,
    val unreadCount: Int = 0
)
