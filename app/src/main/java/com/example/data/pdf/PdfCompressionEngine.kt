package com.example.data.pdf

import android.graphics.Bitmap
import com.example.domain.model.CompressionLevel
import com.example.domain.model.CompressionStats
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

class PdfCompressionEngine {

    suspend fun compressPdf(
        inputFile: File,
        outputFile: File,
        level: CompressionLevel,
        customQuality: Int = level.jpegQuality,
        customMaxDimPx: Int = level.maxImageDimensionPx
    ): Result<CompressionStats> = withContext(Dispatchers.IO) {
        runCatching {
            require(inputFile.exists()) { "Input PDF file does not exist." }
            val originalBytes = inputFile.length()
            val quality = (if (level == CompressionLevel.CUSTOM) customQuality else level.jpegQuality)
                .coerceIn(15, 95) / 100f
            val maxDim = (if (level == CompressionLevel.CUSTOM) customMaxDimPx else level.maxImageDimensionPx)
                .coerceIn(400, 3200)

            PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                for (pageIndex in 0 until doc.numberOfPages) {
                    val page = doc.getPage(pageIndex)
                    val resources = page.resources ?: continue
                    val names = resources.xObjectNames.toList()
                    for (name in names) {
                        val xObj = runCatching { resources.getXObject(name) }.getOrNull()
                        if (xObj is PDImageXObject) {
                            val bmp = runCatching { xObj.image }.getOrNull() ?: continue
                            try {
                                val scaled = downscaleIfNeeded(bmp, maxDim)
                                val replacement = JPEGFactory.createFromImage(doc, scaled, quality)
                                resources.put(name, replacement)
                                if (scaled !== bmp) scaled.recycle()
                            } finally {
                                bmp.recycle()
                            }
                        }
                    }
                }
                doc.save(outputFile)
            }

            CompressionStats(
                originalBytes = originalBytes,
                compressedBytes = outputFile.length(),
                outputFile = outputFile
            )
        }
    }

    private fun downscaleIfNeeded(src: Bitmap, maxDim: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= maxDim && h <= maxDim) return src
        val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
        val newW = (w * ratio).roundToInt().coerceAtLeast(1)
        val newH = (h * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, newW, newH, true)
    }
}
