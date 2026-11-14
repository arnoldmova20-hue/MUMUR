package com.example.core.ui.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import java.io.File
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.BubbleChart
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.core.util.MediaProcessor
import com.example.core.util.SafetyValidator
import com.example.domain.model.FeedPostBundle
import com.example.domain.model.LegalContent
import com.example.domain.model.MediaType
import com.example.domain.model.Poll
import com.example.domain.model.ReactionType

@Composable
fun MurmurAvatar(
    photoData: String?,
    fallbackLabel: String,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bytes = remember(photoData) { MediaProcessor.decodeDataUriToBytes(photoData) }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (bytes != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(bytes).crossfade(true).build(),
                contentDescription = "Foto de perfil",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (!photoData.isNullOrBlank() && photoData.startsWith("http")) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(photoData).crossfade(true).build(),
                contentDescription = "Foto de perfil",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val initial = fallbackLabel.trim().removePrefix("@").firstOrNull()?.uppercase() ?: "M"
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun Base64OrUrlImage(
    data: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val bytes = remember(data) { MediaProcessor.decodeDataUriToBytes(data) }
    val modelData: Any? = bytes ?: data?.takeIf { it.startsWith("http") }
    if (modelData != null) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(modelData).crossfade(true).build(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    }
}

@Composable
fun AnonymousPostCard(
    bundle: FeedPostBundle,
    onReact: (ReactionType) -> Unit,
    onOpenComments: () -> Unit,
    onToggleSave: () -> Unit,
    onShare: () -> Unit,
    onVotePoll: (String, Int) -> Unit,
    onReportPost: () -> Unit,
    canDeleteOwnPost: Boolean = false,
    onDeleteOwnPost: (() -> Unit)? = null,
    localVideoUri: Uri? = null,
    onLoadVideoFile: (suspend (String) -> Result<File>)? = null,
    modifier: Modifier = Modifier
) {
    val post = bundle.post
    var showReactionTray by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showFullscreenImage by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("post_card_${post.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Uniform anonymous header — never reveals author identity
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BubbleChart,
                            contentDescription = "Publicación anónima",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Publicación anónima",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = SafetyValidator.formatRelativeTime(post.createdAt?.toDate()?.time),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.testTag("post_options_${post.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Opciones de publicación",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Denunciar publicación") },
                            leadingIcon = {
                                Icon(Icons.Default.Flag, contentDescription = null)
                            },
                            onClick = {
                                showMenu = false
                                onReportPost()
                            }
                        )
                        if (canDeleteOwnPost && onDeleteOwnPost != null) {
                            DropdownMenuItem(
                                text = { Text("Eliminar mi publicación") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onDeleteOwnPost()
                                }
                            )
                        }
                    }
                }
            }

            if (post.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = post.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            val mediaType = MediaType.fromValue(post.mediaType)
            if (mediaType == MediaType.VIDEO) {
                Spacer(modifier = Modifier.height(12.dp))
                MurmurPostVideoPlayer(
                    postId = post.id,
                    thumbnailData = post.mediaData,
                    durationMs = post.mediaDurationMs,
                    videoChunkCount = post.videoChunkCount,
                    localUri = localVideoUri,
                    onLoadVideoFile = onLoadVideoFile
                )
            } else if (mediaType == MediaType.IMAGE && !post.mediaData.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 300.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { showFullscreenImage = true }
                ) {
                    Base64OrUrlImage(
                        data = post.mediaData,
                        contentDescription = "Fotografía adjunta",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            if (bundle.poll != null) {
                Spacer(modifier = Modifier.height(12.dp))
                PollCardSection(
                    poll = bundle.poll,
                    myVoteIndex = bundle.myPollVoteIndex,
                    onVote = { optionIndex -> onVotePoll(bundle.poll.id, optionIndex) }
                )
            }

            // Reaction summary pills if any
            val activeBreakdown = ReactionType.entries.filter { post.countFor(it) > 0L }
            if (activeBreakdown.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    activeBreakdown.forEach { rType ->
                        val selected = bundle.myReaction == rType
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.clickable { onReact(rType) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = rType.emoji, style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = post.countFor(rType).toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Expandable 6-reaction tray
            AnimatedVisibility(visible = showReactionTray) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ReactionType.entries.forEach { reaction ->
                            val isSelected = bundle.myReaction == reaction
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                } else Color.Transparent,
                                modifier = Modifier
                                    .clickable {
                                        showReactionTray = false
                                        onReact(reaction)
                                    }
                                    .testTag("reaction_option_${reaction.value}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(text = reaction.emoji, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = reaction.labelEs,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Primary action bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = { showReactionTray = !showReactionTray },
                        modifier = Modifier.testTag("react_button_${post.id}")
                    ) {
                        if (bundle.myReaction != null) {
                            Text(bundle.myReaction.emoji)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = post.reactionsCount.toString(),
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.FavoriteBorder,
                                contentDescription = "Reaccionar",
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = if (post.reactionsCount > 0) "${post.reactionsCount}" else "Reaccionar")
                        }
                    }

                    TextButton(
                        onClick = onOpenComments,
                        modifier = Modifier.testTag("comments_button_${post.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = "Comentarios",
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = post.commentsCount.toString())
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleSave,
                        modifier = Modifier.testTag("save_button_${post.id}")
                    ) {
                        Icon(
                            imageVector = if (bundle.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = if (bundle.isSaved) "Quitar de guardados" else "Guardar publicación",
                            tint = if (bundle.isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.testTag("share_button_${post.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Compartir publicación",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showFullscreenImage && !post.mediaData.isNullOrBlank()) {
        FullscreenImageViewerDialog(
            data = post.mediaData,
            onDismiss = { showFullscreenImage = false }
        )
    }
}

@Composable
fun PollCardSection(
    poll: Poll,
    myVoteIndex: Int?,
    onVote: (Int) -> Unit
) {
    val hasVoted = myVoteIndex != null
    val isExpired = poll.isExpired()
    val showResults = hasVoted || isExpired
    val total = poll.totalVotes.coerceAtLeast(0L)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = poll.question,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(10.dp))

            poll.options.forEachIndexed { index, optionText ->
                val count = poll.countAt(index)
                val pct = if (total > 0L) (count.toFloat() / total.toFloat()) else 0f
                val animatedPct by animateFloatAsState(targetValue = if (showResults) pct else 0f, label = "poll_bar_$index")
                val isSelected = myVoteIndex == index

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !hasVoted && !isExpired) { onVote(index) }
                        .testTag("poll_option_${poll.id}_$index")
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (showResults && animatedPct > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(animatedPct)
                                    .background(
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                                        } else {
                                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
                                        }
                                    )
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Tu voto",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = optionText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (showResults) {
                                Text(
                                    text = "${(pct * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$total ${if (total == 1L) "voto" else "votos"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isExpired) {
                    Text(
                        text = "Encuesta finalizada",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

@Composable
fun MurmurStateView(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(18.dp))
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun MurmurLoadingView(
    message: String = "Cargando...",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MurmurErrorView(
    message: String,
    isOffline: Boolean = false,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    MurmurStateView(
        icon = if (isOffline) Icons.Outlined.CloudOff else Icons.Outlined.ErrorOutline,
        title = if (isOffline) "Sin conexión" else "Algo salió mal",
        subtitle = message,
        actionLabel = "Reintentar",
        onAction = onRetry,
        modifier = modifier
    )
}

@Composable
fun LegalDocumentDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = body.trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Entendido")
            }
        }
    )
}

@Composable
fun ReportContentDialog(
    targetLabel: String,
    onDismiss: () -> Unit,
    onSubmit: (reason: String, details: String) -> Unit
) {
    val reasons = remember {
        listOf(
            "Acoso, amenazas o intimidación",
            "Exposición de datos personales",
            "Contenido íntimo no consentido",
            "Spam o suplantación de identidad",
            "Contenido inapropiado para menores"
        )
    }
    var selectedReason by remember { mutableStateOf(reasons.first()) }
    var details by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Denunciar $targetLabel", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Selecciona el motivo de tu denuncia. Tu reporte será registrado de forma confidencial.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                reasons.forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReason = reason }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = reason, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = details,
                    onValueChange = { if (it.length <= 400) details = it },
                    label = { Text("Detalles adicionales (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedReason, details) },
                modifier = Modifier.testTag("submit_report_button")
            ) {
                Text("Enviar denuncia")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
