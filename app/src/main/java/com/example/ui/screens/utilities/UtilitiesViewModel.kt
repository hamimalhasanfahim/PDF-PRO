package com.example.ui.screens.utilities

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.db.PdfDocumentEntity
import com.example.domain.model.PdfBookmarkItem
import com.example.domain.model.PdfDiffResult
import com.example.domain.model.PdfMetadataInfo
import com.example.domain.model.PdfSearchMatch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class UtilitiesUiState(
    val leftDoc: PdfDocumentEntity? = null,
    val rightDoc: PdfDocumentEntity? = null,
    val diffResult: PdfDiffResult? = null,
    val activeDoc: PdfDocumentEntity? = null,
    val metadata: PdfMetadataInfo = PdfMetadataInfo(),
    val bookmarks: List<PdfBookmarkItem> = emptyList(),
    val newBookmarkTitle: String = "",
    val newBookmarkPage: Int = 1,
    val pageNumPrefix: String = "Page ",
    val pageNumStart: Int = 1,
    val pageNumAtBottom: Boolean = true,
    val searchQuery: String = "",
    val searchMatches: List<PdfSearchMatch> = emptyList(),
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
    val outputDoc: PdfDocumentEntity? = null,
    val outputFile: File? = null
)

class UtilitiesViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(UtilitiesUiState())
    val uiState: StateFlow<UtilitiesUiState> = _uiState.asStateFlow()

    fun selectLeftDoc(doc: PdfDocumentEntity) = _uiState.update { it.copy(leftDoc = doc, diffResult = null) }
    fun selectRightDoc(doc: PdfDocumentEntity) = _uiState.update { it.copy(rightDoc = doc, diffResult = null) }

    fun importLeft(uri: Uri) {
        viewModelScope.launch {
            container.repository.importPdf(uri).onSuccess { selectLeftDoc(it) }
                .onFailure { err -> _uiState.update { it.copy(errorMessage = err.message) } }
        }
    }

    fun importRight(uri: Uri) {
        viewModelScope.launch {
            container.repository.importPdf(uri).onSuccess { selectRightDoc(it) }
                .onFailure { err -> _uiState.update { it.copy(errorMessage = err.message) } }
        }
    }

    fun selectActiveDocById(docId: Long) {
        if (docId <= 0L) return
        viewModelScope.launch { container.repository.getDocumentById(docId)?.let(::selectActiveDoc) }
    }

    fun importActiveDoc(uri: Uri) {
        viewModelScope.launch {
            container.repository.importPdf(uri).onSuccess { selectActiveDoc(it) }
                .onFailure { err -> _uiState.update { it.copy(errorMessage = err.message) } }
        }
    }

    fun selectActiveDoc(doc: PdfDocumentEntity) {
        _uiState.update { it.copy(activeDoc = doc, isProcessing = true, errorMessage = null, outputDoc = null, outputFile = null) }
        viewModelScope.launch {
            val file = File(doc.localPath)
            val metaRes = container.securityEngine.readMetadata(file)
            val bmRes = container.securityEngine.readBookmarks(file)
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    metadata = metaRes.getOrDefault(PdfMetadataInfo(title = doc.title, pageCount = doc.pageCount, fileSizeBytes = doc.sizeBytes)),
                    bookmarks = bmRes.getOrDefault(emptyList()),
                    errorMessage = metaRes.exceptionOrNull()?.message
                )
            }
        }
    }

    fun comparePdfs() {
        val left = _uiState.value.leftDoc
        val right = _uiState.value.rightDoc
        if (left == null || right == null) {
            _uiState.update { it.copy(errorMessage = "Select two PDF documents to compare.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            container.toolsEngine.comparePdfs(File(left.localPath), File(right.localPath)).fold(
                onSuccess = { diff ->
                    container.repository.logHistorySuccess("Compare PDFs", "${left.fileName} vs ${right.fileName}", "${diff.pageDiffs.size} pages")
                    _uiState.update { it.copy(isProcessing = false, diffResult = diff) }
                },
                onFailure = { err -> _uiState.update { it.copy(isProcessing = false, errorMessage = err.message) } }
            )
        }
    }

    fun updateMetadata(updater: (PdfMetadataInfo) -> PdfMetadataInfo) {
        _uiState.update { it.copy(metadata = updater(it.metadata)) }
    }

    fun updateNewBookmarkTitle(v: String) = _uiState.update { it.copy(newBookmarkTitle = v) }
    fun updateNewBookmarkPage(v: Int) = _uiState.update { it.copy(newBookmarkPage = v.coerceAtLeast(1)) }

    fun addBookmarkItem() {
        val state = _uiState.value
        if (state.newBookmarkTitle.isBlank()) return
        val maxPage = state.metadata.pageCount.coerceAtLeast(1)
        val item = PdfBookmarkItem(
            title = state.newBookmarkTitle.trim(),
            pageIndex = (state.newBookmarkPage - 1).coerceIn(0, maxPage - 1)
        )
        _uiState.update { it.copy(bookmarks = it.bookmarks + item, newBookmarkTitle = "") }
    }

    fun removeBookmarkAt(idx: Int) {
        _uiState.update { state ->
            val list = state.bookmarks.toMutableList()
            if (idx in list.indices) list.removeAt(idx)
            state.copy(bookmarks = list)
        }
    }

    fun saveMetadataAndBookmarks() {
        val state = _uiState.value
        val doc = state.activeDoc ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            val tempMeta = container.fileStorageManager.createTempFile("meta_step", "pdf")
            val outFinal = container.fileStorageManager.createVaultFile("${doc.title}_updated")
            val r1 = container.securityEngine.updateMetadata(File(doc.localPath), tempMeta, state.metadata)
            if (r1.isFailure) {
                _uiState.update { it.copy(isProcessing = false, errorMessage = r1.exceptionOrNull()?.message) }
                return@launch
            }
            val r2 = container.securityEngine.saveBookmarks(tempMeta, outFinal, state.bookmarks)
            runCatching { tempMeta.delete() }
            r2.fold(
                onSuccess = { f ->
                    val saved = container.repository.registerGeneratedPdf(
                        file = f,
                        title = state.metadata.title.ifBlank { f.nameWithoutExtension },
                        originTag = "edited",
                        actionType = "Update Metadata & Bookmarks",
                        inputName = doc.fileName
                    )
                    _uiState.update { it.copy(isProcessing = false, outputDoc = saved, outputFile = f) }
                },
                onFailure = { err -> _uiState.update { it.copy(isProcessing = false, errorMessage = err.message) } }
            )
        }
    }

    fun updatePageNumPrefix(v: String) = _uiState.update { it.copy(pageNumPrefix = v) }
    fun updatePageNumStart(v: Int) = _uiState.update { it.copy(pageNumStart = v.coerceAtLeast(1)) }
    fun updatePageNumAtBottom(v: Boolean) = _uiState.update { it.copy(pageNumAtBottom = v) }

    fun applyPageNumbers() {
        val state = _uiState.value
        val doc = state.activeDoc ?: run {
            _uiState.update { it.copy(errorMessage = "Select a PDF document first.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            val outFile = container.fileStorageManager.createVaultFile("${doc.title}_numbered")
            container.securityEngine.addPageNumbers(
                inputFile = File(doc.localPath),
                outputFile = outFile,
                prefix = state.pageNumPrefix,
                includeTotal = true,
                atBottom = state.pageNumAtBottom,
                startNumber = state.pageNumStart
            ).fold(
                onSuccess = { f ->
                    val saved = container.repository.registerGeneratedPdf(
                        file = f,
                        title = "${doc.title} (Numbered)",
                        originTag = "edited",
                        actionType = "Add Page Numbers",
                        inputName = doc.fileName
                    )
                    _uiState.update { it.copy(isProcessing = false, outputDoc = saved, outputFile = f) }
                },
                onFailure = { err -> _uiState.update { it.copy(isProcessing = false, errorMessage = err.message) } }
            )
        }
    }

    fun updateSearchQuery(v: String) = _uiState.update { it.copy(searchQuery = v) }

    fun searchInsideActivePdf() {
        val state = _uiState.value
        val doc = state.activeDoc ?: run {
            _uiState.update { it.copy(errorMessage = "Select a PDF document first.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            container.toolsEngine.searchInPdf(File(doc.localPath), state.searchQuery).fold(
                onSuccess = { matches -> _uiState.update { it.copy(isProcessing = false, searchMatches = matches) } },
                onFailure = { err -> _uiState.update { it.copy(isProcessing = false, errorMessage = err.message) } }
            )
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    fun exportToUri(uri: Uri) {
        val file = _uiState.value.outputFile ?: return
        viewModelScope.launch { container.fileStorageManager.exportFileToUri(file, uri) }
    }

    fun shareOutput() {
        val file = _uiState.value.outputFile ?: return
        container.fileStorageManager.shareFile(file)
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return UtilitiesViewModel(container) as T
        }
    }
}
