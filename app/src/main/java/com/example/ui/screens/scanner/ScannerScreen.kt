package com.example.ui.screens.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.pdf.FileStorageManager
import com.example.domain.model.ScanFilter
import com.example.ui.components.CenteredResponsiveContainer
import com.example.ui.components.ErrorBannerCard
import com.example.ui.components.PdfToolTopBar
import com.example.ui.components.ProcessingProgressBanner
import com.example.ui.components.ResultOutputCard
import com.example.ui.components.ToolInstructionCard
import com.example.ui.components.findActivity
import com.example.ui.theme.AccentScan
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandPurple
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel,
    fileStorageManager: FileStorageManager,
    onOpenPreview: (Long) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val photoImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.addImageFromUri(uri)
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PdfToolTopBar(
            title = "Document Scanner",
            subtitle = "CameraX capture, edge crop & filters (${state.pages.size} pages)",
            onBack = onBack
        )

        if (state.isEditingPage && state.activePage != null) {
            val page = state.activePage!!
            var showCornerAdjuster by remember { mutableStateOf(false) }

            CenteredResponsiveContainer(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Adjust Page ${state.activePageIndex + 1}", style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = showCornerAdjuster,
                                    onClick = { showCornerAdjuster = !showCornerAdjuster },
                                    leadingIcon = { Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text(if (showCornerAdjuster) "Preview" else "Crop Corners") }
                                )
                                Button(onClick = viewModel::closePageEditor) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Done")
                                }
                            }
                        }
                    }

                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.75f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.Black)
                        ) {
                            AsyncImage(
                                model = if (showCornerAdjuster) page.originalFile else page.processedFile,
                                contentDescription = "Scanned page",
                                contentScale = if (showCornerAdjuster) ContentScale.FillBounds else ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )

                            if (showCornerAdjuster) {
                                var draggingCorner by remember { mutableIntStateOf(-1) }
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(page.id) {
                                            detectDragGestures(
                                                onDragStart = { start ->
                                                    val nx = start.x / size.width
                                                    val ny = start.y / size.height
                                                    draggingCorner = page.corners.indices.minByOrNull { idx ->
                                                        val c = page.corners[idx]
                                                        hypot(c.first - nx, c.second - ny)
                                                    } ?: -1
                                                },
                                                onDrag = { change, _ ->
                                                    change.consume()
                                                    if (draggingCorner != -1) {
                                                        viewModel.updateCorner(
                                                            draggingCorner,
                                                            change.position.x / size.width,
                                                            change.position.y / size.height
                                                        )
                                                    }
                                                },
                                                onDragEnd = {
                                                    draggingCorner = -1
                                                    viewModel.reprocessActivePage()
                                                }
                                            )
                                        }
                                ) {
                                    val pts = page.corners.map { Offset(it.first * size.width, it.second * size.height) }
                                    if (pts.size == 4) {
                                        val path = Path().apply {
                                            moveTo(pts[0].x, pts[0].y)
                                            lineTo(pts[1].x, pts[1].y)
                                            lineTo(pts[2].x, pts[2].y)
                                            lineTo(pts[3].x, pts[3].y)
                                            close()
                                        }
                                        drawPath(path, color = BrandBlue, style = Stroke(width = 5f))
                                        pts.forEach { pt ->
                                            drawCircle(Color.White, radius = 16f, center = pt)
                                            drawCircle(BrandPurple, radius = 10f, center = pt)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ScanFilter.entries) { f ->
                                FilterChip(
                                    selected = page.filter == f,
                                    onClick = { viewModel.setFilter(f) },
                                    label = { Text(f.label) }
                                )
                            }
                            item {
                                FilledTonalButton(onClick = viewModel::rotateActivePage) {
                                    Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Rotate")
                                }
                            }
                        }
                    }

                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Brightness: ${page.brightness.roundToInt()}", style = MaterialTheme.typography.labelLarge)
                                Slider(
                                    value = page.brightness,
                                    onValueChange = viewModel::setBrightness,
                                    onValueChangeFinished = viewModel::reprocessActivePage,
                                    valueRange = -50f..50f
                                )
                                Text("Contrast: ${"%.2f".format(page.contrast)}x", style = MaterialTheme.typography.labelLarge)
                                Slider(
                                    value = page.contrast,
                                    onValueChange = viewModel::setContrast,
                                    onValueChangeFinished = viewModel::reprocessActivePage,
                                    valueRange = 0.6f..2.0f
                                )
                            }
                        }
                    }
                }
            }
            return@Column
        }

        CenteredResponsiveContainer(modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    if (!hasCameraPermission) {
                        ToolInstructionCard(
                            icon = Icons.Default.DocumentScanner,
                            title = "Camera Permission for Live Scanning",
                            description = "Allow camera access to scan physical documents with automatic edge detection & perspective correction, or import an existing document photo below.",
                            accentColor = AccentScan
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("grant_camera_button")
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Enable Camera")
                            }
                            OutlinedButton(
                                onClick = {
                                    photoImportLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Import Photo")
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color.Black)
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    val previewView = PreviewView(ctx)
                                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                                    providerFuture.addListener({
                                        runCatching {
                                            val provider = providerFuture.get()
                                            val preview = Preview.Builder().build().also {
                                                it.surfaceProvider = previewView.surfaceProvider
                                            }
                                            val capture = ImageCapture.Builder()
                                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                                .build()
                                            imageCapture = capture
                                            provider.unbindAll()
                                            provider.bindToLifecycle(
                                                lifecycleOwner,
                                                CameraSelector.DEFAULT_BACK_CAMERA,
                                                preview,
                                                capture
                                            )
                                        }
                                    }, ContextCompat.getMainExecutor(ctx))
                                    previewView
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        photoImportLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Import Photo")
                                    Spacer(Modifier.width(6.dp))
                                    Text("Gallery")
                                }

                                Button(
                                    onClick = {
                                        val capture = imageCapture ?: return@Button
                                        val outFile = fileStorageManager.createTempFile("cam_raw", "jpg")
                                        val opts = ImageCapture.OutputFileOptions.Builder(outFile).build()
                                        capture.takePicture(
                                            opts,
                                            cameraExecutor,
                                            object : ImageCapture.OnImageSavedCallback {
                                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                                    viewModel.addCapturedFile(outFile)
                                                }

                                                override fun onError(exception: ImageCaptureException) {
                                                    // Handled gracefully
                                                }
                                            }
                                        )
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier.size(64.dp).testTag("camera_capture_button")
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = "Capture Document")
                                }
                            }
                        }
                    }
                }

                if (state.pages.isNotEmpty()) {
                    item {
                        Text("Scanned Pages (${state.pages.size}) — Tap any page to crop or filter", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(state.pages, key = { _, p -> p.id }) { idx, p ->
                                Box(
                                    modifier = Modifier
                                        .width(106.dp)
                                        .aspectRatio(0.75f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { viewModel.selectPageForEditing(idx) }
                                ) {
                                    AsyncImage(
                                        model = p.processedFile,
                                        contentDescription = "Page ${idx + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    IconButton(
                                        onClick = { viewModel.removePage(idx) },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.6f))
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(15.dp))
                                    }
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.65f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                                    ) {
                                        Text(
                                            text = "P.${idx + 1}",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = state.documentTitle,
                            onValueChange = viewModel::updateTitle,
                            label = { Text("Scanned PDF Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = viewModel::saveScannedPdf,
                            enabled = !state.isProcessing,
                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_scanned_pdf_button")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Save ${state.pages.size}-Page Scan as PDF")
                        }
                    }
                }

                item {
                    ProcessingProgressBanner(isVisible = state.isProcessing, message = "Processing scanned pages...")
                    ErrorBannerCard(errorMessage = state.errorMessage, onDismiss = viewModel::clearError)
                }

                val outFile = state.savedFile
                val savedDoc = state.savedDocument
                if (outFile != null && savedDoc != null) {
                    item {
                        ResultOutputCard(
                            outputFile = outFile,
                            title = "Scanned PDF Saved",
                            summaryLine = "${savedDoc.pageCount} pages",
                            onExportToUri = viewModel::exportToUri,
                            onShare = viewModel::shareOutput,
                            onPrint = {
                                context.findActivity()?.let { fileStorageManager.printPdfFile(it, outFile) }
                            },
                            onOpenPreview = { onOpenPreview(savedDoc.id) }
                        )
                    }
                }
            }
        }
    }
}
