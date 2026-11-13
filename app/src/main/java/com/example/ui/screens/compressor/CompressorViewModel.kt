package com.example.ui.screens.compressor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.AppContainer
import com.example.data.local.db.PdfDocumentEntity
import com.example.data.pdf.PdfBackgroundWorker
import com.example.domain.model.CompressionLevel
import com.example.domain.model.CompressionStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class CompressorUiState(
    val selectedDoc: PdfDocumentEntity? = null,
    val level: CompressionLevel = CompressionLevel.MEDIUM,
    val customQuality: Int = 60,
    val customMaxDimPx: Int = 1400,
    val useBackgroundWorker: Boolean = false,
    val isCompressing: Boolean = false,
    val errorMessage: String? = null,
    val stats: CompressionStats? = null,
    val outputDoc: PdfDocumentEntity? = null
)

class CompressorViewModel(
    private val app: Application,
    private val container: AppContainer
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompressorUiState())
    val uiState: StateFlow<CompressorUiState> = _uiState.asStateFlow()

    fun selectDocument(doc: PdfDocumentEntity) {
        _uiState.update { it.copy(selectedDoc = doc, stats = null, outputDoc = null, errorMessage = null) }
    }

    fun selectDocumentById(docId: Long) {
        if (docId <= 0L) return
        viewModelScope.launch {
            container.repository.getDocumentById(docId)?.let(::selectDocument)
        }
    }

    fun importAndSelect(uri: Uri) {
        viewModelScope.launch {
            container.repository.importPdf(uri).fold(
                onSuccess = ::selectDocument,
                onFailure = { err -> _uiState.update { it.copy(errorMessage = err.message) } }
            )
        }
    }

    fun setLevel(lvl: CompressionLevel) = _uiState.update {
        it.copy(
            level = lvl,
            customQuality = if (lvl == CompressionLevel.CUSTOM) it.customQuality else lvl.jpegQuality,
            customMaxDimPx = if (lvl == CompressionLevel.CUSTOM) it.customMaxDimPx else lvl.maxImageDimensionPx
        )
    }

    fun setCustomQuality(q: Int) = _uiState.update { it.copy(level = CompressionLevel.CUSTOM, customQuality = q.coerceIn(15, 95)) }
    fun setCustomMaxDim(d: Int) = _uiState.update { it.copy(level = CompressionLevel.CUSTOM, customMaxDimPx = d.coerceIn(500, 3000)) }
    fun setUseBackgroundWorker(useBg: Boolean) = _uiState.update { it.copy(useBackgroundWorker = useBg) }
    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    fun compress() {
        val state = _uiState.value
        val doc = state.selectedDoc ?: run {
            _uiState.update { it.copy(errorMessage = "Select a PDF document first.") }
            return
        }
        val inFile = File(doc.localPath)
        if (!inFile.exists()) {
            _uiState.update { it.copy(errorMessage = "Source file is missing.") }
            return
        }

        if (state.useBackgroundWorker) {
            _uiState.update { it.copy(isCompressing = true, errorMessage = null) }
            val workId = PdfBackgroundWorker.enqueueCompression(
                context = app,
                inputFile = inFile,
                level = state.level,
                customQuality = state.customQuality,
                customMaxDim = state.customMaxDimPx
            )
            viewModelScope.launch {
                WorkManager.getInstance(app).getWorkInfoByIdFlow(workId).collect { info ->
                    if (info != null && info.state.isFinished) {
                        if (info.state == WorkInfo.State.SUCCEEDED) {
                            val outId = info.outputData.getLong(PdfBackgroundWorker.KEY_OUTPUT_DOC_ID, -1L)
                            val outPath = info.outputData.getString(PdfBackgroundWorker.KEY_OUTPUT_PATH).orEmpty()
                            val origBytes = info.outputData.getLong(PdfBackgroundWorker.KEY_ORIGINAL_BYTES, doc.sizeBytes)
                            val outBytes = info.outputData.getLong(PdfBackgroundWorker.KEY_OUTPUT_BYTES, 0L)
                            val outDoc = container.repository.getDocumentById(outId)
                            _uiState.update {
                                it.copy(
                                    isCompressing = false,
                                    outputDoc = outDoc,
                                    stats = CompressionStats(origBytes, outBytes, File(outPath))
                                )
                            }
                        } else {
                            val err = info.outputData.getString(PdfBackgroundWorker.KEY_ERROR) ?: "Background compression failed"
                            _uiState.update { it.copy(isCompressing = false, errorMessage = err) }
                        }
                    }
                }
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCompressing = true, errorMessage = null) }
            val outFile = container.fileStorageManager.createVaultFile("${doc.title}_compressed")
            val res = container.compressionEngine.compressPdf(
                inputFile = inFile,
                outputFile = outFile,
                level = state.level,
                customQuality = state.customQuality,
                customMaxDimPx = state.customMaxDimPx
            )
            res.fold(
                onSuccess = { stats ->
                    val savedDoc = container.repository.registerGeneratedPdf(
                        file = stats.outputFile,
                        title = "${doc.title} (Compressed)",
                        originTag = "compressed",
                        actionType = "Compress PDF (${state.level.label})",
                        inputName = doc.fileName,
                        originalSizeBytes = stats.originalBytes,
                        details = "Saved ${stats.reductionPercent}%"
                    )
                    _uiState.update { it.copy(isCompressing = false, stats = stats, outputDoc = savedDoc) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isCompressing = false, errorMessage = err.message ?: "Compression failed") }
                }
            )
        }
    }

    fun exportToUri(uri: Uri) {
        val file = _uiState.value.stats?.outputFile ?: return
        viewModelScope.launch { container.fileStorageManager.exportFileToUri(file, uri) }
    }

    fun shareOutput() {
        val file = _uiState.value.stats?.outputFile ?: return
        container.fileStorageManager.shareFile(file)
    }

    class Factory(private val app: Application, private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CompressorViewModel(app, container) as T
        }
    }
}
