package com.example.feature.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ui.components.MurmurAvatar
import com.example.core.ui.components.MurmurLoadingView
import com.example.core.util.SafetyValidator
import com.example.domain.model.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val profileState by viewModel.myProfileState.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(feedback) {
        feedback?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    val currentProfile = (profileState as? UiState.Success)?.data

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Editar perfil", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        if (currentProfile == null) {
            MurmurLoadingView(modifier = Modifier.padding(innerPadding))
            return@Scaffold
        }

        var username by remember(currentProfile.uid) { mutableStateOf(currentProfile.username) }
        var displayName by remember(currentProfile.uid) { mutableStateOf(currentProfile.displayName) }
        var bio by remember(currentProfile.uid) { mutableStateOf(currentProfile.bio) }
        var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
        var allowMessageRequests by remember(currentProfile.uid) { mutableStateOf(currentProfile.allowMessageRequests) }
        var isPrivateProfile by remember(currentProfile.uid) { mutableStateOf(currentProfile.isPrivateProfile) }
        var showIdentityInComments by remember(currentProfile.uid) { mutableStateOf(currentProfile.showIdentityInComments) }

        val photoPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) selectedPhotoUri = uri
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .widthIn(max = 560.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            MurmurAvatar(
                photoData = currentProfile.photoData,
                fallbackLabel = username,
                size = 86.dp
            )

            OutlinedButton(
                onClick = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null)
                Spacer(modifier = Modifier.padding(4.dp))
                Text(if (selectedPhotoUri != null) "Nueva foto seleccionada" else "Cambiar foto de perfil")
            }

            OutlinedTextField(
                value = username,
                onValueChange = { username = SafetyValidator.sanitizeUsername(it).take(24) },
                label = { Text("Nombre de usuario") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = displayName,
                onValueChange = { if (it.length <= 40) displayName = it },
                label = { Text("Nombre visible (opcional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = bio,
                onValueChange = { if (it.length <= 200) bio = it },
                label = { Text("Biografía") },
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Privacidad del perfil",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            )

            SettingSwitchRow(
                title = "Permitir solicitudes de mensajes",
                subtitle = "Personas que no sigues pueden enviarte solicitudes.",
                checked = allowMessageRequests,
                onCheckedChange = { allowMessageRequests = it }
            )

            SettingSwitchRow(
                title = "Ocultar biografía en perfil privado",
                subtitle = "Limita la biografía pública frente a desconocidos.",
                checked = isPrivateProfile,
                onCheckedChange = { isPrivateProfile = it }
            )

            SettingSwitchRow(
                title = "Mostrar mi @usuario por defecto al comentar",
                subtitle = "Si está desactivado, comentarás como Eco anónimo.",
                checked = showIdentityInComments,
                onCheckedChange = { showIdentityInComments = it }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    viewModel.updateProfile(
                        current = currentProfile,
                        username = username,
                        displayName = displayName,
                        bio = bio,
                        newPhotoUri = selectedPhotoUri,
                        context = context,
                        allowMessageRequests = allowMessageRequests,
                        isPrivateProfile = isPrivateProfile,
                        showIdentityInComments = showIdentityInComments,
                        onSuccess = onBack
                    )
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("Guardar cambios")
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
