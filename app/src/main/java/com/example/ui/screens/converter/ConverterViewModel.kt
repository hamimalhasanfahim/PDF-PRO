package com.example.ui.screens.converter

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

enum class ConversionMode(
    val label: String,
    val capabilityNote: String
) {
    PDF_TO_JPG("PDF → JPG", "Renders every PDF page as crisp high-resolution JPG images (packaged into a ZIP or individually shareable)."),
    PDF_TO_PNG("PDF → PNG", "Renders every PDF page as lossless PNG images."),
    PDF_TO_DOCX("PDF → DOCX", "Local text-based .docx export via PDFTextStripper. Complex multi-column layouts, embedded fonts & vector graphics require a cloud/desktop engine."),
    PDF_TO_XLSX("PDF → Excel", "Local line/tab-delimited .xlsx workbook export. Best for structured text tables; visual grid layout reconstruction is simplified."),
    PDF_TO_PPTX("PDF → PowerPoint", "Local slide-image .pptx presentation export (rasterizes each PDF page onto a 4:3 slide so layout stays visually intact)."),
    DOCX_TO_PDF("DOCX → PDF", "Extracts paragraphs from Word .docx OpenXML locally into PDF."),
    XLSX_TO_PDF("XLSX → PDF", "Extracts sheet rows from Excel .xlsx OpenXML locally into PDF."),
    PPTX_TO_PDF("PPTX → PDF", "Extracts slide text from PowerPoint .pptx OpenXML locally into PDF.")
}

data class ConverterUiState(
    val mode: ConversionMode = ConversionMode.PDF_TO_JPG,
    val selectedPdfDoc: PdfDocumentEntity? = null,
    val selectedExternalUri: Uri? = null,
    val selectedExternalName: String = "",
    val isConverting: Boolean = false,
    val errorMessage: String? = null,
    val outputFile: File? = null,
    val outputMimeType: String = "application/pdf",
    val outputPdfDoc: PdfDocumentEntity? = null,
    val outputImageFiles: List<File> = emptyList()
)

class ConverterViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(ConverterUiState())
    val uiState: StateFlow<ConverterUiState> = _uiState.asStateFlow()

    fun setMode(mode: ConversionMode) = _uiState.update {
        it.copy(mode = mode, errorMessage = null, outputFile = null, outputPdfDoc = null, outputImageFiles = emptyList())
    }

    fun selectPdfDocument(doc: PdfDocumentEntity) = _uiState.update {
        it.copy(selectedPdfDoc = doc, errorMessage = null, outputFile = null, outputImageFiles = emptyList())
    }

    fun selectPdfById(docId: Long) {
        if (docId <= 0L) return
        viewModelScope.launch {
            container.repository.getDocumentById(docId)?.let(::selectPdfDocument)
        }
    }

    fun importPdf(uri: Uri) {
        viewModelScope.launch {
            container.repository.importPdf(uri).fold(
                onSuccess = ::selectPdfDocument,
                onFailure = { err -> _uiState.update { it.copy(errorMessage = err.message) } }
            )
        }
    }

    fun selectExternalOfficeUri(uri: Uri) {
        val name = container.fileStorageManager.resolveDisplayName(uri) ?: "Document"
        _uiState.update { it.copy(selectedExternalUri = uri, selectedExternalName = name, errorMessage = null) }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    fun convert() {
        val state = _uiState.value
        when (state.mode) {
            ConversionMode.PDF_TO_JPG,
            ConversionMode.PDF_TO_PNG -> convertPdfToImages(state.mode == ConversionMode.PDF_TO_PNG)
            ConversionMode.PDF_TO_DOCX,
            ConversionMode.PDF_TO_XLSX,
            ConversionMode.PDF_TO_PPTX -> convertPdfToOffice(state.mode)
            ConversionMode.DOCX_TO_PDF,
            ConversionMode.XLSX_TO_PDF,
            ConversionMode.PPTX_TO_PDF -> convertOfficeToPdf(state.mode)
        }
    }

    private fun convertPdfToImages(asPng: Boolean) {
        val doc = _uiState.value.selectedPdfDoc ?: run {
            _uiState.update { it.copy(errorMessage = "Select a PDF document first.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isConverting = true, errorMessage = null, outputImageFiles = emptyList()) }
            val outDir = container.fileStorageManager.createTempFile("pdf_imgs", "dir").apply { mkdirs() }
            val res = container.converterEngine.pdfToImages(File(doc.localPath), outDir, asPng = asPng)
            res.fold(
                onSuccess = { imgs ->
                    val zipFile = container.fileStorageManager.createTempFile(
                        "${doc.title}_${if (asPng) "png" else "jpg"}",
                        "zip"
                    )
                    container.converterEngine.packageFilesIntoZip(imgs, zipFile)
                    val primaryFile = if (imgs.size == 1) imgs.first() else zipFile
                    val mime = when {
                        imgs.size > 1 -> "application/zip"
                        asPng -> "image/png"
                        else -> "image/jpeg"
                    }
                    container.repository.logHistorySuccess(
                        actionType = if (asPng) "PDF to PNG" else "PDF to JPG",
                        inputName = doc.fileName,
                        outputName = primaryFile.name,
                        outputPath = primaryFile.absolutePath,
                        details = "${imgs.size} images",
                        originalSizeBytes = doc.sizeBytes,
                        outputSizeBytes = primaryFile.length()
                    )
                    _uiState.update {
                        it.copy(
                            isConverting = false,
                            outputImageFiles = imgs,
                            outputFile = primaryFile,
                            outputMimeType = mime
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isConverting = false, errorMessage = err.message ?: "Image conversion failed") }
                }
            )
        }
    }

    private fun convertPdfToOffice(mode: ConversionMode) {
        val doc = _uiState.value.selectedPdfDoc ?: run {
            _uiState.update { it.copy(errorMessage = "Select a PDF document first.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isConverting = true, errorMessage = null) }
            val (ext, mime) = when (mode) {
                ConversionMode.PDF_TO_DOCX -> "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                ConversionMode.PDF_TO_XLSX -> "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                else -> "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            }
            val outFile = container.fileStorageManager.createTempFile(doc.title, ext)
            val res = when (mode) {
                ConversionMode.PDF_TO_DOCX -> container.converterEngine.pdfToDocxTextBased(File(doc.localPath), outFile)
                ConversionMode.PDF_TO_XLSX -> container.converterEngine.pdfToXlsxTextBased(File(doc.localPath), outFile)
                else -> container.converterEngine.pdfToPptxSlideImageBased(File(doc.localPath), outFile)
            }
            res.fold(
                onSuccess = { f ->
                    container.repository.logHistorySuccess(
                        actionType = mode.label,
                        inputName = doc.fileName,
                        outputName = f.name,
                        outputPath = f.absolutePath,
                        originalSizeBytes = doc.sizeBytes,
                        outputSizeBytes = f.length()
                    )
                    _uiState.update { it.copy(isConverting = false, outputFile = f, outputMimeType = mime) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isConverting = false, errorMessage = err.message ?: "Conversion failed") }
                }
            )
        }
    }

    private fun convertOfficeToPdf(mode: ConversionMode) {
        val uri = _uiState.value.selectedExternalUri ?: run {
            _uiState.update { it.copy(errorMessage = "Pick a source file first.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isConverting = true, errorMessage = null) }
            val copied = container.fileStorageManager.copyUriToTempFile(uri, "bin")
            copied.fold(
                onSuccess = { (tmp, name) ->
                    try {
                        val outFile = container.fileStorageManager.createVaultFile(name.substringBeforeLast("."))
                        val res = when (mode) {
                            ConversionMode.DOCX_TO_PDF -> container.converterEngine.docxToPdf(tmp, outFile)
                            ConversionMode.XLSX_TO_PDF -> container.converterEngine.xlsxToPdf(tmp, outFile)
                            else -> container.converterEngine.pptxToPdf(tmp, outFile)
                        }
                        res.fold(
                            onSuccess = { pdfFile ->
                                val registered = container.repository.registerGeneratedPdf(
                                    file = pdfFile,
                                    title = pdfFile.nameWithoutExtension,
                                    originTag = "converted",
                                    actionType = mode.label,
                                    inputName = name,
                                    originalSizeBytes = tmp.length()
                                )
                                _uiState.update {
                                    it.copy(
                                        isConverting = false,
                                        outputFile = pdfFile,
                                        outputMimeType = "application/pdf",
                                        outputPdfDoc = registered
                                    )
                                }
                            },
                            onFailure = { err ->
                                _uiState.update { it.copy(isConverting = false, errorMessage = err.message ?: "Conversion failed") }
                            }
                        )
                    } finally {
                        runCatching { tmp.delete() }
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isConverting = false, errorMessage = err.message) }
                }
            )
        }
    }

    fun exportToUri(uri: Uri) {
        val file = _uiState.value.outputFile ?: return
        viewModelScope.launch { container.fileStorageManager.exportFileToUri(file, uri) }
    }

    fun shareOutput() {
        val state = _uiState.value
        val file = state.outputFile ?: return
        container.fileStorageManager.shareFile(file, state.outputMimeType)
    }

    fun shareImage(file: File, isPng: Boolean) {
        container.fileStorageManager.shareFile(file, if (isPng) "image/png" else "image/jpeg")
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ConverterViewModel(container) as T
        }
    }
}
