package com.example.feature.comments

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.util.SafetyValidator
import com.example.core.util.ValidationResult
import com.example.core.util.extractFirestoreException
import com.example.core.util.toFriendlySpanishError
import com.example.data.repository.AuthUserRepository
import com.example.data.repository.NotificationAndSafetyRepository
import com.example.data.repository.PostRepository
import com.example.domain.model.Comment
import com.example.domain.model.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CommentsViewModel(
    private val postRepository: PostRepository,
    private val userRepository: AuthUserRepository,
    private val safetyRepository: NotificationAndSafetyRepository,
    private val postId: String,
    private val currentUserId: String
) : ViewModel() {

    val commentsState: StateFlow<UiState<List<Comment>>> = postRepository.observeComments(postId)
        .map<List<Comment>, UiState<List<Comment>>> { UiState.Success(it) }
        .catch { error ->
            val fsError = error.extractFirestoreException()
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.w("CommentsVM", "Error observing comments", root)
            emit(UiState.Error(root.toFriendlySpanishError()))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    private val _commentText = MutableStateFlow("")
    val commentText: StateFlow<String> = _commentText.asStateFlow()

    private val _replyTo = MutableStateFlow<Comment?>(null)
    val replyTo: StateFlow<Comment?> = _replyTo.asStateFlow()

    private val _editingComment = MutableStateFlow<Comment?>(null)
    val editingComment: StateFlow<Comment?> = _editingComment.asStateFlow()

    private val _showMyUsername = MutableStateFlow(false)
    val showMyUsername: StateFlow<Boolean> = _showMyUsername.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    private var myUsername: String = ""

    init {
        viewModelScope.launch {
            runCatching { userRepository.getProfile(currentUserId) }.getOrNull()?.let { profile ->
                myUsername = profile.username
                _showMyUsername.value = profile.showIdentityInComments
            }
        }
    }

    fun onTextChange(value: String) {
        if (value.length <= 500) {
            _commentText.value = value
        }
    }

    fun setReplyTo(comment: Comment?) {
        _editingComment.value = null
        _replyTo.value = comment
    }

    fun startEditing(comment: Comment) {
        _replyTo.value = null
        _editingComment.value = comment
        _commentText.value = comment.text
    }

    fun cancelEditingOrReply() {
        _replyTo.value = null
        _editingComment.value = null
        _commentText.value = ""
    }

    fun toggleShowUsername(show: Boolean) {
        _showMyUsername.value = show
    }

    fun submitComment() {
        val text = _commentText.value.trim()
        if (text.isBlank()) return
        when (val check = SafetyValidator.validateContentSafety(text)) {
            is ValidationResult.Blocked -> {
                _feedback.value = check.message
                return
            }
            else -> Unit
        }

        val editTarget = _editingComment.value
        val replyTarget = _replyTo.value

        viewModelScope.launch {
            _isSending.value = true
            try {
                if (editTarget != null) {
                    postRepository.editComment(postId, editTarget.id, text)
                    _editingComment.value = null
                } else {
                    postRepository.addComment(
                        postId = postId,
                        text = text,
                        parentCommentId = replyTarget?.id,
                        showUsername = _showMyUsername.value,
                        username = myUsername
                    )
                    _replyTo.value = null
                }
                _commentText.value = ""
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _feedback.value = e.toFriendlySpanishError()
            } finally {
                _isSending.value = false
            }
        }
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch {
            runCatching { postRepository.deleteComment(postId, commentId) }
                .onFailure { _feedback.value = it.toFriendlySpanishError() }
        }
    }

    fun reportComment(commentId: String, reason: String, details: String) {
        viewModelScope.launch {
            runCatching { safetyRepository.submitReport("comment", commentId, reason, details) }
                .onSuccess { _feedback.value = "Comentario denunciado." }
                .onFailure { _feedback.value = it.toFriendlySpanishError() }
        }
    }

    fun clearFeedback() {
        _feedback.value = null
    }
}
