package com.example.feature.profile

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.core.ui.components.MurmurAvatar
import com.example.core.ui.components.MurmurErrorView
import com.example.core.ui.components.MurmurLoadingView
import com.example.core.ui.components.MurmurStateView
import com.example.core.util.toFriendlySpanishError
import com.example.data.repository.AuthUserRepository
import com.example.domain.model.UserPublicProfile
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowersListScreen(
    targetUid: String,
    initialTab: Int,
    userRepository: AuthUserRepository,
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    var followers by remember { mutableStateOf<List<UserPublicProfile>>(emptyList()) }
    var following by remember { mutableStateOf<List<UserPublicProfile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            isLoading = true
            errorMsg = null
            try {
                followers = userRepository.getFollowersProfiles(targetUid)
                following = userRepository.getFollowingProfiles(targetUid)
            } catch (e: Exception) {
                errorMsg = e.toFriendlySpanishError()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(targetUid) {
        load()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Conexiones", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Seguidores (${followers.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Seguidos (${following.size})") }
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    isLoading -> MurmurLoadingView(modifier = Modifier.align(Alignment.Center))
                    errorMsg != null -> MurmurErrorView(
                        message = errorMsg!!,
                        onRetry = { load() },
                        modifier = Modifier.align(Alignment.Center)
                    )
                    else -> {
                        val list = if (selectedTab == 0) followers else following
                        if (list.isEmpty()) {
                            MurmurStateView(
                                icon = Icons.Outlined.Group,
                                title = if (selectedTab == 0) "Sin seguidores aún" else "No sigue a nadie aún",
                                subtitle = "Las relaciones sociales aparecerán aquí.",
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(list, key = { it.uid }) { user ->
                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpenUser(user.uid) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            MurmurAvatar(
                                                photoData = user.photoData,
                                                fallbackLabel = user.username,
                                                size = 44.dp
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = user.displayName.ifBlank { "@${user.username}" },
                                                    style = MaterialTheme.typography.titleMedium
                                                )
                                                Text(
                                                    text = "@${user.username}",
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
                }
            }
        }
    }
}
