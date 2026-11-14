package com.example.feature.publish

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.util.MediaProcessor
import com.example.core.util.ProcessedMedia
import com.example.core.util.SafetyValidator
import com.example.core.util.ValidationResult
import com.example.core.util.toFriendlySpanishError
import com.example.data.repository.PostRepository
import com.example.domain.model.MediaType
import com.google.firebase.Timestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date

class PublishViewModel(
    private val postRepository: PostRepository
) : ViewModel() {

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    private val _processedMedia = MutableStateFlow<ProcessedMedia?>(null)
    val processedMedia: StateFlow<ProcessedMedia?> = _processedMedia.asStateFlow()

    private val _mediaProgress = MutableStateFlow<Float?>(null)
    val mediaProgress: StateFlow<Float?> = _mediaProgress.asStateFlow()

    private val _mediaError = MutableStateFlow<String?>(null)
    val mediaError: StateFlow<String?> = _mediaError.asStateFlow()

    private val _includePoll = MutableStateFlow(false)
    val includePoll: StateFlow<Boolean> = _includePoll.asStateFlow()

    private val _pollQuestion = MutableStateFlow("")
    val pollQuestion: StateFlow<String> = _pollQuestion.asStateFlow()

    private val _pollOptions = MutableStateFlow(listOf("", ""))
    val pollOptions: StateFlow<List<String>> = _pollOptions.asStateFlow()

    private val _pollDurationHours = MutableStateFlow<Int?>(24)
    val pollDurationHours: StateFlow<Int?> = _pollDurationHours.asStateFlow()

    private val _isPublishing = MutableStateFlow(false)
    val isPublishing: StateFlow<Boolean> = _isPublishing.asStateFlow()

    private val _publishError = MutableStateFlow<String?>(null)
    val publishError: StateFlow<String?> = _publishError.asStateFlow()

    private val _publishSuccess = MutableStateFlow(false)
    val publishSuccess: StateFlow<Boolean> = _publishSuccess.asStateFlow()

    private var lastMediaUri: Uri? = null
    private var lastMediaIsVideo: Boolean = false
    private var mediaJob: Job? = null

    fun onTextChange(value: String) {
        if (value.length <= 1000) {
            _text.value = value
            _publishError.value = null
        }
    }

    fun togglePoll(enabled: Boolean) {
        _includePoll.value = enabled
        _publishError.value = null
    }

    fun onPollQuestionChange(q: String) {
        if (q.length <= 200) {
            _pollQuestion.value = q
            _publishError.value = null
        }
    }

    fun onPollOptionChange(index: Int, value: String) {
        if (value.length > 80) return
        _pollOptions.value = _pollOptions.value.mapIndexed { i, old ->
            if (i == index) value else old
        }
        _publishError.value = null
    }

    fun addPollOption() {
        if (_pollOptions.value.size < 4) {
            _pollOptions.value = _pollOptions.value + ""
        }
    }

    fun removePollOption(index: Int) {
        if (_pollOptions.value.size > 2) {
            _pollOptions.value = _pollOptions.value.filterIndexed { i, _ -> i != index }
        }
    }

    fun setPollDurationHours(hours: Int?) {
        _pollDurationHours.value = hours
    }

    fun processSelectedMedia(context: Context, uri: Uri, isVideo: Boolean) {
        lastMediaUri = uri
        lastMediaIsVideo = isVideo
        mediaJob?.cancel()
        mediaJob = viewModelScope.launch {
            _mediaError.value = null
            _mediaProgress.value = 0.05f
            val result = if (isVideo) {
                MediaProcessor.processVideo(context, uri) { p -> _mediaProgress.value = p }
            } else {
                MediaProcessor.processImage(context, uri) { p -> _mediaProgress.value = p }
            }
            result.onSuccess { processed ->
                _processedMedia.value = processed
                _mediaProgress.value = null
            }.onFailure { err ->
                if (err is CancellationException) return@onFailure
                _mediaProgress.value = null
                _mediaError.value = err.localizedMessage ?: "No se pudo procesar el archivo."
            }
        }
    }

    fun retryMediaProcessing(context: Context) {
        val uri = lastMediaUri ?: return
        processSelectedMedia(context, uri, lastMediaIsVideo)
    }

    fun cancelMediaProcessing() {
        mediaJob?.cancel()
        _mediaProgress.value = null
    }

    fun removeMedia() {
        mediaJob?.cancel()
        _processedMedia.value = null
        _mediaProgress.value = null
        _mediaError.value = null
        lastMediaUri = null
    }

    fun publish(onSuccess: () -> Unit) {
        val bodyText = _text.value.trim()
        val media = _processedMedia.value
        val hasPoll = _includePoll.value
        val question = _pollQuestion.value.trim()
        val cleanOptions = _pollOptions.value.map { it.trim() }.filter { it.isNotEmpty() }

        if (bodyText.isBlank() && media == null && !hasPoll) {
            _publishError.value = "Escribe un texto, adjunta un archivo o crea una encuesta para publicar."
            return
        }

        if (bodyText.isNotBlank()) {
            when (val safety = SafetyValidator.validateContentSafety(bodyText)) {
                is ValidationResult.Blocked -> {
                    _publishError.value = safety.message
                    return
                }
                is ValidationResult.Warning -> { /* allowed */ }
                ValidationResult.Valid -> Unit
            }
        }

        if (hasPoll) {
            if (question.isBlank()) {
                _publishError.value = "Escribe la pregunta de la encuesta."
                return
            }
            if (cleanOptions.size < 2) {
                _publishError.value = "Agrega al menos 2 opciones válidas en la encuesta."
                return
            }
            when (val safety = SafetyValidator.validateContentSafety(question)) {
                is ValidationResult.Blocked -> {
                    _publishError.value = safety.message
                    return
                }
                else -> Unit
            }
        }

        viewModelScope.launch {
            _isPublishing.value = true
            _publishError.value = null
            try {
                val expiresAt = _pollDurationHours.value?.let { hours ->
                    Timestamp(Date(System.currentTimeMillis() + hours * 3600_000L))
                }
                postRepository.createAnonymousPost(
                    text = bodyText,
                    mediaType = media?.type ?: MediaType.NONE.value,
                    mediaData = media?.encodedData,
                    mediaDurationMs = media?.durationMs ?: 0L,
                    videoChunks = media?.videoChunks ?: emptyList(),
                    pollQuestion = if (hasPoll) question else null,
                    pollOptions = if (hasPoll) cleanOptions else emptyList(),
                    pollExpiresAt = if (hasPoll) expiresAt else null
                )
                resetComposer()
                _publishSuccess.value = true
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _publishError.value = e.toFriendlySpanishError()
            } finally {
                _isPublishing.value = false
            }
        }
    }

    fun consumePublishSuccess() {
        _publishSuccess.value = false
    }

    private fun resetComposer() {
        _text.value = ""
        _processedMedia.value = null
        _mediaProgress.value = null
        _mediaError.value = null
        _includePoll.value = false
        _pollQuestion.value = ""
        _pollOptions.value = listOf("", "")
    }
}
