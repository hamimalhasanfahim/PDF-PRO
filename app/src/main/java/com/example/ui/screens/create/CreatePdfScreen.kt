package com.example.ui.screens.create

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.pdf.FileStorageManager
import com.example.domain.model.PageOrientation
import com.example.domain.model.PageSizeOption
import com.example.domain.model.UserPreferences
import com.example.ui.components.CenteredResponsiveContainer
import com.example.ui.components.ErrorBannerCard
import com.example.ui.components.PdfToolTopBar
import com.example.ui.components.ProcessingProgressBanner
import com.example.ui.components.ResultOutputCard
import com.example.ui.components.ToolInstructionCard
import com.example.ui.components.findActivity
import com.example.ui.localization.LocalAppStrings
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandPurple
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePdfScreen(
    initialMode: String,
    preferences: UserPreferences,
    viewModel: CreatePdfViewModel,
    fileStorageManager: FileStorageManager,
    onOpenPreview: (Long) -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.applyDefaults(
            pageSize = preferences.defaultPageSize,
            orientation = preferences.defaultOrientation,
            fontSize = preferences.defaultFontSize,
            marginPt = preferences.defaultMarginPt
        )
    }

    var selectedTab by remember(initialMode) {
        mutableIntStateOf(
            when (initialMode) {
                "images" -> 1
                "blank" -> 2
                "doc" -> 3
                else -> 0
            }
        )
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30)
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.setSelectedImages(uris)
    }

    val docPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.setSelectedSourceDocument(uri)
    }

    val tabs = listOf("Text & বাংলা", "Images", "Blank PDF", "From Document")

    Column(modifier = Modifier.fillMaxSize()) {
        PdfToolTopBar(
            title = strings.createPdf,
            subtitle = "Custom sizes, margins, headers & Unicode fonts",
            onBack = onBack
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    ToolInstructionCard(
                        icon = Icons.Default.PictureAsPdf,
                        title = "Local PDF Creation Studio",
                        description = "Supports English & Bengali (বাংলা) Unicode fonts, A3/A4/A5/Letter/Legal/Custom sizes, Portrait & Landscape, margins, headers, footers & numbering."
                    )
                }

                item {
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = viewModel::updateTitle,
                        label = { Text("Output File Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("create_pdf_title_input")
                    )
                }

                when (selectedTab) {
                    0 -> {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Document Content", style = MaterialTheme.typography.titleSmall)
                                OutlinedButton(
                                    onClick = viewModel::insertBengaliSample,
                                    modifier = Modifier.testTag("create_insert_bengali_button")
                                ) {
                                    Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Sample বাংলা + EN")
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = state.bodyText,
                                onValueChange = viewModel::updateBodyText,
                                label = { Text("Write or paste English / বাংলা text...") },
                                minLines = 6,
                                maxLines = 12,
                                modifier = Modifier.fillMaxWidth().testTag("create_pdf_body_input")
                            )
                        }

                        item {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("Typography, Header & Footer", style = MaterialTheme.typography.titleSmall)

                                    Text("Font Size: ${state.fontSizePt.roundToInt()} pt", style = MaterialTheme.typography.labelLarge)
                                    Slider(
                                        value = state.fontSizePt,
                                        onValueChange = viewModel::updateFontSize,
                                        valueRange = 10f..28f
                                    )

                                    Text("Text Color", style = MaterialTheme.typography.labelLarge)
                                    val colors = listOf(
                                        Color(0xFF141726),
                                        BrandPurple,
                                        BrandBlue,
                                        Color(0xFF0F766E),
                                        Color(0xFFB91C1C)
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        colors.forEach { col ->
                                            val selected = state.textColorArgb == col.toArgb()
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(CircleShape)
                                                    .background(col)
                                                    .border(
                                                        width = if (selected) 3.dp else 1.dp,
                                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                        shape = CircleShape
                                                    )
                                                    .clickable { viewModel.updateTextColor(col.toArgb()) }
                                            )
                                        }
                                    }

                                    OutlinedTextField(
                                        value = state.headerText,
                                        onValueChange = viewModel::updateHeaderText,
                                        label = { Text("Optional Header") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = state.footerText,
                                        onValueChange = viewModel::updateFooterText,
                                        label = { Text("Optional Footer") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        item {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        "Zero-Permission Photo Picker",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        "Pick single or multiple photos to combine into one clean PDF.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Button(
                                        onClick = {
                                            photoPicker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("pick_images_button")
                                    ) {
                                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Select Images (${state.selectedImageUris.size} selected)")
                                    }

                                    if (state.selectedImageUris.isNotEmpty()) {
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(state.selectedImageUris) { uri ->
                                                Box(modifier = Modifier.size(92.dp)) {
                                                    AsyncImage(
                                                        model = uri,
                                                        contentDescription = "Selected photo",
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .clip(RoundedCornerShape(12.dp))
                                                    )
                                                    IconButton(
                                                        onClick = { viewModel.removeSelectedImage(uri) },
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            .size(26.dp)
                                                            .clip(CircleShape)
                                                            .background(Color.Black.copy(alpha = 0.6f))
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Close,
                                                            contentDescription = "Remove",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(16.dp)
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

                    2 -> {
                        item {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("Blank Pages Count: ${state.blankPageCount}", style = MaterialTheme.typography.titleSmall)
                                    Slider(
                                        value = state.blankPageCount.toFloat(),
                                        onValueChange = { viewModel.updateBlankPageCount(it.roundToInt()) },
                                        valueRange = 1f..50f
                                    )
                                }
                            }
                        }
                    }

                    3 -> {
                        item {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("Convert Supported Document to PDF", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "Supports plain text (.txt, .md, .csv, .json) and local OpenXML text extraction (.docx, .xlsx, .pptx). Complex visual layouts are simplified.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            docPicker.launch(
                                                arrayOf(
                                                    "text/*",
                                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                                    "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                                                )
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Default.Description, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(state.selectedDocName.ifBlank { "Pick TXT / DOCX / XLSX / PPTX" })
                                    }
                                }
                            }
                        }
                    }
                }

                // Shared Page Layout Options
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Page Setup", style = MaterialTheme.typography.titleSmall)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(PageSizeOption.entries) { size ->
                                    FilterChip(
                                        selected = state.pageSize == size,
                                        onClick = { viewModel.updatePageSize(size) },
                                        label = { Text(size.label) }
                                    )
                                }
                            }

                            if (state.pageSize == PageSizeOption.CUSTOM) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = state.customWidthPt.roundToInt().toString(),
                                        onValueChange = { it.toFloatOrNull()?.let(viewModel::updateCustomWidth) },
                                        label = { Text("Width (pt)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = state.customHeightPt.roundToInt().toString(),
                                        onValueChange = { it.toFloatOrNull()?.let(viewModel::updateCustomHeight) },
                                        label = { Text("Height (pt)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PageOrientation.entries.forEach { ori ->
                                    FilterChip(
                                        selected = state.orientation == ori,
                                        onClick = { viewModel.updateOrientation(ori) },
                                        label = { Text(ori.label) }
                                    )
                                }
                            }

                            if (selectedTab != 2) {
                                Text("Page Margins: ${state.marginPt.roundToInt()} pt", style = MaterialTheme.typography.labelLarge)
                                Slider(
                                    value = state.marginPt,
                                    onValueChange = viewModel::updateMargin,
                                    valueRange = 16f..96f
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Include Page Numbering", style = MaterialTheme.typography.bodyMedium)
                                    Switch(
                                        checked = state.includePageNumbers,
                                        onCheckedChange = viewModel::updateIncludePageNumbers
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    ProcessingProgressBanner(isVisible = state.isProcessing, message = strings.processing)
                    ErrorBannerCard(errorMessage = state.errorMessage, onDismiss = viewModel::clearError)
                }

                item {
                    Button(
                        onClick = {
                            when (selectedTab) {
                                0 -> viewModel.generateTextPdf()
                                1 -> viewModel.generateImagesPdf()
                                2 -> viewModel.generateBlankPdf()
                                3 -> viewModel.generateFromDocumentUri()
                            }
                        },
                        enabled = !state.isProcessing,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("create_pdf_generate_button")
                    ) {
                        Icon(
                            imageVector = if (selectedTab == 2) Icons.Default.NoteAdd else Icons.Default.CreateNewFolder,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Create PDF")
                    }
                }

                val outFile = state.outputFile
                val createdDoc = state.createdDocument
                if (outFile != null && createdDoc != null) {
                    item {
                        ResultOutputCard(
                            outputFile = outFile,
                            title = "PDF Created",
                            summaryLine = "${createdDoc.pageCount} pages ready",
                            onExportToUri = viewModel::exportToUri,
                            onShare = viewModel::shareOutput,
                            onPrint = {
                                context.findActivity()?.let { act ->
                                    fileStorageManager.printPdfFile(act, outFile)
                                }
                            },
                            onOpenPreview = { onOpenPreview(createdDoc.id) }
                        )
                    }
                }
            }
        }
    }
}
