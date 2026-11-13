package com.example.ui.screens.scanner

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.db.PdfDocumentEntity
import com.example.data.pdf.DocumentScannerProcessor
import com.example.domain.model.PageOrientation
import com.example.domain.model.PageSizeOption
import com.example.domain.model.ScanFilter
import com.example.domain.model.ScannedPageItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class ScannerUiState(
    val pages: List<ScannedPageItem> = emptyList(),
    val activePageIndex: Int = -1,
    val isEditingPage: Boolean = false,
    val isProcessing: Boolean = false,
    val documentTitle: String = "Scanned_Document",
    val errorMessage: String? = null,
    val savedDocument: PdfDocumentEntity? = null,
    val savedFile: File? = null
) {
    val activePage: ScannedPageItem? get() = pages.getOrNull(activePageIndex)
}

class ScannerViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    fun addCapturedFile(rawFile: File) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            val corners = container.scannerProcessor.detectDocumentEdges(rawFile)
            val processed = container.fileStorageManager.createTempFile("scan_proc", "jpg")
            container.scannerProcessor.processScannedPage(
                sourceFile = rawFile,
                outputFile = processed,
                corners = corners,
                rotationDegrees = 0,
                filter = ScanFilter.COLOR_ENHANCED,
                brightness = 0f,
                contrast = 1.0f
            ).fold(
                onSuccess = { out ->
                    val item = ScannedPageItem(
                        id = UUID.randomUUID().toString(),
                        originalFile = rawFile,
                        processedFile = out,
                        filter = ScanFilter.COLOR_ENHANCED,
                        corners = corners
                    )
                    _uiState.update { state ->
                        val nextList = state.pages + item
                        state.copy(
                            pages = nextList,
                            activePageIndex = nextList.lastIndex,
                            isEditingPage = true,
                            isProcessing = false
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isProcessing = false, errorMessage = err.message) }
                }
            )
        }
    }

    fun addImageFromUri(uri: Uri) {
        viewModelScope.launch {
            container.fileStorageManager.copyUriToTempFile(uri, "jpg").fold(
                onSuccess = { (file, _) -> addCapturedFile(file) },
                onFailure = { err -> _uiState.update { it.copy(errorMessage = err.message) } }
            )
        }
    }

    fun selectPageForEditing(index: Int) {
        _uiState.update { it.copy(activePageIndex = index, isEditingPage = true) }
    }

    fun closePageEditor() {
        _uiState.update { it.copy(isEditingPage = false) }
    }

    fun updateCorner(cornerIdx: Int, normX: Float, normY: Float) {
        val state = _uiState.value
        val page = state.activePage ?: return
        val nextCorners = page.corners.toMutableList()
        if (cornerIdx !in nextCorners.indices) return
        nextCorners[cornerIdx] = normX.coerceIn(0.02f, 0.98f) to normY.coerceIn(0.02f, 0.98f)
        updateActivePage(page.copy(corners = nextCorners), reprocess = false)
    }

    fun reprocessActivePage() {
        val page = _uiState.value.activePage ?: return
        reprocessPage(page)
    }

    fun setFilter(filter: ScanFilter) {
        val page = _uiState.value.activePage ?: return
        reprocessPage(page.copy(filter = filter))
    }

    fun rotateActivePage() {
        val page = _uiState.value.activePage ?: return
        reprocessPage(page.copy(rotationDegrees = (page.rotationDegrees + 90) % 360))
    }

    fun setBrightness(value: Float) {
        val page = _uiState.value.activePage ?: return
        updateActivePage(page.copy(brightness = value), reprocess = false)
    }

    fun setContrast(value: Float) {
        val page = _uiState.value.activePage ?: return
        updateActivePage(page.copy(contrast = value), reprocess = false)
    }

    private fun updateActivePage(updated: ScannedPageItem, reprocess: Boolean) {
        _uiState.update { state ->
            val idx = state.activePageIndex
            if (idx !in state.pages.indices) return@update state
            val list = state.pages.toMutableList()
            list[idx] = updated
            state.copy(pages = list)
        }
        if (reprocess) reprocessPage(updated)
    }

    private fun reprocessPage(page: ScannedPageItem) {
        val idx = _uiState.value.activePageIndex
        if (idx !in _uiState.value.pages.indices) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val nextFile = container.fileStorageManager.createTempFile("scan_proc", "jpg")
            container.scannerProcessor.processScannedPage(
                sourceFile = page.originalFile,
                outputFile = nextFile,
                corners = page.corners,
                rotationDegrees = page.rotationDegrees,
                filter = page.filter,
                brightness = page.brightness,
                contrast = page.contrast
            ).fold(
                onSuccess = { out ->
                    val updated = page.copy(processedFile = out)
                    _uiState.update { state ->
                        val list = state.pages.toMutableList()
                        if (idx in list.indices) list[idx] = updated
                        state.copy(pages = list, isProcessing = false)
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isProcessing = false, errorMessage = err.message) }
                }
            )
        }
    }

    fun removePage(index: Int) {
        _uiState.update { state ->
            val list = state.pages.toMutableList()
            if (index in list.indices) list.removeAt(index)
            state.copy(
                pages = list,
                activePageIndex = list.lastIndex,
                isEditingPage = false
            )
        }
    }

    fun updateTitle(title: String) = _uiState.update { it.copy(documentTitle = title) }
    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    fun saveScannedPdf() {
        val state = _uiState.value
        if (state.pages.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Capture or import at least one document page.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            val outFile = container.fileStorageManager.createVaultFile(state.documentTitle.ifBlank { "Scanned_Doc" })
            val res = container.creationEngine.createFromImageFiles(
                outputFile = outFile,
                imageFiles = state.pages.map { it.processedFile },
                pageSize = PageSizeOption.A4,
                orientation = PageOrientation.PORTRAIT,
                marginPt = 16f,
                includePageNumbers = true
            )
            res.fold(
                onSuccess = { file ->
                    val doc = container.repository.registerGeneratedPdf(
                        file = file,
                        title = state.documentTitle.ifBlank { file.nameWithoutExtension },
                        originTag = "scanned",
                        actionType = "Scan Document",
                        inputName = "${state.pages.size} scanned pages",
                        details = "${state.pages.size} pages"
                    )
                    _uiState.update { it.copy(isProcessing = false, savedDocument = doc, savedFile = file, isEditingPage = false) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isProcessing = false, errorMessage = err.message ?: "Failed saving scanned PDF") }
                }
            )
        }
    }

    fun exportToUri(uri: Uri) {
        val file = _uiState.value.savedFile ?: return
        viewModelScope.launch { container.fileStorageManager.exportFileToUri(file, uri) }
    }

    fun shareOutput() {
        val file = _uiState.value.savedFile ?: return
        container.fileStorageManager.shareFile(file)
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ScannerViewModel(container) as T
        }
    }
}
