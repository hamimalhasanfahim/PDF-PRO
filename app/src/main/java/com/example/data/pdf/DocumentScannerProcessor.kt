package com.example.data.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import com.example.domain.model.ScanFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class DocumentScannerProcessor {

    /**
     * Heuristic edge/document quad detection using luminance gradient boundaries.
     * Falls back cleanly to a snug default document frame if contrast is uniform.
     */
    suspend fun detectDocumentEdges(imageFile: File): List<Pair<Float, Float>> =
        withContext(Dispatchers.IO) {
            val bmp = PdfCreationEngine.decodeSampledBitmapFromFile(imageFile, 360, 480)
                ?: return@withContext defaultCorners()
            try {
                val w = bmp.width
                val h = bmp.height
                if (w < 32 || h < 32) return@withContext defaultCorners()

                val pixels = IntArray(w * h)
                bmp.getPixels(pixels, 0, w, 0, 0, w, h)

                fun lum(x: Int, y: Int): Int {
                    val c = pixels[y * w + x]
                    return (Color.red(c) * 30 + Color.green(c) * 59 + Color.blue(c) * 11) / 100
                }

                // Estimate border background luminance
                var borderSum = 0L
                var borderCount = 0
                for (x in 0 until w step 4) {
                    borderSum += lum(x, 2) + lum(x, h - 3)
                    borderCount += 2
                }
                val bgLum = (borderSum / max(1, borderCount)).toInt()

                var minX = w
                var maxX = 0
                var minY = h
                var maxY = 0
                var found = 0

                for (y in 4 until h - 4 step 3) {
                    for (x in 4 until w - 4 step 3) {
                        val l = lum(x, y)
                        if (abs(l - bgLum) > 28 && l > 95) {
                            if (x < minX) minX = x
                            if (x > maxX) maxX = x
                            if (y < minY) minY = y
                            if (y > maxY) maxY = y
                            found++
                        }
                    }
                }

                if (found < 40 || (maxX - minX) < w * 0.25f || (maxY - minY) < h * 0.25f) {
                    return@withContext defaultCorners()
                }

                val left = (minX.toFloat() / w).coerceIn(0.03f, 0.35f)
                val right = (maxX.toFloat() / w).coerceIn(0.65f, 0.97f)
                val top = (minY.toFloat() / h).coerceIn(0.03f, 0.35f)
                val bottom = (maxY.toFloat() / h).coerceIn(0.65f, 0.97f)

                listOf(
                    left to top,
                    right to top,
                    right to bottom,
                    left to bottom
                )
            } finally {
                bmp.recycle()
            }
        }

    suspend fun processScannedPage(
        sourceFile: File,
        outputFile: File,
        corners: List<Pair<Float, Float>>,
        rotationDegrees: Int,
        filter: ScanFilter,
        brightness: Float,
        contrast: Float
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val rawBmp = PdfCreationEngine.decodeSampledBitmapFromFile(sourceFile, 1800, 1800)
                ?: throw IllegalStateException("Cannot decode captured image.")

            val warped = warpPerspectiveAndRotate(rawBmp, corners, rotationDegrees)
            if (warped !== rawBmp) rawBmp.recycle()

            val filtered = applyFilterAndAdjustments(warped, filter, brightness, contrast)
            if (filtered !== warped) warped.recycle()

            try {
                FileOutputStream(outputFile).use { fos ->
                    filtered.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                }
            } finally {
                filtered.recycle()
            }
            outputFile
        }
    }

    private fun warpPerspectiveAndRotate(
        src: Bitmap,
        corners: List<Pair<Float, Float>>,
        rotationDegrees: Int
    ): Bitmap {
        val pts = if (corners.size == 4) corners else defaultCorners()
        val sw = src.width.toFloat()
        val sh = src.height.toFloat()

        val tlX = pts[0].first * sw
        val tlY = pts[0].second * sh
        val trX = pts[1].first * sw
        val trY = pts[1].second * sh
        val brX = pts[2].first * sw
        val brY = pts[2].second * sh
        val blX = pts[3].first * sw
        val blY = pts[3].second * sh

        val outW = max(hypot(trX - tlX, trY - tlY), hypot(brX - blX, brY - blY))
            .roundToInt().coerceIn(200, 2000)
        val outH = max(hypot(blX - tlX, blY - tlY), hypot(brX - trX, brY - trY))
            .roundToInt().coerceIn(200, 2400)

        val srcPts = floatArrayOf(tlX, tlY, trX, trY, brX, brY, blX, blY)
        val dstPts = floatArrayOf(0f, 0f, outW.toFloat(), 0f, outW.toFloat(), outH.toFloat(), 0f, outH.toFloat())

        val matrix = Matrix()
        matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)

        val warped = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(warped)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(src, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

        val normRot = ((rotationDegrees % 360) + 360) % 360
        if (normRot == 0) return warped

        val rotMatrix = Matrix().apply { postRotate(normRot.toFloat()) }
        val rotated = Bitmap.createBitmap(warped, 0, 0, warped.width, warped.height, rotMatrix, true)
        if (rotated !== warped) warped.recycle()
        return rotated
    }

    private fun applyFilterAndAdjustments(
        src: Bitmap,
        filter: ScanFilter,
        brightness: Float,
        contrast: Float
    ): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val effectiveContrast = when (filter) {
            ScanFilter.COLOR_ENHANCED -> (contrast * 1.18f).coerceIn(0.5f, 2.5f)
            ScanFilter.BLACK_AND_WHITE -> (contrast * 1.6f).coerceIn(0.8f, 3.0f)
            else -> contrast.coerceIn(0.5f, 2.5f)
        }
        val translate = (-0.5f * effectiveContrast + 0.5f) * 255f + brightness.coerceIn(-80f, 80f)

        val cm = ColorMatrix(
            floatArrayOf(
                effectiveContrast, 0f, 0f, 0f, translate,
                0f, effectiveContrast, 0f, 0f, translate,
                0f, 0f, effectiveContrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )

        if (filter == ScanFilter.GRAYSCALE || filter == ScanFilter.BLACK_AND_WHITE) {
            val gray = ColorMatrix().apply { setSaturation(0f) }
            cm.postConcat(gray)
        } else if (filter == ScanFilter.COLOR_ENHANCED) {
            val sat = ColorMatrix().apply { setSaturation(1.25f) }
            cm.postConcat(sat)
        }

        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(src, 0f, 0f, paint)

        if (filter == ScanFilter.BLACK_AND_WHITE) {
            val w = out.width
            val h = out.height
            val pixels = IntArray(w * h)
            out.getPixels(pixels, 0, w, 0, 0, w, h)
            for (i in pixels.indices) {
                val c = pixels[i]
                val lum = Color.red(c)
                pixels[i] = if (lum > 142) Color.WHITE else Color.BLACK
            }
            out.setPixels(pixels, 0, w, 0, 0, w, h)
        }

        return out
    }

    companion object {
        fun defaultCorners(): List<Pair<Float, Float>> = listOf(
            0.06f to 0.06f,
            0.94f to 0.06f,
            0.94f to 0.94f,
            0.06f to 0.94f
        )
    }
}
