package com.example.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.example.R
import com.example.domain.model.PageOrientation
import com.example.domain.model.PageSizeOption
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

class PdfCreationEngine(private val context: Context) {

    fun resolvePageRectangle(
        pageSize: PageSizeOption,
        orientation: PageOrientation,
        customWidthPt: Float = pageSize.widthPt,
        customHeightPt: Float = pageSize.heightPt
    ): PDRectangle {
        val rawW = if (pageSize == PageSizeOption.CUSTOM) customWidthPt.coerceIn(144f, 2500f) else pageSize.widthPt
        val rawH = if (pageSize == PageSizeOption.CUSTOM) customHeightPt.coerceIn(144f, 3500f) else pageSize.heightPt
        return if (orientation == PageOrientation.LANDSCAPE) {
            PDRectangle(max(rawW, rawH), min(rawW, rawH))
        } else {
            PDRectangle(min(rawW, rawH), max(rawW, rawH))
        }
    }

    suspend fun createFromText(
        outputFile: File,
        text: String,
        pageSize: PageSizeOption = PageSizeOption.A4,
        orientation: PageOrientation = PageOrientation.PORTRAIT,
        customWidthPt: Float = pageSize.widthPt,
        customHeightPt: Float = pageSize.heightPt,
        marginPt: Float = 48f,
        fontSizePt: Float = 14f,
        textColorArgb: Int = Color.BLACK,
        headerText: String = "",
        footerText: String = "",
        includePageNumbers: Boolean = true
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val rect = resolvePageRectangle(pageSize, orientation, customWidthPt, customHeightPt)
            val safeMargin = marginPt.coerceIn(16f, min(rect.width, rect.height) / 3f)
            val usableWidth = (rect.width - safeMargin * 2f).coerceAtLeast(80f)
            val lineHeight = fontSizePt * 1.45f

            PDDocument().use { document ->
                val latinFont = loadFontSafely(document, R.font.plus_jakarta_sans) ?: PDType1Font.HELVETICA
                val bengaliFont = loadFontSafely(document, R.font.noto_sans_bengali) ?: latinFont

                val rawParagraphs = text.replace("\r\n", "\n").replace("\t", "    ").split("\n")
                val wrappedLines = mutableListOf<String>()
                for (paragraph in rawParagraphs) {
                    if (paragraph.isBlank()) {
                        wrappedLines.add("")
                    } else {
                        val activeFont = chooseFontForText(paragraph, latinFont, bengaliFont)
                        wrappedLines.addAll(wrapParagraph(paragraph, activeFont, fontSizePt, usableWidth))
                    }
                }
                if (wrappedLines.isEmpty()) wrappedLines.add(" ")

                val topOffset = if (headerText.isNotBlank()) 24f else 0f
                val bottomOffset = if (footerText.isNotBlank() || includePageNumbers) 28f else 0f
                val usableHeight = (rect.height - safeMargin * 2f - topOffset - bottomOffset).coerceAtLeast(lineHeight)
                val linesPerPage = max(1, (usableHeight / lineHeight).toInt())
                val chunks = wrappedLines.chunked(linesPerPage)
                val totalPages = chunks.size

                val r = Color.red(textColorArgb)
                val g = Color.green(textColorArgb)
                val b = Color.blue(textColorArgb)

                chunks.forEachIndexed { pageIdx, pageLines ->
                    val page = PDPage(rect)
                    document.addPage(page)
                    PDPageContentStream(document, page).use { cs ->
                        // Header
                        if (headerText.isNotBlank()) {
                            val hFont = chooseFontForText(headerText, latinFont, bengaliFont)
                            val safeHeader = filterSupportedGlyphs(headerText, hFont)
                            cs.beginText()
                            cs.setFont(hFont, 10f)
                            cs.setNonStrokingColor(100, 108, 130)
                            cs.newLineAtOffset(safeMargin, rect.height - safeMargin + 8f)
                            cs.showText(safeHeader)
                            cs.endText()
                        }

                        // Body text
                        var y = rect.height - safeMargin - topOffset - fontSizePt
                        for (line in pageLines) {
                            if (line.isNotEmpty()) {
                                val lineFont = chooseFontForText(line, latinFont, bengaliFont)
                                val safeLine = filterSupportedGlyphs(line, lineFont)
                                if (safeLine.isNotEmpty()) {
                                    cs.beginText()
                                    cs.setFont(lineFont, fontSizePt)
                                    cs.setNonStrokingColor(r, g, b)
                                    cs.newLineAtOffset(safeMargin, y)
                                    cs.showText(safeLine)
                                    cs.endText()
                                }
                            }
                            y -= lineHeight
                        }

                        // Footer & Page Number
                        val footerY = safeMargin - 16f
                        if (footerText.isNotBlank()) {
                            val fFont = chooseFontForText(footerText, latinFont, bengaliFont)
                            val safeFooter = filterSupportedGlyphs(footerText, fFont)
                            cs.beginText()
                            cs.setFont(fFont, 10f)
                            cs.setNonStrokingColor(100, 108, 130)
                            cs.newLineAtOffset(safeMargin, footerY)
                            cs.showText(safeFooter)
                            cs.endText()
                        }
                        if (includePageNumbers) {
                            val pageLabel = "Page ${pageIdx + 1} of $totalPages"
                            val labelWidth = runCatching { latinFont.getStringWidth(pageLabel) / 1000f * 10f }.getOrDefault(60f)
                            cs.beginText()
                            cs.setFont(latinFont, 10f)
                            cs.setNonStrokingColor(100, 108, 130)
                            cs.newLineAtOffset(rect.width - safeMargin - labelWidth, footerY)
                            cs.showText(pageLabel)
                            cs.endText()
                        }
                    }
                }
                document.save(outputFile)
            }
            outputFile
        }
    }

    suspend fun createFromImageFiles(
        outputFile: File,
        imageFiles: List<File>,
        pageSize: PageSizeOption = PageSizeOption.A4,
        orientation: PageOrientation = PageOrientation.PORTRAIT,
        marginPt: Float = 24f,
        includePageNumbers: Boolean = false
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(imageFiles.isNotEmpty()) { "Select at least one image." }
            val rect = resolvePageRectangle(pageSize, orientation)
            val safeMargin = marginPt.coerceIn(0f, min(rect.width, rect.height) / 4f)
            val maxW = (rect.width - safeMargin * 2f).coerceAtLeast(50f)
            val maxH = (rect.height - safeMargin * 2f - if (includePageNumbers) 20f else 0f).coerceAtLeast(50f)

            PDDocument().use { document ->
                val font = loadFontSafely(document, R.font.plus_jakarta_sans) ?: PDType1Font.HELVETICA
                imageFiles.forEachIndexed { idx, imgFile ->
                    val bitmap = decodeSampledBitmapFromFile(imgFile, 1800, 1800)
                        ?: throw IllegalStateException("Could not decode image: ${imgFile.name}")
                    try {
                        val page = PDPage(rect)
                        document.addPage(page)
                        val pdImage = JPEGFactory.createFromImage(document, bitmap, 0.88f)
                        val imgRatio = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
                        val boxRatio = maxW / maxH
                        val drawW: Float
                        val drawH: Float
                        if (imgRatio > boxRatio) {
                            drawW = maxW
                            drawH = maxW / imgRatio
                        } else {
                            drawH = maxH
                            drawW = maxH * imgRatio
                        }
                        val x = (rect.width - drawW) / 2f
                        val y = (rect.height - drawH) / 2f + if (includePageNumbers) 10f else 0f

                        PDPageContentStream(document, page).use { cs ->
                            cs.drawImage(pdImage, x, y, drawW, drawH)
                            if (includePageNumbers) {
                                val label = "${idx + 1} / ${imageFiles.size}"
                                val w = runCatching { font.getStringWidth(label) / 1000f * 10f }.getOrDefault(30f)
                                cs.beginText()
                                cs.setFont(font, 10f)
                                cs.setNonStrokingColor(90, 95, 115)
                                cs.newLineAtOffset((rect.width - w) / 2f, 14f)
                                cs.showText(label)
                                cs.endText()
                            }
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
                document.save(outputFile)
            }
            outputFile
        }
    }

    suspend fun createFromImageUris(
        outputFile: File,
        uris: List<Uri>,
        fileManager: FileStorageManager,
        pageSize: PageSizeOption = PageSizeOption.A4,
        orientation: PageOrientation = PageOrientation.PORTRAIT,
        marginPt: Float = 24f,
        includePageNumbers: Boolean = false
    ): Result<File> = withContext(Dispatchers.IO) {
        val tempFiles = mutableListOf<File>()
        try {
            for (uri in uris) {
                val (tmp, _) = fileManager.copyUriToTempFile(uri, "jpg").getOrThrow()
                tempFiles.add(tmp)
            }
            createFromImageFiles(outputFile, tempFiles, pageSize, orientation, marginPt, includePageNumbers)
        } finally {
            tempFiles.forEach { runCatching { it.delete() } }
        }
    }

    suspend fun createBlankPdf(
        outputFile: File,
        pageCount: Int = 1,
        pageSize: PageSizeOption = PageSizeOption.A4,
        orientation: PageOrientation = PageOrientation.PORTRAIT,
        customWidthPt: Float = pageSize.widthPt,
        customHeightPt: Float = pageSize.heightPt
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val count = pageCount.coerceIn(1, 200)
            val rect = resolvePageRectangle(pageSize, orientation, customWidthPt, customHeightPt)
            PDDocument().use { document ->
                repeat(count) {
                    document.addPage(PDPage(rect))
                }
                document.save(outputFile)
            }
            outputFile
        }
    }

    private fun loadFontSafely(document: PDDocument, fontResId: Int): PDFont? {
        return runCatching {
            val stream: InputStream = context.resources.openRawResource(fontResId)
            stream.use { PDType0Font.load(document, it, true) }
        }.getOrNull()
    }

    private fun chooseFontForText(text: String, latinFont: PDFont, bengaliFont: PDFont): PDFont {
        val hasBengali = text.any { ch -> ch in '\u0980'..'\u09FF' }
        return if (hasBengali) bengaliFont else latinFont
    }

    private fun filterSupportedGlyphs(input: String, font: PDFont): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (ch == ' ') {
                sb.append(ch)
                continue
            }
            val ok = runCatching {
                font.getStringWidth(ch.toString())
                true
            }.getOrDefault(false)
            if (ok) sb.append(ch) else sb.append(' ')
        }
        return sb.toString()
    }

    private fun wrapParagraph(
        paragraph: String,
        font: PDFont,
        fontSize: Float,
        maxWidth: Float
    ): List<String> {
        val words = paragraph.split(Regex("(?<=\\s)|(?=\\s)")).filter { it.isNotEmpty() }
        val lines = mutableListOf<String>()
        var current = StringBuilder()

        for (token in words) {
            val candidate = current.toString() + token
            val width = safeMeasureWidth(filterSupportedGlyphs(candidate, font), font, fontSize)
            if (width <= maxWidth) {
                current.append(token)
            } else {
                if (current.isNotEmpty()) {
                    lines.add(current.toString().trimEnd())
                    current = StringBuilder()
                }
                val trimmed = token.trimStart()
                if (safeMeasureWidth(filterSupportedGlyphs(trimmed, font), font, fontSize) <= maxWidth) {
                    current.append(trimmed)
                } else {
                    // Break ultra-long word character by character
                    for (ch in trimmed) {
                        val next = current.toString() + ch
                        if (safeMeasureWidth(filterSupportedGlyphs(next, font), font, fontSize) > maxWidth && current.isNotEmpty()) {
                            lines.add(current.toString())
                            current = StringBuilder()
                        }
                        current.append(ch)
                    }
                }
            }
        }
        if (current.isNotEmpty()) {
            lines.add(current.toString().trimEnd())
        }
        return lines
    }

    private fun safeMeasureWidth(text: String, font: PDFont, fontSize: Float): Float {
        return runCatching { font.getStringWidth(text) / 1000f * fontSize }
            .getOrDefault(text.length * fontSize * 0.55f)
    }

    companion object {
        fun decodeSampledBitmapFromFile(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return null
            var inSampleSize = 1
            if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                val halfH = options.outHeight / 2
                val halfW = options.outWidth / 2
                while ((halfH / inSampleSize) >= reqHeight && (halfW / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2
                }
            }
            val decodeOpts = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            return BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
        }
    }
}
