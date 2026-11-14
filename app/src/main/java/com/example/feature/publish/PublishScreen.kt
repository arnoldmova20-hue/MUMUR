package com.example.feature.publish

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ui.components.AnonymousPostCard
import com.example.core.ui.components.Base64OrUrlImage
import com.example.core.ui.components.FloatingBubblesBackground
import com.example.core.ui.components.MurmurPostVideoPlayer
import com.example.domain.model.AnonymousPost
import com.example.domain.model.FeedPostBundle
import com.example.domain.model.Poll
import com.google.firebase.Timestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishScreen(
    viewModel: PublishViewModel,
    onPublished: () -> Unit
) {
    val context = LocalContext.current
    val text by viewModel.text.collectAsStateWithLifecycle()
    val processedMedia by viewModel.processedMedia.collectAsStateWithLifecycle()
    val mediaProgress by viewModel.mediaProgress.collectAsStateWithLifecycle()
    val mediaError by viewModel.mediaError.collectAsStateWithLifecycle()
    val includePoll by viewModel.includePoll.collectAsStateWithLifecycle()
    val pollQuestion by viewModel.pollQuestion.collectAsStateWithLifecycle()
    val pollOptions by viewModel.pollOptions.collectAsStateWithLifecycle()
    val pollDurationHours by viewModel.pollDurationHours.collectAsStateWithLifecycle()
    val isPublishing by viewModel.isPublishing.collectAsStateWithLifecycle()
    val publishError by viewModel.publishError.collectAsStateWithLifecycle()

    var showPreview by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.processSelectedMedia(context, uri, isVideo = false)
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.processSelectedMedia(context, uri, isVideo = true)
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri).orEmpty()
            val isVideo = mime.startsWith("video/")
            viewModel.processSelectedMedia(context, uri, isVideo = isVideo)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Nueva publicación",
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                actions = {
                    TextButton(onClick = { showPreview = !showPreview }) {
                        Text(if (showPreview) "Editar" else "Vista previa")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            FloatingBubblesBackground()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .widthIn(max = 600.dp)
                    .align(Alignment.TopCenter),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Automatic anonymity banner (zero identity selector)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Publicación automática anónima. Tu nombre de usuario y foto nunca aparecerán vinculados a esta historia.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                if (showPreview) {
                    Text(
                        text = "Vista previa en el feed",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val previewPost = AnonymousPost(
                        id = "preview",
                        text = text,
                        mediaType = processedMedia?.type ?: "none",
                        mediaData = processedMedia?.encodedData,
                        mediaDurationMs = processedMedia?.durationMs ?: 0L,
                        videoChunkCount = processedMedia?.videoChunks?.size?.toLong() ?: 0L,
                        createdAt = Timestamp.now()
                    )
                    val previewPoll = if (includePoll && pollQuestion.isNotBlank()) {
                        Poll(
                            id = "preview_poll",
                            question = pollQuestion,
                            options = pollOptions.filter { it.isNotBlank() }.ifEmpty { listOf("Opción 1", "Opción 2") }
                        )
                    } else null
                    AnonymousPostCard(
                        bundle = FeedPostBundle(post = previewPost, poll = previewPoll),
                        onReact = {},
                        onOpenComments = {},
                        onToggleSave = {},
                        onShare = {},
                        onVotePoll = { _, _ -> },
                        onReportPost = {},
                        localVideoUri = processedMedia?.localUri
                    )
                } else {
                    OutlinedTextField(
                        value = text,
                        onValueChange = viewModel::onTextChange,
                        label = { Text("¿Qué quieres contar hoy?") },
                        placeholder = { Text("Comparte una confesión, pregunta o pensamiento...") },
                        supportingText = {
                            Text("${text.length} / 1000 caracteres")
                        },
                        minLines = 5,
                        maxLines = 10,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("publish_text_input")
                    )

                    // Native zero-permission Photo Picker & Documents buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                imagePickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pick_image_button")
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Foto")
                        }
                        OutlinedButton(
                            onClick = {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pick_video_button")
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Vídeo")
                        }
                        OutlinedButton(
                            onClick = {
                                documentPickerLauncher.launch(arrayOf("image/*", "video/*"))
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Archivos")
                        }
                    }

                    // Media processing progress / error / preview
                    if (mediaProgress != null) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Optimizando archivo multimedia...", style = MaterialTheme.typography.bodyMedium)
                                    TextButton(onClick = { viewModel.cancelMediaProcessing() }) {
                                        Text("Cancelar")
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = { mediaProgress ?: 0f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    if (mediaError != null) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = mediaError!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.retryMediaProcessing(context) }) {
                                    Text("Reintentar")
                                }
                            }
                        }
                    }

                    if (processedMedia != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            if (processedMedia!!.type == "video") {
                                MurmurPostVideoPlayer(
                                    postId = "composer_preview",
                                    thumbnailData = processedMedia!!.encodedData,
                                    durationMs = processedMedia!!.durationMs,
                                    videoChunkCount = processedMedia!!.videoChunks.size.toLong(),
                                    localUri = processedMedia!!.localUri
                                )
                            } else {
                                Base64OrUrlImage(
                                    data = processedMedia!!.encodedData,
                                    contentDescription = "Vista previa seleccionada",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 180.dp, max = 260.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.removeMedia() },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .testTag("remove_media_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Eliminar archivo seleccionado",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    // Poll Builder Toggle
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Poll,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Agregar encuesta",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                Switch(
                                    checked = includePoll,
                                    onCheckedChange = viewModel::togglePoll,
                                    modifier = Modifier.testTag("toggle_poll_switch")
                                )
                            }

                            AnimatedVisibility(visible = includePoll) {
                                Column(
                                    modifier = Modifier.padding(top = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = pollQuestion,
                                        onValueChange = viewModel::onPollQuestionChange,
                                        label = { Text("Pregunta de la encuesta") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("poll_question_input")
                                    )
                                    pollOptions.forEachIndexed { idx, opt ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = opt,
                                                onValueChange = { viewModel.onPollOptionChange(idx, it) },
                                                label = { Text("Opción ${idx + 1}") },
                                                singleLine = true,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("poll_option_input_$idx")
                                            )
                                            if (pollOptions.size > 2) {
                                                IconButton(onClick = { viewModel.removePollOption(idx) }) {
                                                    Icon(Icons.Default.Close, contentDescription = "Quitar opción")
                                                }
                                            }
                                        }
                                    }
                                    if (pollOptions.size < 4) {
                                        TextButton(onClick = { viewModel.addPollOption() }) {
                                            Icon(Icons.Default.Add, contentDescription = null)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Agregar opción (máx. 4)")
                                        }
                                    }

                                    Text(
                                        text = "Duración opcional:",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(24 to "24 h", 72 to "3 días", null to "Sin límite").forEach { (hours, label) ->
                                            FilterChip(
                                                selected = pollDurationHours == hours,
                                                onClick = { viewModel.setPollDurationHours(hours) },
                                                label = { Text(label) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (publishError != null) {
                    Text(
                        text = publishError!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Button(
                    onClick = { viewModel.publish(onSuccess = onPublished) },
                    enabled = !isPublishing && mediaProgress == null,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("publish_submit_button")
                ) {
                    if (isPublishing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "PUBLICAR ANÓNIMAMENTE",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }
}
