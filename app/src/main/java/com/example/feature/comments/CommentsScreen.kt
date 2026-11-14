package com.example.feature.comments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ui.components.MurmurErrorView
import com.example.core.ui.components.MurmurLoadingView
import com.example.core.ui.components.MurmurStateView
import com.example.core.ui.components.ReportContentDialog
import com.example.core.util.SafetyValidator
import com.example.domain.model.Comment
import com.example.domain.model.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsScreen(
    viewModel: CommentsViewModel,
    currentUserId: String,
    onBack: () -> Unit
) {
    val commentsState by viewModel.commentsState.collectAsStateWithLifecycle()
    val commentText by viewModel.commentText.collectAsStateWithLifecycle()
    val replyTo by viewModel.replyTo.collectAsStateWithLifecycle()
    val editingComment by viewModel.editingComment.collectAsStateWithLifecycle()
    val showMyUsername by viewModel.showMyUsername.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var reportingCommentId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(feedback) {
        feedback?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Comentarios", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (replyTo != null || editingComment != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (editingComment != null) "Editando tu comentario"
                                else "Respondiendo a ${replyTo?.authorLabel}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = { viewModel.cancelEditingOrReply() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cancelar", modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !showMyUsername,
                            onClick = { viewModel.toggleShowUsername(false) },
                            label = { Text("Anónimo") }
                        )
                        FilterChip(
                            selected = showMyUsername,
                            onClick = { viewModel.toggleShowUsername(true) },
                            label = { Text("Con mi @usuario") }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = commentText,
                            onValueChange = viewModel::onTextChange,
                            placeholder = { Text("Escribe un comentario respetuoso...") },
                            singleLine = false,
                            maxLines = 3,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("comment_input")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { viewModel.submitComment() },
                            enabled = commentText.isNotBlank() && !isSending,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("send_comment_button")
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Enviar comentario",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = commentsState) {
                is UiState.Loading -> {
                    MurmurLoadingView(
                        message = "Cargando conversación...",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is UiState.Error -> {
                    MurmurErrorView(
                        message = state.message,
                        onRetry = { /* snapshots retry automatically on re-collect */ },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is UiState.Success -> {
                    val allComments = state.data
                    if (allComments.isEmpty()) {
                        MurmurStateView(
                            icon = Icons.Outlined.ChatBubbleOutline,
                            title = "Aún no hay comentarios",
                            subtitle = "Inicia la conversación con respeto.",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        val topLevel = allComments.filter { it.parentCommentId.isNullOrBlank() }
                        val repliesByParent = allComments
                            .filter { !it.parentCommentId.isNullOrBlank() }
                            .groupBy { it.parentCommentId!! }

                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            topLevel.forEach { parent ->
                                item(key = parent.id) {
                                    CommentItemCard(
                                        comment = parent,
                                        isMine = parent.authorUid == currentUserId,
                                        isReply = false,
                                        onReply = { viewModel.setReplyTo(parent) },
                                        onEdit = { viewModel.startEditing(parent) },
                                        onDelete = { viewModel.deleteComment(parent.id) },
                                        onReport = { reportingCommentId = parent.id },
                                        modifier = Modifier.widthIn(max = 600.dp)
                                    )
                                }
                                val children = repliesByParent[parent.id].orEmpty()
                                items(children, key = { it.id }) { reply ->
                                    CommentItemCard(
                                        comment = reply,
                                        isMine = reply.authorUid == currentUserId,
                                        isReply = true,
                                        onReply = { viewModel.setReplyTo(parent) },
                                        onEdit = { viewModel.startEditing(reply) },
                                        onDelete = { viewModel.deleteComment(reply.id) },
                                        onReport = { reportingCommentId = reply.id },
                                        modifier = Modifier
                                            .widthIn(max = 600.dp)
                                            .padding(start = 24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (reportingCommentId != null) {
        ReportContentDialog(
            targetLabel = "comentario",
            onDismiss = { reportingCommentId = null },
            onSubmit = { reason, details ->
                val id = reportingCommentId!!
                reportingCommentId = null
                viewModel.reportComment(id, reason, details)
            }
        )
    }
}

@Composable
private fun CommentItemCard(
    comment: Comment,
    isMine: Boolean,
    isReply: Boolean,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isReply) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = comment.authorLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (comment.isAnonymous) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = SafetyValidator.formatRelativeTime(comment.createdAt?.toDate()?.time),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Opciones de comentario",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (isMine) {
                            DropdownMenuItem(
                                text = { Text("Editar") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Eliminar") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    onDelete()
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Denunciar") },
                                leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onReport()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onReply) {
                    Icon(
                        Icons.AutoMirrored.Filled.Reply,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Responder", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
