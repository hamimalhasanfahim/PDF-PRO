package com.example.ui.screens.ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import com.example.ui.theme.AccentScan
import com.example.ui.theme.WarningAmber

@Composable
fun OcrScreen(
    docId: Long,
    availableDocs: List<PdfDocumentEntity>,
    viewModel: OcrViewModel,
    fileStorageManager: FileStorageManager,
    onOpenPreview: (Long) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var copiedNotice by remember { mutableStateOf(false) }

    LaunchedEffect(docId) {
        if (docId > 0L) viewModel.selectPdfById(docId)
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.selectImage(uri)
    }
    val txtExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) viewModel.exportTextToUri(uri)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PdfToolTopBar(
            title = "OCR Text Recognition",
            subtitle = "Extract, copy, search & export text offline",
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
                        icon = Icons.Default.TextFields,
                        title = "Google ML Kit On-Device OCR",
                        description = "Recognizes text from images and scanned PDFs locally without uploading documents.",
                        accentColor = AccentScan
                    )
                }

                item {
                    Text("OCR Language Model", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.ocrLanguageCode == "en",
                            onClick = { viewModel.setOcrLanguage("en") },
                            label = { Text("Latin / English (Available)") }
                        )
                        FilterChip(
                            selected = state.ocrLanguageCode == "bn",
                            onClick = { viewModel.setOcrLanguage("bn") },
                            label = { Text("Bengali (Check Model)") }
                        )
                    }
                }

                state.modelNotice?.let { notice ->
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = WarningAmber.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = WarningAmber)
                                Spacer(Modifier.width(10.dp))
                                Text(notice, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                item {
                    OutlinedButton(
                        onClick = {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.selectedImageUri != null) "Image Selected (Tap to Change)" else "Pick Image for OCR")
                    }
                }

                item {
                    DocumentSelectionCard(
                        selectedDoc = state.selectedPdfDoc,
                        availableDocs = availableDocs,
                        label = "Or Select Scanned / Digital PDF",
                        onSelectExisting = viewModel::selectPdf,
                        onImportUri = viewModel::importPdf
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = viewModel::runOcr,
                            enabled = !state.isRunning,
                            modifier = Modifier.weight(1f).height(50.dp).testTag("ocr_run_button")
                        ) { Text("Run ML Kit OCR") }

                        if (state.selectedPdfDoc != null) {
                            FilledTonalButton(
                                onClick = viewModel::extractEmbeddedPdfText,
                                enabled = !state.isRunning,
                                modifier = Modifier.weight(1f).height(50.dp)
                            ) { Text("Extract PDF Text") }
                        }
                    }
                }

                item {
                    ProcessingProgressBanner(isVisible = state.isRunning, message = "Recognizing text...")
                    ErrorBannerCard(errorMessage = state.errorMessage, onDismiss = viewModel::clearError)
                }

                if (state.recognizedText.isNotBlank()) {
                    item {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = viewModel::updateSearchQuery,
                            label = { Text("Search recognized text (${state.searchMatchCount} matches)") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = state.recognizedText,
                            onValueChange = viewModel::updateRecognizedText,
                            label = { Text("Recognized Output") },
                            minLines = 7,
                            maxLines = 16,
                            modifier = Modifier.fillMaxWidth().testTag("ocr_result_text")
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("OCR Text", state.recognizedText))
                                    copiedNotice = true
                                },
                                modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (copiedNotice) "Copied!" else "Copy")
                            }
                            FilledTonalButton(
                                onClick = { txtExportLauncher.launch("extracted_text.txt") },
                                modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.SaveAlt, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Export .TXT")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = viewModel::saveRecognizedAsPdf,
                            modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Save Recognized Text as New PDF")
                        }
                    }
                }

                val outPdf = state.savedPdfFile
                val outDoc = state.savedPdfDoc
                if (outPdf != null && outDoc != null) {
                    item {
                        ResultOutputCard(
                            outputFile = outPdf,
                            title = "Searchable Text PDF Saved",
                            summaryLine = "${outDoc.pageCount} pages",
                            onExportToUri = viewModel::exportSavedPdfToUri,
                            onShare = viewModel::shareSavedPdf,
                            onPrint = {
                                context.findActivity()?.let { fileStorageManager.printPdfFile(it, outPdf) }
                            },
                            onOpenPreview = { onOpenPreview(outDoc.id) }
                        )
                    }
                }
            }
        }
    }
}
