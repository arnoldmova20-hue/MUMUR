package com.example.feature.notifications

import android.content.Context
import android.util.Log
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.core.notifications.MurmurNotificationHelper
import com.example.core.ui.components.FloatingBubblesBackground
import com.example.core.ui.components.MurmurErrorView
import com.example.core.ui.components.MurmurLoadingView
import com.example.core.ui.components.MurmurStateView
import com.example.core.util.SafetyValidator
import com.example.core.util.extractFirestoreException
import com.example.core.util.toFriendlySpanishError
import com.example.data.repository.AuthUserRepository
import com.example.data.repository.NotificationAndSafetyRepository
import com.example.domain.model.NotificationItem
import com.example.domain.model.UiState
import com.example.domain.model.UserPrivateInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotificationsViewModel(
    private val appContext: Context,
    private val safetyRepository: NotificationAndSafetyRepository,
    userRepository: AuthUserRepository,
    private val currentUserId: String
) : ViewModel() {

    private val seenIds = mutableSetOf<String>()
    private var initialLoaded = false

    private val privateInfoFlow = userRepository.observePrivateInfo(currentUserId)
        .catch { emit(null) }

    val notificationsState: StateFlow<UiState<List<NotificationItem>>> =
        combine(
            safetyRepository.observeNotifications(currentUserId),
            privateInfoFlow
        ) { items, privateInfo ->
            items to privateInfo
        }
            .onEach { (items, privateInfo) ->
                if (!initialLoaded) {
                    seenIds.addAll(items.map { it.id })
                    initialLoaded = true
                } else {
                    items.filter { !it.read && it.id !in seenIds }.forEach { item ->
                        seenIds.add(item.id)
                        if (shouldNotifyCategory(item.type, privateInfo)) {
                            MurmurNotificationHelper.showLocalNotification(
                                context = appContext,
                                id = item.id.hashCode(),
                                title = titleForType(item.type),
                                body = item.previewText.ifBlank { "Nueva actividad en MURMUR" }
                            )
                        }
                    }
                }
            }
            .map<Pair<List<NotificationItem>, UserPrivateInfo?>, UiState<List<NotificationItem>>> { (items, _) ->
                UiState.Success(items)
            }
            .catch { error ->
                val fsErr = error.extractFirestoreException()
                if (fsErr == null && error is CancellationException) throw error
                val root = fsErr ?: error
                Log.w("NotificationsVM", "Error observing notifications", root)
                emit(UiState.Error(root.toFriendlySpanishError()))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _isMarkingAll = MutableStateFlow(false)

    fun markRead(notificationId: String) {
        viewModelScope.launch {
            runCatching { safetyRepository.markNotificationRead(notificationId) }
        }
    }

    fun markAllRead() {
        if (_isMarkingAll.value) return
        _isMarkingAll.value = true
        viewModelScope.launch {
            runCatching { safetyRepository.markAllNotificationsRead() }
            _isMarkingAll.value = false
        }
    }

    private fun shouldNotifyCategory(type: String, info: UserPrivateInfo?): Boolean {
        val prefs = info ?: return true
        return when (type) {
            "follower" -> prefs.notifyFollowers
            "reaction" -> prefs.notifyReactions
            "comment" -> prefs.notifyComments
            "reply" -> prefs.notifyReplies
            "message" -> prefs.notifyMessages
            "security" -> prefs.notifySecurity
            else -> true
        }
    }

    private fun titleForType(type: String): String = when (type) {
        "follower" -> "Nuevo seguidor en MURMUR"
        "reaction" -> "Nueva reacción"
        "comment" -> "Nuevo comentario"
        "reply" -> "Nueva respuesta"
        "message" -> "Nuevo mensaje privado"
        "security" -> "Aviso de seguridad"
        else -> "MURMUR"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel,
    onBack: () -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenComments: (String) -> Unit
) {
    val state by viewModel.notificationsState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Notificaciones",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("notifications_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    val hasUnread = (state as? UiState.Success)?.data?.any { !it.read } == true
                    if (hasUnread) {
                        TextButton(
                            onClick = { viewModel.markAllRead() },
                            modifier = Modifier.testTag("mark_all_read_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Marcar leídas")
                        }
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

            when (val ui = state) {
                is UiState.Loading -> MurmurLoadingView("Cargando notificaciones...")
                is UiState.Error -> MurmurErrorView(
                    message = ui.message,
                    onRetry = { /* snapshot listener auto-recovers */ }
                )
                is UiState.Success -> {
                    val items = ui.data
                    if (items.isEmpty()) {
                        MurmurStateView(
                            icon = Icons.Outlined.NotificationsNone,
                            title = "Sin notificaciones nuevas",
                            subtitle = "Aquí aparecerán tus nuevos seguidores, respuestas, mensajes y avisos de seguridad."
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .widthIn(max = 640.dp)
                                .align(Alignment.TopCenter)
                                .fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(items, key = { it.id }) { item ->
                                NotificationRowCard(
                                    item = item,
                                    onClick = {
                                        if (!item.read) viewModel.markRead(item.id)
                                        when (item.type) {
                                            "follower" -> if (item.actorUid.isNotBlank()) onOpenUserProfile(item.actorUid)
                                            "message" -> if (item.actorUid.isNotBlank()) onOpenChat(item.actorUid)
                                            "comment", "reply", "reaction" -> if (item.referenceId.isNotBlank()) onOpenComments(item.referenceId)
                                        }
                                    }
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
private fun NotificationRowCard(
    item: NotificationItem,
    onClick: () -> Unit
) {
    val icon: ImageVector = when (item.type) {
        "follower" -> Icons.Default.PersonAdd
        "reaction" -> Icons.Default.Favorite
        "comment" -> Icons.Default.ChatBubble
        "reply" -> Icons.AutoMirrored.Filled.Reply
        "message" -> Icons.AutoMirrored.Filled.Message
        else -> Icons.Default.Security
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("notification_item_${item.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.read) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            }
        ),
        border = BorderStroke(
            1.dp,
            if (item.read) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.previewText.ifBlank { item.actorLabel },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (item.read) FontWeight.Normal else FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = SafetyValidator.formatRelativeTime(item.createdAt?.toDate()?.time),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!item.read) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}
