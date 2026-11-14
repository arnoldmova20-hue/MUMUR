package com.example.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.preferences.AppearanceSettings
import com.example.ui.theme.MurmurAccentTheme
import com.example.ui.theme.MurmurNavBarStyle

@Composable
fun AppearanceCustomizationDialog(
    settings: AppearanceSettings,
    onDarkThemeChange: (Boolean) -> Unit,
    onAccentThemeChange: (MurmurAccentTheme) -> Unit,
    onNavBarStyleChange: (MurmurNavBarStyle) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit = onConfirm
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column {
                            Text(
                                text = "Personaliza tu MURMUR",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Elige cómo se verá tu interfaz. Puedes cambiarlo cuando quieras.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 1. Modo Claro u Oscuro
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "1. Modo de pantalla",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ThemeModeCard(
                                label = "Modo oscuro",
                                icon = Icons.Default.DarkMode,
                                selected = settings.isDarkTheme,
                                previewBg = Color(0xFF090A0D),
                                previewText = Color(0xFFF5F5F7),
                                onClick = { onDarkThemeChange(true) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("appearance_mode_dark")
                            )
                            ThemeModeCard(
                                label = "Modo claro",
                                icon = Icons.Default.LightMode,
                                selected = !settings.isDarkTheme,
                                previewBg = Color(0xFFF7F7FA),
                                previewText = Color(0xFF12131A),
                                onClick = { onDarkThemeChange(false) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("appearance_mode_light")
                            )
                        }
                    }

                    // 2. Color de la interfaz
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "2. Color de la interfaz",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val rows = MurmurAccentTheme.entries.chunked(3)
                        rows.forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowItems.forEach { palette ->
                                    val selected = settings.accentTheme == palette
                                    val swatchColor = if (settings.isDarkTheme) palette.darkPrimary else palette.lightPrimary
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (selected) {
                                            swatchColor.copy(alpha = 0.16f)
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                        border = BorderStroke(
                                            width = if (selected) 2.dp else 1.dp,
                                            color = if (selected) swatchColor else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable { onAccentThemeChange(palette) }
                                            .testTag("appearance_color_${palette.key}")
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(swatchColor),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (selected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Seleccionado",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = palette.labelEs,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Distribución de la barra de navegación
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "3. Distribución de navegación",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            NavLayoutPreviewCard(
                                style = MurmurNavBarStyle.DOCKED,
                                selected = settings.navBarStyle == MurmurNavBarStyle.DOCKED,
                                onClick = { onNavBarStyleChange(MurmurNavBarStyle.DOCKED) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("appearance_nav_original")
                            )
                            NavLayoutPreviewCard(
                                style = MurmurNavBarStyle.FLOATING,
                                selected = settings.navBarStyle == MurmurNavBarStyle.FLOATING,
                                onClick = { onNavBarStyleChange(MurmurNavBarStyle.FLOATING) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("appearance_nav_floating")
                            )
                        }
                    }

                    Button(
                        onClick = onConfirm,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("appearance_confirm_button")
                    ) {
                        Text(
                            text = "APLICAR Y CONTINUAR",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeModeCard(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    previewBg: Color,
    previewText: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = previewBg,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else previewText,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = previewText,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun NavLayoutPreviewCard(
    style: MurmurNavBarStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val borderColor = if (selected) accent else MaterialTheme.colorScheme.outlineVariant
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Miniature phone outline showing Docked vs Floating iOS pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            ) {
                if (style == MurmurNavBarStyle.DOCKED) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(16.dp)
                            .background(accent.copy(alpha = 0.85f))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(50))
                            .background(accent.copy(alpha = 0.9f))
                    )
                }
            }
            Text(
                text = style.labelEs,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}
