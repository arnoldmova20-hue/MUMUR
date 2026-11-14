package com.example.feature.messages

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ui.components.Base64OrUrlImage
import com.example.core.ui.components.MurmurAvatar
import com.example.core.ui.components.MurmurErrorView
import com.example.core.ui.components.MurmurLoadingView
import com.example.core.ui.components.MurmurStateView
import com.example.core.ui.components.ReportContentDialog
import com.example.core.util.SafetyValidator
import com.example.domain.model.UiState
import com.example.domain.model.UserPublicProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: MessagesViewModel,
    currentUserId: String,
    targetUid: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val conversationId = remember(currentUserId, targetUid) {
        listOf(currentUserId, targetUid).sorted().joinToString("_")
    }
    val messagesFlow = remember(conversationId) { viewModel.observeChatMessages(conversationId) }
    val messagesState by messagesFlow.collectAsStateWithLifecycle()
    val conversationsState by viewModel.conversationsState.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()

    var otherUser by remember { mutableStateOf<UserPublicProfile?>(null) }
    var textInput by remember { mutableStateOf("") }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    val currentConv = (conversationsState as? UiState.Success)?.data
        ?.firstOrNull { it.conversation.id == conversationId }?.conversation
    val isPendingRequest = currentConv != null &&
        currentConv.isRequest &&
        !currentConv.requestAccepted &&
        currentConv.lastMessageSenderUid != currentUserId

    LaunchedEffect(targetUid) {
        otherUser = viewModel.loadOtherUser(targetUid)
        viewModel.markRead(conversationId)
    }

    LaunchedEffect(feedback) {
        feedback?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) selectedPhotoUri = uri
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MurmurAvatar(
                            photoData = otherUser?.photoData,
                            fallbackLabel = otherUser?.username ?: "U",
                            size = 36.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = otherUser?.displayName?.ifBlank { "@${otherUser?.username}" }
                                    ?: "@${otherUser?.username ?: "Usuario"}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "@${otherUser?.username ?: "..."}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Opciones de chat")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Bloquear usuario") },
                                leadingIcon = {
                                    Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = {
                                    menuOpen = false
                                    viewModel.blockUser(targetUid) { onBack() }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Denunciar usuario") },
                                leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    showReportDialog = true
                                }
                            )
                        }
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
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    if (isPendingRequest) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Solicitud de mensaje. ¿Aceptar?",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = { viewModel.acceptRequest(conversationId) }) {
                                Text("Aceptar")
                            }
                        }
                    }
                    if (selectedPhotoUri != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Fotografía lista para enviar",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = { selectedPhotoUri = null },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Quitar foto", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.testTag("chat_pick_photo_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "Adjuntar fotografía",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { if (it.length <= 1000) textInput = it },
                            placeholder = { Text("Escribe un mensaje...") },
                            maxLines = 4,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_message_input")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                viewModel.sendMessage(
                                    targetUid = targetUid,
                                    text = textInput,
                                    photoUri = selectedPhotoUri,
                                    context = context,
                                    onSent = {
                                        textInput = ""
                                        selectedPhotoUri = null
                                    }
                                )
                            },
                            enabled = textInput.isNotBlank() || selectedPhotoUri != null,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("chat_send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Enviar mensaje",
                                tint = MaterialTheme.colorScheme.primary
                            )
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
            when (val state = messagesState) {
                is UiState.Loading -> MurmurLoadingView(modifier = Modifier.align(Alignment.Center))
                is UiState.Error -> MurmurErrorView(
                    message = state.message,
                    onRetry = {},
                    modifier = Modifier.align(Alignment.Center)
                )
                is UiState.Success -> {
                    val msgs = state.data
                    LaunchedEffect(msgs.size) {
                        if (msgs.isNotEmpty()) {
                            listState.animateScrollToItem(msgs.lastIndex)
                            viewModel.markRead(conversationId)
                        }
                    }
                    if (msgs.isEmpty()) {
                        MurmurStateView(
                            icon = Icons.Outlined.Lock,
                            title = "Chat privado",
                            subtitle = "Solo tú y esta persona pueden leer los mensajes de esta conversación.",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(msgs, key = { it.id }) { msg ->
                                val isMine = msg.senderUid == currentUserId
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(
                                            topStart = 18.dp,
                                            topEnd = 18.dp,
                                            bottomStart = if (isMine) 18.dp else 4.dp,
                                            bottomEnd = if (isMine) 4.dp else 18.dp
                                        ),
                                        color = if (isMine) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.widthIn(max = 290.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            if (!msg.mediaData.isNullOrBlank()) {
                                                Base64OrUrlImage(
                                                    data = msg.mediaData,
                                                    contentDescription = "Fotografía enviada",
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(min = 120.dp, max = 220.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                )
                                                if (msg.text.isNotBlank()) Spacer(modifier = Modifier.height(6.dp))
                                            }
                                            if (msg.text.isNotBlank()) {
                                                Text(
                                                    text = msg.text,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer
                                                    else MaterialTheme.colorScheme.onBackground
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = SafetyValidator.formatRelativeTime(msg.createdAt?.toDate()?.time),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showReportDialog) {
        ReportContentDialog(
            targetLabel = "usuario",
            onDismiss = { showReportDialog = false },
            onSubmit = { reason, details ->
                showReportDialog = false
                viewModel.reportUser(targetUid, reason, details)
            }
        )
    }
}
