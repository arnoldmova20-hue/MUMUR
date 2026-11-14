package com.example.feature.authentication

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.core.ui.components.FloatingBubblesBackground
import com.example.core.ui.components.LegalDocumentDialog
import com.example.core.util.SafetyValidator
import com.example.core.util.toFriendlySpanishError
import com.example.data.repository.AuthUserRepository
import com.example.domain.model.LegalContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OnboardingProfileScreen(
    userRepository: AuthUserRepository,
    defaultDisplayName: String,
    onOpenAppearanceMenu: () -> Unit = {},
    onCompleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf(defaultDisplayName.take(40)) }
    var bio by remember { mutableStateOf("") }
    var birthYearInput by remember { mutableStateOf("") }
    var acceptedTerms by remember { mutableStateOf(false) }

    var isCheckingUsername by remember { mutableStateOf(false) }
    var isUsernameAvailable by remember { mutableStateOf<Boolean?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var showTerms by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }

    LaunchedEffect(username) {
        val clean = SafetyValidator.sanitizeUsername(username)
        if (!SafetyValidator.isValidUsernameFormat(clean)) {
            isUsernameAvailable = null
            isCheckingUsername = false
            return@LaunchedEffect
        }
        isCheckingUsername = true
        delay(450)
        isUsernameAvailable = runCatching {
            userRepository.isUsernameAvailable(clean)
        }.getOrNull()
        isCheckingUsername = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        FloatingBubblesBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .widthIn(max = 520.dp)
                .align(Alignment.Center)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Completa tu perfil",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onOpenAppearanceMenu,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("onboarding_appearance_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "Personalizar apariencia",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Apariencia", style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Tu perfil sirve para conectar y chatear, pero tus publicaciones siempre serán anónimas.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { raw ->
                    username = SafetyValidator.sanitizeUsername(raw).take(24)
                    errorMsg = null
                },
                label = { Text("Nombre de usuario único") },
                placeholder = { Text("ej. eco_nocturno") },
                leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
                trailingIcon = {
                    when {
                        isCheckingUsername -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        isUsernameAvailable == true -> Icon(Icons.Default.CheckCircle, contentDescription = "Disponible", tint = MaterialTheme.colorScheme.primary)
                        isUsernameAvailable == false -> Icon(Icons.Default.ErrorOutline, contentDescription = "No disponible", tint = MaterialTheme.colorScheme.error)
                    }
                },
                supportingText = {
                    val clean = SafetyValidator.sanitizeUsername(username)
                    when {
                        username.isBlank() -> Text("3–24 caracteres: minúsculas, números y _")
                        !SafetyValidator.isValidUsernameFormat(clean) -> Text("Formato inválido (mínimo 3 caracteres)")
                        isUsernameAvailable == true -> Text("Disponible: @$clean")
                        isUsernameAvailable == false -> Text("Ese nombre ya está en uso", color = MaterialTheme.colorScheme.error)
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_username_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = displayName,
                onValueChange = { if (it.length <= 40) displayName = it },
                label = { Text("Nombre visible (opcional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = bio,
                onValueChange = { if (it.length <= 200) bio = it },
                label = { Text("Biografía (opcional)") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = birthYearInput,
                onValueChange = { input ->
                    if (input.length <= 4 && input.all { it.isDigit() }) {
                        birthYearInput = input
                        errorMsg = null
                    }
                },
                label = { Text("Año de nacimiento (control de edad 13+)") },
                placeholder = { Text("ej. 2007") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_birth_year_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { acceptedTerms = !acceptedTerms }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = acceptedTerms,
                        onCheckedChange = { acceptedTerms = it },
                        modifier = Modifier.testTag("accept_terms_checkbox")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Acepto los Términos y Condiciones y la Política de Privacidad de MURMUR.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Ver Términos",
                                style = MaterialTheme.typography.labelSmall.copy(textDecoration = TextDecoration.Underline),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { showTerms = true }
                                    .padding(vertical = 4.dp)
                            )
                            Text(
                                text = "Ver Privacidad",
                                style = MaterialTheme.typography.labelSmall.copy(textDecoration = TextDecoration.Underline),
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier
                                    .clickable { showPrivacy = true }
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (errorMsg != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMsg!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Button(
                onClick = {
                    val clean = SafetyValidator.sanitizeUsername(username)
                    val year = birthYearInput.toIntOrNull()
                    when {
                        !SafetyValidator.isValidUsernameFormat(clean) ->
                            errorMsg = "Ingresa un nombre de usuario válido (3–24 caracteres)."
                        isUsernameAvailable == false ->
                            errorMsg = "Ese nombre de usuario no está disponible."
                        year == null || !SafetyValidator.isEligibleAge(year) ->
                            errorMsg = "Ingresa un año de nacimiento válido. Debes tener al menos 13 años para usar MURMUR."
                        !acceptedTerms ->
                            errorMsg = "Debes aceptar los Términos y Condiciones para crear tu cuenta."
                        else -> {
                            isSaving = true
                            errorMsg = null
                            scope.launch {
                                runCatching {
                                    userRepository.completeOnboarding(
                                        username = clean,
                                        displayName = displayName,
                                        bio = bio,
                                        birthYear = year
                                    )
                                }.onSuccess {
                                    isSaving = false
                                    onCompleted()
                                }.onFailure { err ->
                                    isSaving = false
                                    errorMsg = err.toFriendlySpanishError()
                                }
                            }
                        }
                    }
                },
                enabled = !isSaving,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("complete_onboarding_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "CREAR MI CUENTA",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    signOutFromMurmur(credentialManager, scope)
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Usar otra cuenta de Google")
            }
        }
    }

    if (showTerms) {
        LegalDocumentDialog(
            title = LegalContent.OFFICIAL_TERMS_TITLE,
            body = LegalContent.OFFICIAL_TERMS_BODY,
            onDismiss = { showTerms = false }
        )
    }

    if (showPrivacy) {
        LegalDocumentDialog(
            title = LegalContent.PRIVACY_POLICY_TITLE,
            body = LegalContent.PRIVACY_POLICY_BODY,
            onDismiss = { showPrivacy = false }
        )
    }
}
