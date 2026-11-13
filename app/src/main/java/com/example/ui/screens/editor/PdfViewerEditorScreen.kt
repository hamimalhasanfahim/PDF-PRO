package com.example.ui.screens.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StrikethroughS
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.db.PdfDocumentEntity
import com.example.data.pdf.FileStorageManager
import com.example.domain.model.EditorTool
import com.example.domain.model.PdfOverlayAnnotation
import com.example.domain.model.ShapeKind
import com.example.ui.components.DocumentSelectionCard
import com.example.ui.components.ErrorBannerCard
import com.example.ui.components.PdfToolTopBar
import com.example.ui.components.ProcessingProgressBanner
import com.example.ui.components.ResultOutputCard
import com.example.ui.components.findActivity
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandPurple
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.min

@Composable
fun PdfViewerEditorScreen(
    docId: Long,
    startInEditMode: Boolean,
    availableDocs: List<PdfDocumentEntity>,
    viewModel: PdfEditorViewModel,
    fileStorageManager: FileStorageManager,
    onOpenNewDoc: (Long) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(docId) {
        if (docId > 0L) viewModel.loadDocumentById(docId, startInEditMode)
    }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var showSearchBar by remember { mutableStateOf(false) }
    var pendingTapNormalized by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var showTextDialog by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    var showCommentDialog by remember { mutableStateOf(false) }
    var commentInput by remember { mutableStateOf("") }
    var showWatermarkDialog by remember { mutableStateOf(false) }
    var watermarkInput by remember { mutableStateOf("CONFIDENTIAL") }
    var showSignatureDialog by remember { mutableStateOf(false) }
    var selectedShapeKind by remember { mutableStateOf(ShapeKind.RECTANGLE) }

    val activePoints = remember { mutableStateListOf<Pair<Float, Float>>() }
    var dragStartNorm by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var dragEndNorm by remember { mutableStateOf<Pair<Float, Float>?>(null) }

    val imageOverlayPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.addImageOverlayFromUri(uri)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PdfToolTopBar(
            title = state.document?.title ?: "PDF Viewer & Editor",
            subtitle = if (state.pageOrder.isNotEmpty()) {
                "Page ${state.currentPagePosition + 1} of ${state.pageOrder.size} • Overlay Annotations"
            } else "Select a PDF to view or edit",
            onBack = onBack,
            actions = {
                if (state.document != null) {
                    IconButton(onClick = { showSearchBar = !showSearchBar }) {
                        Icon(Icons.Default.Search, contentDescription = "Search PDF")
                    }
                    IconButton(onClick = viewModel::undo, enabled = state.canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = viewModel::redo, enabled = state.canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    IconButton(onClick = viewModel::shareCurrentOrSaved) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = {
                        val file = state.savedFile ?: state.document?.let { File(it.localPath) }
                        if (file != null) context.findActivity()?.let { fileStorageManager.printPdfFile(it, file) }
                    }) {
                        Icon(Icons.Default.Print, contentDescription = "Print")
                    }
                }
            }
        )

        if (state.document == null) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DocumentSelectionCard(
                    selectedDoc = null,
                    availableDocs = availableDocs,
                    label = "Choose PDF to View or Annotate",
                    onSelectExisting = { viewModel.loadDocumentById(it.id, startInEditMode) },
                    onImportUri = viewModel::selectAndImportPdf
                )
            }
            return@Column
        }

        // In-PDF Search Bar
        if (showSearchBar) {
            Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = viewModel::updateSearchQuery,
                            placeholder = { Text("Search text inside PDF...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = viewModel::runSearch) { Text("Find") }
                    }
                    if (state.searchMatches.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.searchMatches) { m ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        val pos = state.pageOrder.indexOf(m.pageIndex)
                                        if (pos >= 0) viewModel.goToPagePosition(pos)
                                    },
                                    label = { Text("P.${m.pageIndex + 1} (${m.matchCountOnPage})") }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Annotation Mode Banner
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (state.activeTool == EditorTool.VIEW) {
                    "View Mode • Tap a tool below to add overlay annotations (preserves original PDF)"
                } else {
                    "Active Tool: ${state.activeTool.name} • Tap or drag on page. Saves as new annotated PDF."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        // Main Page Canvas Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            val bmp = state.pageBitmap
            if (state.isLoadingPage) {
                CircularProgressIndicator()
            } else if (bmp != null) {
                val aspect = (bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(1f)).coerceIn(0.4f, 2.2f)
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (aspect >= 1f) 0.96f else 0.9f)
                            .aspectRatio(aspect)
                            .graphicsLayer {
                                scaleX = zoomScale
                                scaleY = zoomScale
                            }
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "PDF page ${state.currentPagePosition + 1}",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Interactive Overlay Canvas
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(state.activeTool, state.currentOriginalPageIndex, state.selectedColorArgb, selectedShapeKind) {
                                    if (state.activeTool in listOf(
                                            EditorTool.TEXT,
                                            EditorTool.COMMENT,
                                            EditorTool.DATE_STAMP
                                        )
                                    ) {
                                        detectTapGestures { offset ->
                                            val nx = (offset.x / size.width).coerceIn(0.05f, 0.9f)
                                            val ny = (offset.y / size.height).coerceIn(0.05f, 0.92f)
                                            when (state.activeTool) {
                                                EditorTool.TEXT -> {
                                                    pendingTapNormalized = nx to ny
                                                    showTextDialog = true
                                                }
                                                EditorTool.COMMENT -> {
                                                    pendingTapNormalized = nx to ny
                                                    showCommentDialog = true
                                                }
                                                EditorTool.DATE_STAMP -> viewModel.addDateStampAt(nx, ny)
                                                else -> Unit
                                            }
                                        }
                                    } else if (state.activeTool in listOf(
                                            EditorTool.DRAW,
                                            EditorTool.SHAPE,
                                            EditorTool.HIGHLIGHT,
                                            EditorTool.UNDERLINE,
                                            EditorTool.STRIKETHROUGH
                                        )
                                    ) {
                                        detectDragGestures(
                                            onDragStart = { start ->
                                                val nx = (start.x / size.width).coerceIn(0f, 1f)
                                                val ny = (start.y / size.height).coerceIn(0f, 1f)
                                                if (state.activeTool == EditorTool.DRAW) {
                                                    activePoints.clear()
                                                    activePoints.add(nx to ny)
                                                } else {
                                                    dragStartNorm = nx to ny
                                                    dragEndNorm = nx to ny
                                                }
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                val nx = (change.position.x / size.width).coerceIn(0f, 1f)
                                                val ny = (change.position.y / size.height).coerceIn(0f, 1f)
                                                if (state.activeTool == EditorTool.DRAW) {
                                                    activePoints.add(nx to ny)
                                                } else {
                                                    dragEndNorm = nx to ny
                                                }
                                            },
                                            onDragEnd = {
                                                val pageIdx = state.currentOriginalPageIndex
                                                if (state.activeTool == EditorTool.DRAW && activePoints.size >= 2) {
                                                    viewModel.addAnnotation(
                                                        PdfOverlayAnnotation.FreehandOverlay(
                                                            id = UUID.randomUUID().toString(),
                                                            pageIndex = pageIdx,
                                                            normPoints = activePoints.toList(),
                                                            colorArgb = state.selectedColorArgb
                                                        )
                                                    )
                                                    activePoints.clear()
                                                } else {
                                                    val s = dragStartNorm
                                                    val e = dragEndNorm
                                                    if (s != null && e != null) {
                                                        if (state.activeTool == EditorTool.SHAPE) {
                                                            viewModel.addAnnotation(
                                                                PdfOverlayAnnotation.ShapeOverlay(
                                                                    id = UUID.randomUUID().toString(),
                                                                    pageIndex = pageIdx,
                                                                    kind = selectedShapeKind,
                                                                    normStartX = s.first,
                                                                    normStartY = s.second,
                                                                    normEndX = e.first,
                                                                    normEndY = e.second,
                                                                    colorArgb = state.selectedColorArgb
                                                                )
                                                            )
                                                        } else {
                                                            viewModel.addAnnotation(
                                                                PdfOverlayAnnotation.MarkupOverlay(
                                                                    id = UUID.randomUUID().toString(),
                                                                    pageIndex = pageIdx,
                                                                    kind = state.activeTool,
                                                                    normStartX = s.first,
                                                                    normStartY = s.second,
                                                                    normEndX = e.first,
                                                                    normEndY = e.second,
                                                                    colorArgb = if (state.activeTool == EditorTool.HIGHLIGHT) 0xFFFACC15.toInt() else state.selectedColorArgb
                                                                )
                                                            )
                                                        }
                                                    }
                                                    dragStartNorm = null
                                                    dragEndNorm = null
                                                }
                                            }
                                        )
                                    }
                                }
                        ) {
                            val pageIdx = state.currentOriginalPageIndex
                            val visible = state.annotations.filter { it.pageIndex == pageIdx || it.pageIndex == -1 }
                            for (ann in visible) {
                                when (ann) {
                                    is PdfOverlayAnnotation.ShapeOverlay -> {
                                        val x1 = ann.normStartX * size.width
                                        val y1 = ann.normStartY * size.height
                                        val x2 = ann.normEndX * size.width
                                        val y2 = ann.normEndY * size.height
                                        val col = Color(ann.colorArgb)
                                        when (ann.kind) {
                                            ShapeKind.RECTANGLE -> drawRect(
                                                color = col,
                                                topLeft = Offset(min(x1, x2), min(y1, y2)),
                                                size = Size(abs(x2 - x1), abs(y2 - y1)),
                                                style = Stroke(width = 4f)
                                            )
                                            ShapeKind.CIRCLE -> drawOval(
                                                color = col,
                                                topLeft = Offset(min(x1, x2), min(y1, y2)),
                                                size = Size(abs(x2 - x1), abs(y2 - y1)),
                                                style = Stroke(width = 4f)
                                            )
                                            ShapeKind.LINE, ShapeKind.ARROW -> drawLine(
                                                color = col,
                                                start = Offset(x1, y1),
                                                end = Offset(x2, y2),
                                                strokeWidth = 4f
                                            )
                                        }
                                    }
                                    is PdfOverlayAnnotation.MarkupOverlay -> {
                                        val x1 = ann.normStartX * size.width
                                        val y1 = ann.normStartY * size.height
                                        val x2 = ann.normEndX * size.width
                                        val y2 = ann.normEndY * size.height
                                        when (ann.kind) {
                                            EditorTool.HIGHLIGHT -> drawRect(
                                                color = Color(ann.colorArgb).copy(alpha = 0.35f),
                                                topLeft = Offset(min(x1, x2), min(y1, y2)),
                                                size = Size(abs(x2 - x1).coerceAtLeast(18f), abs(y2 - y1).coerceAtLeast(18f))
                                            )
                                            EditorTool.UNDERLINE -> drawLine(
                                                color = Color(ann.colorArgb),
                                                start = Offset(min(x1, x2), maxOf(y1, y2)),
                                                end = Offset(maxOf(x1, x2), maxOf(y1, y2)),
                                                strokeWidth = 3.5f
                                            )
                                            EditorTool.STRIKETHROUGH -> {
                                                val midY = (y1 + y2) / 2f
                                                drawLine(
                                                    color = Color(ann.colorArgb),
                                                    start = Offset(min(x1, x2), midY),
                                                    end = Offset(maxOf(x1, x2), midY),
                                                    strokeWidth = 3.5f
                                                )
                                            }
                                            else -> Unit
                                        }
                                    }
                                    is PdfOverlayAnnotation.FreehandOverlay -> {
                                        if (ann.normPoints.size >= 2) {
                                            val p = Path().apply {
                                                moveTo(ann.normPoints.first().first * size.width, ann.normPoints.first().second * size.height)
                                                for (i in 1 until ann.normPoints.size) {
                                                    lineTo(ann.normPoints[i].first * size.width, ann.normPoints[i].second * size.height)
                                                }
                                            }
                                            drawPath(p, color = Color(ann.colorArgb), style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                        }
                                    }
                                    is PdfOverlayAnnotation.TextOverlay -> {
                                        drawCircle(Color(ann.colorArgb), radius = 8f, center = Offset(ann.normX * size.width, ann.normY * size.height))
                                    }
                                    is PdfOverlayAnnotation.CommentOverlay -> {
                                        drawCircle(Color(0xFFF59E0B), radius = 12f, center = Offset(ann.normX * size.width, ann.normY * size.height))
                                    }
                                    is PdfOverlayAnnotation.ImageOverlay -> {
                                        drawRect(
                                            color = BrandBlue,
                                            topLeft = Offset(ann.normX * size.width, ann.normY * size.height),
                                            size = Size(ann.normWidth * size.width, ann.normHeight * size.height),
                                            style = Stroke(width = 3f)
                                        )
                                    }
                                    is PdfOverlayAnnotation.SignatureOverlay -> {
                                        drawRect(
                                            color = BrandPurple,
                                            topLeft = Offset(ann.normX * size.width, ann.normY * size.height),
                                            size = Size(ann.normWidth * size.width, ann.normHeight * size.height),
                                            style = Stroke(width = 3f)
                                        )
                                    }
                                    is PdfOverlayAnnotation.WatermarkOverlay -> {
                                        drawRect(
                                            color = Color(ann.colorArgb).copy(alpha = 0.14f),
                                            topLeft = Offset(size.width * 0.2f, size.height * 0.44f),
                                            size = Size(size.width * 0.6f, size.height * 0.12f)
                                        )
                                    }
                                }
                            }

                            // Active drag previews
                            if (activePoints.size >= 2) {
                                val p = Path().apply {
                                    moveTo(activePoints.first().first * size.width, activePoints.first().second * size.height)
                                    for (i in 1 until activePoints.size) {
                                        lineTo(activePoints[i].first * size.width, activePoints[i].second * size.height)
                                    }
                                }
                                drawPath(p, color = Color(state.selectedColorArgb), style = Stroke(width = 5f, cap = StrokeCap.Round))
                            }
                            val ds = dragStartNorm
                            val de = dragEndNorm
                            if (ds != null && de != null) {
                                drawRect(
                                    color = Color(state.selectedColorArgb).copy(alpha = 0.25f),
                                    topLeft = Offset(min(ds.first, de.first) * size.width, min(ds.second, de.second) * size.height),
                                    size = Size(abs(de.first - ds.first) * size.width, abs(de.second - ds.second) * size.height)
                                )
                            }
                        }
                    }
                }
            } else {
                Text("Unable to render PDF page preview.", style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Status / Save Result
        ProcessingProgressBanner(isVisible = state.isSaving, message = "Applying annotations & saving PDF...")
        ErrorBannerCard(errorMessage = state.errorMessage, onDismiss = viewModel::clearError)

        state.savedFile?.let { file ->
            state.savedDocument?.let { savedDoc ->
                Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    ResultOutputCard(
                        outputFile = file,
                        title = "Edited PDF Saved",
                        summaryLine = "${state.annotations.size} annotations • ${savedDoc.pageCount} pages",
                        onExportToUri = viewModel::exportSavedToUri,
                        onShare = viewModel::shareCurrentOrSaved,
                        onPrint = {
                            context.findActivity()?.let { fileStorageManager.printPdfFile(it, file) }
                        },
                        onOpenPreview = { onOpenNewDoc(savedDoc.id) }
                    )
                }
            }
        }

        // Bottom Toolbar: Page Controls + Tools + Save
        Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                // Row 1: Pagination, Zoom, Page Rotate/Move/Delete, Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.goToPagePosition(state.currentPagePosition - 1) },
                            enabled = state.currentPagePosition > 0
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Page")
                        }
                        Text(
                            text = "${state.currentPagePosition + 1}/${state.pageOrder.size}",
                            style = MaterialTheme.typography.labelLarge
                        )
                        IconButton(
                            onClick = { viewModel.goToPagePosition(state.currentPagePosition + 1) },
                            enabled = state.currentPagePosition < state.pageOrder.lastIndex
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Page")
                        }
                        IconButton(onClick = { zoomScale = (zoomScale - 0.25f).coerceAtLeast(0.75f) }) {
                            Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out")
                        }
                        IconButton(onClick = { zoomScale = (zoomScale + 0.25f).coerceAtMost(2.5f) }) {
                            Icon(Icons.Default.ZoomIn, contentDescription = "Zoom in")
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = viewModel::rotateCurrentPage) {
                            Icon(Icons.Default.RotateRight, contentDescription = "Rotate page")
                        }
                        IconButton(onClick = { viewModel.moveCurrentPage(1) }) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = "Move page")
                        }
                        IconButton(onClick = viewModel::deleteCurrentPage) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete page")
                        }
                        Button(
                            onClick = viewModel::saveEditedPdf,
                            enabled = !state.isSaving,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("editor_save_pdf_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save")
                        }
                    }
                }

                // Shape / Color sub-row if drawing or shaping
                if (state.activeTool != EditorTool.VIEW) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            Color(0xFF7047EB),
                            Color(0xFF3478F6),
                            Color(0xFF10B981),
                            Color(0xFFEF4444),
                            Color(0xFF141726)
                        ).forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(
                                        if (state.selectedColorArgb == c.toArgb()) 2.dp else 0.dp,
                                        Color.White,
                                        CircleShape
                                    )
                                    .clickable { viewModel.setSelectedColor(c.toArgb()) }
                            )
                        }
                        if (state.activeTool == EditorTool.SHAPE) {
                            Spacer(Modifier.width(8.dp))
                            ShapeKind.entries.forEach { kind ->
                                FilterChip(
                                    selected = selectedShapeKind == kind,
                                    onClick = { selectedShapeKind = kind },
                                    label = { Text(kind.name.lowercase().replaceFirstChar { it.uppercase() }) }
                                )
                            }
                        }
                    }
                }

                // Row 2: Editor Tools Strip
                val toolChips = listOf(
                    Triple(EditorTool.VIEW, "View", Icons.Default.Visibility),
                    Triple(EditorTool.TEXT, "Text", Icons.Default.TextFields),
                    Triple(EditorTool.IMAGE, "Image", Icons.Default.AddPhotoAlternate),
                    Triple(EditorTool.SHAPE, "Shape", Icons.Default.CropSquare),
                    Triple(EditorTool.HIGHLIGHT, "Highlight", Icons.Default.Highlight),
                    Triple(EditorTool.UNDERLINE, "Underline", Icons.Default.FormatUnderlined),
                    Triple(EditorTool.STRIKETHROUGH, "Strike", Icons.Default.StrikethroughS),
                    Triple(EditorTool.DRAW, "Draw", Icons.Default.Brush),
                    Triple(EditorTool.COMMENT, "Comment", Icons.Default.Comment),
                    Triple(EditorTool.SIGNATURE, "Sign", Icons.Default.Draw),
                    Triple(EditorTool.DATE_STAMP, "Date", Icons.Default.CalendarToday),
                    Triple(EditorTool.WATERMARK, "Watermark", Icons.Default.WaterDrop)
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(toolChips) { (tool, label, icon) ->
                        FilterChip(
                            selected = state.activeTool == tool,
                            onClick = {
                                when (tool) {
                                    EditorTool.IMAGE -> imageOverlayPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                    EditorTool.WATERMARK -> showWatermarkDialog = true
                                    EditorTool.SIGNATURE -> showSignatureDialog = true
                                    else -> viewModel.setActiveTool(tool)
                                }
                            },
                            leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    }

    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("Add Text Annotation") },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text("Text (English or বাংলা)") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val pos = pendingTapNormalized ?: (0.2f to 0.2f)
                    if (textInput.isNotBlank()) {
                        viewModel.addAnnotation(
                            PdfOverlayAnnotation.TextOverlay(
                                id = UUID.randomUUID().toString(),
                                pageIndex = state.currentOriginalPageIndex,
                                normX = pos.first,
                                normY = pos.second,
                                text = textInput,
                                colorArgb = state.selectedColorArgb
                            )
                        )
                    }
                    textInput = ""
                    showTextDialog = false
                }) { Text("Place") }
            },
            dismissButton = { TextButton(onClick = { showTextDialog = false }) { Text("Cancel") } }
        )
    }

    if (showCommentDialog) {
        AlertDialog(
            onDismissRequest = { showCommentDialog = false },
            title = { Text("Add Comment Note") },
            text = {
                OutlinedTextField(
                    value = commentInput,
                    onValueChange = { commentInput = it },
                    label = { Text("Comment") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val pos = pendingTapNormalized ?: (0.25f to 0.25f)
                    if (commentInput.isNotBlank()) {
                        viewModel.addAnnotation(
                            PdfOverlayAnnotation.CommentOverlay(
                                id = UUID.randomUUID().toString(),
                                pageIndex = state.currentOriginalPageIndex,
                                normX = pos.first,
                                normY = pos.second,
                                commentText = commentInput
                            )
                        )
                    }
                    commentInput = ""
                    showCommentDialog = false
                }) { Text("Add Note") }
            },
            dismissButton = { TextButton(onClick = { showCommentDialog = false }) { Text("Cancel") } }
        )
    }

    if (showWatermarkDialog) {
        AlertDialog(
            onDismissRequest = { showWatermarkDialog = false },
            title = { Text("Add Page Watermark") },
            text = {
                OutlinedTextField(
                    value = watermarkInput,
                    onValueChange = { watermarkInput = it },
                    label = { Text("Watermark Text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (watermarkInput.isNotBlank()) {
                        viewModel.addAnnotation(
                            PdfOverlayAnnotation.WatermarkOverlay(
                                id = UUID.randomUUID().toString(),
                                pageIndex = -1,
                                text = watermarkInput,
                                colorArgb = state.selectedColorArgb
                            )
                        )
                    }
                    showWatermarkDialog = false
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showWatermarkDialog = false }) { Text("Cancel") } }
        )
    }

    if (showSignatureDialog) {
        SignaturePadDialog(
            onDismiss = { showSignatureDialog = false },
            onConfirmStrokes = { strokes ->
                viewModel.saveSignatureStrokesAndPlace(strokes)
                showSignatureDialog = false
            }
        )
    }
}

@Composable
fun SignaturePadDialog(
    onDismiss: () -> Unit,
    onConfirmStrokes: (List<List<Pair<Float, Float>>>) -> Unit
) {
    val strokes = remember { mutableStateListOf<List<Pair<Float, Float>>>() }
    val currentStroke = remember { mutableStateListOf<Pair<Float, Float>>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Draw Signature") },
        text = {
            Column {
                Text(
                    "Sign inside the box below using your finger or stylus.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { start ->
                                        currentStroke.clear()
                                        currentStroke.add((start.x / size.width) to (start.y / size.height))
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentStroke.add(
                                            (change.position.x / size.width).coerceIn(0f, 1f) to
                                                (change.position.y / size.height).coerceIn(0f, 1f)
                                        )
                                    },
                                    onDragEnd = {
                                        if (currentStroke.size >= 2) {
                                            strokes.add(currentStroke.toList())
                                        }
                                        currentStroke.clear()
                                    }
                                )
                            }
                    ) {
                        val all = strokes + listOf(currentStroke.toList())
                        for (st in all) {
                            if (st.size >= 2) {
                                val p = Path().apply {
                                    moveTo(st.first().first * size.width, st.first().second * size.height)
                                    for (i in 1 until st.size) {
                                        lineTo(st[i].first * size.width, st[i].second * size.height)
                                    }
                                }
                                drawPath(p, color = Color(0xFF141726), style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmStrokes(strokes.toList()) },
                enabled = strokes.isNotEmpty()
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Use Signature")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { strokes.clear(); currentStroke.clear() }) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
