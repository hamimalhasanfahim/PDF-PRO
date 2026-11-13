package com.example.data.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

class PdfRendererHelper {
    private val mutex = Mutex()

    suspend fun getPageCount(file: File): Int = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext 0
        mutex.withLock {
            runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        renderer.pageCount
                    }
                }
            }.getOrDefault(0)
        }
    }

    suspend fun renderPage(
        file: File,
        pageIndex: Int,
        maxDimensionPx: Int = 1200
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        mutex.withLock {
            runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@use null
                        renderer.openPage(pageIndex).use { page ->
                            val scale = minOf(
                                maxDimensionPx.toFloat() / page.width.coerceAtLeast(1),
                                maxDimensionPx.toFloat() / page.height.coerceAtLeast(1)
                            ).coerceIn(0.25f, 3.0f)
                            val w = (page.width * scale).roundToInt().coerceAtLeast(1)
                            val h = (page.height * scale).roundToInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap
                        }
                    }
                }
            }.getOrNull()
        }
    }

    suspend fun renderThumbnails(
        file: File,
        maxCount: Int = 40,
        thumbMaxPx: Int = 320
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        mutex.withLock {
            val result = mutableListOf<Bitmap>()
            runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        val count = minOf(renderer.pageCount, maxCount)
                        for (i in 0 until count) {
                            renderer.openPage(i).use { page ->
                                val scale = minOf(
                                    thumbMaxPx.toFloat() / page.width.coerceAtLeast(1),
                                    thumbMaxPx.toFloat() / page.height.coerceAtLeast(1)
                                ).coerceIn(0.15f, 1.5f)
                                val w = (page.width * scale).roundToInt().coerceAtLeast(1)
                                val h = (page.height * scale).roundToInt().coerceAtLeast(1)
                                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                result.add(bmp)
                            }
                        }
                    }
                }
            }
            result
        }
    }
}
