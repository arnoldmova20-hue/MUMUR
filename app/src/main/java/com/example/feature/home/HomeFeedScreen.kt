package com.example.feature.home

import android.content.Intent
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ui.components.AnonymousPostCard
import com.example.core.ui.components.FloatingBubblesBackground
import com.example.core.ui.components.MurmurErrorView
import com.example.core.ui.components.MurmurLoadingView
import com.example.core.ui.components.MurmurStateView
import com.example.core.ui.components.ReportContentDialog
import com.example.domain.model.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeFeedScreen(
    viewModel: HomeFeedViewModel,
    unreadNotificationsCount: Int,
    onOpenComments: (String) -> Unit,
    onOpenPublish: () -> Unit,
    onOpenNotifications: () -> Unit
) {
    val context = LocalContext.current
    val feedState by viewModel.feedState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val feedback by viewModel.feedbackMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var reportingPostId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(feedback) {
        feedback?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 3
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && hasMore && !isLoadingMore) {
            viewModel.loadMore()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "MURMUR",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Feed anónimo de la comunidad",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.loadInitialFeed() },
                        modifier = Modifier.testTag("refresh_feed_button")
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Actualizar feed"
                            )
                        }
                    }
                    IconButton(
                        onClick = onOpenNotifications,
                        modifier = Modifier.testTag("notifications_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadNotificationsCount > 0) {
                                    Badge {
                                        Text(
                                            text = unreadNotificationsCount.coerceAtMost(99).toString()
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notificaciones"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.94f)
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

            when (val state = feedState) {
                is UiState.Loading -> {
                    MurmurLoadingView(
                        message = "Cargando historias anónimas...",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is UiState.Error -> {
                    MurmurErrorView(
                        message = state.message,
                        isOffline = state.isOffline,
                        onRetry = { viewModel.loadInitialFeed() },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is UiState.Success -> {
                    val itemsList = state.data
                    if (itemsList.isEmpty()) {
                        MurmurStateView(
                            icon = Icons.Outlined.Forum,
                            title = "Aún no hay publicaciones",
                            subtitle = "Sé la primera persona en compartir una historia, pregunta o encuesta de manera anónima.",
                            actionLabel = "Crear publicación",
                            onAction = onOpenPublish,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(itemsList, key = { it.post.id }) { bundle ->
                                AnonymousPostCard(
                                    bundle = bundle,
                                    onReact = { rType -> viewModel.toggleReaction(bundle.post.id, rType) },
                                    onOpenComments = { onOpenComments(bundle.post.id) },
                                    onToggleSave = { viewModel.toggleSave(bundle.post.id) },
                                    onShare = {
                                        val shareText = buildString {
                                            append("Publicación anónima en MURMUR:\n\n")
                                            if (bundle.post.text.isNotBlank()) append(bundle.post.text)
                                            else if (bundle.poll != null) append("Encuesta: ${bundle.poll.question}")
                                        }
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                        }
                                        context.startActivity(
                                            Intent.createChooser(intent, "Compartir publicación anónima")
                                        )
                                    },
                                    onVotePoll = { pollId, idx -> viewModel.votePoll(pollId, idx) },
                                    onReportPost = { reportingPostId = bundle.post.id },
                                    onLoadVideoFile = { postId -> viewModel.loadPostVideoFile(context, postId) },
                                    modifier = Modifier.widthIn(max = 600.dp)
                                )
                            }

                            if (isLoadingMore) {
                                item(key = "loading_more") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    }
                                }
                            } else if (hasMore) {
                                item(key = "load_more_trigger") {
                                    TextButton(onClick = { viewModel.loadMore() }) {
                                        Text("Cargar más publicaciones")
                                    }
                                }
                            }
                            item(key = "bottom_spacer") {
                                Spacer(modifier = Modifier.height(72.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (reportingPostId != null) {
        ReportContentDialog(
            targetLabel = "publicación anónima",
            onDismiss = { reportingPostId = null },
            onSubmit = { reason, details ->
                val targetId = reportingPostId!!
                reportingPostId = null
                viewModel.reportPost(targetId, reason, details)
            }
        )
    }
}
