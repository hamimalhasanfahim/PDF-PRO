package com.example.ui.screens.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.db.PdfDocumentEntity
import com.example.domain.model.EditorTool
import com.example.domain.model.PdfOverlayAnnotation
import com.example.domain.model.PdfSearchMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class EditorSnapshot(
    val annotations: List<PdfOverlayAnnotation>,
    val pageOrder: List<Int>,
    val pageRotations: Map<Int, Int>
)

data class PdfEditorUiState(
    val document: PdfDocumentEntity? = null,
    val currentPagePosition: Int = 0,
    val pageOrder: List<Int> = emptyList(),
    val pageRotations: Map<Int, Int> = emptyMap(),
    val pageBitmap: Bitmap? = null,
    val activeTool: EditorTool = EditorTool.VIEW,
    val selectedColorArgb: Int = 0xFF7047EB.toInt(),
    val annotations: List<PdfOverlayAnnotation> = emptyList(),
    val undoStack: List<EditorSnapshot> = emptyList(),
    val redoStack: List<EditorSnapshot> = emptyList(),
    val searchQuery: String = "",
    val searchMatches: List<PdfSearchMatch> = emptyList(),
    val isSearching: Boolean = false,
    val isLoadingPage: Boolean = false,
    val isSaving: Boolean = false,
    val passwordPromptNeeded: Boolean = false,
    val unlockedPassword: String? = null,
    val errorMessage: String? = null,
    val savedDocument: PdfDocumentEntity? = null,
    val savedFile: File? = null
) {
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val currentOriginalPageIndex: Int get() = pageOrder.getOrElse(currentPagePosition) { 0 }
}

class PdfEditorViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(PdfEditorUiState())
    val uiState: StateFlow<PdfEditorUiState> = _uiState.asStateFlow()

    fun loadDocumentById(docId: Long, startInEditMode: Boolean) {
        if (docId <= 0L) return
        viewModelScope.launch {
            val doc = container.repository.getDocumentById(docId) ?: run {
                _uiState.update { it.copy(errorMessage = "Document not found.") }
                return@launch
            }
            val file = File(doc.localPath)
            if (!file.exists()) {
                _uiState.update { it.copy(errorMessage = "PDF file is missing on storage.") }
                return@launch
            }
            container.repository.markDocumentOpened(doc)
            val count = container.rendererHelper.getPageCount(file).coerceAtLeast(doc.pageCount).coerceAtLeast(1)
            val order = (0 until count).toList()
            _uiState.update {
                it.copy(
                    document = doc,
                    pageOrder = order,
                    pageRotations = emptyMap(),
                    currentPagePosition = 0,
                    annotations = emptyList(),
                    undoStack = emptyList(),
                    redoStack = emptyList(),
                    activeTool = if (startInEditMode) EditorTool.TEXT else EditorTool.VIEW,
                    savedDocument = null,
                    savedFile = null,
                    errorMessage = null
                )
            }
            renderCurrentPage()
        }
    }

    fun selectAndImportPdf(uri: Uri) {
        viewModelScope.launch {
            container.repository.importPdf(uri).fold(
                onSuccess = { doc -> loadDocumentById(doc.id, false) },
                onFailure = { err -> _uiState.update { it.copy(errorMessage = err.message ?: "Failed to import PDF") } }
            )
        }
    }

    fun setActiveTool(tool: EditorTool) = _uiState.update { it.copy(activeTool = tool) }
    fun setSelectedColor(argb: Int) = _uiState.update { it.copy(selectedColorArgb = argb) }
    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    fun goToPagePosition(pos: Int) {
        val maxPos = (_uiState.value.pageOrder.size - 1).coerceAtLeast(0)
        val clamped = pos.coerceIn(0, maxPos)
        if (clamped != _uiState.value.currentPagePosition) {
            _uiState.update { it.copy(currentPagePosition = clamped) }
            renderCurrentPage()
        }
    }

    private fun renderCurrentPage() {
        val state = _uiState.value
        val doc = state.document ?: return
        val file = File(doc.localPath)
        val origIdx = state.currentOriginalPageIndex
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPage = true) }
            val bmp = container.rendererHelper.renderPage(file, origIdx, 1400)
            val rot = state.pageRotations[origIdx] ?: 0
            val finalBmp = if (bmp != null && rot != 0) {
                withContext(Dispatchers.Default) {
                    val m = android.graphics.Matrix().apply { postRotate(rot.toFloat()) }
                    val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                    if (rotated !== bmp) bmp.recycle()
                    rotated
                }
            } else bmp

            _uiState.update {
                it.copy(
                    pageBitmap = finalBmp,
                    isLoadingPage = false,
                    passwordPromptNeeded = finalBmp == null && doc.isEncrypted
                )
            }
        }
    }

    private fun pushSnapshotAndMutate(mutator: (PdfEditorUiState) -> PdfEditorUiState) {
        _uiState.update { state ->
            val snapshot = EditorSnapshot(
                annotations = state.annotations,
                pageOrder = state.pageOrder,
                pageRotations = state.pageRotations
            )
            mutator(
                state.copy(
                    undoStack = (state.undoStack + snapshot).takeLast(30),
                    redoStack = emptyList()
                )
            )
        }
    }

    fun undo() {
        val state = _uiState.value
        val prev = state.undoStack.lastOrNull() ?: return
        val currentSnapshot = EditorSnapshot(state.annotations, state.pageOrder, state.pageRotations)
        _uiState.update {
            it.copy(
                annotations = prev.annotations,
                pageOrder = prev.pageOrder,
                pageRotations = prev.pageRotations,
                currentPagePosition = it.currentPagePosition.coerceIn(0, (prev.pageOrder.size - 1).coerceAtLeast(0)),
                undoStack = it.undoStack.dropLast(1),
                redoStack = it.redoStack + currentSnapshot
            )
        }
        renderCurrentPage()
    }

    fun redo() {
        val state = _uiState.value
        val next = state.redoStack.lastOrNull() ?: return
        val currentSnapshot = EditorSnapshot(state.annotations, state.pageOrder, state.pageRotations)
        _uiState.update {
            it.copy(
                annotations = next.annotations,
                pageOrder = next.pageOrder,
                pageRotations = next.pageRotations,
                currentPagePosition = it.currentPagePosition.coerceIn(0, (next.pageOrder.size - 1).coerceAtLeast(0)),
                undoStack = it.undoStack + currentSnapshot,
                redoStack = it.redoStack.dropLast(1)
            )
        }
        renderCurrentPage()
    }

    fun addAnnotation(annotation: PdfOverlayAnnotation) {
        pushSnapshotAndMutate { state ->
            state.copy(annotations = state.annotations + annotation)
        }
    }

    fun addDateStampAt(normX: Float, normY: Float) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        addAnnotation(
            PdfOverlayAnnotation.TextOverlay(
                id = UUID.randomUUID().toString(),
                pageIndex = _uiState.value.currentOriginalPageIndex,
                normX = normX,
                normY = normY,
                text = "Date: $stamp",
                fontSizePt = 13f,
                colorArgb = _uiState.value.selectedColorArgb
            )
        )
    }

    fun addImageOverlayFromUri(uri: Uri) {
        viewModelScope.launch {
            val copied = container.fileStorageManager.copyUriToTempFile(uri, "png")
            copied.onSuccess { (tmp, _) ->
                addAnnotation(
                    PdfOverlayAnnotation.ImageOverlay(
                        id = UUID.randomUUID().toString(),
                        pageIndex = _uiState.value.currentOriginalPageIndex,
                        normX = 0.25f,
                        normY = 0.25f,
                        normWidth = 0.35f,
                        normHeight = 0.25f,
                        imagePath = tmp.absolutePath
                    )
                )
            }
        }
    }

    fun saveSignatureStrokesAndPlace(strokes: List<List<Pair<Float, Float>>>) {
        if (strokes.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val bmp = Bitmap.createBitmap(600, 260, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.TRANSPARENT)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(20, 23, 38)
                strokeWidth = 7f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            for (stroke in strokes) {
                if (stroke.size >= 2) {
                    val path = Path()
                    path.moveTo(stroke.first().first * 600f, stroke.first().second * 260f)
                    for (i in 1 until stroke.size) {
                        path.lineTo(stroke[i].first * 600f, stroke[i].second * 260f)
                    }
                    canvas.drawPath(path, paint)
                }
            }
            val sigFile = container.fileStorageManager.createTempFile("sig", "png")
            FileOutputStream(sigFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()

            addAnnotation(
                PdfOverlayAnnotation.SignatureOverlay(
                    id = UUID.randomUUID().toString(),
                    pageIndex = _uiState.value.currentOriginalPageIndex,
                    normX = 0.55f,
                    normY = 0.72f,
                    normWidth = 0.32f,
                    normHeight = 0.14f,
                    signatureImagePath = sigFile.absolutePath
                )
            )
        }
    }

    fun rotateCurrentPage() {
        val origIdx = _uiState.value.currentOriginalPageIndex
        pushSnapshotAndMutate { state ->
            val current = state.pageRotations[origIdx] ?: 0
            val nextMap = state.pageRotations.toMutableMap().apply {
                put(origIdx, (current + 90) % 360)
            }
            state.copy(pageRotations = nextMap)
        }
        renderCurrentPage()
    }

    fun deleteCurrentPage() {
        val state = _uiState.value
        if (state.pageOrder.size <= 1) {
            _uiState.update { it.copy(errorMessage = "Cannot delete the only remaining page.") }
            return
        }
        val pos = state.currentPagePosition
        pushSnapshotAndMutate { s ->
            val updated = s.pageOrder.toMutableList().apply { removeAt(pos) }
            s.copy(
                pageOrder = updated,
                currentPagePosition = pos.coerceIn(0, updated.lastIndex)
            )
        }
        renderCurrentPage()
    }

    fun moveCurrentPage(delta: Int) {
        val state = _uiState.value
        val from = state.currentPagePosition
        val to = (from + delta).coerceIn(0, state.pageOrder.lastIndex)
        if (from == to) return
        pushSnapshotAndMutate { s ->
            val mutable = s.pageOrder.toMutableList()
            val item = mutable.removeAt(from)
            mutable.add(to, item)
            s.copy(pageOrder = mutable, currentPagePosition = to)
        }
    }

    fun updateSearchQuery(q: String) = _uiState.update { it.copy(searchQuery = q) }

    fun runSearch() {
        val doc = _uiState.value.document ?: return
        val q = _uiState.value.searchQuery
        if (q.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            container.toolsEngine.searchInPdf(File(doc.localPath), q).fold(
                onSuccess = { matches -> _uiState.update { it.copy(isSearching = false, searchMatches = matches) } },
                onFailure = { err -> _uiState.update { it.copy(isSearching = false, errorMessage = err.message) } }
            )
        }
    }

    fun saveEditedPdf() {
        val state = _uiState.value
        val doc = state.document ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val outFile = container.fileStorageManager.createVaultFile("${doc.title}_edited")
            val res = container.editorEngine.applyEdits(
                sourceFile = File(doc.localPath),
                outputFile = outFile,
                annotations = state.annotations,
                pageOrderZeroBased = state.pageOrder,
                pageRotations = state.pageRotations,
                password = state.unlockedPassword
            )
            res.fold(
                onSuccess = { saved ->
                    val registered = container.repository.registerGeneratedPdf(
                        file = saved,
                        title = "${doc.title} (Edited)",
                        originTag = "edited",
                        actionType = "Edit PDF",
                        inputName = doc.fileName,
                        originalSizeBytes = doc.sizeBytes,
                        details = "${state.annotations.size} annotations"
                    )
                    _uiState.update { it.copy(isSaving = false, savedDocument = registered, savedFile = saved) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isSaving = false, errorMessage = err.message ?: "Failed saving edits") }
                }
            )
        }
    }

    fun exportSavedToUri(uri: Uri) {
        val file = _uiState.value.savedFile ?: _uiState.value.document?.let { File(it.localPath) } ?: return
        viewModelScope.launch { container.fileStorageManager.exportFileToUri(file, uri) }
    }

    fun shareCurrentOrSaved() {
        val file = _uiState.value.savedFile ?: _uiState.value.document?.let { File(it.localPath) } ?: return
        container.fileStorageManager.shareFile(file)
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PdfEditorViewModel(container) as T
        }
    }
}
