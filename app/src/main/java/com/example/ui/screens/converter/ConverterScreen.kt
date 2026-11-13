package com.example.ui.screens.converter

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.ui.theme.AccentConvert

@Composable
fun ConverterScreen(
    initialMode: String,
    docId: Long,
    availableDocs: List<PdfDocumentEntity>,
    viewModel: ConverterViewModel,
    fileStorageManager: FileStorageManager,
    onOpenCreateImages: () -> Unit,
    onOpenCreateText: () -> Unit,
    onOpenPreview: (Long) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(docId, initialMode) {
        if (docId > 0L) viewModel.selectPdfById(docId)
        if (initialMode == "pdf_to_png") viewModel.setMode(ConversionMode.PDF_TO_PNG)
    }

    val officePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.selectExternalOfficeUri(uri)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PdfToolTopBar(
            title = "PDF Converter",
            subtitle = "Images, Word, Excel & PowerPoint conversions",
            onBack = onBack
        )

        CenteredResponsiveContainer(modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = onOpenCreateImages,
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                        ) { Text("Images → PDF") }
                        FilledTonalButton(
                            onClick = onOpenCreateText,
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                        ) { Text("Text → PDF") }
                    }
                }

                item {
                    Text("Select Conversion Direction", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ConversionMode.entries) { m ->
                            FilterChip(
                                selected = state.mode == m,
                                onClick = { viewModel.setMode(m) },
                                label = { Text(m.label) }
                            )
                        }
                    }
                }

                item {
                    ToolInstructionCard(
                        icon = Icons.Default.SwapHoriz,
                        title = state.mode.label,
                        description = state.mode.capabilityNote,
                        accentColor = AccentConvert
                    )
                }

                val isPdfSource = state.mode in listOf(
                    ConversionMode.PDF_TO_JPG,
                    ConversionMode.PDF_TO_PNG,
                    ConversionMode.PDF_TO_DOCX,
                    ConversionMode.PDF_TO_XLSX,
                    ConversionMode.PDF_TO_PPTX
                )

                if (isPdfSource) {
                    item {
                        DocumentSelectionCard(
                            selectedDoc = state.selectedPdfDoc,
                            availableDocs = availableDocs,
                            onSelectExisting = viewModel::selectPdfDocument,
                            onImportUri = viewModel::importPdf
                        )
                    }
                } else {
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Source Document", style = MaterialTheme.typography.titleSmall)
                                OutlinedButton(
                                    onClick = {
                                        val mime = when (state.mode) {
                                            ConversionMode.DOCX_TO_PDF -> arrayOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                                            ConversionMode.XLSX_TO_PDF -> arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                                            else -> arrayOf("application/vnd.openxmlformats-officedocument.presentationml.presentation")
                                        }
                                        officePicker.launch(mime)
                                    },
                                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Default.Description, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(state.selectedExternalName.ifBlank { "Choose ${state.mode.label.substringBefore(" ")}" })
                                }
                            }
                        }
                    }
                }

                item {
                    ProcessingProgressBanner(isVisible = state.isConverting, message = "Converting ${state.mode.label}...")
                    ErrorBannerCard(errorMessage = state.errorMessage, onDismiss = viewModel::clearError)
                }

                item {
                    Button(
                        onClick = viewModel::convert,
                        enabled = !state.isConverting,
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("convert_execute_button")
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Convert Now")
                    }
                }

                if (state.outputImageFiles.isNotEmpty()) {
                    item {
                        Text("Rendered Pages (${state.outputImageFiles.size})", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(state.outputImageFiles) { img ->
                                Card(shape = RoundedCornerShape(14.dp)) {
                                    Column(
                                        modifier = Modifier.width(120.dp).padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        AsyncImage(
                                            model = img,
                                            contentDescription = img.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(10.dp))
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        FilledTonalButton(
                                            onClick = { viewModel.shareImage(img, state.mode == ConversionMode.PDF_TO_PNG) },
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

                val outFile = state.outputFile
                if (outFile != null) {
                    item {
                        val isPdf = state.outputMimeType == "application/pdf"
                        ResultOutputCard(
                            outputFile = outFile,
                            title = "${state.mode.label} Ready",
                            summaryLine = if (state.outputImageFiles.size > 1) "${state.outputImageFiles.size} images packaged" else "Converted locally",
                            mimeType = state.outputMimeType,
                            isPdf = isPdf,
                            onExportToUri = viewModel::exportToUri,
                            onShare = viewModel::shareOutput,
                            onPrint = if (isPdf) {
                                { context.findActivity()?.let { fileStorageManager.printPdfFile(it, outFile) } }
                            } else null,
                            onOpenPreview = state.outputPdfDoc?.let { doc -> { onOpenPreview(doc.id) } }
                        )
                    }
                }
            }
        }
    }
}
