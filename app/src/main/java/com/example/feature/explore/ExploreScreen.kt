package com.example.feature.explore

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ui.components.AnonymousPostCard
import com.example.core.ui.components.FloatingBubblesBackground
import com.example.core.ui.components.MurmurAvatar
import com.example.core.ui.components.MurmurErrorView
import com.example.core.ui.components.MurmurLoadingView
import com.example.core.ui.components.MurmurStateView
import com.example.core.ui.components.ReportContentDialog
import com.example.domain.model.UiState
import com.example.domain.model.UserPublicProfile

private enum class ExploreFilter(val label: String) {
    ALL("Todo"),
    USERS("Personas"),
    POSTS("Publicaciones")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel,
    onOpenUserProfile: (String) -> Unit,
    onOpenComments: (String) -> Unit
) {
    val context = LocalContext.current
    val query by viewModel.query.collectAsStateWithLifecycle()
    val resultsState by viewModel.resultsState.collectAsStateWithLifecycle()
    val history by viewModel.searchHistory.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()

    var selectedFilter by remember { mutableStateOf(ExploreFilter.ALL) }
    var reportingPostId by remember { mutableStateOf<String?>(null) }
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
                        text = "Explorar",
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
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::onQueryChanged,
                    placeholder = { Text("Buscar @usuarios, frases o historias...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChanged("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpiar búsqueda")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.submitSearch(query) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .testTag("explore_search_input")
                )

                // Search history chips
                if (history.isNotEmpty() && query.isBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Búsquedas recientes",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { viewModel.clearHistory() }) {
                            Text("Borrar")
                        }
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(history, key = { it }) { item ->
                            InputChip(
                                selected = false,
                                onClick = { viewModel.submitSearch(item) },
                                label = { Text(item) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Quitar",
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { viewModel.removeHistoryItem(item) }
                                    )
                                }
                            )
                        }
                    }
                }

                // Category filter chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExploreFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.label) },
                            modifier = Modifier.testTag("explore_filter_${filter.name.lowercase()}")
                        )
                    }
                }

                when (val state = resultsState) {
                    is UiState.Loading -> {
                        MurmurLoadingView(message = "Buscando en MURMUR...")
                    }
                    is UiState.Error -> {
                        MurmurErrorView(
                            message = state.message,
                            onRetry = { viewModel.submitSearch(query) }
                        )
                    }
                    is UiState.Success -> {
                        val showUsers = selectedFilter != ExploreFilter.POSTS
                        val showPosts = selectedFilter != ExploreFilter.USERS
                        val users = if (showUsers) state.data.users else emptyList()
                        val posts = if (showPosts) state.data.posts else emptyList()

                        if (users.isEmpty() && posts.isEmpty()) {
                            MurmurStateView(
                                icon = Icons.Outlined.SearchOff,
                                title = "Sin resultados",
                                subtitle = "No encontramos perfiles ni publicaciones públicas para \"${state.data.query}\"."
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                if (users.isNotEmpty()) {
                                    item(key = "users_header") {
                                        Text(
                                            text = "Personas (${users.size})",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    items(users, key = { "user_${it.uid}" }) { user ->
                                        UserResultRow(
                                            user = user,
                                            onClick = { onOpenUserProfile(user.uid) }
                                        )
                                    }
                                }

                                if (posts.isNotEmpty()) {
                                    item(key = "posts_header") {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Publicaciones anónimas (${posts.size})",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    items(posts, key = { "post_${it.post.id}" }) { bundle ->
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
                                            onReportPost = { reportingPostId = bundle.post.id },
                                            onLoadVideoFile = { postId -> viewModel.loadPostVideoFile(context, postId) }
                                        )
                                    }
                                }

                                item(key = "explore_bottom") {
                                    Spacer(modifier = Modifier.height(72.dp))
                                }
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
                val id = reportingPostId!!
                reportingPostId = null
                viewModel.reportPost(id, reason, details)
            }
        )
    }
}

@Composable
private fun UserResultRow(
    user: UserPublicProfile,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("user_result_${user.uid}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MurmurAvatar(
                photoData = user.photoData,
                fallbackLabel = user.username,
                size = 46.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.displayName.ifBlank { "@${user.username}" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "@${user.username} • ${user.followersCount} seguidores",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (user.bio.isNotBlank()) {
                    Text(
                        text = user.bio,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
