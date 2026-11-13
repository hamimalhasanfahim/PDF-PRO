package com.example.ui.screens.documents

import android.net.Uri
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.db.PdfDocumentEntity
import com.example.data.local.db.ProcessingHistoryEntity
import com.example.data.local.db.RecentDocumentEntity
import com.example.data.pdf.FileStorageManager
import com.example.domain.model.StorageBreakdown
import com.example.ui.components.CenteredResponsiveContainer
import com.example.ui.components.DocumentItemRow
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.PdfToolTopBar
import com.example.ui.components.findActivity
import com.example.ui.localization.LocalAppStrings
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.SuccessEmerald
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    initialTab: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    activeDocuments: List<PdfDocumentEntity>,
    recentDocuments: List<RecentDocumentEntity>,
    favoriteDocuments: List<PdfDocumentEntity>,
    deletedDocuments: List<PdfDocumentEntity>,
    historyEntries: List<ProcessingHistoryEntity>,
    storageBreakdown: StorageBreakdown,
    fileStorageManager: FileStorageManager,
    onImportUri: (Uri) -> Unit,
    onOpenViewer: (Long, Boolean) -> Unit,
    onToggleFavorite: (PdfDocumentEntity) -> Unit,
    onRename: (PdfDocumentEntity, String) -> Unit,
    onExportUri: (PdfDocumentEntity, Uri) -> Unit,
    onShare: (PdfDocumentEntity) -> Unit,
    onMoveToTrash: (PdfDocumentEntity) -> Unit,
    onRestoreFromTrash: (PdfDocumentEntity) -> Unit,
    onDeletePermanently: (PdfDocumentEntity) -> Unit,
    onEmptyTrash: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 4)) }
    var actionTargetDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }
    var renameDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var exportTargetDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportUri(uri)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val doc = exportTargetDoc
        if (uri != null && doc != null) {
            onExportUri(doc, uri)
        }
        exportTargetDoc = null
    }

    val tabs = listOf(
        strings.allFilesTab,
        strings.recentTab,
        strings.favoritesTab,
        strings.trashTab,
        strings.historyTab
    )

    Column(modifier = modifier.fillMaxSize()) {
        PdfToolTopBar(
            title = strings.navDocuments,
            subtitle = "${storageBreakdown.documentCount} files • ${FileStorageManager.formatBytes(storageBreakdown.totalVaultBytes)}",
            actions = {
                IconButton(
                    onClick = { importLauncher.launch(arrayOf("application/pdf")) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("docs_import_button")
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = strings.importPdf)
                }
            }
        )

        PrimaryScrollableTabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { idx, label ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = { Text(label) }
                )
            }
        }

        CenteredResponsiveContainer(modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (selectedTab == 0) {
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            modifier = Modifier.fillMaxWidth().testTag("docs_search_input"),
                            placeholder = { Text("Search documents by title or filename...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }

                when (selectedTab) {
                    0 -> {
                        if (activeDocuments.isEmpty()) {
                            item {
                                EmptyStatePanel(
                                    icon = Icons.Default.PictureAsPdf,
                                    title = strings.emptyDocuments,
                                    subtitle = strings.emptyDocumentsTip,
                                    actionLabel = strings.importPdf,
                                    onAction = { importLauncher.launch(arrayOf("application/pdf")) }
                                )
                            }
                        } else {
                            items(activeDocuments, key = { it.id }) { doc ->
                                DocumentItemRow(
                                    doc = doc,
                                    onClick = { onOpenViewer(doc.id, false) },
                                    onToggleFavorite = { onToggleFavorite(doc) },
                                    onMoreClick = { actionTargetDoc = doc }
                                )
                            }
                        }
                    }

                    1 -> {
                        val docs = recentDocuments.mapNotNull { r -> activeDocuments.firstOrNull { it.id == r.documentId } }
                        if (docs.isEmpty()) {
                            item {
                                EmptyStatePanel(
                                    icon = Icons.Default.History,
                                    title = "No recently opened files",
                                    subtitle = "Open or create a PDF to see it here."
                                )
                            }
                        } else {
                            items(docs, key = { it.id }) { doc ->
                                DocumentItemRow(
                                    doc = doc,
                                    onClick = { onOpenViewer(doc.id, false) },
                                    onToggleFavorite = { onToggleFavorite(doc) },
                                    onMoreClick = { actionTargetDoc = doc }
                                )
                            }
                        }
                    }

                    2 -> {
                        if (favoriteDocuments.isEmpty()) {
                            item {
                                EmptyStatePanel(
                                    icon = Icons.Default.PictureAsPdf,
                                    title = "No favorite documents",
                                    subtitle = "Tap the heart icon on any PDF to bookmark it here."
                                )
                            }
                        } else {
                            items(favoriteDocuments, key = { it.id }) { doc ->
                                DocumentItemRow(
                                    doc = doc,
                                    onClick = { onOpenViewer(doc.id, false) },
                                    onToggleFavorite = { onToggleFavorite(doc) },
                                    onMoreClick = { actionTargetDoc = doc }
                                )
                            }
                        }
                    }

                    3 -> {
                        if (deletedDocuments.isEmpty()) {
                            item {
                                EmptyStatePanel(
                                    icon = Icons.Default.DeleteOutline,
                                    title = "Trash is empty",
                                    subtitle = "Deleted PDFs remain recoverable here until permanently erased."
                                )
                            }
                        } else {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${deletedDocuments.size} deleted • ${FileStorageManager.formatBytes(storageBreakdown.deletedBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TextButton(
                                        onClick = onEmptyTrash,
                                        colors = ButtonDefaults.textButtonColors(contentColor = ErrorCoral)
                                    ) {
                                        Text("Empty Trash")
                                    }
                                }
                            }
                            items(deletedDocuments, key = { it.id }) { doc ->
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(doc.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                            Text(
                                                "${doc.pageCount}p • ${FileStorageManager.formatBytes(doc.sizeBytes)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = { onRestoreFromTrash(doc) }) {
                                            Icon(Icons.Default.RestoreFromTrash, contentDescription = "Restore", tint = SuccessEmerald)
                                        }
                                        IconButton(onClick = { onDeletePermanently(doc) }) {
                                            Icon(Icons.Default.DeleteForever, contentDescription = "Delete permanently", tint = ErrorCoral)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    4 -> {
                        if (historyEntries.isEmpty()) {
                            item {
                                EmptyStatePanel(
                                    icon = Icons.Default.History,
                                    title = "No processing history",
                                    subtitle = "Creations, merges, compressions, and conversions are logged here."
                                )
                            }
                        } else {
                            items(historyEntries, key = { it.id }) { entry ->
                                val timeStr = remember(entry.timestamp) {
                                    SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(entry.timestamp))
                                }
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(entry.actionType, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                entry.status,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (entry.status == "SUCCESS") SuccessEmerald else ErrorCoral
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "${entry.inputName} → ${entry.outputName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (entry.details.isNotBlank()) {
                                            Text(
                                                text = "${entry.details} • $timeStr",
                                                style = MaterialTheme.typography.labelSmall,
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

    actionTargetDoc?.let { doc ->
        ModalBottomSheet(onDismissRequest = { actionTargetDoc = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(doc.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${doc.pageCount} pages • ${FileStorageManager.formatBytes(doc.sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))

                SheetActionRow(Icons.Default.Edit, "Open in Editor") {
                    actionTargetDoc = null
                    onOpenViewer(doc.id, true)
                }
                SheetActionRow(Icons.Default.DriveFileRenameOutline, "Rename File") {
                    renameText = doc.title
                    renameDoc = doc
                    actionTargetDoc = null
                }
                SheetActionRow(Icons.Default.SaveAlt, "Export via Files (SAF)") {
                    exportTargetDoc = doc
                    actionTargetDoc = null
                    exportLauncher.launch(doc.fileName)
                }
                SheetActionRow(Icons.Default.Share, strings.shareOutput) {
                    actionTargetDoc = null
                    onShare(doc)
                }
                SheetActionRow(Icons.Default.Print, strings.printOutput) {
                    actionTargetDoc = null
                    val act = context.findActivity()
                    if (act != null) fileStorageManager.printPdfFile(act, File(doc.localPath))
                }
                SheetActionRow(Icons.Default.DeleteOutline, "Move to Trash", tint = ErrorCoral) {
                    actionTargetDoc = null
                    onMoveToTrash(doc)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    renameDoc?.let { target ->
        AlertDialog(
            onDismissRequest = { renameDoc = null },
            title = { Text("Rename Document") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("New Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    onRename(target, renameText)
                    renameDoc = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameDoc = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SheetActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, color = tint)
        }
    }
}
