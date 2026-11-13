package com.example.data.pdf

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import com.example.R
import com.example.domain.model.PdfBookmarkItem
import com.example.domain.model.PdfMetadataInfo
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

class PdfSecurityEngine(private val context: Context) {

    suspend fun protectWithPassword(
        inputFile: File,
        outputFile: File,
        userPassword: String,
        ownerPassword: String = userPassword,
        allowPrinting: Boolean = true,
        allowCopying: Boolean = false
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(userPassword.isNotBlank()) { "Password cannot be empty." }
            PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val accessPermission = AccessPermission().apply {
                    setCanPrint(allowPrinting)
                    setCanPrintDegraded(allowPrinting)
                    setCanExtractContent(allowCopying)
                    setCanExtractForAccessibility(true)
                    setCanModify(false)
                }
                val policy = StandardProtectionPolicy(
                    ownerPassword.ifBlank { userPassword },
                    userPassword,
                    accessPermission
                ).apply {
                    encryptionKeyLength = 128
                }
                doc.protect(policy)
                doc.save(outputFile)
            }
            outputFile
        }
    }

    suspend fun removePasswordAuthorized(
        inputFile: File,
        password: String,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(password.isNotEmpty()) { "Enter the document password to unlock." }
            PDDocument.load(inputFile, password, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                doc.isAllSecurityToBeRemoved = true
                doc.save(outputFile)
            }
            outputFile
        }
    }

    suspend fun addWatermark(
        inputFile: File,
        outputFile: File,
        watermarkText: String,
        opacity: Float = 0.25f,
        angleDeg: Float = 45f,
        colorArgb: Int = 0xFF7047EB.toInt()
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(watermarkText.isNotBlank()) { "Watermark text cannot be empty." }
            PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val font = loadFont(doc, R.font.plus_jakarta_sans) ?: PDType1Font.HELVETICA_BOLD
                val bengaliFont = loadFont(doc, R.font.noto_sans_bengali) ?: font
                val activeFont = if (watermarkText.any { it in '\u0980'..'\u09FF' }) bengaliFont else font

                for (i in 0 until doc.numberOfPages) {
                    val page = doc.getPage(i)
                    val box = page.cropBox ?: page.mediaBox
                    val pw = box.width
                    val ph = box.height
                    val fontSize = (min(pw, ph) / 9f).coerceIn(24f, 64f)
                    val textW = runCatching { activeFont.getStringWidth(watermarkText) / 1000f * fontSize }.getOrDefault(180f)

                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                        val gs = PDExtendedGraphicsState().apply {
                            nonStrokingAlphaConstant = opacity.coerceIn(0.08f, 0.85f)
                        }
                        cs.setGraphicsStateParameters(gs)
                        cs.beginText()
                        cs.setFont(activeFont, fontSize)
                        cs.setNonStrokingColor(
                            Color.red(colorArgb),
                            Color.green(colorArgb),
                            Color.blue(colorArgb)
                        )
                        val m = Matrix.getRotateInstance(
                            Math.toRadians(angleDeg.toDouble()),
                            box.lowerLeftX + pw / 2f,
                            box.lowerLeftY + ph / 2f
                        )
                        m.translate(-textW / 2f, -fontSize / 3f)
                        cs.setTextMatrix(m)
                        cs.showText(watermarkText)
                        cs.endText()
                    }
                }
                doc.save(outputFile)
            }
            outputFile
        }
    }

    suspend fun applySignatureStamp(
        inputFile: File,
        outputFile: File,
        signatureFile: File,
        signerName: String,
        reason: String,
        targetPageZeroBased: Int = 0,
        normX: Float = 0.58f,
        normY: Float = 0.80f
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(signatureFile.exists()) { "Please draw a signature first." }
            val digestHex = computeSha256(inputFile).take(16)
            val stampTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

            PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val pageIdx = targetPageZeroBased.coerceIn(0, (doc.numberOfPages - 1).coerceAtLeast(0))
                val page = doc.getPage(pageIdx)
                val box = page.cropBox ?: page.mediaBox
                val pw = box.width
                val ph = box.height

                val bmp = BitmapFactory.decodeFile(signatureFile.absolutePath)
                    ?: throw IllegalStateException("Failed to decode signature bitmap.")
                try {
                    val font = loadFont(doc, R.font.plus_jakarta_sans) ?: PDType1Font.HELVETICA
                    val pdImg = LosslessFactory.createFromImage(doc, bmp)
                    val stampW = (pw * 0.28f).coerceIn(110f, 220f)
                    val stampH = stampW * (bmp.height.toFloat() / bmp.width.toFloat().coerceAtLeast(1f))
                    val x = box.lowerLeftX + (normX.coerceIn(0.05f, 0.72f) * pw)
                    val y = box.lowerLeftY + ((1f - normY.coerceIn(0.1f, 0.9f)) * ph)

                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                        cs.drawImage(pdImg, x, y, stampW, stampH)
                        cs.beginText()
                        cs.setFont(font, 7.5f)
                        cs.setNonStrokingColor(70, 75, 95)
                        cs.newLineAtOffset(x, (y - 10f).coerceAtLeast(12f))
                        val line1 = "Signed by ${signerName.ifBlank { "Authorized User" }} | $stampTime"
                        cs.showText(line1)
                        cs.endText()

                        cs.beginText()
                        cs.setFont(font, 7f)
                        cs.setNonStrokingColor(100, 108, 130)
                        cs.newLineAtOffset(x, (y - 19f).coerceAtLeast(4f))
                        cs.showText("Reason: ${reason.ifBlank { "Approval" }} | SHA256:$digestHex")
                        cs.endText()
                    }
                } finally {
                    bmp.recycle()
                }
                val info = doc.documentInformation
                info.setCustomMetadataValue("SignedBy", signerName.ifBlank { "User" })
                info.setCustomMetadataValue("SignedAt", stampTime)
                info.setCustomMetadataValue("SourceSHA256", digestHex)
                doc.save(outputFile)
            }
            outputFile
        }
    }

    suspend fun readMetadata(inputFile: File, password: String? = null): Result<PdfMetadataInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val doc = if (password.isNullOrEmpty()) {
                    PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly())
                } else {
                    PDDocument.load(inputFile, password, MemoryUsageSetting.setupTempFileOnly())
                }
                doc.use {
                    val info = it.documentInformation
                    PdfMetadataInfo(
                        title = info.title.orEmpty(),
                        author = info.author.orEmpty(),
                        subject = info.subject.orEmpty(),
                        keywords = info.keywords.orEmpty(),
                        creator = info.creator.orEmpty(),
                        producer = info.producer.orEmpty(),
                        pageCount = it.numberOfPages,
                        fileSizeBytes = inputFile.length(),
                        isEncrypted = it.isEncrypted
                    )
                }
            }
        }

    suspend fun updateMetadata(
        inputFile: File,
        outputFile: File,
        metadata: PdfMetadataInfo
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val info = doc.documentInformation
                info.title = metadata.title
                info.author = metadata.author
                info.subject = metadata.subject
                info.keywords = metadata.keywords
                info.creator = metadata.creator.ifBlank { "PDF Master Pro" }
                doc.documentInformation = info
                doc.save(outputFile)
            }
            outputFile
        }
    }

    suspend fun readBookmarks(inputFile: File): Result<List<PdfBookmarkItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                    val outline = doc.documentCatalog.documentOutline ?: return@use emptyList()
                    val items = mutableListOf<PdfBookmarkItem>()
                    var current: PDOutlineItem? = outline.firstChild
                    while (current != null) {
                        val destPage = runCatching { current.findDestinationPage(doc) }.getOrNull()
                        val idx = if (destPage != null) doc.pages.indexOf(destPage).coerceAtLeast(0) else 0
                        items.add(PdfBookmarkItem(title = current.title ?: "Bookmark", pageIndex = idx))
                        current = current.nextSibling
                    }
                    items
                }
            }
        }

    suspend fun saveBookmarks(
        inputFile: File,
        outputFile: File,
        bookmarks: List<PdfBookmarkItem>
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val outline = PDDocumentOutline()
                bookmarks.forEach { bm ->
                    val pageIdx = bm.pageIndex.coerceIn(0, (doc.numberOfPages - 1).coerceAtLeast(0))
                    val item = PDOutlineItem().apply {
                        title = bm.title
                        setDestination(doc.getPage(pageIdx))
                    }
                    outline.addLast(item)
                }
                doc.documentCatalog.documentOutline = outline
                doc.save(outputFile)
            }
            outputFile
        }
    }

    suspend fun addPageNumbers(
        inputFile: File,
        outputFile: File,
        prefix: String = "Page ",
        includeTotal: Boolean = true,
        atBottom: Boolean = true,
        startNumber: Int = 1
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val font = loadFont(doc, R.font.plus_jakarta_sans) ?: PDType1Font.HELVETICA
                val total = doc.numberOfPages
                for (i in 0 until total) {
                    val page = doc.getPage(i)
                    val box = page.cropBox ?: page.mediaBox
                    val num = startNumber + i
                    val label = if (includeTotal) "$prefix$num / ${startNumber + total - 1}" else "$prefix$num"
                    val textW = runCatching { font.getStringWidth(label) / 1000f * 10f }.getOrDefault(45f)
                    val x = box.lowerLeftX + (box.width - textW) / 2f
                    val y = if (atBottom) box.lowerLeftY + 18f else box.lowerLeftY + box.height - 24f

                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                        cs.beginText()
                        cs.setFont(font, 10f)
                        cs.setNonStrokingColor(80, 86, 106)
                        cs.newLineAtOffset(x, y)
                        cs.showText(label)
                        cs.endText()
                    }
                }
                doc.save(outputFile)
            }
            outputFile
        }
    }

    private fun loadFont(doc: PDDocument, resId: Int): PDFont? = runCatching {
        context.resources.openRawResource(resId).use { PDType0Font.load(doc, it, true) }
    }.getOrNull()

    private fun computeSha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(8192)
            var r: Int
            while (fis.read(buf).also { r = it } != -1) {
                md.update(buf, 0, r)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
