package com.example.ui.screens.organizer

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.db.PdfDocumentEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class OrganizerPageTile(
    val originalZeroIndex: Int,
    val thumbnail: Bitmap?,
    val isSelected: Boolean = false
)

data class OrganizerUiState(
    val mergeQueue: List<PdfDocumentEntity> = emptyList(),
    val selectedDoc: PdfDocumentEntity? = null,
    val pageTiles: List<OrganizerPageTile> = emptyList(),
    val splitRangesSpec: String = "1-2",
    val splitEveryN: Int = 2,
    val insertAfterPage: Int = 1,
    val blankInsertCount: Int = 1,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
    val outputDocs: List<PdfDocumentEntity> = emptyList(),
    val extractedImages: List<File> = emptyList()
)

class OrganizerViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(OrganizerUiState())
    val uiState: StateFlow<OrganizerUiState> = _uiState.asStateFlow()

    fun addToMergeQueue(doc: PdfDocumentEntity) {
        _uiState.update { state ->
            if (state.mergeQueue.any { it.id == doc.id }) state
            else state.copy(mergeQueue = state.mergeQueue + doc)
        }
    }

    fun importToMergeQueue(uri: Uri) {
        viewModelScope.launch {
            container.repository.importPdf(uri).fold(
                onSuccess = { addToMergeQueue(it) },
                onFailure = { err -> _uiState.update { it.copy(errorMessage = err.message) } }
            )
        }
    }

    fun removeFromMergeQueue(docId: Long) {
        _uiState.update { it.copy(mergeQueue = it.mergeQueue.filterNot { d -> d.id == docId }) }
    }

    fun moveMergeItem(index: Int, delta: Int) {
        _uiState.update { state ->
            val target = (index + delta).coerceIn(0, state.mergeQueue.lastIndex)
            if (index == target || index !in state.mergeQueue.indices) return@update state
            val list = state.mergeQueue.toMutableList()
            val item = list.removeAt(index)
            list.add(target, item)
            state.copy(mergeQueue = list)
        }
    }

    fun selectDocumentForPages(doc: PdfDocumentEntity) {
        _uiState.update { it.copy(selectedDoc = doc, isProcessing = true, errorMessage = null, outputDocs = emptyList()) }
        viewModelScope.launch {
            val file = File(doc.localPath)
            val thumbs = container.rendererHelper.renderThumbnails(file, maxCount = 60, thumbMaxPx = 280)
            val count = maxOf(thumbs.size, doc.pageCount)
            val tiles = (0 until count).map { idx ->
                OrganizerPageTile(
                    originalZeroIndex = idx,
                    thumbnail = thumbs.getOrNull(idx),
                    isSelected = false
                )
            }
            _uiState.update { it.copy(pageTiles = tiles, isProcessing = false) }
        }
    }

    fun selectDocumentById(docId: Long) {
        if (docId <= 0L) return
        viewModelScope.launch {
            container.repository.getDocumentById(docId)?.let { selectDocumentForPages(it) }
        }
    }

    fun importAndSelectDocument(uri: Uri) {
        viewModelScope.launch {
            container.repository.importPdf(uri).fold(
                onSuccess = { selectDocumentForPages(it) },
                onFailure = { err -> _uiState.update { it.copy(errorMessage = err.message) } }
            )
        }
    }

    fun toggleTileSelection(position: Int) {
        _uiState.update { state ->
            val updated = state.pageTiles.mapIndexed { idx, tile ->
                if (idx == position) tile.copy(isSelected = !tile.isSelected) else tile
            }
            state.copy(pageTiles = updated)
        }
    }

    fun reorderPageTile(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            if (fromIndex !in state.pageTiles.indices || toIndex !in state.pageTiles.indices || fromIndex == toIndex) {
                return@update state
            }
            val list = state.pageTiles.toMutableList()
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            state.copy(pageTiles = list)
        }
    }

    fun updateSplitRanges(v: String) = _uiState.update { it.copy(splitRangesSpec = v) }
    fun updateSplitEveryN(v: Int) = _uiState.update { it.copy(splitEveryN = v.coerceIn(1, 50)) }
    fun updateInsertAfterPage(v: Int) = _uiState.update { it.copy(insertAfterPage = v.coerceAtLeast(0)) }
    fun updateBlankInsertCount(v: Int) = _uiState.update { it.copy(blankInsertCount = v.coerceIn(1, 20)) }
    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    fun mergeQueue() {
        val queue = _uiState.value.mergeQueue
        if (queue.size < 2) {
            _uiState.update { it.copy(errorMessage = "Add at least 2 PDF files to merge.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            val outFile = container.fileStorageManager.createVaultFile("Merged_${queue.first().title}")
            val res = container.organizerEngine.mergePdfs(queue.map { File(it.localPath) }, outFile)
            res.fold(
                onSuccess = { file ->
                    val doc = container.repository.registerGeneratedPdf(
                        file = file,
                        title = file.nameWithoutExtension,
                        originTag = "organized",
                        actionType = "Merge PDFs",
                        inputName = "${queue.size} PDFs",
                        originalSizeBytes = queue.sumOf { it.sizeBytes },
                        details = "Merged ${queue.size} documents"
                    )
                    _uiState.update { it.copy(isProcessing = false, outputDocs = listOf(doc)) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isProcessing = false, errorMessage = err.message ?: "Merge failed") }
                }
            )
        }
    }

    fun splitByRanges() {
        val doc = _uiState.value.selectedDoc ?: return
        val spec = _uiState.value.splitRangesSpec
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            val res = container.organizerEngine.splitByRanges(File(doc.localPath), spec) { idx, suffix ->
                container.fileStorageManager.createVaultFile("${doc.title}_$suffix")
            }
            handleSplitResult(doc, "Split PDF (Ranges: $spec)", res)
        }
    }

    fun splitEveryN() {
        val doc = _uiState.value.selectedDoc ?: return
        val n = _uiState.value.splitEveryN
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            val res = container.organizerEngine.splitEveryNPages(File(doc.localPath), n) { _, suffix ->
                container.fileStorageManager.createVaultFile("${doc.title}_$suffix")
            }
            handleSplitResult(doc, "Split PDF (Every $n pages)", res)
        }
    }

    private suspend fun handleSplitResult(
        source: PdfDocumentEntity,
        action: String,
        res: Result<List<File>>
    ) {
        res.fold(
            onSuccess = { files ->
                val docs = files.map { f ->
                    container.repository.registerGeneratedPdf(
                        file = f,
                        title = f.nameWithoutExtension,
                        originTag = "organized",
                        actionType = action,
                        inputName = source.fileName,
                        originalSizeBytes = source.sizeBytes
                    )
                }
                _uiState.update { it.copy(isProcessing = false, outputDocs = docs) }
            },
            onFailure = { err ->
                _uiState.update { it.copy(isProcessing = false, errorMessage = err.message ?: "Split failed") }
            }
        )
    }

    fun extractSelectedPages() {
        val doc = _uiState.value.selectedDoc ?: return
        val selected = _uiState.value.pageTiles.filter { it.isSelected }.map { it.originalZeroIndex + 1 }
        if (selected.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Tap one or more page thumbnails first.") }
            return
        }
        runSingleOutputOp(doc, "Extract Pages", "extracted") { out ->
            container.organizerEngine.extractPages(File(doc.localPath), selected, out)
        }
    }

    fun deleteSelectedPages() {
        val doc = _uiState.value.selectedDoc ?: return
        val selected = _uiState.value.pageTiles.filter { it.isSelected }.map { it.originalZeroIndex + 1 }.toSet()
        if (selected.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Select pages to remove.") }
            return
        }
        runSingleOutputOp(doc, "Delete Pages", "trimmed") { out ->
            container.organizerEngine.deletePages(File(doc.localPath), selected, out)
        }
    }

    fun rotatePages(degrees: Int) {
        val doc = _uiState.value.selectedDoc ?: return
        val selected = _uiState.value.pageTiles.filter { it.isSelected }.map { it.originalZeroIndex + 1 }.toSet()
        val targetPages = selected.ifEmpty { null } // null rotates all pages
        runSingleOutputOp(doc, "Rotate Pages (${degrees}°)", "rotated") { out ->
            container.organizerEngine.rotatePages(File(doc.localPath), targetPages, degrees, out)
        }
    }

    fun applyReorderedPages() {
        val doc = _uiState.value.selectedDoc ?: return
        val order = _uiState.value.pageTiles.map { it.originalZeroIndex }
        runSingleOutputOp(doc, "Rearrange Pages", "reordered") { out ->
            container.organizerEngine.rearrangePages(File(doc.localPath), order, out)
        }
    }

    fun duplicateSelectedPages() {
        val doc = _uiState.value.selectedDoc ?: return
        val selected = _uiState.value.pageTiles.filter { it.isSelected }.map { it.originalZeroIndex + 1 }.toSet()
        if (selected.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Select pages to duplicate.") }
            return
        }
        runSingleOutputOp(doc, "Duplicate Pages", "duplicated") { out ->
            container.organizerEngine.duplicatePages(File(doc.localPath), selected, out)
        }
    }

    fun reverseAllPages() {
        val doc = _uiState.value.selectedDoc ?: return
        runSingleOutputOp(doc, "Reverse Page Order", "reversed") { out ->
            container.organizerEngine.reversePageOrder(File(doc.localPath), out)
        }
    }

    fun insertBlankPages() {
        val doc = _uiState.value.selectedDoc ?: return
        val state = _uiState.value
        runSingleOutputOp(doc, "Insert Blank Pages", "with_blanks") { out ->
            container.organizerEngine.insertBlankPages(
                File(doc.localPath),
                state.insertAfterPage,
                state.blankInsertCount,
                out
            )
        }
    }

    fun extractEmbeddedImages() {
        val doc = _uiState.value.selectedDoc ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null, extractedImages = emptyList()) }
            val dir = container.fileStorageManager.createTempFile("extracted_imgs", "dir").apply { mkdirs() }
            val res = container.organizerEngine.extractImages(File(doc.localPath), dir)
            res.fold(
                onSuccess = { imgs ->
                    if (imgs.isEmpty()) {
                        _uiState.update { it.copy(isProcessing = false, errorMessage = "No embedded raster images found in this PDF.") }
                    } else {
                        container.repository.logHistorySuccess("Extract Images", doc.fileName, "${imgs.size} PNGs")
                        _uiState.update { it.copy(isProcessing = false, extractedImages = imgs) }
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isProcessing = false, errorMessage = err.message) }
                }
            )
        }
    }

    private fun runSingleOutputOp(
        source: PdfDocumentEntity,
        action: String,
        suffix: String,
        block: suspend (File) -> Result<File>
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            val outFile = container.fileStorageManager.createVaultFile("${source.title}_$suffix")
            val res = block(outFile)
            res.fold(
                onSuccess = { f ->
                    val doc = container.repository.registerGeneratedPdf(
                        file = f,
                        title = f.nameWithoutExtension,
                        originTag = "organized",
                        actionType = action,
                        inputName = source.fileName,
                        originalSizeBytes = source.sizeBytes
                    )
                    _uiState.update { it.copy(isProcessing = false, outputDocs = listOf(doc)) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isProcessing = false, errorMessage = err.message ?: "$action failed") }
                }
            )
        }
    }

    fun exportDocToUri(doc: PdfDocumentEntity, uri: Uri) {
        viewModelScope.launch { container.fileStorageManager.exportFileToUri(File(doc.localPath), uri) }
    }

    fun shareDoc(doc: PdfDocumentEntity) = container.fileStorageManager.shareFile(File(doc.localPath))
    fun shareImage(file: File) = container.fileStorageManager.shareFile(file, "image/png")

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return OrganizerViewModel(container) as T
        }
    }
}
