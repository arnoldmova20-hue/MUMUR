package com.example.feature.profile

import android.content.Intent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ui.components.AnonymousPostCard
import com.example.core.ui.components.FloatingBubblesBackground
import com.example.core.ui.components.MurmurAvatar
import com.example.core.ui.components.MurmurErrorView
import com.example.core.ui.components.MurmurLoadingView
import com.example.core.ui.components.MurmurStateView
import com.example.domain.model.UiState
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    currentUserId: String,
    onEditProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFollowers: (uid: String, initialTab: Int) -> Unit,
    onOpenComments: (postId: String) -> Unit
) {
    val context = LocalContext.current
    val profileState by viewModel.myProfileState.collectAsStateWithLifecycle()
    val myPostsState by viewModel.myPostsState.collectAsStateWithLifecycle()
    val savedPostsState by viewModel.savedPostsState.collectAsStateWithLifecycle()
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
                        text = "Mi perfil",
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshPrivateCollections() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("open_settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Configuración")
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

            when (val pState = profileState) {
                is UiState.Loading -> MurmurLoadingView(modifier = Modifier.align(Alignment.Center))
                is UiState.Error -> MurmurErrorView(
                    message = pState.message,
                    onRetry = { viewModel.refreshPrivateCollections() },
                    modifier = Modifier.align(Alignment.Center)
                )
                is UiState.Success -> {
                    val profile = pState.data
                    if (profile == null) {
                        MurmurLoadingView(modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item(key = "profile_header") {
                                Card(
                                    shape = RoundedCornerShape(22.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(18.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            MurmurAvatar(
                                                photoData = profile.photoData,
                                                fallbackLabel = profile.username,
                                                size = 72.dp
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = profile.displayName.ifBlank { "@${profile.username}" },
                                                    style = MaterialTheme.typography.titleLarge,
                                                    color = MaterialTheme.colorScheme.onBackground
                                                )
                                                Text(
                                                    text = "@${profile.username}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                val createdDate = profile.createdAt?.toDate()?.let {
                                                    SimpleDateFormat("MMM yyyy", Locale("es")).format(it)
                                                } ?: "Reciente"
                                                Text(
                                                    text = "Miembro desde $createdDate",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        if (profile.bio.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = profile.bio,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier
                                                    .clickable { onOpenFollowers(currentUserId, 0) }
                                                    .padding(8.dp)
                                                    .testTag("followers_count_button")
                                            ) {
                                                Text(
                                                    text = profile.followersCount.toString(),
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Seguidores",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier
                                                    .clickable { onOpenFollowers(currentUserId, 1) }
                                                    .padding(8.dp)
                                                    .testTag("following_count_button")
                                            ) {
                                                Text(
                                                    text = profile.followingCount.toString(),
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Seguidos",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        OutlinedButton(
                                            onClick = onEditProfile,
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("edit_profile_button")
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Editar perfil y privacidad")
                                        }
                                    }
                                }
                            }

                            item(key = "private_notice") {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Tus publicaciones anónimas y guardados son privados; nadie más puede verlos en tu perfil.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            item(key = "profile_tabs") {
                                TabRow(
                                    selectedTabIndex = selectedTab,
                                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()
                                ) {
                                    Tab(
                                        selected = selectedTab == 0,
                                        onClick = { selectedTab = 0 },
                                        text = { Text("Mis anónimas") },
                                        modifier = Modifier.testTag("profile_tab_my_posts")
                                    )
                                    Tab(
                                        selected = selectedTab == 1,
                                        onClick = { selectedTab = 1 },
                                        text = { Text("Guardados") },
                                        modifier = Modifier.testTag("profile_tab_saved")
                                    )
                                }
                            }

                            val activeListState = if (selectedTab == 0) myPostsState else savedPostsState
                            when (activeListState) {
                                is UiState.Loading -> {
                                    item(key = "tab_loading") {
                                        MurmurLoadingView()
                                    }
                                }
                                is UiState.Error -> {
                                    item(key = "tab_error") {
                                        MurmurErrorView(
                                            message = activeListState.message,
                                            onRetry = { viewModel.refreshPrivateCollections() }
                                        )
                                    }
                                }
                                is UiState.Success -> {
                                    val posts = activeListState.data
                                    if (posts.isEmpty()) {
                                        item(key = "tab_empty") {
                                            MurmurStateView(
                                                icon = if (selectedTab == 0) Icons.Outlined.PostAdd else Icons.Outlined.BookmarkBorder,
                                                title = if (selectedTab == 0) "Sin publicaciones propias" else "Sin publicaciones guardadas",
                                                subtitle = if (selectedTab == 0) {
                                                    "Las publicaciones anónimas que crees aparecerán aquí solo para ti."
                                                } else {
                                                    "Guarda publicaciones desde el feed para leerlas luego en privado."
                                                }
                                            )
                                        }
                                    } else {
                                        items(posts, key = { "${selectedTab}_${it.post.id}" }) { bundle ->
                                            AnonymousPostCard(
                                                bundle = bundle,
                                                onReact = { r -> viewModel.toggleReaction(bundle.post.id, r) },
                                                onOpenComments = { onOpenComments(bundle.post.id) },
                                                onToggleSave = { viewModel.toggleSave(bundle.post.id) },
                                                onShare = {
                                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(Intent.EXTRA_TEXT, bundle.post.text)
                                                    }
                                                    context.startActivity(Intent.createChooser(intent, "Compartir"))
                                                },
                                                onVotePoll = { pollId, idx -> viewModel.votePoll(pollId, idx) },
                                                onReportPost = {},
                                                canDeleteOwnPost = selectedTab == 0,
                                                onDeleteOwnPost = { viewModel.deleteMyPost(bundle.post.id) },
                                                onLoadVideoFile = { postId -> viewModel.loadPostVideoFile(context, postId) },
                                                modifier = Modifier.widthIn(max = 600.dp)
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
