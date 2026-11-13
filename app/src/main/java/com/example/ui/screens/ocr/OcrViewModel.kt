package com.example.ui.screens.ocr

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.db.PdfDocumentEntity
import com.example.domain.model.PageOrientation
import com.example.domain.model.PageSizeOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class OcrUiState(
    val selectedPdfDoc: PdfDocumentEntity? = null,
    val selectedImageUri: Uri? = null,
    val ocrLanguageCode: String = "en", // "en" (Latin ML Kit) or "bn" (Availability notice + Unicode PDF extractor)
    val recognizedText: String = "",
    val searchQuery: String = "",
    val modelNotice: String? = null,
    val isRunning: Boolean = false,
    val errorMessage: String? = null,
    val savedPdfDoc: PdfDocumentEntity? = null,
    val savedPdfFile: File? = null
) {
    val searchMatchCount: Int
        get() = if (searchQuery.isBlank() || recognizedText.isBlank()) 0
        else Regex(Regex.escape(searchQuery.trim()), RegexOption.IGNORE_CASE)
            .findAll(recognizedText)
            .count()
}

class OcrViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(OcrUiState())
    val uiState: StateFlow<OcrUiState> = _uiState.asStateFlow()

    fun selectPdf(doc: PdfDocumentEntity) = _uiState.update {
        it.copy(selectedPdfDoc = doc, selectedImageUri = null, errorMessage = null)
    }

    fun selectPdfById(docId: Long) {
        if (docId <= 0L) return
        viewModelScope.launch { container.repository.getDocumentById(docId)?.let(::selectPdf) }
    }

    fun importPdf(uri: Uri) {
        viewModelScope.launch {
            container.repository.importPdf(uri).fold(
                onSuccess = ::selectPdf,
                onFailure = { err -> _uiState.update { it.copy(errorMessage = err.message) } }
            )
        }
    }

    fun selectImage(uri: Uri) = _uiState.update {
        it.copy(selectedImageUri = uri, selectedPdfDoc = null, errorMessage = null)
    }

    fun setOcrLanguage(code: String) {
        val notice = if (code == "bn") {
            "On-device ML Kit v2 currently includes Latin OCR only; offline Bengali OCR model is unavailable. If your PDF already contains selectable Bengali Unicode text, use 'Extract Embedded PDF Text' below."
        } else null
        _uiState.update { it.copy(ocrLanguageCode = code, modelNotice = notice) }
    }

    fun updateSearchQuery(q: String) = _uiState.update { it.copy(searchQuery = q) }
    fun updateRecognizedText(txt: String) = _uiState.update { it.copy(recognizedText = txt) }
    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    fun runOcr() {
        val state = _uiState.value
        val imgUri = state.selectedImageUri
        val pdfDoc = state.selectedPdfDoc

        if (imgUri == null && pdfDoc == null) {
            _uiState.update { it.copy(errorMessage = "Select an image or PDF document first.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isRunning = true, errorMessage = null) }
            if (imgUri != null) {
                val copied = container.fileStorageManager.copyUriToTempFile(imgUri, "jpg")
                copied.fold(
                    onSuccess = { (tmp, name) ->
                        try {
                            container.ocrEngine.recognizeImageFile(tmp, state.ocrLanguageCode).fold(
                                onSuccess = { res ->
                                    container.repository.logHistorySuccess("OCR Image", name, "${res.fullText.length} chars")
                                    _uiState.update {
                                        it.copy(
                                            isRunning = false,
                                            recognizedText = res.fullText.ifBlank { "(No text detected in image)" },
                                            modelNotice = res.modelNotice
                                        )
                                    }
                                },
                                onFailure = { err ->
                                    _uiState.update { it.copy(isRunning = false, errorMessage = err.message) }
                                }
                            )
                        } finally {
                            runCatching { tmp.delete() }
                        }
                    },
                    onFailure = { err -> _uiState.update { it.copy(isRunning = false, errorMessage = err.message) } }
                )
            } else if (pdfDoc != null) {
                container.ocrEngine.recognizePdfFile(File(pdfDoc.localPath), state.ocrLanguageCode).fold(
                    onSuccess = { res ->
                        container.repository.logHistorySuccess("OCR PDF", pdfDoc.fileName, "${res.fullText.length} chars")
                        _uiState.update {
                            it.copy(
                                isRunning = false,
                                recognizedText = res.fullText.ifBlank { "(No text detected in rendered pages)" },
                                modelNotice = res.modelNotice
                            )
                        }
                    },
                    onFailure = { err ->
                        _uiState.update { it.copy(isRunning = false, errorMessage = err.message) }
                    }
                )
            }
        }
    }

    fun extractEmbeddedPdfText() {
        val pdfDoc = _uiState.value.selectedPdfDoc ?: run {
            _uiState.update { it.copy(errorMessage = "Select a PDF document first.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isRunning = true, errorMessage = null) }
            container.toolsEngine.extractTextByPage(File(pdfDoc.localPath)).fold(
                onSuccess = { pages ->
                    val txt = pages.joinToString("\n\n") { (p, body) -> "--- Page $p ---\n$body" }
                    _uiState.update {
                        it.copy(
                            isRunning = false,
                            recognizedText = txt.ifBlank { "(No embedded text layer; use ML Kit OCR above for scanned pages)" }
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isRunning = false, errorMessage = err.message) }
                }
            )
        }
    }

    fun exportTextToUri(uri: Uri) {
        val txt = _uiState.value.recognizedText
        if (txt.isBlank()) return
        viewModelScope.launch {
            val tmp = container.fileStorageManager.createTempFile("ocr_export", "txt")
            tmp.writeText(txt, Charsets.UTF_8)
            container.fileStorageManager.exportFileToUri(tmp, uri)
            runCatching { tmp.delete() }
        }
    }

    fun saveRecognizedAsPdf() {
        val txt = _uiState.value.recognizedText
        if (txt.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRunning = true, errorMessage = null) }
            val outFile = container.fileStorageManager.createVaultFile("OCR_Extracted")
            container.creationEngine.createFromText(
                outputFile = outFile,
                text = txt,
                pageSize = PageSizeOption.A4,
                orientation = PageOrientation.PORTRAIT,
                headerText = "OCR Extracted Text",
                includePageNumbers = true
            ).fold(
                onSuccess = { f ->
                    val doc = container.repository.registerGeneratedPdf(
                        file = f,
                        title = f.nameWithoutExtension,
                        originTag = "created",
                        actionType = "OCR to PDF"
                    )
                    _uiState.update { it.copy(isRunning = false, savedPdfDoc = doc, savedPdfFile = f) }
                },
                onFailure = { err -> _uiState.update { it.copy(isRunning = false, errorMessage = err.message) } }
            )
        }
    }

    fun exportSavedPdfToUri(uri: Uri) {
        val file = _uiState.value.savedPdfFile ?: return
        viewModelScope.launch { container.fileStorageManager.exportFileToUri(file, uri) }
    }

    fun shareSavedPdf() {
        val file = _uiState.value.savedPdfFile ?: return
        container.fileStorageManager.shareFile(file)
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return OcrViewModel(container) as T
        }
    }
}
