package com.example.data.pdf

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import com.example.R
import com.example.domain.model.EditorTool
import com.example.domain.model.PdfOverlayAnnotation
import com.example.domain.model.ShapeKind
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

class PdfEditorEngine(private val context: Context) {

    suspend fun applyEdits(
        sourceFile: File,
        outputFile: File,
        annotations: List<PdfOverlayAnnotation>,
        pageOrderZeroBased: List<Int>? = null,
        pageRotations: Map<Int, Int> = emptyMap(),
        password: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val loaded = if (password.isNullOrEmpty()) {
                PDDocument.load(sourceFile, MemoryUsageSetting.setupTempFileOnly())
            } else {
                PDDocument.load(sourceFile, password, MemoryUsageSetting.setupTempFileOnly())
            }

            loaded.use { srcDoc ->
                val outDoc = PDDocument(MemoryUsageSetting.setupTempFileOnly())
                outDoc.use { doc ->
                    val latinFont = loadFont(doc, R.font.plus_jakarta_sans) ?: PDType1Font.HELVETICA
                    val bengaliFont = loadFont(doc, R.font.noto_sans_bengali) ?: latinFont

                    val totalSrc = srcDoc.numberOfPages
                    val order = (pageOrderZeroBased ?: (0 until totalSrc).toList())
                        .filter { it in 0 until totalSrc }
                    require(order.isNotEmpty()) { "Document must keep at least one page." }

                    // Build pages in new order, keeping track of original pageIndex -> new index
                    val originalToNewIndices = mutableMapOf<Int, MutableList<Int>>()
                    order.forEachIndexed { newIdx, origIdx ->
                        val imported = doc.importPage(srcDoc.getPage(origIdx))
                        val extraRot = pageRotations[origIdx] ?: 0
                        if (extraRot != 0) {
                            imported.rotation = (imported.rotation + extraRot) % 360
                        }
                        originalToNewIndices.getOrPut(origIdx) { mutableListOf() }.add(newIdx)
                    }

                    // Apply page-specific and global annotations
                    for (annotation in annotations) {
                        val targetIndices = if (annotation.pageIndex == -1) {
                            (0 until doc.numberOfPages).toList()
                        } else {
                            originalToNewIndices[annotation.pageIndex] ?: emptyList()
                        }

                        for (targetIdx in targetIndices) {
                            val page = doc.getPage(targetIdx)
                            drawAnnotationOnPage(doc, page, annotation, latinFont, bengaliFont)
                        }
                    }
                    doc.save(outputFile)
                }
            }
            outputFile
        }
    }

    private fun drawAnnotationOnPage(
        doc: PDDocument,
        page: PDPage,
        annotation: PdfOverlayAnnotation,
        latinFont: PDFont,
        bengaliFont: PDFont
    ) {
        val box = page.cropBox ?: page.mediaBox
        val pw = box.width
        val ph = box.height
        val ox = box.lowerLeftX
        val oy = box.lowerLeftY

        fun toPdfX(nx: Float) = ox + nx.coerceIn(0f, 1f) * pw
        fun toPdfY(ny: Float) = oy + (1f - ny.coerceIn(0f, 1f)) * ph

        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            when (annotation) {
                is PdfOverlayAnnotation.TextOverlay -> {
                    val font = if (annotation.text.any { it in '\u0980'..'\u09FF' }) bengaliFont else latinFont
                    val safe = sanitizeText(annotation.text, font)
                    if (safe.isNotBlank()) {
                        val x = toPdfX(annotation.normX)
                        val y = toPdfY(annotation.normY)
                        cs.beginText()
                        cs.setFont(font, annotation.fontSizePt)
                        cs.setNonStrokingColor(
                            Color.red(annotation.colorArgb),
                            Color.green(annotation.colorArgb),
                            Color.blue(annotation.colorArgb)
                        )
                        cs.newLineAtOffset(x, y)
                        cs.showText(safe)
                        cs.endText()
                    }
                }

                is PdfOverlayAnnotation.ImageOverlay -> {
                    val file = File(annotation.imagePath)
                    if (file.exists()) {
                        val bmp = BitmapFactory.decodeFile(file.absolutePath)
                        if (bmp != null) {
                            try {
                                val pdImg = LosslessFactory.createFromImage(doc, bmp)
                                val w = annotation.normWidth * pw
                                val h = annotation.normHeight * ph
                                val x = toPdfX(annotation.normX)
                                val y = toPdfY(annotation.normY) - h
                                cs.drawImage(pdImg, x, y, w, h)
                            } finally {
                                bmp.recycle()
                            }
                        }
                    }
                }

                is PdfOverlayAnnotation.SignatureOverlay -> {
                    val file = File(annotation.signatureImagePath)
                    if (file.exists()) {
                        val bmp = BitmapFactory.decodeFile(file.absolutePath)
                        if (bmp != null) {
                            try {
                                val pdImg = LosslessFactory.createFromImage(doc, bmp)
                                val w = annotation.normWidth * pw
                                val h = annotation.normHeight * ph
                                val x = toPdfX(annotation.normX)
                                val y = toPdfY(annotation.normY) - h
                                cs.drawImage(pdImg, x, y, w, h)
                            } finally {
                                bmp.recycle()
                            }
                        }
                    }
                }

                is PdfOverlayAnnotation.ShapeOverlay -> {
                    val x1 = toPdfX(annotation.normStartX)
                    val y1 = toPdfY(annotation.normStartY)
                    val x2 = toPdfX(annotation.normEndX)
                    val y2 = toPdfY(annotation.normEndY)
                    cs.setStrokingColor(
                        Color.red(annotation.colorArgb),
                        Color.green(annotation.colorArgb),
                        Color.blue(annotation.colorArgb)
                    )
                    cs.setLineWidth(annotation.strokeWidthPt)

                    when (annotation.kind) {
                        ShapeKind.RECTANGLE -> {
                            val rx = min(x1, x2)
                            val ry = min(y1, y2)
                            cs.addRect(rx, ry, kotlin.math.abs(x2 - x1), kotlin.math.abs(y2 - y1))
                            cs.stroke()
                        }
                        ShapeKind.CIRCLE -> {
                            val cx = (x1 + x2) / 2f
                            val cy = (y1 + y2) / 2f
                            val rx = kotlin.math.abs(x2 - x1) / 2f
                            val ry = kotlin.math.abs(y2 - y1) / 2f
                            drawEllipse(cs, cx, cy, rx.coerceAtLeast(4f), ry.coerceAtLeast(4f))
                            cs.stroke()
                        }
                        ShapeKind.LINE -> {
                            cs.moveTo(x1, y1)
                            cs.lineTo(x2, y2)
                            cs.stroke()
                        }
                        ShapeKind.ARROW -> {
                            cs.moveTo(x1, y1)
                            cs.lineTo(x2, y2)
                            cs.stroke()
                            val angle = atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
                            val headLen = 14.0
                            val a1 = angle + PI * 0.82
                            val a2 = angle - PI * 0.82
                            cs.moveTo(x2, y2)
                            cs.lineTo((x2 + headLen * cos(a1)).toFloat(), (y2 + headLen * sin(a1)).toFloat())
                            cs.moveTo(x2, y2)
                            cs.lineTo((x2 + headLen * cos(a2)).toFloat(), (y2 + headLen * sin(a2)).toFloat())
                            cs.stroke()
                        }
                    }
                }

                is PdfOverlayAnnotation.MarkupOverlay -> {
                    val x1 = toPdfX(annotation.normStartX)
                    val y1 = toPdfY(annotation.normStartY)
                    val x2 = toPdfX(annotation.normEndX)
                    val y2 = toPdfY(annotation.normEndY)
                    val left = min(x1, x2)
                    val right = kotlin.math.max(x1, x2)
                    val bottom = min(y1, y2)
                    val top = kotlin.math.max(y1, y2)

                    when (annotation.kind) {
                        EditorTool.HIGHLIGHT -> {
                            val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.35f }
                            cs.setGraphicsStateParameters(gs)
                            cs.setNonStrokingColor(
                                Color.red(annotation.colorArgb),
                                Color.green(annotation.colorArgb),
                                Color.blue(annotation.colorArgb)
                            )
                            cs.addRect(left, bottom, (right - left).coerceAtLeast(12f), (top - bottom).coerceAtLeast(14f))
                            cs.fill()
                        }
                        EditorTool.UNDERLINE -> {
                            cs.setStrokingColor(
                                Color.red(annotation.colorArgb),
                                Color.green(annotation.colorArgb),
                                Color.blue(annotation.colorArgb)
                            )
                            cs.setLineWidth(2f)
                            cs.moveTo(left, bottom)
                            cs.lineTo(right, bottom)
                            cs.stroke()
                        }
                        EditorTool.STRIKETHROUGH -> {
                            cs.setStrokingColor(
                                Color.red(annotation.colorArgb),
                                Color.green(annotation.colorArgb),
                                Color.blue(annotation.colorArgb)
                            )
                            cs.setLineWidth(2f)
                            val midY = (top + bottom) / 2f
                            cs.moveTo(left, midY)
                            cs.lineTo(right, midY)
                            cs.stroke()
                        }
                        else -> Unit
                    }
                }

                is PdfOverlayAnnotation.FreehandOverlay -> {
                    if (annotation.normPoints.size >= 2) {
                        cs.setStrokingColor(
                            Color.red(annotation.colorArgb),
                            Color.green(annotation.colorArgb),
                            Color.blue(annotation.colorArgb)
                        )
                        cs.setLineWidth(annotation.strokeWidthPt)
                        cs.setLineCapStyle(1)
                        cs.setLineJoinStyle(1)
                        val first = annotation.normPoints.first()
                        cs.moveTo(toPdfX(first.first), toPdfY(first.second))
                        for (i in 1 until annotation.normPoints.size) {
                            val pt = annotation.normPoints[i]
                            cs.lineTo(toPdfX(pt.first), toPdfY(pt.second))
                        }
                        cs.stroke()
                    }
                }

                is PdfOverlayAnnotation.CommentOverlay -> {
                    val x = toPdfX(annotation.normX)
                    val y = toPdfY(annotation.normY)
                    // Draw visible comment badge + add real PDF sticky note annotation
                    cs.setNonStrokingColor(254, 243, 199)
                    cs.setStrokingColor(217, 119, 6)
                    cs.setLineWidth(1.2f)
                    cs.addRect(x, y - 22f, 160f.coerceAtMost(pw - 16f), 24f)
                    cs.fillAndStroke()

                    val safe = sanitizeText(annotation.commentText.take(28), latinFont)
                    cs.beginText()
                    cs.setFont(latinFont, 9f)
                    cs.setNonStrokingColor(120, 53, 15)
                    cs.newLineAtOffset(x + 6f, y - 14f)
                    cs.showText(safe)
                    cs.endText()

                    val sticky = PDAnnotationText().apply {
                        rectangle = PDRectangle(x, y - 20f, 20f, 20f)
                        contents = annotation.commentText
                        titlePopup = annotation.author
                    }
                    page.annotations.add(sticky)
                }

                is PdfOverlayAnnotation.WatermarkOverlay -> {
                    val font = if (annotation.text.any { it in '\u0980'..'\u09FF' }) bengaliFont else latinFont
                    val safe = sanitizeText(annotation.text, font)
                    if (safe.isNotBlank()) {
                        val gs = PDExtendedGraphicsState().apply {
                            nonStrokingAlphaConstant = annotation.opacity.coerceIn(0.05f, 0.9f)
                        }
                        cs.setGraphicsStateParameters(gs)
                        val fontSize = (min(pw, ph) / 10f).coerceIn(24f, 64f)
                        val textWidth = runCatching { font.getStringWidth(safe) / 1000f * fontSize }.getOrDefault(180f)
                        cs.beginText()
                        cs.setFont(font, fontSize)
                        cs.setNonStrokingColor(
                            Color.red(annotation.colorArgb),
                            Color.green(annotation.colorArgb),
                            Color.blue(annotation.colorArgb)
                        )
                        val cx = ox + pw / 2f
                        val cy = oy + ph / 2f
                        val rad = Math.toRadians(annotation.angleDeg.toDouble())
                        val matrix = Matrix.getRotateInstance(rad, cx, cy)
                        matrix.translate(-textWidth / 2f, -fontSize / 3f)
                        cs.setTextMatrix(matrix)
                        cs.showText(safe)
                        cs.endText()
                    }
                }
            }
        }
    }

    private fun drawEllipse(cs: PDPageContentStream, cx: Float, cy: Float, rx: Float, ry: Float) {
        val k = 0.5522847498f
        val ox = rx * k
        val oy = ry * k
        cs.moveTo(cx - rx, cy)
        cs.curveTo(cx - rx, cy + oy, cx - ox, cy + ry, cx, cy + ry)
        cs.curveTo(cx + ox, cy + ry, cx + rx, cy + oy, cx + rx, cy)
        cs.curveTo(cx + rx, cy - oy, cx + ox, cy - ry, cx, cy - ry)
        cs.curveTo(cx - ox, cy - ry, cx - rx, cy - oy, cx - rx, cy)
        cs.closePath()
    }

    private fun loadFont(doc: PDDocument, resId: Int): PDFont? = runCatching {
        context.resources.openRawResource(resId).use { PDType0Font.load(doc, it, true) }
    }.getOrNull()

    private fun sanitizeText(input: String, font: PDFont): String {
        val sb = StringBuilder()
        for (ch in input.replace("\n", " ").replace("\r", "")) {
            val ok = runCatching { font.getStringWidth(ch.toString()); true }.getOrDefault(false)
            if (ok) sb.append(ch) else sb.append(' ')
        }
        return sb.toString()
    }
}
