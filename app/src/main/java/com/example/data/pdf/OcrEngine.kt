package com.example.data.pdf

import android.graphics.Bitmap
import com.example.domain.model.OcrResultData
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OcrEngine(
    private val rendererHelper: PdfRendererHelper
) {
    private val latinRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizeBitmap(
        bitmap: Bitmap,
        requestedLanguageCode: String
    ): Result<OcrResultData> = withContext(Dispatchers.Default) {
        runCatching {
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognized = suspendCancellableCoroutine<String> { cont ->
                latinRecognizer.process(image)
                    .addOnSuccessListener { result -> cont.resume(result.text) }
                    .addOnFailureListener { exc -> cont.resumeWithException(exc) }
            }

            val notice = if (requestedLanguageCode == "bn") {
                "Notice: Google ML Kit on-device OCR currently provides Latin script recognition; a dedicated offline Bengali OCR model is unavailable in ML Kit v2. Any Latin text on the image was recognized below."
            } else null

            OcrResultData(
                fullText = recognized,
                pagesText = listOf(1 to recognized),
                modelNotice = notice
            )
        }
    }

    suspend fun recognizeImageFile(
        file: File,
        requestedLanguageCode: String
    ): Result<OcrResultData> = withContext(Dispatchers.IO) {
        runCatching {
            val bmp = PdfCreationEngine.decodeSampledBitmapFromFile(file, 1600, 1600)
                ?: throw IllegalStateException("Unable to decode image for OCR.")
            try {
                recognizeBitmap(bmp, requestedLanguageCode).getOrThrow()
            } finally {
                bmp.recycle()
            }
        }
    }

    suspend fun recognizePdfFile(
        pdfFile: File,
        requestedLanguageCode: String,
        maxPages: Int = 25
    ): Result<OcrResultData> = withContext(Dispatchers.IO) {
        runCatching {
            val totalPages = rendererHelper.getPageCount(pdfFile)
            require(totalPages > 0) { "Unable to render PDF pages for OCR." }
            val count = minOf(totalPages, maxPages)
            val pagesOut = mutableListOf<Pair<Int, String>>()

            for (i in 0 until count) {
                val bmp = rendererHelper.renderPage(pdfFile, i, 1500) ?: continue
                try {
                    val pageOcr = recognizeBitmap(bmp, requestedLanguageCode).getOrThrow()
                    pagesOut.add((i + 1) to pageOcr.fullText)
                } finally {
                    bmp.recycle()
                }
            }

            val combined = pagesOut.joinToString("\n\n") { (p, txt) ->
                "--- Page $p ---\n$txt"
            }

            val notice = buildString {
                if (requestedLanguageCode == "bn") {
                    append("Notice: On-device ML Kit OCR supports Latin script only; offline Bengali OCR model is unavailable in ML Kit v2. For Unicode Bengali PDFs, use 'Extract Embedded Text' to read exact Bengali characters.")
                }
                if (totalPages > maxPages) {
                    if (isNotEmpty()) append(" ")
                    append("Processed first $maxPages of $totalPages pages.")
                }
            }.ifBlank { null }

            OcrResultData(
                fullText = combined,
                pagesText = pagesOut,
                modelNotice = notice
            )
        }
    }
}
