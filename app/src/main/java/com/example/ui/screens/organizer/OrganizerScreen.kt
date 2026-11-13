package com.example.ui.screens.organizer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
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
import com.example.ui.theme.AccentOrganize
import com.example.ui.theme.BrandPurple
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizerScreen(
    initialTool: String,
    docId: Long,
    availableDocs: List<PdfDocumentEntity>,
    viewModel: OrganizerViewModel,
    fileStorageManager: FileStorageManager,
    onOpenPreview: (Long) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(docId) {
        if (docId > 0L) viewModel.selectDocumentById(docId)
    }

    var selectedTab by remember(initialTool) {
        mutableIntStateOf(
            when (initialTool) {
                "split" -> 1
                "organize" -> 2
                "extract_images" -> 3
                else -> 0
            }
        )
    }

    val mergeImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importToMergeQueue(uri)
    }

    val tabs = listOf("Merge", "Split", "Pages", "Images")

    Column(modifier = Modifier.fillMaxSize()) {
        PdfToolTopBar(
            title = "PDF Organizer",
            subtitle = "Merge, split, rearrange, rotate, extract & duplicate",
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
                    // MERGE
                    0 -> {
                        item {
                            ToolInstructionCard(
                                icon = Icons.AutoMirrored.Filled.MergeType,
                                title = "Merge Multiple PDFs",
                                description = "Add 2 or more PDF files from your Vault or device, reorder them, and combine into a single PDF.",
                                accentColor = AccentOrganize
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { mergeImportLauncher.launch(arrayOf("application/pdf")) },
                                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Default.FileOpen, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Import PDF")
                                }
                            }
                            if (availableDocs.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text("Tap from Vault to add:", style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.height(6.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(availableDocs, key = { it.id }) { doc ->
                                        FilledTonalButton(onClick = { viewModel.addToMergeQueue(doc) }) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(doc.title.take(18))
                                        }
                                    }
                                }
                            }
                        }

                        if (state.mergeQueue.isNotEmpty()) {
                            item {
                                Text("Merge Order (${state.mergeQueue.size} files)", style = MaterialTheme.typography.titleSmall)
                            }
                            items(state.mergeQueue.indices.toList(), key = { idx -> state.mergeQueue[idx].id }) { idx ->
                                val item = state.mergeQueue[idx]
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("${idx + 1}.", style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(26.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                            Text("${item.pageCount}p • ${FileStorageManager.formatBytes(item.sizeBytes)}", style = MaterialTheme.typography.labelSmall)
                                        }
                                        IconButton(onClick = { viewModel.moveMergeItem(idx, -1) }, enabled = idx > 0) {
                                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move up")
                                        }
                                        IconButton(onClick = { viewModel.moveMergeItem(idx, 1) }, enabled = idx < state.mergeQueue.lastIndex) {
                                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move down")
                                        }
                                        IconButton(onClick = { viewModel.removeFromMergeQueue(item.id) }) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Remove")
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Button(
                                onClick = viewModel::mergeQueue,
                                enabled = state.mergeQueue.size >= 2 && !state.isProcessing,
                                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("merge_execute_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.MergeType, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Merge ${state.mergeQueue.size} PDFs")
                            }
                        }
                    }

                    // SPLIT
                    1 -> {
                        item {
                            ToolInstructionCard(
                                icon = Icons.AutoMirrored.Filled.CallSplit,
                                title = "Split PDF into Multiple Files",
                                description = "Split by custom ranges (e.g. 1-3, 4-7) or split every N pages.",
                                accentColor = AccentOrganize
                            )
                        }
                        item {
                            DocumentSelectionCard(
                                selectedDoc = state.selectedDoc,
                                availableDocs = availableDocs,
                                onSelectExisting = viewModel::selectDocumentForPages,
                                onImportUri = viewModel::importAndSelectDocument
                            )
                        }
                        if (state.selectedDoc != null) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("Split by Page Ranges", style = MaterialTheme.typography.titleSmall)
                                        OutlinedTextField(
                                            value = state.splitRangesSpec,
                                            onValueChange = viewModel::updateSplitRanges,
                                            label = { Text("Ranges (e.g. 1-2, 3-5)") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth().testTag("split_ranges_input")
                                        )
                                        Button(
                                            onClick = viewModel::splitByRanges,
                                            modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                                        ) { Text("Split by Ranges") }

                                        Spacer(Modifier.height(6.dp))
                                        Text("Or Split Every N Pages", style = MaterialTheme.typography.titleSmall)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = state.splitEveryN.toString(),
                                                onValueChange = { it.toIntOrNull()?.let(viewModel::updateSplitEveryN) },
                                                label = { Text("Pages per part") },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f)
                                            )
                                            FilledTonalButton(
                                                onClick = viewModel::splitEveryN,
                                                modifier = Modifier.minimumInteractiveComponentSize()
                                            ) { Text("Split Every ${state.splitEveryN}") }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ORGANIZE PAGES
                    2 -> {
                        item {
                            ToolInstructionCard(
                                icon = Icons.Default.ViewCarousel,
                                title = "Visual Page Organizer",
                                description = "Tap thumbnails to select pages, long-press & drag or use arrows to reorder, then extract, delete, rotate, duplicate, reverse or insert blanks.",
                                accentColor = AccentOrganize
                            )
                        }
                        item {
                            DocumentSelectionCard(
                                selectedDoc = state.selectedDoc,
                                availableDocs = availableDocs,
                                onSelectExisting = viewModel::selectDocumentForPages,
                                onImportUri = viewModel::importAndSelectDocument
                            )
                        }
                        if (state.pageTiles.isNotEmpty()) {
                            item {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(state.pageTiles.indices.toList(), key = { idx -> "${state.pageTiles[idx].originalZeroIndex}_$idx" }) { idx ->
                                        val tile = state.pageTiles[idx]
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .width(114.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .border(
                                                    width = if (tile.isSelected) 2.5.dp else 1.dp,
                                                    color = if (tile.isSelected) BrandPurple else MaterialTheme.colorScheme.outlineVariant,
                                                    shape = RoundedCornerShape(14.dp)
                                                )
                                                .background(MaterialTheme.colorScheme.surface)
                                                .clickable { viewModel.toggleTileSelection(idx) }
                                                .pointerInput(idx, state.pageTiles.size) {
                                                    detectDragGesturesAfterLongPress { change, dragAmount ->
                                                        change.consume()
                                                        if (dragAmount.x > 24f && idx < state.pageTiles.lastIndex) {
                                                            viewModel.reorderPageTile(idx, idx + 1)
                                                        } else if (dragAmount.x < -24f && idx > 0) {
                                                            viewModel.reorderPageTile(idx, idx - 1)
                                                        }
                                                    }
                                                }
                                                .padding(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(0.72f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.White)
                                            ) {
                                                tile.thumbnail?.let { bmp ->
                                                    Image(
                                                        bitmap = bmp.asImageBitmap(),
                                                        contentDescription = "Page ${tile.originalZeroIndex + 1}",
                                                        contentScale = ContentScale.Fit,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                                if (tile.isSelected) {
                                                    Icon(
                                                        Icons.Default.CheckCircle,
                                                        contentDescription = "Selected",
                                                        tint = BrandPurple,
                                                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp)
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            Text("Page ${tile.originalZeroIndex + 1}", style = MaterialTheme.typography.labelSmall)
                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                IconButton(
                                                    onClick = { viewModel.reorderPageTile(idx, idx - 1) },
                                                    enabled = idx > 0,
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move left", modifier = Modifier.size(16.dp))
                                                }
                                                IconButton(
                                                    onClick = { viewModel.reorderPageTile(idx, idx + 1) },
                                                    enabled = idx < state.pageTiles.lastIndex,
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move right", modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = viewModel::applyReorderedPages,
                                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                                        ) { Text("Save Order") }
                                        FilledTonalButton(
                                            onClick = viewModel::extractSelectedPages,
                                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                                        ) { Text("Extract Selected") }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilledTonalButton(
                                            onClick = { viewModel.rotatePages(90) },
                                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                                        ) {
                                            Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Rotate 90°")
                                        }
                                        FilledTonalButton(
                                            onClick = viewModel::duplicateSelectedPages,
                                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Duplicate")
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = viewModel::reverseAllPages,
                                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                                        ) {
                                            Icon(Icons.Default.FlipCameraAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Reverse Order")
                                        }
                                        OutlinedButton(
                                            onClick = viewModel::deleteSelectedPages,
                                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Delete Selected")
                                        }
                                    }

                                    // Insert Blank Pages
                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = state.insertAfterPage.toString(),
                                                onValueChange = { it.toIntOrNull()?.let(viewModel::updateInsertAfterPage) },
                                                label = { Text("After page") },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f)
                                            )
                                            OutlinedTextField(
                                                value = state.blankInsertCount.toString(),
                                                onValueChange = { it.toIntOrNull()?.let(viewModel::updateBlankInsertCount) },
                                                label = { Text("Blanks") },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f)
                                            )
                                            FilledTonalButton(onClick = viewModel::insertBlankPages) {
                                                Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Insert")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // EXTRACT IMAGES
                    3 -> {
                        item {
                            ToolInstructionCard(
                                icon = Icons.Default.PhotoLibrary,
                                title = "Extract Embedded Images",
                                description = "Extracts embedded raster images inside the PDF into PNG files.",
                                accentColor = AccentOrganize
                            )
                        }
                        item {
                            DocumentSelectionCard(
                                selectedDoc = state.selectedDoc,
                                availableDocs = availableDocs,
                                onSelectExisting = viewModel::selectDocumentForPages,
                                onImportUri = viewModel::importAndSelectDocument
                            )
                        }
                        item {
                            Button(
                                onClick = viewModel::extractEmbeddedImages,
                                enabled = state.selectedDoc != null && !state.isProcessing,
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) {
                                Text("Extract Images from PDF")
                            }
                        }
                        if (state.extractedImages.isNotEmpty()) {
                            item {
                                Text("Extracted ${state.extractedImages.size} Images", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(state.extractedImages) { imgFile ->
                                        Card(shape = RoundedCornerShape(14.dp)) {
                                            Column(
                                                modifier = Modifier.width(120.dp).padding(8.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                AsyncImage(
                                                    model = imgFile,
                                                    contentDescription = imgFile.name,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(10.dp))
                                                )
                                                Spacer(Modifier.height(6.dp))
                                                FilledTonalButton(
                                                    onClick = { viewModel.shareImage(imgFile) },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Share", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    ProcessingProgressBanner(isVisible = state.isProcessing, message = "Organizing PDF...")
                    ErrorBannerCard(errorMessage = state.errorMessage, onDismiss = viewModel::clearError)
                }

                items(state.outputDocs, key = { it.id }) { doc ->
                    val f = File(doc.localPath)
                    ResultOutputCard(
                        outputFile = f,
                        title = "Output Ready",
                        summaryLine = "${doc.pageCount} pages",
                        onExportToUri = { uri -> viewModel.exportDocToUri(doc, uri) },
                        onShare = { viewModel.shareDoc(doc) },
                        onPrint = { context.findActivity()?.let { fileStorageManager.printPdfFile(it, f) } },
                        onOpenPreview = { onOpenPreview(doc.id) }
                    )
                }
            }
        }
    }
}
