package com.example.feature.messages

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.util.MediaProcessor
import com.example.core.util.SafetyValidator
import com.example.core.util.ValidationResult
import com.example.core.util.extractFirestoreException
import com.example.core.util.toFriendlySpanishError
import com.example.data.repository.AuthUserRepository
import com.example.data.repository.MessageRepository
import com.example.data.repository.NotificationAndSafetyRepository
import com.example.domain.model.Conversation
import com.example.domain.model.DirectMessage
import com.example.domain.model.UiState
import com.example.domain.model.UserPublicProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ConversationRowUi(
    val conversation: Conversation,
    val otherUser: UserPublicProfile?
)

class MessagesViewModel(
    private val messageRepository: MessageRepository,
    private val userRepository: AuthUserRepository,
    private val safetyRepository: NotificationAndSafetyRepository,
    private val currentUserId: String
) : ViewModel() {

    private val profileCache = mutableMapOf<String, UserPublicProfile>()

    private val _userProfiles = MutableStateFlow<Map<String, UserPublicProfile>>(emptyMap())
    val userProfiles: StateFlow<Map<String, UserPublicProfile>> = _userProfiles.asStateFlow()

    val conversationsState: StateFlow<UiState<List<ConversationRowUi>>> =
        messageRepository.observeConversations(currentUserId)
            .onEach { list ->
                list.forEach { conv ->
                    val otherUid = conv.otherParticipant(currentUserId)
                    if (otherUid.isNotBlank() && !profileCache.containsKey(otherUid)) {
                        runCatching { userRepository.getProfile(otherUid) }.getOrNull()?.let { p ->
                            profileCache[otherUid] = p
                            _userProfiles.value = profileCache.toMap()
                        }
                    }
                }
            }
            .map<List<Conversation>, UiState<List<ConversationRowUi>>> { list ->
                UiState.Success(
                    list.map { conv ->
                        val otherUid = conv.otherParticipant(currentUserId)
                        ConversationRowUi(conv, profileCache[otherUid])
                    }
                )
            }
            .catch { error ->
                val fsErr = error.extractFirestoreException()
                if (fsErr == null && error is CancellationException) throw error
                val root = fsErr ?: error
                Log.w("MessagesVM", "Error observing conversations", root)
                emit(UiState.Error(root.toFriendlySpanishError()))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    private var myUsername: String = ""

    init {
        viewModelScope.launch {
            runCatching { userRepository.getProfile(currentUserId) }.getOrNull()?.let {
                myUsername = it.username
            }
        }
    }

    fun observeChatMessages(conversationId: String): StateFlow<UiState<List<DirectMessage>>> {
        return messageRepository.observeMessages(conversationId, currentUserId)
            .map<List<DirectMessage>, UiState<List<DirectMessage>>> { UiState.Success(it) }
            .catch { error ->
                val fsErr = error.extractFirestoreException()
                if (fsErr == null && error is CancellationException) throw error
                emit(UiState.Error((fsErr ?: error).toFriendlySpanishError()))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )
    }

    suspend fun loadOtherUser(uid: String): UserPublicProfile? {
        return profileCache[uid] ?: runCatching { userRepository.getProfile(uid) }.getOrNull()?.also {
            profileCache[uid] = it
            _userProfiles.value = profileCache.toMap()
        }
    }

    fun markRead(conversationId: String) {
        viewModelScope.launch {
            messageRepository.markConversationRead(conversationId)
        }
    }

    fun acceptRequest(conversationId: String) {
        viewModelScope.launch {
            runCatching { messageRepository.acceptMessageRequest(conversationId) }
                .onSuccess { _feedback.value = "Solicitud aceptada" }
                .onFailure { _feedback.value = it.toFriendlySpanishError() }
        }
    }

    fun hideConversation(conversationId: String) {
        viewModelScope.launch {
            runCatching { messageRepository.hideConversationForMe(conversationId) }
                .onSuccess { _feedback.value = "Conversación eliminada de tu bandeja" }
                .onFailure { _feedback.value = it.toFriendlySpanishError() }
        }
    }

    fun sendMessage(targetUid: String, text: String, photoUri: Uri?, context: Context, onSent: () -> Unit) {
        val cleanText = text.trim()
        if (cleanText.isBlank() && photoUri == null) return

        if (cleanText.isNotBlank()) {
            when (val check = SafetyValidator.validateContentSafety(cleanText)) {
                is ValidationResult.Blocked -> {
                    _feedback.value = check.message
                    return
                }
                else -> Unit
            }
        }

        viewModelScope.launch {
            try {
                val encodedPhoto = if (photoUri != null) {
                    MediaProcessor.processImage(context, photoUri).getOrThrow().encodedData
                } else null

                messageRepository.sendMessage(
                    targetUid = targetUid,
                    text = cleanText,
                    mediaData = encodedPhoto,
                    myUsername = myUsername
                )
                onSent()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _feedback.value = e.toFriendlySpanishError()
            }
        }
    }

    fun blockUser(targetUid: String, onBlocked: () -> Unit) {
        viewModelScope.launch {
            runCatching { userRepository.blockUser(targetUid) }
                .onSuccess {
                    _feedback.value = "Usuario bloqueado"
                    onBlocked()
                }
                .onFailure { _feedback.value = it.toFriendlySpanishError() }
        }
    }

    fun reportUser(targetUid: String, reason: String, details: String) {
        viewModelScope.launch {
            runCatching { safetyRepository.submitReport("user", targetUid, reason, details) }
                .onSuccess { _feedback.value = "Reporte enviado" }
                .onFailure { _feedback.value = it.toFriendlySpanishError() }
        }
    }

    fun clearFeedback() {
        _feedback.value = null
    }
}
