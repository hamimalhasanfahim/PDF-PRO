package com.example.ui.screens.utilities

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.db.PdfDocumentEntity
import com.example.data.pdf.FileStorageManager
import com.example.ui.components.CenteredResponsiveContainer
import com.example.ui.components.DocumentSelectionCard
import com.example.ui.components.ErrorBannerCard
import com.example.ui.components.PdfToolTopBar
import com.example.ui.components.ProcessingProgressBanner
import com.example.ui.components.ResultOutputCard
import com.example.ui.components.ToolInstructionCard
import com.example.ui.components.findActivity
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandPurple
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.SuccessEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UtilitiesScreen(
    initialTab: String,
    docId: Long,
    availableDocs: List<PdfDocumentEntity>,
    viewModel: UtilitiesViewModel,
    fileStorageManager: FileStorageManager,
    onOpenPreview: (Long) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(docId) {
        if (docId > 0L) viewModel.selectActiveDocById(docId)
    }

    var selectedTab by remember(initialTab) {
        mutableIntStateOf(
            when (initialTab) {
                "metadata" -> 1
                "numbers" -> 2
                "search" -> 3
                else -> 0
            }
        )
    }

    val tabs = listOf("Compare", "Metadata & Bookmarks", "Page Numbers", "Search")

    Column(modifier = Modifier.fillMaxSize()) {
        PdfToolTopBar(
            title = "PDF Utilities",
            subtitle = "Compare, inspect metadata, bookmarks, numbering & search",
            onBack = onBack
        )

        PrimaryScrollableTabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { idx, label ->
                Tab(selected = selectedTab == idx, onClick = { selectedTab = idx }, text = { Text(label) })
            }
        }

        CenteredResponsiveContainer(modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (selectedTab) {
                    // COMPARE
                    0 -> {
                        item {
                            ToolInstructionCard(
                                icon = Icons.AutoMirrored.Filled.CompareArrows,
                                title = "Compare Two PDFs",
                                description = "Performs a page-by-page text diff highlighting added and removed lines.",
                                accentColor = BrandBlue
                            )
                        }
                        item {
                            DocumentSelectionCard(
                                selectedDoc = state.leftDoc,
                                availableDocs = availableDocs,
                                label = "Original PDF (A)",
                                onSelectExisting = viewModel::selectLeftDoc,
                                onImportUri = viewModel::importLeft
                            )
                        }
                        item {
                            DocumentSelectionCard(
                                selectedDoc = state.rightDoc,
                                availableDocs = availableDocs,
                                label = "Modified PDF (B)",
                                onSelectExisting = viewModel::selectRightDoc,
                                onImportUri = viewModel::importRight
                            )
                        }
                        item {
                            Button(
                                onClick = viewModel::comparePdfs,
                                enabled = !state.isProcessing,
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) { Text("Compare Documents") }
                        }
                        state.diffResult?.let { diff ->
                            items(diff.pageDiffs) { pageDiff ->
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (pageDiff.isIdentical) SuccessEmerald.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "Page ${pageDiff.pageNumber}: ${if (pageDiff.isIdentical) "Identical" else "Differences Found"}",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (pageDiff.isIdentical) SuccessEmerald else BrandPurple
                                        )
                                        pageDiff.removedLines.take(8).forEach { line ->
                                            Text("- $line", style = MaterialTheme.typography.bodySmall, color = ErrorCoral)
                                        }
                                        pageDiff.addedLines.take(8).forEach { line ->
                                            Text("+ $line", style = MaterialTheme.typography.bodySmall, color = SuccessEmerald)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // METADATA & BOOKMARKS
                    1 -> {
                        item {
                            ToolInstructionCard(
                                icon = Icons.Default.Bookmarks,
                                title = "Metadata Viewer, Editor & Bookmarks",
                                description = "Inspect file info, edit Title/Author/Subject/Keywords, and manage PDF outline bookmarks.",
                                accentColor = BrandPurple
                            )
                        }
                        item {
                            DocumentSelectionCard(
                                selectedDoc = state.activeDoc,
                                availableDocs = availableDocs,
                                onSelectExisting = viewModel::selectActiveDoc,
                                onImportUri = viewModel::importActiveDoc
                            )
                        }
                        if (state.activeDoc != null) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            "Pages: ${state.metadata.pageCount} • Size: ${FileStorageManager.formatBytes(state.metadata.fileSizeBytes)} • Producer: ${state.metadata.producer.ifBlank { "Standard" }}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        OutlinedTextField(
                                            value = state.metadata.title,
                                            onValueChange = { v -> viewModel.updateMetadata { it.copy(title = v) } },
                                            label = { Text("Title") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = state.metadata.author,
                                            onValueChange = { v -> viewModel.updateMetadata { it.copy(author = v) } },
                                            label = { Text("Author") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = state.metadata.subject,
                                            onValueChange = { v -> viewModel.updateMetadata { it.copy(subject = v) } },
                                            label = { Text("Subject") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = state.metadata.keywords,
                                            onValueChange = { v -> viewModel.updateMetadata { it.copy(keywords = v) } },
                                            label = { Text("Keywords") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                            item {
                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Bookmarks / Outlines (${state.bookmarks.size})", style = MaterialTheme.typography.titleSmall)
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = state.newBookmarkTitle,
                                                onValueChange = viewModel::updateNewBookmarkTitle,
                                                label = { Text("Bookmark title") },
                                                singleLine = true,
                                                modifier = Modifier.weight(2f)
                                            )
                                            OutlinedTextField(
                                                value = state.newBookmarkPage.toString(),
                                                onValueChange = { it.toIntOrNull()?.let(viewModel::updateNewBookmarkPage) },
                                                label = { Text("Page") },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f)
                                            )
                                            FilledTonalButton(onClick = viewModel::addBookmarkItem) {
                                                Icon(Icons.Default.Add, contentDescription = "Add Bookmark")
                                            }
                                        }
                                        state.bookmarks.forEachIndexed { idx, bm ->
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("${bm.title} (Page ${bm.pageIndex + 1})", modifier = Modifier.weight(1f))
                                                    IconButton(onClick = { viewModel.removeBookmarkAt(idx) }) {
                                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Remove bookmark")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            item {
                                Button(
                                    onClick = viewModel::saveMetadataAndBookmarks,
                                    enabled = !state.isProcessing,
                                    modifier = Modifier.fillMaxWidth().height(52.dp)
                                ) { Text("Save Metadata & Bookmarks") }
                            }
                        }
                    }

                    // PAGE NUMBERS
                    2 -> {
                        item {
                            ToolInstructionCard(
                                icon = Icons.Default.FormatListNumbered,
                                title = "Add Page Numbering",
                                description = "Stamps formatted page numbers onto every page of an existing PDF.",
                                accentColor = BrandPurple
                            )
                        }
                        item {
                            DocumentSelectionCard(
                                selectedDoc = state.activeDoc,
                                availableDocs = availableDocs,
                                onSelectExisting = viewModel::selectActiveDoc,
                                onImportUri = viewModel::importActiveDoc
                            )
                        }
                        item {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = state.pageNumPrefix,
                                        onValueChange = viewModel::updatePageNumPrefix,
                                        label = { Text("Prefix (e.g. Page )") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = state.pageNumStart.toString(),
                                        onValueChange = { it.toIntOrNull()?.let(viewModel::updatePageNumStart) },
                                        label = { Text("Start Number") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(
                                            selected = state.pageNumAtBottom,
                                            onClick = { viewModel.updatePageNumAtBottom(true) },
                                            label = { Text("Bottom Center") }
                                        )
                                        FilterChip(
                                            selected = !state.pageNumAtBottom,
                                            onClick = { viewModel.updatePageNumAtBottom(false) },
                                            label = { Text("Top Center") }
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            Button(
                                onClick = viewModel::applyPageNumbers,
                                enabled = state.activeDoc != null && !state.isProcessing,
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) { Text("Stamp Page Numbers") }
                        }
                    }

                    // PDF SEARCH
                    3 -> {
                        item {
                            ToolInstructionCard(
                                icon = Icons.Default.Search,
                                title = "Full-Text PDF Search",
                                description = "Searches across every page of the selected PDF and lists matching snippets.",
                                accentColor = BrandBlue
                            )
                        }
                        item {
                            DocumentSelectionCard(
                                selectedDoc = state.activeDoc,
                                availableDocs = availableDocs,
                                onSelectExisting = viewModel::selectActiveDoc,
                                onImportUri = viewModel::importActiveDoc
                            )
                        }
                        item {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = state.searchQuery,
                                    onValueChange = viewModel::updateSearchQuery,
                                    label = { Text("Keyword") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(onClick = viewModel::searchInsideActivePdf) { Text("Search") }
                            }
                        }
                        items(state.searchMatches) { m ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Page ${m.pageIndex + 1} • ${m.matchCountOnPage} matches", style = MaterialTheme.typography.titleSmall, color = BrandPurple)
                                    Spacer(Modifier.height(4.dp))
                                    Text(m.snippet, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                item {
                    ProcessingProgressBanner(isVisible = state.isProcessing, message = "Processing...")
                    ErrorBannerCard(errorMessage = state.errorMessage, onDismiss = viewModel::clearError)
                }

                val outFile = state.outputFile
                val outDoc = state.outputDoc
                if (outFile != null && outDoc != null) {
                    item {
                        ResultOutputCard(
                            outputFile = outFile,
                            title = "Updated PDF Ready",
                            summaryLine = "${outDoc.pageCount} pages",
                            onExportToUri = viewModel::exportToUri,
                            onShare = viewModel::shareOutput,
                            onPrint = {
                                context.findActivity()?.let { fileStorageManager.printPdfFile(it, outFile) }
                            },
                            onOpenPreview = { onOpenPreview(outDoc.id) }
                        )
                    }
                }
            }
        }
    }
}
