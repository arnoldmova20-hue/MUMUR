package com.example.feature.navigation

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.core.preferences.AppearanceSettings
import com.example.core.preferences.AppPreferencesRepository
import com.example.data.repository.AuthUserRepository
import com.example.data.repository.MessageRepository
import com.example.data.repository.NotificationAndSafetyRepository
import com.example.data.repository.PostRepository
import com.example.domain.model.UiState
import com.example.feature.comments.CommentsScreen
import com.example.feature.comments.CommentsViewModel
import com.example.feature.explore.ExploreScreen
import com.example.feature.explore.ExploreViewModel
import com.example.feature.home.HomeFeedScreen
import com.example.feature.home.HomeFeedViewModel
import com.example.feature.messages.ChatScreen
import com.example.feature.messages.MessagesScreen
import com.example.feature.messages.MessagesViewModel
import com.example.feature.notifications.NotificationsScreen
import com.example.feature.notifications.NotificationsViewModel
import com.example.feature.profile.EditProfileScreen
import com.example.feature.profile.FollowersListScreen
import com.example.feature.profile.ProfileScreen
import com.example.feature.profile.ProfileViewModel
import com.example.feature.profile.UserProfileScreen
import com.example.feature.publish.PublishScreen
import com.example.feature.publish.PublishViewModel
import com.example.feature.settings.SettingsScreen
import com.example.ui.theme.MurmurNavBarStyle
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

object MurmurRoutes {
    const val HOME = "home"
    const val EXPLORE = "explore"
    const val PUBLISH = "publish"
    const val MESSAGES = "messages"
    const val PROFILE = "profile"
    const val COMMENTS = "comments/{postId}"
    const val CHAT = "chat/{targetUid}"
    const val USER_PROFILE = "user_profile/{targetUid}"
    const val EDIT_PROFILE = "edit_profile"
    const val FOLLOWERS = "followers/{uid}/{tab}"
    const val NOTIFICATIONS = "notifications"
    const val SETTINGS = "settings"

    fun commentsRoute(postId: String) = "comments/${Uri.encode(postId)}"
    fun chatRoute(targetUid: String) = "chat/${Uri.encode(targetUid)}"
    fun userProfileRoute(targetUid: String) = "user_profile/${Uri.encode(targetUid)}"
    fun followersRoute(uid: String, tab: Int) = "followers/${Uri.encode(uid)}/$tab"
}

private data class MurmurTopTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TopTabs = listOf(
    MurmurTopTab(MurmurRoutes.HOME, "Inicio", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
    MurmurTopTab(MurmurRoutes.EXPLORE, "Explorar", Icons.Filled.Search, Icons.Outlined.Search, "nav_explore"),
    MurmurTopTab(MurmurRoutes.PUBLISH, "Publicar", Icons.Filled.AddCircle, Icons.Outlined.AddCircleOutline, "nav_publish"),
    MurmurTopTab(MurmurRoutes.MESSAGES, "Mensajes", Icons.Filled.Mail, Icons.Outlined.MailOutline, "nav_messages"),
    MurmurTopTab(MurmurRoutes.PROFILE, "Perfil", Icons.Filled.Person, Icons.Outlined.PersonOutline, "nav_profile")
)

@Composable
fun MurmurAppRoot(
    currentUserId: String,
    userRepository: AuthUserRepository,
    prefsRepository: AppPreferencesRepository,
    appearance: AppearanceSettings,
    onOpenAppearanceMenu: () -> Unit,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext

    val db = remember(currentUserId) {
        FirebaseFirestore.getInstance(appContext.getString(R.string.firestore_database_id))
    }
    val postRepository = remember(currentUserId) { PostRepository(db, Firebase.auth) }
    val messageRepository = remember(currentUserId) { MessageRepository(db, Firebase.auth) }
    val safetyRepository = remember(currentUserId) { NotificationAndSafetyRepository(db, Firebase.auth) }

    val homeFeedViewModel: HomeFeedViewModel = viewModel(
        key = "home_$currentUserId",
        factory = simpleFactory { HomeFeedViewModel(postRepository, safetyRepository, currentUserId) }
    )
    val exploreViewModel: ExploreViewModel = viewModel(
        key = "explore_$currentUserId",
        factory = simpleFactory { ExploreViewModel(userRepository, postRepository, safetyRepository, prefsRepository) }
    )
    val publishViewModel: PublishViewModel = viewModel(
        key = "publish_$currentUserId",
        factory = simpleFactory { PublishViewModel(postRepository) }
    )
    val messagesViewModel: MessagesViewModel = viewModel(
        key = "messages_$currentUserId",
        factory = simpleFactory { MessagesViewModel(messageRepository, userRepository, safetyRepository, currentUserId) }
    )
    val profileViewModel: ProfileViewModel = viewModel(
        key = "profile_$currentUserId",
        factory = simpleFactory { ProfileViewModel(userRepository, postRepository, safetyRepository, currentUserId) }
    )
    val notificationsViewModel: NotificationsViewModel = viewModel(
        key = "notifications_$currentUserId",
        factory = simpleFactory {
            NotificationsViewModel(appContext, safetyRepository, userRepository, currentUserId)
        }
    )

    val notificationsState by notificationsViewModel.notificationsState.collectAsStateWithLifecycle()
    val unreadNotificationsCount = (notificationsState as? UiState.Success)?.data?.count { !it.read } ?: 0

    val conversationsState by messagesViewModel.conversationsState.collectAsStateWithLifecycle()
    val unreadMessagesCount = (conversationsState as? UiState.Success)?.data?.sumOf {
        it.conversation.unreadFor(currentUserId).toInt()
    } ?: 0

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showTopLevelNav = currentRoute in TopTabs.map { it.route }

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 720.dp
        val useFloatingBottomBar = showTopLevelNav &&
            !useRail &&
            appearance.navBarStyle == MurmurNavBarStyle.FLOATING

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showTopLevelNav && !useRail && !useFloatingBottomBar) {
                    DockedBottomNavigationBar(
                        currentRoute = currentRoute,
                        unreadMessagesCount = unreadMessagesCount,
                        onSelectTab = ::navigateTopLevel
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = innerPadding.calculateBottomPadding())
                ) {
                    if (showTopLevelNav && useRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            TopTabs.forEach { tab ->
                                val selected = currentRoute == tab.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(tab.route) },
                                    icon = {
                                        if (tab.route == MurmurRoutes.MESSAGES && unreadMessagesCount > 0) {
                                            BadgedBox(badge = { Badge { Text("$unreadMessagesCount") } }) {
                                                Icon(
                                                    if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                    contentDescription = tab.label
                                                )
                                            }
                                        } else {
                                            Icon(
                                                if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        }
                                    },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        NavHost(
                            navController = navController,
                            startDestination = MurmurRoutes.HOME
                        ) {
                            composable(MurmurRoutes.HOME) {
                                HomeFeedScreen(
                                    viewModel = homeFeedViewModel,
                                    unreadNotificationsCount = unreadNotificationsCount,
                                    onOpenComments = { postId ->
                                        navController.navigate(MurmurRoutes.commentsRoute(postId))
                                    },
                                    onOpenPublish = { navigateTopLevel(MurmurRoutes.PUBLISH) },
                                    onOpenNotifications = {
                                        navController.navigate(MurmurRoutes.NOTIFICATIONS)
                                    }
                                )
                            }

                            composable(MurmurRoutes.EXPLORE) {
                                ExploreScreen(
                                    viewModel = exploreViewModel,
                                    onOpenUserProfile = { targetUid ->
                                        if (targetUid == currentUserId) {
                                            navigateTopLevel(MurmurRoutes.PROFILE)
                                        } else {
                                            navController.navigate(MurmurRoutes.userProfileRoute(targetUid))
                                        }
                                    },
                                    onOpenComments = { postId ->
                                        navController.navigate(MurmurRoutes.commentsRoute(postId))
                                    }
                                )
                            }

                            composable(MurmurRoutes.PUBLISH) {
                                PublishScreen(
                                    viewModel = publishViewModel,
                                    onPublished = {
                                        homeFeedViewModel.loadInitialFeed()
                                        profileViewModel.refreshPrivateCollections()
                                        navigateTopLevel(MurmurRoutes.HOME)
                                    }
                                )
                            }

                            composable(MurmurRoutes.MESSAGES) {
                                MessagesScreen(
                                    viewModel = messagesViewModel,
                                    currentUserId = currentUserId,
                                    onOpenChat = { targetUid ->
                                        navController.navigate(MurmurRoutes.chatRoute(targetUid))
                                    }
                                )
                            }

                            composable(MurmurRoutes.PROFILE) {
                                ProfileScreen(
                                    viewModel = profileViewModel,
                                    currentUserId = currentUserId,
                                    onEditProfile = { navController.navigate(MurmurRoutes.EDIT_PROFILE) },
                                    onOpenSettings = { navController.navigate(MurmurRoutes.SETTINGS) },
                                    onOpenFollowers = { uid, tab ->
                                        navController.navigate(MurmurRoutes.followersRoute(uid, tab))
                                    },
                                    onOpenComments = { postId ->
                                        navController.navigate(MurmurRoutes.commentsRoute(postId))
                                    }
                                )
                            }

                            composable(
                                route = MurmurRoutes.COMMENTS,
                                arguments = listOf(navArgument("postId") { type = NavType.StringType })
                            ) { entry ->
                                val postId = entry.arguments?.getString("postId") ?: ""
                                val commentsVm: CommentsViewModel = viewModel(
                                    key = "comments_${currentUserId}_$postId",
                                    factory = simpleFactory {
                                        CommentsViewModel(
                                            postRepository = postRepository,
                                            userRepository = userRepository,
                                            safetyRepository = safetyRepository,
                                            postId = postId,
                                            currentUserId = currentUserId
                                        )
                                    }
                                )
                                CommentsScreen(
                                    viewModel = commentsVm,
                                    currentUserId = currentUserId,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable(
                                route = MurmurRoutes.CHAT,
                                arguments = listOf(navArgument("targetUid") { type = NavType.StringType })
                            ) { entry ->
                                val targetUid = entry.arguments?.getString("targetUid") ?: ""
                                ChatScreen(
                                    viewModel = messagesViewModel,
                                    currentUserId = currentUserId,
                                    targetUid = targetUid,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable(
                                route = MurmurRoutes.USER_PROFILE,
                                arguments = listOf(navArgument("targetUid") { type = NavType.StringType })
                            ) { entry ->
                                val targetUid = entry.arguments?.getString("targetUid") ?: ""
                                UserProfileScreen(
                                    targetUid = targetUid,
                                    currentUserId = currentUserId,
                                    userRepository = userRepository,
                                    safetyRepository = safetyRepository,
                                    onBack = { navController.popBackStack() },
                                    onOpenChat = { chatUid ->
                                        navController.navigate(MurmurRoutes.chatRoute(chatUid))
                                    },
                                    onOpenFollowers = { uid, tab ->
                                        navController.navigate(MurmurRoutes.followersRoute(uid, tab))
                                    }
                                )
                            }

                            composable(MurmurRoutes.EDIT_PROFILE) {
                                EditProfileScreen(
                                    viewModel = profileViewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable(
                                route = MurmurRoutes.FOLLOWERS,
                                arguments = listOf(
                                    navArgument("uid") { type = NavType.StringType },
                                    navArgument("tab") { type = NavType.IntType }
                                )
                            ) { entry ->
                                val uid = entry.arguments?.getString("uid") ?: currentUserId
                                val tab = entry.arguments?.getInt("tab") ?: 0
                                FollowersListScreen(
                                    targetUid = uid,
                                    initialTab = tab,
                                    userRepository = userRepository,
                                    onBack = { navController.popBackStack() },
                                    onOpenUser = { targetUid ->
                                        if (targetUid == currentUserId) {
                                            navigateTopLevel(MurmurRoutes.PROFILE)
                                        } else {
                                            navController.navigate(MurmurRoutes.userProfileRoute(targetUid))
                                        }
                                    }
                                )
                            }

                            composable(MurmurRoutes.NOTIFICATIONS) {
                                NotificationsScreen(
                                    viewModel = notificationsViewModel,
                                    onBack = { navController.popBackStack() },
                                    onOpenUserProfile = { uid ->
                                        navController.navigate(MurmurRoutes.userProfileRoute(uid))
                                    },
                                    onOpenChat = { uid ->
                                        navController.navigate(MurmurRoutes.chatRoute(uid))
                                    },
                                    onOpenComments = { postId ->
                                        navController.navigate(MurmurRoutes.commentsRoute(postId))
                                    }
                                )
                            }

                            composable(MurmurRoutes.SETTINGS) {
                                SettingsScreen(
                                    profileViewModel = profileViewModel,
                                    userRepository = userRepository,
                                    prefsRepository = prefsRepository,
                                    appearance = appearance,
                                    onOpenAppearanceMenu = onOpenAppearanceMenu,
                                    onBack = { navController.popBackStack() },
                                    onEditProfile = { navController.navigate(MurmurRoutes.EDIT_PROFILE) },
                                    onSignedOut = onSignedOut
                                )
                            }
                        }
                    }
                }

                if (useFloatingBottomBar) {
                    FloatingIosBottomNavigationBar(
                        currentRoute = currentRoute,
                        unreadMessagesCount = unreadMessagesCount,
                        onSelectTab = ::navigateTopLevel,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }
}

@Composable
private fun DockedBottomNavigationBar(
    currentRoute: String?,
    unreadMessagesCount: Int,
    onSelectTab: (String) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        TopTabs.forEach { tab ->
            val selected = currentRoute == tab.route
            val isPublish = tab.route == MurmurRoutes.PUBLISH
            NavigationBarItem(
                selected = selected,
                onClick = { onSelectTab(tab.route) },
                icon = {
                    NavTabIcon(
                        tab = tab,
                        selected = selected,
                        isPublish = isPublish,
                        unreadMessagesCount = unreadMessagesCount
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                ),
                modifier = Modifier.testTag(tab.testTag)
            )
        }
    }
}

@Composable
private fun FloatingIosBottomNavigationBar(
    currentRoute: String?,
    unreadMessagesCount: Int,
    onSelectTab: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
        shadowElevation = 14.dp,
        tonalElevation = 6.dp,
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .testTag("floating_ios_nav_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TopTabs.forEach { tab ->
                val selected = currentRoute == tab.route
                val isPublish = tab.route == MurmurRoutes.PUBLISH
                val pillBg by animateColorAsState(
                    targetValue = if (selected && !isPublish) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else Color.Transparent,
                    label = "floating_tab_bg_${tab.route}"
                )
                val tintColor = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(24.dp))
                        .background(pillBg)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true)
                        ) { onSelectTab(tab.route) }
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                        .testTag(tab.testTag),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        NavTabIcon(
                            tab = tab,
                            selected = selected,
                            isPublish = isPublish,
                            unreadMessagesCount = unreadMessagesCount,
                            tintOverride = tintColor
                        )
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = tintColor,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavTabIcon(
    tab: MurmurTopTab,
    selected: Boolean,
    isPublish: Boolean,
    unreadMessagesCount: Int,
    tintOverride: Color? = null
) {
    when {
        isPublish -> {
            Surface(
                shape = CircleShape,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = tab.label,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.padding(5.dp)
                )
            }
        }
        tab.route == MurmurRoutes.MESSAGES && unreadMessagesCount > 0 -> {
            BadgedBox(
                badge = {
                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                        Text(unreadMessagesCount.coerceAtMost(99).toString())
                    }
                }
            ) {
                Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = tab.label,
                    tint = tintOverride ?: Color.Unspecified
                )
            }
        }
        else -> {
            Icon(
                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                contentDescription = tab.label,
                tint = tintOverride ?: Color.Unspecified
            )
        }
    }
}

private fun <T : ViewModel> simpleFactory(creator: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = creator() as VM
    }
