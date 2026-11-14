package com.example.feature.messages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ui.components.FloatingBubblesBackground
import com.example.core.ui.components.MurmurAvatar
import com.example.core.ui.components.MurmurErrorView
import com.example.core.ui.components.MurmurLoadingView
import com.example.core.ui.components.MurmurStateView
import com.example.core.util.SafetyValidator
import com.example.domain.model.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    viewModel: MessagesViewModel,
    currentUserId: String,
    onOpenChat: (targetUid: String) -> Unit
) {
    val conversationsState by viewModel.conversationsState.collectAsStateWithLifecycle()
    val userProfiles by viewModel.userProfiles.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

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
                title = {
                    Text(
                        text = "Mensajes",
                        style = MaterialTheme.typography.headlineMedium
                    )
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
                    .widthIn(max = 640.dp)
                    .align(Alignment.TopCenter)
            ) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Principal") },
                        modifier = Modifier.testTag("messages_tab_primary")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Solicitudes") },
                        modifier = Modifier.testTag("messages_tab_requests")
                    )
                }

                when (val state = conversationsState) {
                    is UiState.Loading -> {
                        MurmurLoadingView(message = "Sincronizando conversaciones...")
                    }
                    is UiState.Error -> {
                        MurmurErrorView(message = state.message, onRetry = {})
                    }
                    is UiState.Success -> {
                        val filtered = state.data.filter { row ->
                            val isReq = row.conversation.isRequest &&
                                !row.conversation.requestAccepted &&
                                row.conversation.lastMessageSenderUid != currentUserId
                            if (selectedTab == 1) isReq else !isReq
                        }

                        if (filtered.isEmpty()) {
                            MurmurStateView(
                                icon = Icons.Outlined.MailOutline,
                                title = if (selectedTab == 0) "Sin mensajes privados" else "Sin solicitudes pendientes",
                                subtitle = if (selectedTab == 0) {
                                    "Busca a alguien en Explorar y envíale un mensaje directo desde su perfil."
                                } else {
                                    "Las solicitudes de personas que aún no sigues aparecerán aquí."
                                }
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filtered, key = { it.conversation.id }) { row ->
                                    val conv = row.conversation
                                    val otherUid = conv.otherParticipant(currentUserId)
                                    val otherProfile = userProfiles[otherUid] ?: row.otherUser
                                    val unread = conv.unreadFor(currentUserId)

                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            if (unread > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                            else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpenChat(otherUid) }
                                            .testTag("conversation_${conv.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box {
                                                MurmurAvatar(
                                                    photoData = otherProfile?.photoData,
                                                    fallbackLabel = otherProfile?.username ?: "U",
                                                    size = 48.dp
                                                )
                                                if (unread > 0) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(12.dp)
                                                            .clip(CircleShape)
                                                            .background(MaterialTheme.colorScheme.primary)
                                                            .align(Alignment.TopEnd)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = otherProfile?.displayName?.ifBlank { "@${otherProfile.username}" }
                                                            ?: "@${otherProfile?.username ?: "usuario"}",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = SafetyValidator.formatRelativeTime(
                                                            conv.updatedAt?.toDate()?.time
                                                        ),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = conv.lastMessageText,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = if (unread > 0) MaterialTheme.colorScheme.onBackground
                                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    if (unread > 0) {
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Badge { Text(unread.toString()) }
                                                    }
                                                }
                                            }
                                            IconButton(onClick = { viewModel.hideConversation(conv.id) }) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "Eliminar conversación",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                                item { Spacer(modifier = Modifier.height(72.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
