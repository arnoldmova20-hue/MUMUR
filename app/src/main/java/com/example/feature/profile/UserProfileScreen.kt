package com.example.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.ui.components.FloatingBubblesBackground
import com.example.core.ui.components.MurmurAvatar
import com.example.core.ui.components.MurmurErrorView
import com.example.core.ui.components.MurmurLoadingView
import com.example.core.ui.components.ReportContentDialog
import com.example.core.util.toFriendlySpanishError
import com.example.data.repository.AuthUserRepository
import com.example.data.repository.NotificationAndSafetyRepository
import com.example.domain.model.UserPublicProfile
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    targetUid: String,
    currentUserId: String,
    userRepository: AuthUserRepository,
    safetyRepository: NotificationAndSafetyRepository,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenFollowers: (String, Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var profile by remember { mutableStateOf<UserPublicProfile?>(null) }
    var myUsername by remember { mutableStateOf("") }
    var isFollowing by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }

    fun loadData() {
        scope.launch {
            isLoading = true
            errorMsg = null
            try {
                profile = userRepository.getProfile(targetUid)
                myUsername = userRepository.getProfile(currentUserId)?.username ?: ""
                isFollowing = userRepository.isFollowing(targetUid)
            } catch (e: Exception) {
                errorMsg = e.toFriendlySpanishError()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(targetUid) {
        loadData()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(profile?.username?.let { "@$it" } ?: "Perfil", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Bloquear usuario") },
                                leadingIcon = {
                                    Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = {
                                    menuOpen = false
                                    scope.launch {
                                        runCatching { userRepository.blockUser(targetUid) }
                                            .onSuccess {
                                                snackbarHostState.showSnackbar("Usuario bloqueado")
                                                onBack()
                                            }
                                            .onFailure { snackbarHostState.showSnackbar(it.toFriendlySpanishError()) }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Denunciar perfil") },
                                leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    showReportDialog = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            FloatingBubblesBackground()

            when {
                isLoading -> MurmurLoadingView(modifier = Modifier.align(Alignment.Center))
                errorMsg != null -> MurmurErrorView(
                    message = errorMsg!!,
                    onRetry = { loadData() },
                    modifier = Modifier.align(Alignment.Center)
                )
                profile != null -> {
                    val user = profile!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                            .widthIn(max = 600.dp)
                            .align(Alignment.TopCenter),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    MurmurAvatar(
                                        photoData = user.photoData,
                                        fallbackLabel = user.username,
                                        size = 72.dp
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            text = user.displayName.ifBlank { "@${user.username}" },
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                        Text(
                                            text = "@${user.username}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        val createdDate = user.createdAt?.toDate()?.let {
                                            SimpleDateFormat("MMM yyyy", Locale("es")).format(it)
                                        } ?: "Reciente"
                                        Text(
                                            text = "En MURMUR desde $createdDate",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (user.bio.isNotBlank() && !user.isPrivateProfile) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(text = user.bio, style = MaterialTheme.typography.bodyMedium)
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clickable { onOpenFollowers(targetUid, 0) }
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = user.followersCount.toString(),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text("Seguidores", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clickable { onOpenFollowers(targetUid, 1) }
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = user.followingCount.toString(),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text("Seguidos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (isFollowing) {
                                        OutlinedButton(
                                            onClick = {
                                                scope.launch {
                                                    runCatching { userRepository.unfollowUser(targetUid) }
                                                        .onSuccess {
                                                            isFollowing = false
                                                            profile = user.copy(
                                                                followersCount = (user.followersCount - 1L).coerceAtLeast(0L)
                                                            )
                                                        }
                                                        .onFailure { snackbarHostState.showSnackbar(it.toFriendlySpanishError()) }
                                                }
                                            },
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("unfollow_button")
                                        ) {
                                            Icon(Icons.Default.PersonRemove, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Dejar de seguir")
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    runCatching { userRepository.followUser(targetUid, myUsername) }
                                                        .onSuccess {
                                                            isFollowing = true
                                                            profile = user.copy(followersCount = user.followersCount + 1L)
                                                        }
                                                        .onFailure { snackbarHostState.showSnackbar(it.toFriendlySpanishError()) }
                                                }
                                            },
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("follow_button")
                                        ) {
                                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Seguir")
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = { onOpenChat(targetUid) },
                                        enabled = user.allowMessageRequests || isFollowing,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("send_message_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Mensaje")
                                    }
                                }
                            }
                        }

                        // Explicit anonymity protection notice: no anonymous posts shown on other users' profiles
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Por privacidad y anonimato, los perfiles de MURMUR nunca muestran las publicaciones anónimas realizadas por una persona.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showReportDialog) {
        ReportContentDialog(
            targetLabel = "perfil",
            onDismiss = { showReportDialog = false },
            onSubmit = { reason, details ->
                showReportDialog = false
                scope.launch {
                    runCatching { safetyRepository.submitReport("user", targetUid, reason, details) }
                        .onSuccess { snackbarHostState.showSnackbar("Denuncia enviada.") }
                        .onFailure { snackbarHostState.showSnackbar(it.toFriendlySpanishError()) }
                }
            }
        )
    }
}
