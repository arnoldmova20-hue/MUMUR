package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.notifications.MurmurNotificationHelper
import com.example.core.preferences.AppearanceSettings
import com.example.core.preferences.AppPreferencesRepository
import com.example.core.ui.components.AppearanceCustomizationDialog
import com.example.core.ui.components.FloatingBubblesBackground
import com.example.core.ui.components.MurmurLoadingView
import com.example.data.repository.AuthUserRepository
import com.example.domain.model.UserPublicProfile
import com.example.feature.authentication.OnboardingProfileScreen
import com.example.feature.authentication.attemptAutoSignIn
import com.example.feature.navigation.MurmurAppRoot
import com.example.feature.welcome.WelcomeScreen
import com.example.ui.theme.MurmurTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MurmurNotificationHelper.ensureChannel(this)

        setContent {
            val appContext = applicationContext
            val prefsRepository = remember { AppPreferencesRepository(appContext) }
            val loadedAppearance by prefsRepository.appearanceSettings.collectAsStateWithLifecycle(initialValue = null)
            val appearance = loadedAppearance ?: AppearanceSettings()
            val scope = rememberCoroutineScope()
            var showAppearanceMenu by remember { mutableStateOf(false) }
            var autoPromptHandled by remember { mutableStateOf(false) }

            LaunchedEffect(loadedAppearance) {
                val loaded = loadedAppearance ?: return@LaunchedEffect
                if (!loaded.hasCompletedSetup && !autoPromptHandled) {
                    autoPromptHandled = true
                    showAppearanceMenu = true
                }
            }

            MurmurTheme(
                darkTheme = appearance.isDarkTheme,
                accentTheme = appearance.accentTheme
            ) {
                MurmurEntryGate(
                    prefsRepository = prefsRepository,
                    appearance = appearance,
                    onOpenAppearanceMenu = { showAppearanceMenu = true }
                )

                if (showAppearanceMenu) {
                    val userRepo = remember { AuthUserRepository(appContext) }
                    AppearanceCustomizationDialog(
                        settings = appearance,
                        onDarkThemeChange = { dark ->
                            scope.launch {
                                prefsRepository.setDarkTheme(dark)
                                if (Firebase.auth.currentUser != null) {
                                    runCatching {
                                        userRepo.updatePrivateSettings(
                                            themeMode = if (dark) "dark" else "light"
                                        )
                                    }
                                }
                            }
                        },
                        onAccentThemeChange = { accent ->
                            scope.launch { prefsRepository.setAccentTheme(accent) }
                        },
                        onNavBarStyleChange = { navStyle ->
                            scope.launch { prefsRepository.setNavBarStyle(navStyle) }
                        },
                        onConfirm = {
                            showAppearanceMenu = false
                            scope.launch { prefsRepository.markAppearanceSetupCompleted() }
                        },
                        onDismiss = {
                            showAppearanceMenu = false
                            scope.launch { prefsRepository.markAppearanceSetupCompleted() }
                        }
                    )
                }
            }
        }
    }
}

private sealed interface ProfileLookupState {
    data object Loading : ProfileLookupState
    data object NeedsOnboarding : ProfileLookupState
    data class Ready(val profile: UserPublicProfile) : ProfileLookupState
}

@Composable
private fun MurmurEntryGate(
    prefsRepository: AppPreferencesRepository,
    appearance: AppearanceSettings,
    onOpenAppearanceMenu: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val userRepository = remember { AuthUserRepository(context.applicationContext) }

    var currentUid by remember { mutableStateOf(Firebase.auth.currentUser?.uid) }
    var isCheckingAutoAuth by remember { mutableStateOf(currentUid == null) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUid = auth.currentUser?.uid
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    currentUid = Firebase.auth.currentUser?.uid
                    isCheckingAutoAuth = false
                },
                onUnauthenticated = {
                    isCheckingAutoAuth = false
                },
                scope = scope
            )
        } else {
            isCheckingAutoAuth = false
        }
    }

    when {
        isCheckingAutoAuth -> {
            MurmurSplashLoader("Iniciando MURMUR...")
        }
        currentUid == null -> {
            WelcomeScreen(
                onAuthSuccess = {
                    currentUid = Firebase.auth.currentUser?.uid
                },
                onOpenAppearanceMenu = onOpenAppearanceMenu
            )
        }
        else -> {
            val uid = currentUid!!
            var profileLookup by remember(uid) { mutableStateOf<ProfileLookupState>(ProfileLookupState.Loading) }

            LaunchedEffect(uid) {
                profileLookup = ProfileLookupState.Loading
                userRepository.observeProfile(uid)
                    .catch {
                        val fallback = runCatching { userRepository.getProfile(uid) }.getOrNull()
                        profileLookup = if (fallback != null) {
                            ProfileLookupState.Ready(fallback)
                        } else {
                            ProfileLookupState.NeedsOnboarding
                        }
                    }
                    .collect { profile ->
                        profileLookup = if (profile != null && profile.username.isNotBlank()) {
                            ProfileLookupState.Ready(profile)
                        } else {
                            ProfileLookupState.NeedsOnboarding
                        }
                    }
            }

            when (profileLookup) {
                is ProfileLookupState.Loading -> {
                    MurmurSplashLoader("Cargando tu espacio seguro...")
                }
                is ProfileLookupState.NeedsOnboarding -> {
                    val defaultName = Firebase.auth.currentUser?.displayName.orEmpty()
                    OnboardingProfileScreen(
                        userRepository = userRepository,
                        defaultDisplayName = defaultName,
                        onOpenAppearanceMenu = onOpenAppearanceMenu,
                        onCompleted = {
                            // Profile snapshot listener automatically advances to Ready
                        }
                    )
                }
                is ProfileLookupState.Ready -> {
                    MurmurAppRoot(
                        currentUserId = uid,
                        userRepository = userRepository,
                        prefsRepository = prefsRepository,
                        appearance = appearance,
                        onOpenAppearanceMenu = onOpenAppearanceMenu,
                        onSignedOut = {
                            currentUid = Firebase.auth.currentUser?.uid
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MurmurSplashLoader(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        FloatingBubblesBackground(intense = true)
        MurmurLoadingView(message = message)
    }
}
