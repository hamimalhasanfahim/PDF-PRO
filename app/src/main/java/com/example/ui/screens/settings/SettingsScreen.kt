package com.example.ui.screens.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.pdf.FileStorageManager
import com.example.domain.model.AppLanguage
import com.example.domain.model.CompressionLevel
import com.example.domain.model.PageOrientation
import com.example.domain.model.PageSizeOption
import com.example.domain.model.StorageBreakdown
import com.example.domain.model.ThemeMode
import com.example.domain.model.UserPreferences
import com.example.ui.components.CenteredResponsiveContainer
import com.example.ui.components.PdfToolTopBar
import com.example.ui.localization.LocalAppStrings
import com.example.ui.theme.ErrorCoral
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    storageBreakdown: StorageBreakdown,
    onSetTheme: (ThemeMode) -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetPageSize: (PageSizeOption) -> Unit,
    onSetOrientation: (PageOrientation) -> Unit,
    onSetCompression: (CompressionLevel) -> Unit,
    onSetDefaultFolder: (String) -> Unit,
    onSetFontSize: (Float) -> Unit,
    onClearCache: () -> Unit,
    onClearHistory: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            onSetDefaultFolder(uri.toString())
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        PdfToolTopBar(
            title = strings.settingsTitle,
            subtitle = strings.appName,
            onBack = onBack
        )

        CenteredResponsiveContainer(modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Language
                item {
                    SettingsSectionCard(
                        icon = Icons.Default.Language,
                        title = strings.languageLabel
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppLanguage.entries.forEach { lang ->
                                FilterChip(
                                    selected = preferences.language == lang,
                                    onClick = { onSetLanguage(lang) },
                                    label = { Text(lang.displayName) },
                                    modifier = Modifier.testTag("settings_lang_${lang.code}")
                                )
                            }
                        }
                    }
                }

                // Theme
                item {
                    SettingsSectionCard(
                        icon = Icons.Default.Palette,
                        title = strings.themeLabel
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = preferences.themeMode == mode,
                                    onClick = { onSetTheme(mode) },
                                    label = {
                                        Text(
                                            when (mode) {
                                                ThemeMode.SYSTEM -> "System"
                                                ThemeMode.LIGHT -> "Light"
                                                ThemeMode.DARK -> "Dark"
                                            }
                                        )
                                    },
                                    modifier = Modifier.testTag("settings_theme_${mode.key}")
                                )
                            }
                        }
                    }
                }

                // Document Creation Defaults
                item {
                    SettingsSectionCard(
                        icon = Icons.Default.Folder,
                        title = "Document & Export Defaults"
                    ) {
                        Text(strings.defaultPageSizeLabel, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                PageSizeOption.A3,
                                PageSizeOption.A4,
                                PageSizeOption.A5,
                                PageSizeOption.LETTER,
                                PageSizeOption.LEGAL
                            ).forEach { size ->
                                FilterChip(
                                    selected = preferences.defaultPageSize == size,
                                    onClick = { onSetPageSize(size) },
                                    label = { Text(size.label) }
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(strings.defaultOrientationLabel, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PageOrientation.entries.forEach { ori ->
                                FilterChip(
                                    selected = preferences.defaultOrientation == ori,
                                    onClick = { onSetOrientation(ori) },
                                    label = { Text(ori.label) }
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(strings.defaultCompressionLabel, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                CompressionLevel.HIGH,
                                CompressionLevel.MEDIUM,
                                CompressionLevel.LOW
                            ).forEach { lvl ->
                                FilterChip(
                                    selected = preferences.defaultCompression == lvl,
                                    onClick = { onSetCompression(lvl) },
                                    label = { Text(lvl.name.lowercase().replaceFirstChar { it.uppercase() }) }
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(
                            "${strings.defaultFontSizeLabel}: ${preferences.defaultFontSize.roundToInt()} pt",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Slider(
                            value = preferences.defaultFontSize,
                            onValueChange = onSetFontSize,
                            valueRange = 10f..28f,
                            steps = 8
                        )

                        Spacer(Modifier.height(8.dp))
                        Text(strings.defaultSaveFolderLabel, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = preferences.defaultSaveFolderUri.ifBlank { "Ask via Files picker on export (SAF)" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { folderPickerLauncher.launch(null) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("Choose Default Folder")
                        }
                    }
                }

                // Storage & History Management
                item {
                    SettingsSectionCard(
                        icon = Icons.Default.CleaningServices,
                        title = strings.storageManagementLabel
                    ) {
                        Text(
                            text = "App Vault: ${FileStorageManager.formatBytes(storageBreakdown.totalVaultBytes)} (${storageBreakdown.documentCount} files)\n" +
                                "Trash: ${FileStorageManager.formatBytes(storageBreakdown.deletedBytes)} (${storageBreakdown.deletedCount} files)\n" +
                                "Temporary Cache: ${FileStorageManager.formatBytes(storageBreakdown.cacheBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onClearCache,
                                modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                            ) {
                                Text("Clean Temp Files")
                            }
                            OutlinedButton(
                                onClick = onClearHistory,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorCoral),
                                modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Clear History")
                            }
                        }
                    }
                }

                // Privacy & Security
                item {
                    SettingsSectionCard(
                        icon = Icons.Default.Security,
                        title = strings.privacyTitle
                    ) {
                        Text(
                            text = strings.privacyDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // About Application
                item {
                    SettingsSectionCard(
                        icon = Icons.Default.Info,
                        title = strings.aboutTitle
                    ) {
                        Text(
                            text = "${strings.appName} — ${strings.tagline}",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${strings.versionLabel}: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                                "Engine: PdfBox-Android + Android PdfRenderer + CameraX + ML Kit OCR",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
