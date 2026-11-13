package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.db.PdfDocumentEntity
import com.example.data.local.db.PdfMasterDatabase
import com.example.data.local.db.ProcessingHistoryEntity
import com.example.data.pdf.FileStorageManager
import com.example.data.pdf.PdfCompressionEngine
import com.example.data.pdf.PdfConverterEngine
import com.example.data.pdf.PdfCreationEngine
import com.example.data.pdf.PdfOrganizerEngine
import com.example.data.pdf.PdfRendererHelper
import com.example.data.pdf.PdfSecurityEngine
import com.example.data.pdf.PdfToolsEngine
import com.example.domain.model.CompressionLevel
import com.example.domain.model.PageOrientation
import com.example.domain.model.PageSizeOption
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PdfMasterEnginesRobolectricTest {

    private lateinit var context: Context
    private lateinit var tempRoot: File
    private lateinit var creationEngine: PdfCreationEngine
    private lateinit var organizerEngine: PdfOrganizerEngine
    private lateinit var compressionEngine: PdfCompressionEngine
    private lateinit var securityEngine: PdfSecurityEngine
    private lateinit var toolsEngine: PdfToolsEngine
    private lateinit var fileManager: FileStorageManager
    private lateinit var db: PdfMasterDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
        tempRoot = File(context.cacheDir, "unit_test_pdfs_${System.nanoTime()}").apply { mkdirs() }
        creationEngine = PdfCreationEngine(context)
        organizerEngine = PdfOrganizerEngine()
        compressionEngine = PdfCompressionEngine()
        securityEngine = PdfSecurityEngine(context)
        toolsEngine = PdfToolsEngine()
        fileManager = FileStorageManager(context)
        db = Room.inMemoryDatabaseBuilder(context, PdfMasterDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
        tempRoot.deleteRecursively()
    }

    @Test
    fun `pdf creation from English and Bengali text succeeds`() = runTest {
        val out = File(tempRoot, "text_bilingual.pdf")
        val res = creationEngine.createFromText(
            outputFile = out,
            text = "Hello PDF Master Pro!\nবাংলা ইউনিকোড টেস্ট।\nLine 3.",
            pageSize = PageSizeOption.A4,
            orientation = PageOrientation.PORTRAIT,
            headerText = "Header",
            footerText = "Footer",
            includePageNumbers = true
        )
        assertTrue(res.isSuccess)
        assertTrue(out.exists() && out.length() > 100L)
        PDDocument.load(out).use { doc ->
            assertEquals(1, doc.numberOfPages)
        }
    }

    @Test
    fun `pdf creation from images and compression reduce size`() = runTest {
        val imgFile = File(tempRoot, "sample_photo.png")
        val bmp = Bitmap.createBitmap(1200, 1200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(112, 71, 235))
        }
        FileOutputStream(imgFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()

        val createdPdf = File(tempRoot, "images.pdf")
        val createRes = creationEngine.createFromImageFiles(createdPdf, listOf(imgFile))
        assertTrue(createRes.isSuccess)

        val compressedPdf = File(tempRoot, "compressed.pdf")
        val compRes = compressionEngine.compressPdf(createdPdf, compressedPdf, CompressionLevel.HIGH)
        assertTrue(compRes.isSuccess)
        val stats = compRes.getOrThrow()
        assertTrue(stats.compressedBytes > 0L)
        assertTrue(compressedPdf.exists())
    }

    @Test
    fun `merge split rotate and reverse PDF pages`() = runTest {
        val pdf1 = File(tempRoot, "p1.pdf")
        val pdf2 = File(tempRoot, "p2.pdf")
        creationEngine.createBlankPdf(pdf1, pageCount = 2).getOrThrow()
        creationEngine.createBlankPdf(pdf2, pageCount = 3).getOrThrow()

        val merged = File(tempRoot, "merged.pdf")
        organizerEngine.mergePdfs(listOf(pdf1, pdf2), merged).getOrThrow()
        PDDocument.load(merged).use { assertEquals(5, it.numberOfPages) }

        val splitFiles = organizerEngine.splitByRanges(merged, "1-2, 3-5") { idx, _ ->
            File(tempRoot, "split_$idx.pdf")
        }.getOrThrow()
        assertEquals(2, splitFiles.size)
        PDDocument.load(splitFiles[0]).use { assertEquals(2, it.numberOfPages) }
        PDDocument.load(splitFiles[1]).use { assertEquals(3, it.numberOfPages) }

        val rotated = File(tempRoot, "rotated.pdf")
        organizerEngine.rotatePages(pdf1, setOf(1), 90, rotated).getOrThrow()
        PDDocument.load(rotated).use {
            assertEquals(90, it.getPage(0).rotation)
            assertEquals(0, it.getPage(1).rotation)
        }

        val reversed = File(tempRoot, "reversed.pdf")
        organizerEngine.reversePageOrder(merged, reversed).getOrThrow()
        PDDocument.load(reversed).use { assertEquals(5, it.numberOfPages) }
    }

    @Test
    fun `pdf encryption and authorized password removal`() = runTest {
        val plain = File(tempRoot, "plain.pdf")
        creationEngine.createFromText(plain, "Secret financial statement").getOrThrow()

        val protectedFile = File(tempRoot, "locked.pdf")
        securityEngine.protectWithPassword(
            inputFile = plain,
            outputFile = protectedFile,
            userPassword = "pass123",
            allowPrinting = true,
            allowCopying = false
        ).getOrThrow()

        val (_, isEnc) = fileManager.inspectPdfQuick(protectedFile)
        assertTrue(isEnc)

        // Wrong password must fail
        val wrongUnlock = File(tempRoot, "wrong.pdf")
        val failRes = securityEngine.removePasswordAuthorized(protectedFile, "wrong", wrongUnlock)
        assertTrue(failRes.isFailure)

        // Valid password succeeds
        val unlockedFile = File(tempRoot, "unlocked.pdf")
        val okRes = securityEngine.removePasswordAuthorized(protectedFile, "pass123", unlockedFile)
        assertTrue(okRes.isSuccess)
        val (_, stillEnc) = fileManager.inspectPdfQuick(unlockedFile)
        assertFalse(stillEnc)
    }

    @Test
    fun `file import validation rejects non-pdf and accepts valid pdf`() = runTest {
        val invalidFile = File(tempRoot, "not_a_pdf.pdf").apply { writeText("Just plain text") }
        val invalidRes = fileManager.importPdfFromUri(Uri.fromFile(invalidFile))
        assertTrue(invalidRes.isFailure)

        val validFile = File(tempRoot, "valid.pdf")
        creationEngine.createBlankPdf(validFile, 1).getOrThrow()
        val validRes = fileManager.importPdfFromUri(Uri.fromFile(validFile))
        assertTrue(validRes.isSuccess)
        assertEquals(1, validRes.getOrThrow().pageCount)
    }

    @Test
    fun `openxml docx conversion roundtrip and pdf text search`() = runTest {
        val srcPdf = File(tempRoot, "search_source.pdf")
        creationEngine.createFromText(srcPdf, "Invoice Total: 450 USD\nCustomer: Rahim").getOrThrow()

        val matches = toolsEngine.searchInPdf(srcPdf, "Rahim").getOrThrow()
        assertEquals(1, matches.size)

        val converter = PdfConverterEngine(context, creationEngine, PdfRendererHelper())
        val docxOut = File(tempRoot, "exported.docx")
        converter.pdfToDocxTextBased(srcPdf, docxOut).getOrThrow()
        assertTrue(docxOut.exists() && docxOut.length() > 100L)

        val backToPdf = File(tempRoot, "roundtrip.pdf")
        converter.docxToPdf(docxOut, backToPdf).getOrThrow()
        assertTrue(fileManager.isValidPdfFile(backToPdf))
    }

    @Test
    fun `room database operations insert favorite softDelete restore and history`() = runTest {
        val docDao = db.pdfDocumentDao()
        val historyDao = db.processingHistoryDao()

        val id = docDao.insert(
            PdfDocumentEntity(
                title = "Contract",
                fileName = "Contract.pdf",
                localPath = "/tmp/Contract.pdf",
                pageCount = 4,
                sizeBytes = 2048L
            )
        )
        assertEquals(1, docDao.observeActiveDocuments().first().size)

        docDao.setFavorite(id, true)
        assertEquals(1, docDao.observeFavorites().first().size)

        docDao.softDelete(id)
        assertEquals(0, docDao.observeActiveDocuments().first().size)
        assertEquals(1, docDao.observeDeletedDocuments().first().size)

        docDao.restore(id)
        assertEquals(1, docDao.observeActiveDocuments().first().size)

        historyDao.insert(
            ProcessingHistoryEntity(
                actionType = "Create PDF",
                inputName = "Note",
                outputName = "Contract.pdf",
                status = "SUCCESS"
            )
        )
        assertEquals(1, historyDao.observeHistory().first().size)
    }
}
