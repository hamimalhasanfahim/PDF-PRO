package com.example.ui.screens.security

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

data class SecurityUiState(
    val selectedDoc: PdfDocumentEntity? = null,
    val userPassword: String = "",
    val ownerPassword: String = "",
    val allowPrinting: Boolean = true,
    val allowCopying: Boolean = false,
    val unlockPassword: String = "",
    val watermarkText: String = "CONFIDENTIAL",
    val watermarkOpacity: Float = 0.25f,
    val watermarkAngle: Float = 45f,
    val watermarkColorArgb: Int = 0xFF7047EB.toInt(),
    val signerName: String = "",
    val signReason: String = "Document Approved",
    val signPageNumber: Int = 1,
    val signatureFile: File? = null,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
    val outputDoc: PdfDocumentEntity? = null,
    val outputFile: File? = null
)

class SecurityViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(SecurityUiState())
    val uiState: StateFlow<SecurityUiState> = _uiState.asStateFlow()

    fun selectDocument(doc: PdfDocumentEntity) = _uiState.update {
        it.copy(selectedDoc = doc, outputDoc = null, outputFile = null, errorMessage = null)
    }

    fun selectDocumentById(docId: Long) {
        if (docId <= 0L) return
        viewModelScope.launch { container.repository.getDocumentById(docId)?.let(::selectDocument) }
    }

    fun importPdf(uri: Uri) {
        viewModelScope.launch {
            container.repository.importPdf(uri).fold(
                onSuccess = ::selectDocument,
                onFailure = { err -> _uiState.update { it.copy(errorMessage = err.message) } }
            )
        }
    }

    fun updateUserPassword(v: String) = _uiState.update { it.copy(userPassword = v) }
    fun updateOwnerPassword(v: String) = _uiState.update { it.copy(ownerPassword = v) }
    fun updateAllowPrinting(v: Boolean) = _uiState.update { it.copy(allowPrinting = v) }
    fun updateAllowCopying(v: Boolean) = _uiState.update { it.copy(allowCopying = v) }
    fun updateUnlockPassword(v: String) = _uiState.update { it.copy(unlockPassword = v) }
    fun updateWatermarkText(v: String) = _uiState.update { it.copy(watermarkText = v) }
    fun updateWatermarkOpacity(v: Float) = _uiState.update { it.copy(watermarkOpacity = v) }
    fun updateWatermarkAngle(v: Float) = _uiState.update { it.copy(watermarkAngle = v) }
    fun updateWatermarkColor(argb: Int) = _uiState.update { it.copy(watermarkColorArgb = argb) }
    fun updateSignerName(v: String) = _uiState.update { it.copy(signerName = v) }
    fun updateSignReason(v: String) = _uiState.update { it.copy(signReason = v) }
    fun updateSignPage(v: Int) = _uiState.update { it.copy(signPageNumber = v.coerceAtLeast(1)) }
    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    fun saveSignatureStrokes(strokes: List<List<Pair<Float, Float>>>) {
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
            for (st in strokes) {
                if (st.size >= 2) {
                    val p = Path()
                    p.moveTo(st.first().first * 600f, st.first().second * 260f)
                    for (i in 1 until st.size) {
                        p.lineTo(st[i].first * 600f, st[i].second * 260f)
                    }
                    canvas.drawPath(p, paint)
                }
            }
            val sigFile = container.fileStorageManager.createTempFile("sec_sig", "png")
            FileOutputStream(sigFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
            _uiState.update { it.copy(signatureFile = sigFile) }
        }
    }

    fun protectPdf() {
        val state = _uiState.value
        val doc = state.selectedDoc ?: run {
            _uiState.update { it.copy(errorMessage = "Select a PDF document first.") }
            return
        }
        if (state.userPassword.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter a document password.") }
            return
        }
        runSecurityOp(doc, "Protect PDF", "protected") { out ->
            container.securityEngine.protectWithPassword(
                inputFile = File(doc.localPath),
                outputFile = out,
                userPassword = state.userPassword,
                ownerPassword = state.ownerPassword.ifBlank { state.userPassword },
                allowPrinting = state.allowPrinting,
                allowCopying = state.allowCopying
            )
        }
    }

    fun unlockPdfAuthorized() {
        val state = _uiState.value
        val doc = state.selectedDoc ?: run {
            _uiState.update { it.copy(errorMessage = "Select a password-protected PDF first.") }
            return
        }
        if (state.unlockPassword.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Enter the authorized document password to unlock.") }
            return
        }
        runSecurityOp(doc, "Unlock PDF", "unlocked") { out ->
            container.securityEngine.removePasswordAuthorized(
                inputFile = File(doc.localPath),
                password = state.unlockPassword,
                outputFile = out
            )
        }
    }

    fun applyWatermark() {
        val state = _uiState.value
        val doc = state.selectedDoc ?: run {
            _uiState.update { it.copy(errorMessage = "Select a PDF document first.") }
            return
        }
        runSecurityOp(doc, "Watermark PDF", "watermarked") { out ->
            container.securityEngine.addWatermark(
                inputFile = File(doc.localPath),
                outputFile = out,
                watermarkText = state.watermarkText,
                opacity = state.watermarkOpacity,
                angleDeg = state.watermarkAngle,
                colorArgb = state.watermarkColorArgb
            )
        }
    }

    fun applyDigitalSignature() {
        val state = _uiState.value
        val doc = state.selectedDoc ?: run {
            _uiState.update { it.copy(errorMessage = "Select a PDF document first.") }
            return
        }
        val sigFile = state.signatureFile ?: run {
            _uiState.update { it.copy(errorMessage = "Draw your signature first.") }
            return
        }
        runSecurityOp(doc, "Digital Sign PDF", "signed") { out ->
            container.securityEngine.applySignatureStamp(
                inputFile = File(doc.localPath),
                outputFile = out,
                signatureFile = sigFile,
                signerName = state.signerName,
                reason = state.signReason,
                targetPageZeroBased = state.signPageNumber - 1
            )
        }
    }

    private fun runSecurityOp(
        doc: PdfDocumentEntity,
        action: String,
        suffix: String,
        block: suspend (File) -> Result<File>
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            val outFile = container.fileStorageManager.createVaultFile("${doc.title}_$suffix")
            block(outFile).fold(
                onSuccess = { f ->
                    val saved = container.repository.registerGeneratedPdf(
                        file = f,
                        title = "${doc.title} ($suffix)",
                        originTag = "secured",
                        actionType = action,
                        inputName = doc.fileName,
                        originalSizeBytes = doc.sizeBytes
                    )
                    _uiState.update { it.copy(isProcessing = false, outputDoc = saved, outputFile = f) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isProcessing = false, errorMessage = err.message ?: "$action failed") }
                }
            )
        }
    }

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
            return SecurityViewModel(container) as T
        }
    }
}
