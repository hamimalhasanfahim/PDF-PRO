package com.example.data.pdf

import android.graphics.Bitmap
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfOrganizerEngine {

    suspend fun mergePdfs(
        inputFiles: List<File>,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(inputFiles.size >= 2) { "Select at least 2 PDF files to merge." }
            val merger = PDFMergerUtility()
            inputFiles.forEach { file ->
                require(file.exists()) { "Missing file: ${file.name}" }
                merger.addSource(file)
            }
            merger.destinationFileName = outputFile.absolutePath
            merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
            outputFile
        }
    }

    suspend fun splitByRanges(
        inputFile: File,
        rangesSpec: String,
        createOutputFile: (index: Int, suffix: String) -> File
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        runCatching {
            PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { src ->
                val total = src.numberOfPages
                val groups = parseRangeGroups(rangesSpec, total)
                require(groups.isNotEmpty()) { "No valid page ranges found for $total-page PDF." }

                val outputs = mutableListOf<File>()
                groups.forEachIndexed { idx, oneBasedPages ->
                    val outFile = createOutputFile(idx + 1, "part_${idx + 1}")
                    PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { partDoc ->
                        oneBasedPages.forEach { p ->
                            partDoc.importPage(src.getPage(p - 1))
                        }
                        partDoc.save(outFile)
                    }
                    outputs.add(outFile)
                }
                outputs
            }
        }
    }

    suspend fun splitEveryNPages(
        inputFile: File,
        pagesPerChunk: Int,
        createOutputFile: (index: Int, suffix: String) -> File
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        runCatching {
            val n = pagesPerChunk.coerceAtLeast(1)
            PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { src ->
                val total = src.numberOfPages
                val outputs = mutableListOf<File>()
                var idx = 1
                for (start in 0 until total step n) {
                    val end = minOf(total, start + n)
                    val outFile = createOutputFile(idx, "pages_${start + 1}_$end")
                    PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { partDoc ->
                        for (p in start until end) {
                            partDoc.importPage(src.getPage(p))
                        }
                        partDoc.save(outFile)
                    }
                    outputs.add(outFile)
                    idx++
                }
                outputs
            }
        }
    }

    suspend fun extractPages(
        inputFile: File,
        oneBasedPages: List<Int>,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(oneBasedPages.isNotEmpty()) { "Select at least one page to extract." }
            PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { src ->
                val total = src.numberOfPages
                val valid = oneBasedPages.filter { it in 1..total }
                require(valid.isNotEmpty()) { "Selected pages are out of range (1..$total)." }
                PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { outDoc ->
                    valid.forEach { pageNum ->
                        outDoc.importPage(src.getPage(pageNum - 1))
                    }
                    outDoc.save(outputFile)
                }
            }
            outputFile
        }
    }

    suspend fun deletePages(
        inputFile: File,
        oneBasedPagesToDelete: Set<Int>,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { src ->
                val total = src.numberOfPages
                val remaining = (1..total).filterNot { it in oneBasedPagesToDelete }
                require(remaining.isNotEmpty()) { "Cannot delete all pages from a PDF." }
                PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { outDoc ->
                    remaining.forEach { p -> outDoc.importPage(src.getPage(p - 1)) }
                    outDoc.save(outputFile)
                }
            }
            outputFile
        }
    }

    suspend fun rotatePages(
        inputFile: File,
        oneBasedPages: Set<Int>?, // null = all pages
        angleDeltaDegrees: Int,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val total = doc.numberOfPages
                for (i in 0 until total) {
                    if (oneBasedPages == null || (i + 1) in oneBasedPages) {
                        val page = doc.getPage(i)
                        page.rotation = ((page.rotation + angleDeltaDegrees) % 360 + 360) % 360
                    }
                }
                doc.save(outputFile)
            }
            outputFile
        }
    }

    suspend fun rearrangePages(
        inputFile: File,
        newZeroBasedOrder: List<Int>,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { src ->
                val total = src.numberOfPages
                val valid = newZeroBasedOrder.filter { it in 0 until total }
                require(valid.isNotEmpty()) { "Page order cannot be empty." }
                PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { outDoc ->
                    valid.forEach { idx -> outDoc.importPage(src.getPage(idx)) }
                    outDoc.save(outputFile)
                }
            }
            outputFile
        }
    }

    suspend fun duplicatePages(
        inputFile: File,
        oneBasedPagesToDuplicate: Set<Int>,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { src ->
                PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { outDoc ->
                    for (i in 0 until src.numberOfPages) {
                        outDoc.importPage(src.getPage(i))
                        if ((i + 1) in oneBasedPagesToDuplicate) {
                            outDoc.importPage(src.getPage(i))
                        }
                    }
                    outDoc.save(outputFile)
                }
            }
            outputFile
        }
    }

    suspend fun reversePageOrder(
        inputFile: File,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { src ->
                PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { outDoc ->
                    for (i in (src.numberOfPages - 1) downTo 0) {
                        outDoc.importPage(src.getPage(i))
                    }
                    outDoc.save(outputFile)
                }
            }
            outputFile
        }
    }

    suspend fun insertBlankPages(
        inputFile: File,
        insertAfterOneBasedPage: Int, // 0 = before first page
        blankCount: Int,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val count = blankCount.coerceIn(1, 50)
            PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { src ->
                val refBox = if (src.numberOfPages > 0) src.getPage(0).mediaBox else PDRectangle.A4
                val targetIndex = insertAfterOneBasedPage.coerceIn(0, src.numberOfPages)
                PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { outDoc ->
                    if (targetIndex == 0) {
                        repeat(count) { outDoc.addPage(PDPage(refBox)) }
                    }
                    for (i in 0 until src.numberOfPages) {
                        outDoc.importPage(src.getPage(i))
                        if (i + 1 == targetIndex) {
                            repeat(count) { outDoc.addPage(PDPage(refBox)) }
                        }
                    }
                    outDoc.save(outputFile)
                }
            }
            outputFile
        }
    }

    suspend fun extractImages(
        inputFile: File,
        outputDir: File
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        runCatching {
            if (!outputDir.exists()) outputDir.mkdirs()
            val extracted = mutableListOf<File>()
            PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                var imgCounter = 1
                for (pageIdx in 0 until doc.numberOfPages) {
                    val resources = doc.getPage(pageIdx).resources ?: continue
                    for (name in resources.xObjectNames) {
                        val xObj = runCatching { resources.getXObject(name) }.getOrNull()
                        if (xObj is PDImageXObject) {
                            val bmp = runCatching { xObj.image }.getOrNull() ?: continue
                            try {
                                val outFile = File(outputDir, "extracted_p${pageIdx + 1}_img$imgCounter.png")
                                FileOutputStream(outFile).use { fos ->
                                    bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
                                }
                                extracted.add(outFile)
                                imgCounter++
                            } finally {
                                bmp.recycle()
                            }
                        }
                    }
                }
            }
            extracted
        }
    }

    companion object {
        fun parseRangeGroups(spec: String, totalPages: Int): List<List<Int>> {
            val groups = mutableListOf<List<Int>>()
            val segments = spec.split(",", ";").map { it.trim() }.filter { it.isNotEmpty() }
            for (seg in segments) {
                if (seg.contains("-")) {
                    val parts = seg.split("-").mapNotNull { it.trim().toIntOrNull() }
                    if (parts.size == 2) {
                        val start = parts[0].coerceIn(1, totalPages)
                        val end = parts[1].coerceIn(1, totalPages)
                        val range = if (start <= end) (start..end).toList() else (start downTo end).toList()
                        if (range.isNotEmpty()) groups.add(range)
                    }
                } else {
                    val single = seg.toIntOrNull()
                    if (single != null && single in 1..totalPages) {
                        groups.add(listOf(single))
                    }
                }
            }
            return groups
        }
    }
}
