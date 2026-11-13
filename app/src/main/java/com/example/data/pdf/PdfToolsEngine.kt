package com.example.data.pdf

import com.example.domain.model.PageDiffItem
import com.example.domain.model.PdfDiffResult
import com.example.domain.model.PdfSearchMatch
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PdfToolsEngine {

    suspend fun extractTextByPage(pdfFile: File, password: String? = null): Result<List<Pair<Int, String>>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val doc = if (password.isNullOrEmpty()) {
                    PDDocument.load(pdfFile, MemoryUsageSetting.setupTempFileOnly())
                } else {
                    PDDocument.load(pdfFile, password, MemoryUsageSetting.setupTempFileOnly())
                }
                doc.use {
                    val stripper = PDFTextStripper()
                    val pages = mutableListOf<Pair<Int, String>>()
                    for (p in 1..it.numberOfPages) {
                        stripper.startPage = p
                        stripper.endPage = p
                        pages.add(p to stripper.getText(it).trim())
                    }
                    pages
                }
            }
        }

    suspend fun searchInPdf(pdfFile: File, query: String): Result<List<PdfSearchMatch>> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(query.isNotBlank()) { "Enter search text." }
                val lowerQ = query.trim().lowercase()
                val pages = extractTextByPage(pdfFile).getOrThrow()
                val matches = mutableListOf<PdfSearchMatch>()
                pages.forEach { (pageNum, text) ->
                    val lowerText = text.lowercase()
                    var idx = lowerText.indexOf(lowerQ)
                    if (idx != -1) {
                        var count = 0
                        val firstIdx = idx
                        while (idx != -1) {
                            count++
                            idx = lowerText.indexOf(lowerQ, idx + lowerQ.length)
                        }
                        val start = (firstIdx - 45).coerceAtLeast(0)
                        val end = (firstIdx + lowerQ.length + 75).coerceAtMost(text.length)
                        val snippet = text.substring(start, end).replace("\n", " ")
                        matches.add(
                            PdfSearchMatch(
                                pageIndex = pageNum - 1,
                                snippet = (if (start > 0) "..." else "") + snippet + (if (end < text.length) "..." else ""),
                                matchCountOnPage = count
                            )
                        )
                    }
                }
                matches
            }
        }

    suspend fun comparePdfs(leftFile: File, rightFile: File): Result<PdfDiffResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val leftPages = extractTextByPage(leftFile).getOrThrow()
                val rightPages = extractTextByPage(rightFile).getOrThrow()
                val maxPages = maxOf(leftPages.size, rightPages.size)
                val diffs = mutableListOf<PageDiffItem>()

                for (i in 0 until maxPages) {
                    val leftLines = leftPages.getOrNull(i)?.second?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
                    val rightLines = rightPages.getOrNull(i)?.second?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
                    val leftSet = leftLines.toSet()
                    val rightSet = rightLines.toSet()

                    val removed = leftLines.filter { it !in rightSet }
                    val added = rightLines.filter { it !in leftSet }
                    diffs.add(
                        PageDiffItem(
                            pageNumber = i + 1,
                            addedLines = added,
                            removedLines = removed,
                            isIdentical = added.isEmpty() && removed.isEmpty() && leftLines == rightLines
                        )
                    )
                }

                PdfDiffResult(
                    leftFileName = leftFile.name,
                    rightFileName = rightFile.name,
                    leftPageCount = leftPages.size,
                    rightPageCount = rightPages.size,
                    pageDiffs = diffs
                )
            }
        }
}
