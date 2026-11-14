package com.example.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.preferences.AppPreferencesRepository
import com.example.core.util.toFriendlySpanishError
import com.example.data.repository.AuthUserRepository
import com.example.data.repository.NotificationAndSafetyRepository
import com.example.data.repository.PostRepository
import com.example.domain.model.FeedPostBundle
import com.example.domain.model.ReactionType
import com.example.domain.model.UiState
import com.example.domain.model.UserPublicProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExploreSearchResults(
    val users: List<UserPublicProfile> = emptyList(),
    val posts: List<FeedPostBundle> = emptyList(),
    val query: String = ""
)

class ExploreViewModel(
    private val userRepository: AuthUserRepository,
    private val postRepository: PostRepository,
    private val safetyRepository: NotificationAndSafetyRepository,
    private val prefsRepository: AppPreferencesRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _resultsState = MutableStateFlow<UiState<ExploreSearchResults>>(UiState.Loading)
    val resultsState: StateFlow<UiState<ExploreSearchResults>> = _resultsState.asStateFlow()

    val searchHistory: StateFlow<List<String>> = prefsRepository.searchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    private var searchJob: Job? = null

    init {
        executeSearch("")
    }

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(380) // controlled debounce
            executeSearch(newQuery)
        }
    }

    fun submitSearch(queryText: String) {
        _query.value = queryText
        searchJob?.cancel()
        viewModelScope.launch {
            if (queryText.isNotBlank()) {
                prefsRepository.addSearchQuery(queryText)
            }
            executeSearch(queryText)
        }
    }

    fun removeHistoryItem(item: String) {
        viewModelScope.launch { prefsRepository.removeSearchQuery(item) }
    }

    fun clearHistory() {
        viewModelScope.launch { prefsRepository.clearSearchHistory() }
    }

    private fun executeSearch(q: String) {
        viewModelScope.launch {
            _resultsState.value = UiState.Loading
            try {
                val users = userRepository.searchUsers(q)
                val posts = postRepository.searchPosts(q)
                _resultsState.value = UiState.Success(
                    ExploreSearchResults(users = users, posts = posts, query = q)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _resultsState.value = UiState.Error(e.toFriendlySpanishError())
            }
        }
    }

    fun toggleReaction(postId: String, type: ReactionType) {
        viewModelScope.launch {
            runCatching { postRepository.toggleReaction(postId, type) }
                .onSuccess { executeSearch(_query.value) }
                .onFailure { _feedback.value = it.toFriendlySpanishError() }
        }
    }

    fun toggleSave(postId: String) {
        viewModelScope.launch {
            runCatching { postRepository.toggleSavePost(postId) }
                .onSuccess { saved ->
                    val current = (_resultsState.value as? UiState.Success)?.data ?: return@onSuccess
                    _resultsState.value = UiState.Success(
                        current.copy(
                            posts = current.posts.map { b ->
                                if (b.post.id == postId) b.copy(isSaved = saved) else b
                            }
                        )
                    )
                }
                .onFailure { _feedback.value = it.toFriendlySpanishError() }
        }
    }

    fun votePoll(pollId: String, optionIndex: Int) {
        viewModelScope.launch {
            runCatching { postRepository.votePoll(pollId, optionIndex) }
                .onSuccess { executeSearch(_query.value) }
                .onFailure { _feedback.value = it.toFriendlySpanishError() }
        }
    }

    fun reportPost(postId: String, reason: String, details: String) {
        viewModelScope.launch {
            runCatching { safetyRepository.submitReport("post", postId, reason, details) }
                .onSuccess { _feedback.value = "Denuncia registrada." }
                .onFailure { _feedback.value = it.toFriendlySpanishError() }
        }
    }

    suspend fun loadPostVideoFile(context: android.content.Context, postId: String): Result<java.io.File> =
        postRepository.loadPostVideoFile(context, postId)

    fun clearFeedback() {
        _feedback.value = null
    }
}
