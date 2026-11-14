package com.example.feature.profile

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.util.MediaProcessor
import com.example.core.util.extractFirestoreException
import com.example.core.util.toFriendlySpanishError
import com.example.data.repository.AuthUserRepository
import com.example.data.repository.NotificationAndSafetyRepository
import com.example.data.repository.PostRepository
import com.example.domain.model.ContentReport
import com.example.domain.model.FeedPostBundle
import com.example.domain.model.ReactionType
import com.example.domain.model.UiState
import com.example.domain.model.UserPrivateInfo
import com.example.domain.model.UserPublicProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val userRepository: AuthUserRepository,
    private val postRepository: PostRepository,
    private val safetyRepository: NotificationAndSafetyRepository,
    private val currentUserId: String
) : ViewModel() {

    val myProfileState: StateFlow<UiState<UserPublicProfile?>> = userRepository.observeProfile(currentUserId)
        .map<UserPublicProfile?, UiState<UserPublicProfile?>> { UiState.Success(it) }
        .catch { error ->
            val fsErr = error.extractFirestoreException()
            if (fsErr == null && error is CancellationException) throw error
            Log.w("ProfileVM", "Error observing profile", fsErr ?: error)
            emit(UiState.Error((fsErr ?: error).toFriendlySpanishError()))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val myPrivateInfo: StateFlow<UserPrivateInfo?> = userRepository.observePrivateInfo(currentUserId)
        .catch { /* logged by repository */ }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    private val _myPostsState = MutableStateFlow<UiState<List<FeedPostBundle>>>(UiState.Loading)
    val myPostsState: StateFlow<UiState<List<FeedPostBundle>>> = _myPostsState.asStateFlow()

    private val _savedPostsState = MutableStateFlow<UiState<List<FeedPostBundle>>>(UiState.Loading)
    val savedPostsState: StateFlow<UiState<List<FeedPostBundle>>> = _savedPostsState.asStateFlow()

    private val _blockedProfiles = MutableStateFlow<List<UserPublicProfile>>(emptyList())
    val blockedProfiles: StateFlow<List<UserPublicProfile>> = _blockedProfiles.asStateFlow()

    private val _myReports = MutableStateFlow<List<ContentReport>>(emptyList())
    val myReports: StateFlow<List<ContentReport>> = _myReports.asStateFlow()

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    init {
        refreshPrivateCollections()
    }

    fun refreshPrivateCollections() {
        viewModelScope.launch {
            _myPostsState.value = UiState.Loading
            runCatching {
                val raw = postRepository.getMyAnonymousPosts()
                postRepository.enrichPosts(raw)
            }.onSuccess {
                _myPostsState.value = UiState.Success(it)
            }.onFailure {
                _myPostsState.value = UiState.Error(it.toFriendlySpanishError())
            }
        }
        viewModelScope.launch {
            _savedPostsState.value = UiState.Loading
            runCatching {
                postRepository.getSavedPosts()
            }.onSuccess {
                _savedPostsState.value = UiState.Success(it)
            }.onFailure {
                _savedPostsState.value = UiState.Error(it.toFriendlySpanishError())
            }
        }
    }

    fun loadBlockedAndReports() {
        viewModelScope.launch {
            runCatching { userRepository.getBlockedProfiles() }
                .onSuccess { _blockedProfiles.value = it }
            runCatching { safetyRepository.getMyReports() }
                .onSuccess { _myReports.value = it }
        }
    }

    fun deleteMyPost(postId: String) {
        viewModelScope.launch {
            runCatching { postRepository.deleteOwnPost(postId) }
                .onSuccess {
                    _feedback.value = "Publicación eliminada"
                    refreshPrivateCollections()
                }
                .onFailure { _feedback.value = it.toFriendlySpanishError() }
        }
    }

    fun toggleSave(postId: String) {
        viewModelScope.launch {
            runCatching { postRepository.toggleSavePost(postId) }
                .onSuccess { refreshPrivateCollections() }
                .onFailure { _feedback.value = it.toFriendlySpanishError() }
        }
    }

    fun toggleReaction(postId: String, type: ReactionType) {
        viewModelScope.launch {
            runCatching { postRepository.toggleReaction(postId, type) }
                .onSuccess { refreshPrivateCollections() }
                .onFailure { _feedback.value = it.toFriendlySpanishError() }
        }
    }

    fun votePoll(pollId: String, optionIndex: Int) {
        viewModelScope.launch {
            runCatching { postRepository.votePoll(pollId, optionIndex) }
                .onSuccess { refreshPrivateCollections() }
                .onFailure { _feedback.value = it.toFriendlySpanishError() }
        }
    }

    fun updateProfile(
        current: UserPublicProfile,
        username: String,
        displayName: String,
        bio: String,
        newPhotoUri: Uri?,
        context: Context,
        allowMessageRequests: Boolean,
        isPrivateProfile: Boolean,
        showIdentityInComments: Boolean,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val photoData = if (newPhotoUri != null) {
                    MediaProcessor.processImage(context, newPhotoUri).getOrThrow().encodedData
                } else current.photoData

                userRepository.updateProfile(
                    currentProfile = current,
                    newUsername = username,
                    newDisplayName = displayName,
                    newBio = bio,
                    newPhotoData = photoData,
                    allowMessageRequests = allowMessageRequests,
                    isPrivateProfile = isPrivateProfile,
                    showIdentityInComments = showIdentityInComments
                )
                _feedback.value = "Perfil actualizado"
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _feedback.value = e.toFriendlySpanishError()
            }
        }
    }

    fun unblockUser(targetUid: String) {
        viewModelScope.launch {
            runCatching { userRepository.unblockUser(targetUid) }
                .onSuccess {
                    _feedback.value = "Usuario desbloqueado"
                    loadBlockedAndReports()
                }
                .onFailure { _feedback.value = it.toFriendlySpanishError() }
        }
    }

    fun deleteAccount(currentUsername: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            runCatching { userRepository.deleteAccount(currentUsername) }
                .onSuccess { onDeleted() }
                .onFailure { _feedback.value = it.toFriendlySpanishError() }
        }
    }

    suspend fun loadPostVideoFile(context: Context, postId: String): Result<java.io.File> =
        postRepository.loadPostVideoFile(context, postId)

    fun clearFeedback() {
        _feedback.value = null
    }
}
