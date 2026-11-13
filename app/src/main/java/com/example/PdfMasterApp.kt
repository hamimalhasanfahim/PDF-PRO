package com.example

import android.app.Application
import com.example.data.local.db.PdfMasterDatabase
import com.example.data.pdf.DocumentScannerProcessor
import com.example.data.pdf.FileStorageManager
import com.example.data.pdf.OcrEngine
import com.example.data.pdf.PdfCompressionEngine
import com.example.data.pdf.PdfConverterEngine
import com.example.data.pdf.PdfCreationEngine
import com.example.data.pdf.PdfEditorEngine
import com.example.data.pdf.PdfOrganizerEngine
import com.example.data.pdf.PdfRendererHelper
import com.example.data.pdf.PdfSecurityEngine
import com.example.data.pdf.PdfToolsEngine
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.DocumentRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class PdfMasterApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    private val context = application.applicationContext

    val database: PdfMasterDatabase by lazy { PdfMasterDatabase.getInstance(context) }
    val settingsDataStore: SettingsDataStore by lazy { SettingsDataStore(context) }
    val fileStorageManager: FileStorageManager by lazy { FileStorageManager(context) }

    val rendererHelper: PdfRendererHelper by lazy { PdfRendererHelper() }
    val creationEngine: PdfCreationEngine by lazy { PdfCreationEngine(context) }
    val editorEngine: PdfEditorEngine by lazy { PdfEditorEngine(context) }
    val organizerEngine: PdfOrganizerEngine by lazy { PdfOrganizerEngine() }
    val compressionEngine: PdfCompressionEngine by lazy { PdfCompressionEngine() }
    val securityEngine: PdfSecurityEngine by lazy { PdfSecurityEngine(context) }
    val converterEngine: PdfConverterEngine by lazy {
        PdfConverterEngine(context, creationEngine, rendererHelper)
    }
    val toolsEngine: PdfToolsEngine by lazy { PdfToolsEngine() }
    val scannerProcessor: DocumentScannerProcessor by lazy { DocumentScannerProcessor() }
    val ocrEngine: OcrEngine by lazy { OcrEngine(rendererHelper) }

    val repository: DocumentRepository by lazy {
        DocumentRepository(
            documentDao = database.pdfDocumentDao(),
            recentDao = database.recentDocumentDao(),
            favoriteDao = database.favoriteDocumentDao(),
            historyDao = database.processingHistoryDao(),
            settingsDao = database.userSettingsDao(),
            settingsDataStore = settingsDataStore,
            fileManager = fileStorageManager
        )
    }
}
