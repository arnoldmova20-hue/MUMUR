package com.example.core.ui.components

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun MurmurPostVideoPlayer(
    postId: String,
    thumbnailData: String?,
    durationMs: Long,
    videoChunkCount: Long,
    localUri: Uri? = null,
    onLoadVideoFile: (suspend (String) -> Result<File>)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeFile by remember(postId, localUri) { mutableStateOf<File?>(null) }
    var isLoadingVideo by remember(postId) { mutableStateOf(false) }
    var loadError by remember(postId) { mutableStateOf<String?>(null) }
    var isPlayerActive by remember(postId, localUri) { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }

    val hasPlayableSource = localUri != null || (videoChunkCount > 0L && onLoadVideoFile != null)

    fun startPlayback() {
        loadError = null
        if (localUri != null || activeFile != null) {
            isPlayerActive = true
            return
        }
        if (videoChunkCount <= 0L || onLoadVideoFile == null) {
            loadError = "Este vídeo no tiene archivo reproducible disponible."
            return
        }
        isLoadingVideo = true
        scope.launch {
            val res = onLoadVideoFile.invoke(postId)
            isLoadingVideo = false
            res.onSuccess { file ->
                activeFile = file
                isPlayerActive = true
            }.onFailure { err ->
                loadError = err.localizedMessage ?: "No se pudo cargar el vídeo."
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
    ) {
        if (isPlayerActive && !isFullscreen && (localUri != null || activeFile != null)) {
            InlineTextureVideoSurface(
                context = context,
                localUri = localUri,
                videoFile = activeFile,
                isFullscreen = false,
                onToggleFullscreen = { isFullscreen = true },
                onClosePlayer = { isPlayerActive = false }
            )
        } else {
            if (!thumbnailData.isNullOrBlank()) {
                Base64OrUrlImage(
                    data = thumbnailData,
                    contentDescription = "Vista previa de vídeo",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
                    .clickable(enabled = !isLoadingVideo) { startPlayback() }
                    .testTag("play_video_button_$postId")
            ) {
                if (isLoadingVideo) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Cargando vídeo...",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                        }
                    }
                } else {
                    Surface(
                        color = Color.Black.copy(alpha = 0.68f),
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(58.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = if (hasPlayableSource) "Reproducir vídeo" else "Vídeo adjunto",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                }

                if (loadError != null) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = loadError!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                } else if (durationMs > 0L) {
                    val secs = (durationMs / 1000L).coerceAtLeast(1L)
                    Surface(
                        color = Color.Black.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = formatDurationSecs(secs),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }

    if (isFullscreen && (localUri != null || activeFile != null)) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                InlineTextureVideoSurface(
                    context = context,
                    localUri = localUri,
                    videoFile = activeFile,
                    isFullscreen = true,
                    onToggleFullscreen = { isFullscreen = false },
                    onClosePlayer = { isFullscreen = false }
                )
            }
        }
    }
}

@Composable
private fun InlineTextureVideoSurface(
    context: android.content.Context,
    localUri: Uri?,
    videoFile: File?,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onClosePlayer: () -> Unit
) {
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPrepared by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isCompleted by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var currentPosMs by remember { mutableIntStateOf(0) }
    var totalDurationMs by remember { mutableIntStateOf(0) }
    var progressFraction by remember { mutableFloatStateOf(0f) }

    DisposableEffect(localUri, videoFile) {
        onDispose {
            runCatching {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            }
            mediaPlayer = null
        }
    }

    LaunchedEffect(isPlaying, isPrepared) {
        while (isPlaying && isPrepared) {
            val mp = mediaPlayer
            if (mp != null) {
                val dur = runCatching { mp.duration }.getOrDefault(0)
                val pos = runCatching { mp.currentPosition }.getOrDefault(0)
                if (dur > 0) {
                    totalDurationMs = dur
                    currentPosMs = pos
                    progressFraction = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                }
            }
            delay(200)
        }
    }

    fun applyAspectFitTransform(textureView: TextureView, videoWidth: Int, videoHeight: Int) {
        val viewWidth = textureView.width.toFloat()
        val viewHeight = textureView.height.toFloat()
        if (viewWidth <= 0f || viewHeight <= 0f || videoWidth <= 0 || videoHeight <= 0) return

        val viewRatio = viewWidth / viewHeight
        val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()
        val scaleX: Float
        val scaleY: Float
        if (videoRatio > viewRatio) {
            scaleX = 1f
            scaleY = viewRatio / videoRatio
        } else {
            scaleX = videoRatio / viewRatio
            scaleY = 1f
        }
        val matrix = Matrix()
        matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
        textureView.setTransform(matrix)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { showControls = !showControls }
    ) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(
                            surfaceTexture: SurfaceTexture,
                            width: Int,
                            height: Int
                        ) {
                            val surface = Surface(surfaceTexture)
                            val mp = MediaPlayer()
                            mediaPlayer = mp
                            runCatching {
                                if (localUri != null) {
                                    mp.setDataSource(ctx, localUri)
                                } else if (videoFile != null) {
                                    mp.setDataSource(videoFile.absolutePath)
                                }
                                mp.setSurface(surface)
                                mp.setOnPreparedListener { preparedMp ->
                                    isPrepared = true
                                    totalDurationMs = preparedMp.duration.coerceAtLeast(1)
                                    applyAspectFitTransform(
                                        this@apply,
                                        preparedMp.videoWidth,
                                        preparedMp.videoHeight
                                    )
                                    preparedMp.start()
                                    isPlaying = true
                                    isCompleted = false
                                }
                                mp.setOnVideoSizeChangedListener { _, vw, vh ->
                                    applyAspectFitTransform(this@apply, vw, vh)
                                }
                                mp.setOnCompletionListener {
                                    isPlaying = false
                                    isCompleted = true
                                    progressFraction = 1f
                                    showControls = true
                                }
                                mp.prepareAsync()
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(
                            surface: SurfaceTexture,
                            width: Int,
                            height: Int
                        ) {
                            val mp = mediaPlayer ?: return
                            if (isPrepared) {
                                applyAspectFitTransform(this@apply, mp.videoWidth, mp.videoHeight)
                            }
                        }

                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            runCatching {
                                mediaPlayer?.stop()
                                mediaPlayer?.release()
                            }
                            mediaPlayer = null
                            isPrepared = false
                            isPlaying = false
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (!isPrepared) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        AnimatedVisibility(
            visible = showControls || !isPlaying,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
            ) {
                if (isFullscreen) {
                    IconButton(
                        onClick = onClosePlayer,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar pantalla completa", tint = Color.White)
                    }
                }

                // Center Play / Pause / Replay button
                IconButton(
                    onClick = {
                        val mp = mediaPlayer ?: return@IconButton
                        if (!isPrepared) return@IconButton
                        if (isPlaying) {
                            mp.pause()
                            isPlaying = false
                        } else {
                            if (isCompleted) {
                                mp.seekTo(0)
                                isCompleted = false
                            }
                            mp.start()
                            isPlaying = true
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                ) {
                    Icon(
                        imageVector = when {
                            isCompleted -> Icons.Default.Replay
                            isPlaying -> Icons.Default.Pause
                            else -> Icons.Default.PlayArrow
                        },
                        contentDescription = if (isPlaying) "Pausar vídeo" else "Reproducir vídeo",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Bottom bar with progress, time, mute, and fullscreen toggle
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${formatDurationSecs((currentPosMs / 1000L).toLong())} / ${formatDurationSecs((totalDurationMs / 1000L).toLong())}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    val mp = mediaPlayer ?: return@IconButton
                                    val nextMute = !isMuted
                                    val vol = if (nextMute) 0f else 1f
                                    runCatching { mp.setVolume(vol, vol) }
                                    isMuted = nextMute
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = if (isMuted) "Activar sonido" else "Silenciar",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = onToggleFullscreen,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = if (isFullscreen) "Salir de pantalla completa" else "Pantalla completa",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FullscreenImageViewerDialog(
    data: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { onDismiss() }
        ) {
            Base64OrUrlImage(
                data = data,
                contentDescription = "Imagen en pantalla completa",
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .heightIn(min = 200.dp)
                    .align(Alignment.Center)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cerrar imagen",
                    tint = Color.White
                )
            }
        }
    }
}

private fun formatDurationSecs(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0L)
    val mins = safe / 60L
    val secs = safe % 60L
    return "%d:%02d".format(mins, secs)
}
