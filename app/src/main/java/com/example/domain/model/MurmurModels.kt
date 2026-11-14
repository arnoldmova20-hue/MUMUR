package com.example.domain.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String, val isOffline: Boolean = false) : UiState<Nothing>
}

@IgnoreExtraProperties
data class UserPublicProfile(
    @DocumentId val id: String = "",
    val uid: String = "",
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val photoData: String? = null,
    val accountStatus: String = "active",
    val followersCount: Long = 0L,
    val followingCount: Long = 0L,
    val allowMessageRequests: Boolean = true,
    val isPrivateProfile: Boolean = false,
    val showIdentityInComments: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserPrivateInfo(
    @DocumentId val id: String = "",
    val email: String? = null,
    val ageConfirmed: Boolean = false,
    val birthYear: Long? = null,
    val acceptedTermsVersion: String = "1.0",
    @ServerTimestamp val acceptedTermsAt: Timestamp? = null,
    val themeMode: String = "dark",
    val notifyFollowers: Boolean = true,
    val notifyReactions: Boolean = true,
    val notifyComments: Boolean = true,
    val notifyReplies: Boolean = true,
    val notifyMessages: Boolean = true,
    val notifySecurity: Boolean = true,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class MediaType(val value: String) {
    NONE("none"),
    IMAGE("image"),
    VIDEO("video");

    companion object {
        fun fromValue(raw: String): MediaType =
            entries.firstOrNull { it.value == raw } ?: NONE
    }
}

enum class ReactionType(
    val value: String,
    val labelEs: String,
    val emoji: String
) {
    LIKE("like", "Me gusta", "\uD83D\uDC4D"),
    FUNNY("funny", "Me divierte", "\uD83D\uDE02"),
    SURPRISED("surprised", "Me sorprende", "\uD83D\uDE2E"),
    SAD("sad", "Me entristece", "\uD83D\uDE22"),
    INTERESTED("interested", "Me interesa", "\uD83E\uDD14"),
    LOVE("love", "Me encanta", "\uD83D\uDC9C");

    companion object {
        fun fromValue(raw: String?): ReactionType? =
            entries.firstOrNull { it.value == raw }
    }
}

@IgnoreExtraProperties
data class AnonymousPost(
    @DocumentId val id: String = "",
    val text: String = "",
    val mediaType: String = MediaType.NONE.value,
    val mediaData: String? = null,
    val mediaDurationMs: Long = 0L,
    val videoChunkCount: Long = 0L,
    val pollId: String? = null,
    val reactionsCount: Long = 0L,
    val likeCount: Long = 0L,
    val funnyCount: Long = 0L,
    val surprisedCount: Long = 0L,
    val sadCount: Long = 0L,
    val interestedCount: Long = 0L,
    val loveCount: Long = 0L,
    val commentsCount: Long = 0L,
    val status: String = "active",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun countFor(type: ReactionType): Long = when (type) {
        ReactionType.LIKE -> likeCount
        ReactionType.FUNNY -> funnyCount
        ReactionType.SURPRISED -> surprisedCount
        ReactionType.SAD -> sadCount
        ReactionType.INTERESTED -> interestedCount
        ReactionType.LOVE -> loveCount
    }
}

@IgnoreExtraProperties
data class PostAuthorRecord(
    @DocumentId val id: String = "",
    val postId: String = "",
    val authorUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PostReaction(
    @DocumentId val id: String = "",
    val postId: String = "",
    val userId: String = "",
    val reactionType: String = ReactionType.LIKE.value,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Comment(
    @DocumentId val id: String = "",
    val postId: String = "",
    val authorUid: String = "",
    val authorLabel: String = "Anónimo",
    val isAnonymous: Boolean = true,
    val text: String = "",
    val parentCommentId: String? = null,
    val status: String = "active",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Poll(
    @DocumentId val id: String = "",
    val postId: String = "",
    val question: String = "",
    val options: List<String> = emptyList(),
    val c0: Long = 0L,
    val c1: Long = 0L,
    val c2: Long = 0L,
    val c3: Long = 0L,
    val totalVotes: Long = 0L,
    val expiresAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun countAt(index: Int): Long = when (index) {
        0 -> c0
        1 -> c1
        2 -> c2
        3 -> c3
        else -> 0L
    }

    fun isExpired(): Boolean {
        val exp = expiresAt ?: return false
        return exp.toDate().time < System.currentTimeMillis()
    }
}

@IgnoreExtraProperties
data class PollVote(
    @DocumentId val id: String = "",
    val pollId: String = "",
    val userId: String = "",
    val optionIndex: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FollowRelationship(
    @DocumentId val id: String = "",
    val followerUid: String = "",
    val followingUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class BlockRelationship(
    @DocumentId val id: String = "",
    val blockerUid: String = "",
    val blockedUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val participantUids: List<String> = emptyList(),
    val lastMessageText: String = "",
    val lastMessageSenderUid: String = "",
    val isRequest: Boolean = false,
    val requestAccepted: Boolean = true,
    val unreadMap: Map<String, Long> = emptyMap(),
    val hiddenForUids: List<String> = emptyList(),
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun otherParticipant(myUid: String): String =
        participantUids.firstOrNull { it != myUid } ?: ""

    fun unreadFor(uid: String): Long =
        (unreadMap[uid] as? Number)?.toLong() ?: 0L
}

@IgnoreExtraProperties
data class DirectMessage(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val participantUids: List<String> = emptyList(),
    val senderUid: String = "",
    val text: String = "",
    val mediaData: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    val readAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SavedPost(
    @DocumentId val id: String = "",
    val userId: String = "",
    val postId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class NotificationItem(
    @DocumentId val id: String = "",
    val recipientUid: String = "",
    val actorUid: String = "",
    val actorLabel: String = "",
    val type: String = "security",
    val referenceId: String = "",
    val previewText: String = "",
    val read: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ContentReport(
    @DocumentId val id: String = "",
    val reporterUid: String = "",
    val contentType: String = "post",
    val contentId: String = "",
    val reason: String = "",
    val details: String = "",
    val status: String = "pending",
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class FeedPostBundle(
    val post: AnonymousPost,
    val myReaction: ReactionType? = null,
    val isSaved: Boolean = false,
    val poll: Poll? = null,
    val myPollVoteIndex: Int? = null
)

/**
 * Arquitectura preparada para una futura versión de Comunidades anónimas
 * sin exponer botones inactivos en la v1.
 */
interface FutureCommunityContract {
    data class CommunityDraft(
        val communityId: String = "",
        val name: String = "",
        val rules: List<String> = emptyList(),
        val adminUids: List<String> = emptyList()
    )
}
