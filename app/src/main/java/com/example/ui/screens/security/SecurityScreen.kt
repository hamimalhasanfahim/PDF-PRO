package com.example.ui.screens.security

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import com.example.ui.screens.editor.SignaturePadDialog
import com.example.ui.theme.AccentSecurity
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandPurple
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(
    initialTab: String,
    docId: Long,
    availableDocs: List<PdfDocumentEntity>,
    viewModel: SecurityViewModel,
    fileStorageManager: FileStorageManager,
    onOpenPreview: (Long) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(docId) {
        if (docId > 0L) viewModel.selectDocumentById(docId)
    }

    var selectedTab by remember(initialTab) {
        mutableIntStateOf(
            when (initialTab) {
                "unlock" -> 1
                "sign" -> 2
                "watermark" -> 3
                else -> 0
            }
        )
    }
    var showSignPad by remember { mutableStateOf(false) }

    val tabs = listOf("Protect", "Unlock", "Sign", "Watermark")

    Column(modifier = Modifier.fillMaxSize()) {
        PdfToolTopBar(
            title = "PDF Security & Signing",
            subtitle = "Encryption, permissions, watermarks & signatures",
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
                item {
                    DocumentSelectionCard(
                        selectedDoc = state.selectedDoc,
                        availableDocs = availableDocs,
                        onSelectExisting = viewModel::selectDocument,
                        onImportUri = viewModel::importPdf
                    )
                }

                when (selectedTab) {
                    0 -> {
                        item {
                            ToolInstructionCard(
                                icon = Icons.Default.Lock,
                                title = "Password & Permissions Encryption",
                                description = "Locks PDF with 128-bit standard encryption and enforces printing & copying permissions.",
                                accentColor = AccentSecurity
                            )
                        }
                        item {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = state.userPassword,
                                        onValueChange = viewModel::updateUserPassword,
                                        label = { Text("Document Open Password") },
                                        visualTransformation = PasswordVisualTransformation(),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("security_user_password")
                                    )
                                    OutlinedTextField(
                                        value = state.ownerPassword,
                                        onValueChange = viewModel::updateOwnerPassword,
                                        label = { Text("Owner / Permissions Password (Optional)") },
                                        visualTransformation = PasswordVisualTransformation(),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Allow Printing")
                                        Switch(checked = state.allowPrinting, onCheckedChange = viewModel::updateAllowPrinting)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Allow Text & Image Copying")
                                        Switch(checked = state.allowCopying, onCheckedChange = viewModel::updateAllowCopying)
                                    }
                                }
                            }
                        }
                        item {
                            Button(
                                onClick = viewModel::protectPdf,
                                enabled = !state.isProcessing,
                                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("protect_pdf_button")
                            ) { Text("Encrypt & Protect PDF") }
                        }
                    }

                    1 -> {
                        item {
                            ToolInstructionCard(
                                icon = Icons.Default.LockOpen,
                                title = "Authorized Password Removal",
                                description = "Requires the valid document password. Never bypasses encryption without authorization.",
                                accentColor = AccentSecurity
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = state.unlockPassword,
                                onValueChange = viewModel::updateUnlockPassword,
                                label = { Text("Enter Current PDF Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            Button(
                                onClick = viewModel::unlockPdfAuthorized,
                                enabled = !state.isProcessing,
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) { Text("Unlock PDF") }
                        }
                    }

                    2 -> {
                        item {
                            ToolInstructionCard(
                                icon = Icons.Default.Draw,
                                title = "Handwritten Signature & SHA-256 Stamp",
                                description = "Draw your signature and stamp it onto a page with signer name, timestamp, and document SHA-256 verification digest.",
                                accentColor = AccentSecurity
                            )
                        }
                        item {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = state.signerName,
                                        onValueChange = viewModel::updateSignerName,
                                        label = { Text("Signer Name") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = state.signReason,
                                        onValueChange = viewModel::updateSignReason,
                                        label = { Text("Approval / Reason") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = state.signPageNumber.toString(),
                                        onValueChange = { it.toIntOrNull()?.let(viewModel::updateSignPage) },
                                        label = { Text("Target Page Number") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedButton(
                                        onClick = { showSignPad = true },
                                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Default.Draw, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (state.signatureFile == null) "Draw Signature" else "Redraw Signature")
                                    }
                                    state.signatureFile?.let { sig ->
                                        AsyncImage(
                                            model = sig,
                                            contentDescription = "Drawn signature preview",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(90.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color.White)
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            Button(
                                onClick = viewModel::applyDigitalSignature,
                                enabled = !state.isProcessing,
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) { Text("Stamp Signature onto PDF") }
                        }
                    }

                    3 -> {
                        item {
                            ToolInstructionCard(
                                icon = Icons.Default.WaterDrop,
                                title = "Custom Page Watermark",
                                description = "Stamps translucent diagonal watermark text across all pages of your PDF.",
                                accentColor = AccentSecurity
                            )
                        }
                        item {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = state.watermarkText,
                                        onValueChange = viewModel::updateWatermarkText,
                                        label = { Text("Watermark Text (English or বাংলা)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text("Opacity: ${(state.watermarkOpacity * 100).roundToInt()}%")
                                    Slider(
                                        value = state.watermarkOpacity,
                                        onValueChange = viewModel::updateWatermarkOpacity,
                                        valueRange = 0.08f..0.75f
                                    )
                                    Text("Angle: ${state.watermarkAngle.roundToInt()}°")
                                    Slider(
                                        value = state.watermarkAngle,
                                        onValueChange = viewModel::updateWatermarkAngle,
                                        valueRange = 0f..90f
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        listOf(BrandPurple, BrandBlue, Color(0xFFEF4444), Color(0xFF334155)).forEach { col ->
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(col)
                                                    .border(
                                                        if (state.watermarkColorArgb == col.toArgb()) 3.dp else 1.dp,
                                                        MaterialTheme.colorScheme.onSurface,
                                                        CircleShape
                                                    )
                                                    .clickable { viewModel.updateWatermarkColor(col.toArgb()) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            Button(
                                onClick = viewModel::applyWatermark,
                                enabled = !state.isProcessing,
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) { Text("Apply Watermark") }
                        }
                    }
                }

                item {
                    ProcessingProgressBanner(isVisible = state.isProcessing, message = "Applying security operation...")
                    ErrorBannerCard(errorMessage = state.errorMessage, onDismiss = viewModel::clearError)
                }

                val outFile = state.outputFile
                val outDoc = state.outputDoc
                if (outFile != null && outDoc != null) {
                    item {
                        ResultOutputCard(
                            outputFile = outFile,
                            title = "Secured Output Ready",
                            summaryLine = if (outDoc.isEncrypted) "Encrypted • ${outDoc.pageCount} pages" else "${outDoc.pageCount} pages",
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

    if (showSignPad) {
        SignaturePadDialog(
            onDismiss = { showSignPad = false },
            onConfirmStrokes = { strokes ->
                viewModel.saveSignatureStrokes(strokes)
                showSignPad = false
            }
        )
    }
}
