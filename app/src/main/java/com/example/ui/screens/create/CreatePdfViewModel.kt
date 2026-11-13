package com.example.ui.screens.create

import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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

data class CreatePdfUiState(
    val title: String = "My_Document",
    val bodyText: String = "",
    val pageSize: PageSizeOption = PageSizeOption.A4,
    val orientation: PageOrientation = PageOrientation.PORTRAIT,
    val customWidthPt: Float = 595f,
    val customHeightPt: Float = 842f,
    val marginPt: Float = 48f,
    val fontSizePt: Float = 14f,
    val textColorArgb: Int = Color(0xFF141726).toArgb(),
    val headerText: String = "",
    val footerText: String = "",
    val includePageNumbers: Boolean = true,
    val blankPageCount: Int = 3,
    val selectedImageUris: List<Uri> = emptyList(),
    val selectedDocUri: Uri? = null,
    val selectedDocName: String = "",
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
    val createdDocument: PdfDocumentEntity? = null,
    val outputFile: File? = null
)

class CreatePdfViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(CreatePdfUiState())
    val uiState: StateFlow<CreatePdfUiState> = _uiState.asStateFlow()

    fun applyDefaults(
        pageSize: PageSizeOption,
        orientation: PageOrientation,
        fontSize: Float,
        marginPt: Float
    ) {
        _uiState.update {
            it.copy(
                pageSize = pageSize,
                orientation = orientation,
                fontSizePt = fontSize,
                marginPt = marginPt
            )
        }
    }

    fun updateTitle(v: String) = _uiState.update { it.copy(title = v) }
    fun updateBodyText(v: String) = _uiState.update { it.copy(bodyText = v) }
    fun updatePageSize(v: PageSizeOption) = _uiState.update { it.copy(pageSize = v) }
    fun updateOrientation(v: PageOrientation) = _uiState.update { it.copy(orientation = v) }
    fun updateCustomWidth(v: Float) = _uiState.update { it.copy(customWidthPt = v) }
    fun updateCustomHeight(v: Float) = _uiState.update { it.copy(customHeightPt = v) }
    fun updateMargin(v: Float) = _uiState.update { it.copy(marginPt = v) }
    fun updateFontSize(v: Float) = _uiState.update { it.copy(fontSizePt = v) }
    fun updateTextColor(argb: Int) = _uiState.update { it.copy(textColorArgb = argb) }
    fun updateHeaderText(v: String) = _uiState.update { it.copy(headerText = v) }
    fun updateFooterText(v: String) = _uiState.update { it.copy(footerText = v) }
    fun updateIncludePageNumbers(v: Boolean) = _uiState.update { it.copy(includePageNumbers = v) }
    fun updateBlankPageCount(v: Int) = _uiState.update { it.copy(blankPageCount = v.coerceIn(1, 100)) }
    fun setSelectedImages(uris: List<Uri>) = _uiState.update { it.copy(selectedImageUris = uris) }
    fun removeSelectedImage(uri: Uri) = _uiState.update {
        it.copy(selectedImageUris = it.selectedImageUris.filterNot { item -> item == uri })
    }

    fun setSelectedSourceDocument(uri: Uri) {
        val name = container.fileStorageManager.resolveDisplayName(uri) ?: "Document"
        _uiState.update { it.copy(selectedDocUri = uri, selectedDocName = name) }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    fun insertBengaliSample() {
        _uiState.update {
            it.copy(
                title = "Bangla_Unicode_Note",
                bodyText = "PDF Master Pro বাংলা ইউনিকোড সাপোর্ট করে।\n\nআপনি সহজেই বাংলা ও ইংরেজি মিশ্রিত ডকুমেন্ট, নোট এবং রিপোর্ট থেকে পরিষ্কার PDF তৈরি করতে পারবেন।\n\nThis paragraph demonstrates seamless bilingual English and বাংলা text rendering in the same PDF document."
            )
        }
    }

    fun generateTextPdf() {
        val state = _uiState.value
        if (state.bodyText.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter some text to generate the PDF.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            val outFile = container.fileStorageManager.createVaultFile(state.title.ifBlank { "Text_PDF" })
            val res = container.creationEngine.createFromText(
                outputFile = outFile,
                text = state.bodyText,
                pageSize = state.pageSize,
                orientation = state.orientation,
                customWidthPt = state.customWidthPt,
                customHeightPt = state.customHeightPt,
                marginPt = state.marginPt,
                fontSizePt = state.fontSizePt,
                textColorArgb = state.textColorArgb,
                headerText = state.headerText,
                footerText = state.footerText,
                includePageNumbers = state.includePageNumbers
            )
            res.fold(
                onSuccess = { file ->
                    val doc = container.repository.registerGeneratedPdf(
                        file = file,
                        title = state.title.ifBlank { file.nameWithoutExtension },
                        originTag = "created",
                        actionType = "Create PDF from Text",
                        details = "${state.pageSize.label} • ${state.orientation.label}"
                    )
                    _uiState.update { it.copy(isProcessing = false, createdDocument = doc, outputFile = file) }
                },
                onFailure = { err ->
                    container.repository.logHistoryFailure("Create PDF from Text", state.title, err.message.orEmpty())
                    _uiState.update { it.copy(isProcessing = false, errorMessage = err.message ?: "Failed to create PDF") }
                }
            )
        }
    }

    fun generateImagesPdf() {
        val state = _uiState.value
        if (state.selectedImageUris.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please pick one or more images first.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            val outFile = container.fileStorageManager.createVaultFile(state.title.ifBlank { "Images_PDF" })
            val res = container.creationEngine.createFromImageUris(
                outputFile = outFile,
                uris = state.selectedImageUris,
                fileManager = container.fileStorageManager,
                pageSize = state.pageSize,
                orientation = state.orientation,
                marginPt = state.marginPt,
                includePageNumbers = state.includePageNumbers
            )
            res.fold(
                onSuccess = { file ->
                    val doc = container.repository.registerGeneratedPdf(
                        file = file,
                        title = state.title.ifBlank { file.nameWithoutExtension },
                        originTag = "created",
                        actionType = "Images to PDF",
                        inputName = "${state.selectedImageUris.size} images",
                        details = "${state.selectedImageUris.size} pages"
                    )
                    _uiState.update { it.copy(isProcessing = false, createdDocument = doc, outputFile = file) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isProcessing = false, errorMessage = err.message ?: "Failed to convert images") }
                }
            )
        }
    }

    fun generateBlankPdf() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            val outFile = container.fileStorageManager.createVaultFile(state.title.ifBlank { "Blank_PDF" })
            val res = container.creationEngine.createBlankPdf(
                outputFile = outFile,
                pageCount = state.blankPageCount,
                pageSize = state.pageSize,
                orientation = state.orientation,
                customWidthPt = state.customWidthPt,
                customHeightPt = state.customHeightPt
            )
            res.fold(
                onSuccess = { file ->
                    val doc = container.repository.registerGeneratedPdf(
                        file = file,
                        title = state.title.ifBlank { file.nameWithoutExtension },
                        originTag = "created",
                        actionType = "Create Blank PDF",
                        details = "${state.blankPageCount} blank pages"
                    )
                    _uiState.update { it.copy(isProcessing = false, createdDocument = doc, outputFile = file) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isProcessing = false, errorMessage = err.message ?: "Failed to create blank PDF") }
                }
            )
        }
    }

    fun generateFromDocumentUri() {
        val state = _uiState.value
        val uri = state.selectedDocUri ?: run {
            _uiState.update { it.copy(errorMessage = "Please select a .txt, .docx, .xlsx, or .pptx file.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            val copied = container.fileStorageManager.copyUriToTempFile(uri, "txt")
            copied.fold(
                onSuccess = { (tempFile, origName) ->
                    try {
                        val ext = origName.substringAfterLast(".", "").lowercase()
                        val outFile = container.fileStorageManager.createVaultFile(
                            origName.substringBeforeLast(".").ifBlank { "Converted_Doc" }
                        )
                        val result = when (ext) {
                            "docx" -> container.converterEngine.docxToPdf(tempFile, outFile)
                            "xlsx" -> container.converterEngine.xlsxToPdf(tempFile, outFile)
                            "pptx" -> container.converterEngine.pptxToPdf(tempFile, outFile)
                            "txt", "md", "csv", "log", "json" -> {
                                val textContent = tempFile.readText(Charsets.UTF_8)
                                container.creationEngine.createFromText(
                                    outputFile = outFile,
                                    text = textContent,
                                    pageSize = state.pageSize,
                                    orientation = state.orientation,
                                    marginPt = state.marginPt,
                                    fontSizePt = state.fontSizePt,
                                    textColorArgb = state.textColorArgb,
                                    headerText = origName,
                                    includePageNumbers = state.includePageNumbers
                                )
                            }
                            else -> Result.failure(IllegalArgumentException("Unsupported format .$ext. Supported: TXT, DOCX, XLSX, PPTX."))
                        }
                        result.fold(
                            onSuccess = { pdfFile ->
                                val doc = container.repository.registerGeneratedPdf(
                                    file = pdfFile,
                                    title = pdfFile.nameWithoutExtension,
                                    originTag = "converted",
                                    actionType = "Document to PDF ($ext)",
                                    inputName = origName
                                )
                                _uiState.update { it.copy(isProcessing = false, createdDocument = doc, outputFile = pdfFile) }
                            },
                            onFailure = { err ->
                                _uiState.update { it.copy(isProcessing = false, errorMessage = err.message ?: "Conversion failed") }
                            }
                        )
                    } finally {
                        runCatching { tempFile.delete() }
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isProcessing = false, errorMessage = err.message ?: "Could not read file") }
                }
            )
        }
    }

    fun exportToUri(uri: Uri) {
        val file = _uiState.value.outputFile ?: return
        viewModelScope.launch {
            container.fileStorageManager.exportFileToUri(file, uri)
        }
    }

    fun shareOutput() {
        val file = _uiState.value.outputFile ?: return
        container.fileStorageManager.shareFile(file)
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CreatePdfViewModel(container) as T
        }
    }
}
