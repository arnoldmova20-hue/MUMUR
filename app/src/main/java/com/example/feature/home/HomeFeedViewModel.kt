package com.example.feature.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.util.extractFirestoreException
import com.example.core.util.toFriendlySpanishError
import com.example.data.repository.NotificationAndSafetyRepository
import com.example.data.repository.PostRepository
import com.example.domain.model.FeedPostBundle
import com.example.domain.model.ReactionType
import com.example.domain.model.UiState
import com.google.firebase.Timestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeFeedViewModel(
    private val postRepository: PostRepository,
    private val safetyRepository: NotificationAndSafetyRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _feedState = MutableStateFlow<UiState<List<FeedPostBundle>>>(UiState.Loading)
    val feedState: StateFlow<UiState<List<FeedPostBundle>>> = _feedState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private var lastCursor: Timestamp? = null

    init {
        loadInitialFeed()
    }

    fun loadInitialFeed() {
        viewModelScope.launch {
            if (_feedState.value !is UiState.Success) {
                _feedState.value = UiState.Loading
            }
            _isRefreshing.value = true
            try {
                val rawPosts = postRepository.loadPostsPage(limit = PAGE_SIZE, startAfterCreatedAt = null)
                val bundles = postRepository.enrichPosts(rawPosts)
                lastCursor = rawPosts.lastOrNull()?.createdAt
                _hasMore.value = rawPosts.size >= PAGE_SIZE.toInt()
                _feedState.value = UiState.Success(bundles)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Error loading feed", e)
                val fsEx = e.extractFirestoreException()
                val isOffline = fsEx?.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE
                _feedState.value = UiState.Error(e.toFriendlySpanishError(), isOffline = isOffline)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value || !_hasMore.value) return
        val cursor = lastCursor ?: return
        val currentList = (_feedState.value as? UiState.Success)?.data ?: return

        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val nextRaw = postRepository.loadPostsPage(limit = PAGE_SIZE, startAfterCreatedAt = cursor)
                val nextBundles = postRepository.enrichPosts(nextRaw)
                val existingIds = currentList.map { it.post.id }.toSet()
                val deduplicated = nextBundles.filter { it.post.id !in existingIds }
                if (nextRaw.isNotEmpty()) {
                    lastCursor = nextRaw.last().createdAt
                }
                _hasMore.value = nextRaw.size >= PAGE_SIZE.toInt()
                _feedState.value = UiState.Success(currentList + deduplicated)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _feedbackMessage.value = e.toFriendlySpanishError()
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun toggleReaction(postId: String, type: ReactionType) {
        viewModelScope.launch {
            runCatching {
                postRepository.toggleReaction(postId, type)
            }.onSuccess {
                refreshSingleOrFeed()
            }.onFailure { err ->
                _feedbackMessage.value = err.toFriendlySpanishError()
            }
        }
    }

    fun toggleSave(postId: String) {
        viewModelScope.launch {
            runCatching {
                postRepository.toggleSavePost(postId)
            }.onSuccess { saved ->
                val current = (_feedState.value as? UiState.Success)?.data ?: return@onSuccess
                _feedState.value = UiState.Success(
                    current.map { bundle ->
                        if (bundle.post.id == postId) bundle.copy(isSaved = saved) else bundle
                    }
                )
                _feedbackMessage.value = if (saved) "Publicación guardada en tu espacio privado" else "Eliminada de guardados"
            }.onFailure { err ->
                _feedbackMessage.value = err.toFriendlySpanishError()
            }
        }
    }

    fun votePoll(pollId: String, optionIndex: Int) {
        viewModelScope.launch {
            runCatching {
                postRepository.votePoll(pollId, optionIndex)
            }.onSuccess {
                refreshSingleOrFeed()
            }.onFailure { err ->
                _feedbackMessage.value = err.toFriendlySpanishError()
            }
        }
    }

    fun reportPost(postId: String, reason: String, details: String) {
        viewModelScope.launch {
            runCatching {
                safetyRepository.submitReport("post", postId, reason, details)
            }.onSuccess {
                _feedbackMessage.value = "Denuncia enviada. Gracias por ayudar a mantener segura la comunidad."
            }.onFailure { err ->
                _feedbackMessage.value = err.toFriendlySpanishError()
            }
        }
    }

    suspend fun loadPostVideoFile(context: android.content.Context, postId: String): Result<java.io.File> =
        postRepository.loadPostVideoFile(context, postId)

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    private fun refreshSingleOrFeed() {
        viewModelScope.launch {
            runCatching {
                val rawPosts = postRepository.loadPostsPage(limit = PAGE_SIZE, startAfterCreatedAt = null)
                val bundles = postRepository.enrichPosts(rawPosts)
                lastCursor = rawPosts.lastOrNull()?.createdAt
                _feedState.value = UiState.Success(bundles)
            }
        }
    }

    private companion object {
        const val TAG = "HomeFeedVM"
        const val PAGE_SIZE = 15L
    }
}
