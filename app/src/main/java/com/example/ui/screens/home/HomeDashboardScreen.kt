package com.example.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.db.PdfDocumentEntity
import com.example.data.local.db.RecentDocumentEntity
import com.example.data.pdf.FileStorageManager
import com.example.domain.model.PdfToolId
import com.example.domain.model.StorageBreakdown
import com.example.domain.model.UserPreferences
import com.example.ui.components.CenteredResponsiveContainer
import com.example.ui.components.DocumentItemRow
import com.example.ui.components.EmptyStatePanel
import com.example.ui.localization.AppStrings
import com.example.ui.localization.LocalAppStrings
import com.example.ui.theme.AccentCompress
import com.example.ui.theme.AccentConvert
import com.example.ui.theme.AccentCreate
import com.example.ui.theme.AccentOrganize
import com.example.ui.theme.AccentScan
import com.example.ui.theme.AccentSecurity
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandPurple

data class PdfToolDescriptor(
    val toolId: PdfToolId,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color
)

@Composable
fun rememberAllToolDescriptors(strings: AppStrings): List<PdfToolDescriptor> = remember(strings) {
    listOf(
        PdfToolDescriptor(PdfToolId.CREATE_TEXT, strings.createPdf, "Text, Bengali, Images, Blank", Icons.Default.AddCircleOutline, AccentCreate),
        PdfToolDescriptor(PdfToolId.SCAN_DOCUMENT, strings.scanDocument, "CameraX + Filters + Crop", Icons.Default.DocumentScanner, AccentScan),
        PdfToolDescriptor(PdfToolId.MERGE_PDF, strings.mergePdf, "Combine multiple PDFs", Icons.AutoMirrored.Filled.MergeType, AccentOrganize),
        PdfToolDescriptor(PdfToolId.COMPRESS_PDF, strings.compressPdf, "Shrink size & compare", Icons.Default.Compress, AccentCompress),
        PdfToolDescriptor(PdfToolId.EDIT_PDF, strings.editPdf, "Annotate, draw, sign, text", Icons.Default.Edit, AccentCreate),
        PdfToolDescriptor(PdfToolId.PDF_TO_IMAGES, strings.convertPdf, "JPG, PNG, DOCX, XLSX, PPTX", Icons.Default.SwapHoriz, AccentConvert),
        PdfToolDescriptor(PdfToolId.SPLIT_PDF, strings.splitPdf, "Split by ranges or pages", Icons.AutoMirrored.Filled.CallSplit, AccentOrganize),
        PdfToolDescriptor(PdfToolId.ORGANIZE_PAGES, strings.organizePages, "Reorder, rotate, duplicate", Icons.Default.ViewCarousel, AccentOrganize),
        PdfToolDescriptor(PdfToolId.OCR_EXTRACT, strings.ocrText, "ML Kit text recognition", Icons.Default.TextFields, AccentScan),
        PdfToolDescriptor(PdfToolId.PROTECT_PDF, strings.protectPdf, "AES password & permissions", Icons.Default.Lock, AccentSecurity),
        PdfToolDescriptor(PdfToolId.UNLOCK_PDF, strings.unlockPdf, "Authorized password removal", Icons.Default.LockOpen, AccentSecurity),
        PdfToolDescriptor(PdfToolId.WATERMARK_PDF, strings.watermarkPdf, "Custom text stamp", Icons.Default.WaterDrop, AccentSecurity),
        PdfToolDescriptor(PdfToolId.SIGN_PDF, strings.signPdf, "Handwritten signature + SHA256", Icons.Default.Draw, AccentSecurity),
        PdfToolDescriptor(PdfToolId.COMPARE_PDF, strings.comparePdf, "Page-by-page diff check", Icons.AutoMirrored.Filled.CompareArrows, BrandBlue),
        PdfToolDescriptor(PdfToolId.METADATA_BOOKMARKS, strings.metadataBookmarks, "Edit tags & outline links", Icons.Default.Bookmarks, BrandPurple),
        PdfToolDescriptor(PdfToolId.PAGE_NUMBERS, strings.pageNumbers, "Stamp page numbers", Icons.Default.FormatListNumbered, AccentCreate),
        PdfToolDescriptor(PdfToolId.EXTRACT_IMAGES, strings.extractImages, "Pull embedded PNG photos", Icons.Default.PhotoLibrary, AccentConvert),
        PdfToolDescriptor(PdfToolId.SEARCH_PDF, strings.pdfSearch, "Find keywords across pages", Icons.Default.Search, BrandBlue),
        PdfToolDescriptor(PdfToolId.CREATE_IMAGES, "Images to PDF", "Batch photos into PDF", Icons.Default.Image, AccentCreate),
        PdfToolDescriptor(PdfToolId.CREATE_BLANK, "Blank PDF", "Custom page templates", Icons.Default.NoteAdd, AccentCreate)
    )
}

@Composable
fun HomeDashboardScreen(
    preferences: UserPreferences,
    recentDocuments: List<RecentDocumentEntity>,
    recentlyCreated: List<PdfDocumentEntity>,
    allDocuments: List<PdfDocumentEntity>,
    storageBreakdown: StorageBreakdown,
    onToolClick: (PdfToolId) -> Unit,
    onOpenDocument: (Long) -> Unit,
    onSeeAllTools: () -> Unit,
    onSeeAllDocuments: () -> Unit,
    onOpenSettings: () -> Unit,
    onImportPdfClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val allTools = rememberAllToolDescriptors(strings)
    var searchQuery by remember { mutableStateOf("") }

    val quickActionIds = remember {
        listOf(
            PdfToolId.CREATE_TEXT,
            PdfToolId.SCAN_DOCUMENT,
            PdfToolId.MERGE_PDF,
            PdfToolId.COMPRESS_PDF,
            PdfToolId.EDIT_PDF,
            PdfToolId.PDF_TO_IMAGES
        )
    }
    val quickActions = remember(allTools) {
        quickActionIds.mapNotNull { id -> allTools.firstOrNull { it.toolId == id } }
    }
    val favoriteTools = remember(allTools, preferences.favoriteToolIds) {
        allTools.filter { it.toolId.id in preferences.favoriteToolIds }
    }
    val filteredTools = remember(allTools, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else allTools.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.subtitle.contains(searchQuery, ignoreCase = true)
        }
    }

    CenteredResponsiveContainer(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Top Brand Header + Settings Shortcut
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Brush.linearGradient(listOf(BrandPurple, BrandBlue))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = strings.appName,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = strings.appName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = strings.tagline,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalIconButton(
                            onClick = onImportPdfClick,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("home_import_button")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = strings.importPdf)
                        }
                        FilledTonalIconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("home_settings_button")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = strings.settingsTitle)
                        }
                    }
                }
            }

            // Hero Welcome Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().height(148.dp)) {
                        Image(
                            painter = painterResource(id = R.drawable.img_hero_banner),
                            contentDescription = strings.welcomeTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color(0xD91A1042),
                                            Color(0x993478F6)
                                        )
                                    )
                                )
                                .padding(20.dp)
                        ) {
                            Column(
                                modifier = Modifier.align(Alignment.CenterStart)
                            ) {
                                Text(
                                    text = strings.welcomeTitle,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = strings.welcomeSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.9f),
                                    maxLines = 2
                                )
                                Spacer(Modifier.height(10.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.2f),
                                    modifier = Modifier.clickable { onToolClick(PdfToolId.CREATE_TEXT) }
                                ) {
                                    Text(
                                        text = "+ ${strings.createPdf}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Search PDF Tools
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_tool_search_input"),
                    placeholder = { Text(strings.searchToolsHint) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )

                if (filteredTools.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        filteredTools.forEach { tool ->
                            ToolListTile(tool = tool, onClick = { onToolClick(tool.toolId) })
                        }
                    }
                }
            }

            // Quick Actions (6 requested actions in crisp 2-column or 3-column grid)
            item {
                SectionHeaderRow(title = strings.quickActions, actionLabel = strings.allPdfTools, onAction = onSeeAllTools)
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    quickActions.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            pair.forEach { tool ->
                                QuickActionGridCard(
                                    tool = tool,
                                    onClick = { onToolClick(tool.toolId) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            // Favorite Tools
            if (favoriteTools.isNotEmpty()) {
                item {
                    SectionHeaderRow(title = strings.favoriteTools)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(favoriteTools, key = { it.toolId.id }) { tool ->
                            Surface(
                                modifier = Modifier
                                    .width(142.dp)
                                    .clickable { onToolClick(tool.toolId) }
                                    .testTag("fav_tool_${tool.toolId.id}"),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(tool.accent.copy(alpha = 0.14f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(tool.icon, contentDescription = null, tint = tool.accent, modifier = Modifier.size(18.dp))
                                        }
                                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        text = tool.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Storage Information Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Storage, contentDescription = null, tint = BrandPurple)
                                Spacer(Modifier.width(8.dp))
                                Text(strings.storageOverview, style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                text = "${storageBreakdown.documentCount} PDFs",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        val totalUsed = storageBreakdown.totalVaultBytes + storageBreakdown.deletedBytes + storageBreakdown.cacheBytes
                        val denominator = (totalUsed + storageBreakdown.deviceFreeBytes).coerceAtLeast(1L)
                        LinearProgressIndicator(
                            progress = { (totalUsed.toFloat() / denominator.toFloat()).coerceIn(0.02f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Vault: ${FileStorageManager.formatBytes(storageBreakdown.totalVaultBytes)} • Cache: ${FileStorageManager.formatBytes(storageBreakdown.cacheBytes)} • Free: ${FileStorageManager.formatBytes(storageBreakdown.deviceFreeBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Recently Created Files
            if (recentlyCreated.isNotEmpty()) {
                item {
                    SectionHeaderRow(
                        title = strings.recentlyCreated,
                        actionLabel = strings.navDocuments,
                        onAction = onSeeAllDocuments
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(recentlyCreated, key = { it.id }) { doc ->
                            Surface(
                                modifier = Modifier
                                    .width(172.dp)
                                    .clickable { onOpenDocument(doc.id) },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Description, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = doc.originTag.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = BrandPurple
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = doc.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${doc.pageCount}p • ${FileStorageManager.formatBytes(doc.sizeBytes)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Recent Documents
            item {
                SectionHeaderRow(
                    title = strings.recentDocuments,
                    actionLabel = strings.navDocuments,
                    onAction = onSeeAllDocuments
                )
            }

            if (recentDocuments.isEmpty() && allDocuments.isEmpty()) {
                item {
                    EmptyStatePanel(
                        icon = Icons.Default.PictureAsPdf,
                        title = strings.emptyDocuments,
                        subtitle = strings.emptyDocumentsTip,
                        actionLabel = strings.createPdf,
                        onAction = { onToolClick(PdfToolId.CREATE_TEXT) }
                    )
                }
            } else {
                val displayDocs = if (recentDocuments.isNotEmpty()) {
                    recentDocuments.mapNotNull { rec -> allDocuments.firstOrNull { it.id == rec.documentId } }
                } else {
                    allDocuments.take(6)
                }
                items(displayDocs.take(6), key = { it.id }) { doc ->
                    DocumentItemRow(
                        doc = doc,
                        onClick = { onOpenDocument(doc.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun QuickActionGridCard(
    tool: PdfToolDescriptor,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag("quick_action_${tool.toolId.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tool.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(tool.icon, contentDescription = tool.title, tint = tool.accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tool.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = tool.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ToolListTile(
    tool: PdfToolDescriptor,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("tool_tile_${tool.toolId.id}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tool.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(tool.icon, contentDescription = tool.title, tint = tool.accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(tool.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    tool.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            trailing?.invoke()
        }
    }
}
