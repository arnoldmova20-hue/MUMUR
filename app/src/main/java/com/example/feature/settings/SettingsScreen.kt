package com.example.feature.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.notifications.MurmurNotificationHelper
import com.example.core.preferences.AppearanceSettings
import com.example.core.preferences.AppPreferencesRepository
import com.example.core.ui.components.FloatingBubblesBackground
import com.example.core.ui.components.LegalDocumentDialog
import com.example.core.ui.components.MurmurAvatar
import com.example.core.ui.components.NavLayoutPreviewCard
import com.example.core.util.SafetyValidator
import com.example.core.util.toFriendlySpanishError
import com.example.data.repository.AuthUserRepository
import com.example.domain.model.LegalContent
import com.example.domain.model.UiState
import com.example.domain.model.UserPrivateInfo
import com.example.feature.authentication.signOutFromMurmur
import com.example.feature.profile.ProfileViewModel
import com.example.ui.theme.MurmurAccentTheme
import com.example.ui.theme.MurmurNavBarStyle
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    profileViewModel: ProfileViewModel,
    userRepository: AuthUserRepository,
    prefsRepository: AppPreferencesRepository,
    appearance: AppearanceSettings,
    onOpenAppearanceMenu: () -> Unit,
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val profileState by profileViewModel.myProfileState.collectAsStateWithLifecycle()
    val privateInfo by profileViewModel.myPrivateInfo.collectAsStateWithLifecycle()
    val blockedProfiles by profileViewModel.blockedProfiles.collectAsStateWithLifecycle()
    val myReports by profileViewModel.myReports.collectAsStateWithLifecycle()
    val feedback by profileViewModel.feedback.collectAsStateWithLifecycle()

    var hasOsNotificationPerm by remember {
        mutableStateOf(MurmurNotificationHelper.hasNotificationPermission(context))
    }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasOsNotificationPerm = granted
        scope.launch {
            snackbarHostState.showSnackbar(
                if (granted) "Notificaciones del sistema activadas"
                else "Permiso de notificaciones denegado en el sistema"
            )
        }
    }

    var showBlockedDialog by remember { mutableStateOf(false) }
    var showReportsDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        profileViewModel.loadBlockedAndReports()
    }

    LaunchedEffect(feedback) {
        feedback?.let {
            snackbarHostState.showSnackbar(it)
            profileViewModel.clearFeedback()
        }
    }

    val profile = (profileState as? UiState.Success)?.data
    val info = privateInfo ?: UserPrivateInfo()
    val currentEmail = info.email ?: Firebase.auth.currentUser?.email ?: "Cuenta Google vinculada"

    fun updatePrivateToggle(block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }.onFailure { err ->
                snackbarHostState.showSnackbar(err.toFriendlySpanishError())
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Configuración y privacidad",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
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

            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .align(Alignment.TopCenter)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. CUENTA
                SettingsSectionCard(title = "Cuenta") {
                    SettingsRowItem(
                        icon = Icons.Default.Edit,
                        title = if (profile != null) "@${profile.username}" else "Mi perfil",
                        subtitle = currentEmail,
                        onClick = onEditProfile,
                        testTag = "settings_edit_profile_row"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRowItem(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        title = "Cerrar sesión",
                        subtitle = "Cierra tu sesión actual en este dispositivo",
                        onClick = {
                            signOutFromMurmur(
                                credentialManager = credentialManager,
                                scope = scope,
                                onSignOutComplete = onSignedOut
                            )
                        },
                        testTag = "sign_out_button"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRowItem(
                        icon = Icons.Default.DeleteForever,
                        title = "Eliminar cuenta",
                        subtitle = "Borra tu perfil, datos privados y reserva de usuario",
                        isDestructive = true,
                        onClick = { showDeleteConfirmDialog = true },
                        testTag = "delete_account_row"
                    )
                }

                // 2. PRIVACIDAD Y SEGURIDAD
                SettingsSectionCard(title = "Privacidad y seguridad") {
                    SettingsRowItem(
                        icon = Icons.Default.Block,
                        title = "Usuarios bloqueados (${blockedProfiles.size})",
                        subtitle = "Administra a las personas que has bloqueado",
                        onClick = {
                            profileViewModel.loadBlockedAndReports()
                            showBlockedDialog = true
                        },
                        testTag = "settings_blocked_users_row"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRowItem(
                        icon = Icons.Default.Flag,
                        title = "Historial de denuncias (${myReports.size})",
                        subtitle = "Consulta el estado de los reportes que enviaste",
                        onClick = {
                            profileViewModel.loadBlockedAndReports()
                            showReportsDialog = true
                        },
                        testTag = "settings_reports_row"
                    )
                    if (profile != null) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsSwitchRow(
                            title = "Aceptar solicitudes de mensajes",
                            subtitle = "Permite que cuentas que no sigues te envíen solicitudes",
                            checked = profile.allowMessageRequests,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    runCatching {
                                        userRepository.updateProfile(
                                            currentProfile = profile,
                                            newUsername = profile.username,
                                            newDisplayName = profile.displayName,
                                            newBio = profile.bio,
                                            newPhotoData = profile.photoData,
                                            allowMessageRequests = enabled,
                                            isPrivateProfile = profile.isPrivateProfile,
                                            showIdentityInComments = profile.showIdentityInComments
                                        )
                                    }.onFailure { err ->
                                        snackbarHostState.showSnackbar(err.toFriendlySpanishError())
                                    }
                                }
                            },
                            testTag = "toggle_allow_message_requests"
                        )
                    }
                }

                // 3. NOTIFICACIONES
                SettingsSectionCard(title = "Notificaciones") {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasOsNotificationPerm) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Permiso de Android desactivado",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Activa las alertas para recibir avisos en tu dispositivo.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                                modifier = Modifier.testTag("grant_notifications_button")
                            ) {
                                Text("Activar")
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    } else {
                        SettingsRowItem(
                            icon = Icons.Default.NotificationsActive,
                            title = "Notificaciones del dispositivo",
                            subtitle = "Activas en este dispositivo",
                            onClick = {}
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }

                    SettingsSwitchRow(
                        title = "Nuevos seguidores",
                        checked = info.notifyFollowers,
                        onCheckedChange = { v -> updatePrivateToggle { userRepository.updatePrivateSettings(notifyFollowers = v) } }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsSwitchRow(
                        title = "Reacciones en tus publicaciones",
                        checked = info.notifyReactions,
                        onCheckedChange = { v -> updatePrivateToggle { userRepository.updatePrivateSettings(notifyReactions = v) } }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsSwitchRow(
                        title = "Comentarios y respuestas",
                        checked = info.notifyComments && info.notifyReplies,
                        onCheckedChange = { v -> updatePrivateToggle { userRepository.updatePrivateSettings(notifyComments = v, notifyReplies = v) } }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsSwitchRow(
                        title = "Mensajes privados",
                        checked = info.notifyMessages,
                        onCheckedChange = { v -> updatePrivateToggle { userRepository.updatePrivateSettings(notifyMessages = v) } }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsSwitchRow(
                        title = "Avisos de seguridad",
                        checked = info.notifySecurity,
                        onCheckedChange = { v -> updatePrivateToggle { userRepository.updatePrivateSettings(notifySecurity = v) } }
                    )
                }

                // 4. APARIENCIA
                SettingsSectionCard(title = "Apariencia") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DarkMode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Modo oscuro",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = if (appearance.isDarkTheme) "Oscuro activo" else "Modo claro activo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = appearance.isDarkTheme,
                            onCheckedChange = { dark ->
                                scope.launch {
                                    prefsRepository.setDarkTheme(dark)
                                    runCatching {
                                        userRepository.updatePrivateSettings(
                                            themeMode = if (dark) "dark" else "light"
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.testTag("dark_theme_switch")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Color de la interfaz (${appearance.accentTheme.labelEs})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MurmurAccentTheme.entries.forEach { palette ->
                                val selected = appearance.accentTheme == palette
                                val swatchColor = if (appearance.isDarkTheme) palette.darkPrimary else palette.lightPrimary
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(swatchColor)
                                        .clickable {
                                            scope.launch { prefsRepository.setAccentTheme(palette) }
                                        }
                                        .testTag("settings_color_${palette.key}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = palette.labelEs,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Barra de navegación",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            NavLayoutPreviewCard(
                                style = MurmurNavBarStyle.DOCKED,
                                selected = appearance.navBarStyle == MurmurNavBarStyle.DOCKED,
                                onClick = { scope.launch { prefsRepository.setNavBarStyle(MurmurNavBarStyle.DOCKED) } },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_nav_original")
                            )
                            NavLayoutPreviewCard(
                                style = MurmurNavBarStyle.FLOATING,
                                selected = appearance.navBarStyle == MurmurNavBarStyle.FLOATING,
                                onClick = { scope.launch { prefsRepository.setNavBarStyle(MurmurNavBarStyle.FLOATING) } },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_nav_floating")
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    SettingsRowItem(
                        icon = Icons.Default.Palette,
                        title = "Abrir menú visual de apariencia",
                        subtitle = "Configura modo, color y barra de navegación en un solo lugar",
                        onClick = onOpenAppearanceMenu,
                        testTag = "settings_open_appearance_menu"
                    )
                }

                // 5. INFORMACIÓN LEGAL Y ACERCA DE
                SettingsSectionCard(title = "Información") {
                    SettingsRowItem(
                        icon = Icons.Default.Description,
                        title = "Términos y Condiciones",
                        subtitle = "Versión ${LegalContent.TERMS_VERSION}",
                        onClick = { showTermsDialog = true },
                        testTag = "settings_terms_row"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRowItem(
                        icon = Icons.Default.PrivacyTip,
                        title = "Política de Privacidad",
                        subtitle = "Cómo protegemos tu anonimato y tus datos",
                        onClick = { showPrivacyDialog = true },
                        testTag = "settings_privacy_row"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRowItem(
                        icon = Icons.Default.Security,
                        title = "Protección de adolescentes",
                        subtitle = "Control de edad 13+, bloqueo y filtro anti-datos personales",
                        onClick = { showAboutDialog = true }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRowItem(
                        icon = Icons.Default.Info,
                        title = "Acerca de MURMUR",
                        subtitle = "Versión 1.0 • «Las historias que nadie se atreve a contar»",
                        onClick = { showAboutDialog = true }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showBlockedDialog) {
        AlertDialog(
            onDismissRequest = { showBlockedDialog = false },
            title = { Text("Usuarios bloqueados", style = MaterialTheme.typography.titleLarge) },
            text = {
                if (blockedProfiles.isEmpty()) {
                    Text(
                        text = "No tienes usuarios bloqueados actualmente.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        blockedProfiles.forEach { user ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    MurmurAvatar(
                                        photoData = user.photoData,
                                        fallbackLabel = user.username,
                                        size = 36.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "@${user.username}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                TextButton(onClick = { profileViewModel.unblockUser(user.uid) }) {
                                    Text("Desbloquear")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showBlockedDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }

    if (showReportsDialog) {
        AlertDialog(
            onDismissRequest = { showReportsDialog = false },
            title = { Text("Mis denuncias", style = MaterialTheme.typography.titleLarge) },
            text = {
                if (myReports.isEmpty()) {
                    Text(
                        text = "No has enviado ninguna denuncia.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        myReports.forEach { report ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(10.dp).fillMaxWidth()) {
                                    Text(
                                        text = "${report.contentType.uppercase()} • ${report.reason}",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    if (report.details.isNotBlank()) {
                                        Text(
                                            text = report.details,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "Estado: ${if (report.status == "pending") "En revisión" else report.status} • ${SafetyValidator.formatRelativeTime(report.createdAt?.toDate()?.time)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showReportsDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("¿Eliminar tu cuenta?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    text = "Esta acción eliminará permanentemente tu perfil (@${profile?.username ?: ""}), tu reserva de nombre de usuario y tu información privada de MURMUR. No se puede deshacer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showDeleteConfirmDialog = false
                        profileViewModel.deleteAccount(profile?.username ?: "") {
                            onSignedOut()
                        }
                    },
                    modifier = Modifier.testTag("confirm_delete_account_button")
                ) {
                    Text("Eliminar definitivamente")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showTermsDialog) {
        LegalDocumentDialog(
            title = LegalContent.OFFICIAL_TERMS_TITLE,
            body = LegalContent.OFFICIAL_TERMS_BODY,
            onDismiss = { showTermsDialog = false }
        )
    }

    if (showPrivacyDialog) {
        LegalDocumentDialog(
            title = LegalContent.PRIVACY_POLICY_TITLE,
            body = LegalContent.PRIVACY_POLICY_BODY,
            onDismiss = { showPrivacyDialog = false }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("MURMUR v1.0", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    text = "«Las historias que nadie se atreve a contar»\n\nMURMUR combina perfiles sociales reales con publicaciones 100% anónimas separadas por arquitectura en Cloud Firestore, junto con controles de edad (13+), bloqueo, denuncia y filtrado preventivo de datos personales.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(onClick = { showAboutDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isDestructive: Boolean = false,
    testTag: String? = null,
    onClick: () -> Unit
) {
    val mod = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 14.dp)
    Row(
        modifier = if (testTag != null) mod.testTag(testTag) else mod,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier
        )
    }
}
