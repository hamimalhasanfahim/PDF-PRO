package com.example.ui.screens.compressor

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.db.PdfDocumentEntity
import com.example.data.pdf.FileStorageManager
import com.example.domain.model.CompressionLevel
import com.example.ui.components.CenteredResponsiveContainer
import com.example.ui.components.DocumentSelectionCard
import com.example.ui.components.ErrorBannerCard
import com.example.ui.components.PdfToolTopBar
import com.example.ui.components.ProcessingProgressBanner
import com.example.ui.components.ResultOutputCard
import com.example.ui.components.ToolInstructionCard
import com.example.ui.components.findActivity
import com.example.ui.theme.AccentCompress
import com.example.ui.theme.SuccessEmerald
import kotlin.math.roundToInt

@Composable
fun CompressorScreen(
    docId: Long,
    defaultLevel: CompressionLevel,
    availableDocs: List<PdfDocumentEntity>,
    viewModel: CompressorViewModel,
    fileStorageManager: FileStorageManager,
    onOpenPreview: (Long) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(docId) {
        viewModel.setLevel(defaultLevel)
        if (docId > 0L) viewModel.selectDocumentById(docId)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PdfToolTopBar(
            title = "PDF Compressor",
            subtitle = "Shrink PDF size without touching original files",
            onBack = onBack
        )

        CenteredResponsiveContainer(modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    ToolInstructionCard(
                        icon = Icons.Default.Compress,
                        title = "Smart Image & Stream Compression",
                        description = "Choose High, Medium, Low, or Custom resolution & JPEG quality. Always creates a compressed copy and never overwrites your original file.",
                        accentColor = AccentCompress
                    )
                }

                item {
                    DocumentSelectionCard(
                        selectedDoc = state.selectedDoc,
                        availableDocs = availableDocs,
                        onSelectExisting = viewModel::selectDocument,
                        onImportUri = viewModel::importAndSelect
                    )
                }

                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Compression Preset", style = MaterialTheme.typography.titleSmall)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(CompressionLevel.entries) { lvl ->
                                    FilterChip(
                                        selected = state.level == lvl,
                                        onClick = { viewModel.setLevel(lvl) },
                                        label = { Text(lvl.label) }
                                    )
                                }
                            }

                            Text("Image Quality: ${state.customQuality}%", style = MaterialTheme.typography.labelLarge)
                            Slider(
                                value = state.customQuality.toFloat(),
                                onValueChange = { viewModel.setCustomQuality(it.roundToInt()) },
                                valueRange = 15f..95f
                            )

                            Text("Max Image Resolution: ${state.customMaxDimPx} px", style = MaterialTheme.typography.labelLarge)
                            Slider(
                                value = state.customMaxDimPx.toFloat(),
                                onValueChange = { viewModel.setCustomMaxDim(it.roundToInt()) },
                                valueRange = 500f..2800f
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Run via WorkManager", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "Recommended for very large multi-page PDFs",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = state.useBackgroundWorker,
                                    onCheckedChange = viewModel::setUseBackgroundWorker
                                )
                            }
                        }
                    }
                }

                item {
                    ProcessingProgressBanner(isVisible = state.isCompressing, message = "Compressing PDF resources...")
                    ErrorBannerCard(errorMessage = state.errorMessage, onDismiss = viewModel::clearError)
                }

                item {
                    Button(
                        onClick = viewModel::compress,
                        enabled = state.selectedDoc != null && !state.isCompressing,
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("compress_execute_button")
                    ) {
                        Icon(Icons.Default.Compress, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Compress PDF")
                    }
                }

                val stats = state.stats
                val outDoc = state.outputDoc
                if (stats != null && outDoc != null) {
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, SuccessEmerald.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Before & After Comparison", style = MaterialTheme.typography.titleMedium)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Original Size", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(FileStorageManager.formatBytes(stats.originalBytes), style = MaterialTheme.typography.titleMedium)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Saved", style = MaterialTheme.typography.bodySmall, color = SuccessEmerald)
                                        Text("${stats.reductionPercent}%", style = MaterialTheme.typography.headlineSmall, color = SuccessEmerald)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Compressed Size", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(FileStorageManager.formatBytes(stats.compressedBytes), style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                                val ratio = if (stats.originalBytes > 0L) {
                                    (stats.compressedBytes.toFloat() / stats.originalBytes.toFloat()).coerceIn(0.05f, 1f)
                                } else 1f
                                LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }

                    item {
                        ResultOutputCard(
                            outputFile = stats.outputFile,
                            title = "Compression Complete",
                            summaryLine = "Reduced by ${stats.reductionPercent}% (${FileStorageManager.formatBytes(stats.savedBytes)} saved)",
                            onExportToUri = viewModel::exportToUri,
                            onShare = viewModel::shareOutput,
                            onPrint = {
                                context.findActivity()?.let { fileStorageManager.printPdfFile(it, stats.outputFile) }
                            },
                            onOpenPreview = { onOpenPreview(outDoc.id) }
                        )
                    }
                }
            }
        }
    }
}
