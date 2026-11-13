package com.example.data.repository

import android.net.Uri
import com.example.data.local.db.FavoriteDocumentDao
import com.example.data.local.db.FavoriteDocumentEntity
import com.example.data.local.db.PdfDocumentDao
import com.example.data.local.db.PdfDocumentEntity
import com.example.data.local.db.ProcessingHistoryDao
import com.example.data.local.db.ProcessingHistoryEntity
import com.example.data.local.db.RecentDocumentDao
import com.example.data.local.db.RecentDocumentEntity
import com.example.data.local.db.UserSettingsDao
import com.example.data.local.db.UserSettingsEntity
import com.example.data.pdf.FileStorageManager
import com.example.data.preferences.SettingsDataStore
import com.example.domain.model.AppLanguage
import com.example.domain.model.CompressionLevel
import com.example.domain.model.PageOrientation
import com.example.domain.model.PageSizeOption
import com.example.domain.model.StorageBreakdown
import com.example.domain.model.ThemeMode
import com.example.domain.model.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

class DocumentRepository(
    private val documentDao: PdfDocumentDao,
    private val recentDao: RecentDocumentDao,
    private val favoriteDao: FavoriteDocumentDao,
    private val historyDao: ProcessingHistoryDao,
    private val settingsDao: UserSettingsDao,
    private val settingsDataStore: SettingsDataStore,
    val fileManager: FileStorageManager
) {
    val activeDocuments: Flow<List<PdfDocumentEntity>> = documentDao.observeActiveDocuments()
    val recentDocuments: Flow<List<RecentDocumentEntity>> = recentDao.observeRecent()
    val recentlyCreated: Flow<List<PdfDocumentEntity>> = documentDao.observeRecentlyCreated()
    val favoriteDocuments: Flow<List<PdfDocumentEntity>> = documentDao.observeFavorites()
    val deletedDocuments: Flow<List<PdfDocumentEntity>> = documentDao.observeDeletedDocuments()
    val processingHistory: Flow<List<ProcessingHistoryEntity>> = historyDao.observeHistory()
    val userPreferences: Flow<UserPreferences> = settingsDataStore.preferencesFlow

    val storageBreakdown: Flow<StorageBreakdown> = combine(
        activeDocuments,
        deletedDocuments
    ) { active, deleted ->
        StorageBreakdown(
            totalVaultBytes = active.sumOf { it.sizeBytes },
            documentCount = active.size,
            deletedBytes = deleted.sumOf { it.sizeBytes },
            deletedCount = deleted.size,
            cacheBytes = fileManager.getDirectorySizeBytes(fileManager.tempDir),
            deviceFreeBytes = fileManager.getDeviceAvailableBytes()
        )
    }

    fun searchDocuments(query: String): Flow<List<PdfDocumentEntity>> =
        if (query.isBlank()) activeDocuments else documentDao.searchActiveDocuments(query.trim())

    suspend fun getDocumentById(id: Long): PdfDocumentEntity? = documentDao.getById(id)

    suspend fun registerGeneratedPdf(
        file: File,
        title: String,
        originTag: String,
        actionType: String,
        inputName: String = "",
        originalSizeBytes: Long = 0L,
        details: String = ""
    ): PdfDocumentEntity = withContext(Dispatchers.IO) {
        val (pageCount, isEncrypted) = fileManager.inspectPdfQuick(file)
        val now = System.currentTimeMillis()
        val entity = PdfDocumentEntity(
            title = title,
            fileName = file.name,
            localPath = file.absolutePath,
            pageCount = pageCount,
            sizeBytes = file.length(),
            isEncrypted = isEncrypted,
            originTag = originTag,
            createdAt = now,
            updatedAt = now
        )
        val newId = documentDao.insert(entity)
        val saved = entity.copy(id = newId)

        recentDao.upsert(
            RecentDocumentEntity(
                documentId = newId,
                title = title,
                localPath = file.absolutePath,
                pageCount = pageCount,
                sizeBytes = file.length(),
                lastOpenedAt = now
            )
        )

        historyDao.insert(
            ProcessingHistoryEntity(
                actionType = actionType,
                inputName = inputName.ifBlank { title },
                outputName = file.name,
                outputPath = file.absolutePath,
                details = details,
                originalSizeBytes = originalSizeBytes,
                outputSizeBytes = file.length(),
                status = "SUCCESS",
                timestamp = now
            )
        )

        saved
    }

    suspend fun importPdf(uri: Uri): Result<PdfDocumentEntity> {
        val imported = fileManager.importPdfFromUri(uri)
        return imported.map { info ->
            val now = System.currentTimeMillis()
            val entity = PdfDocumentEntity(
                title = info.title,
                fileName = info.file.name,
                localPath = info.file.absolutePath,
                sourceUri = uri.toString(),
                pageCount = info.pageCount,
                sizeBytes = info.sizeBytes,
                isEncrypted = info.isEncrypted,
                originTag = "imported",
                createdAt = now,
                updatedAt = now
            )
            val id = documentDao.insert(entity)
            recentDao.upsert(
                RecentDocumentEntity(
                    documentId = id,
                    title = info.title,
                    localPath = info.file.absolutePath,
                    pageCount = info.pageCount,
                    sizeBytes = info.sizeBytes,
                    lastOpenedAt = now
                )
            )
            historyDao.insert(
                ProcessingHistoryEntity(
                    actionType = "Import PDF",
                    inputName = info.file.name,
                    outputName = info.file.name,
                    outputPath = info.file.absolutePath,
                    details = "${info.pageCount} pages",
                    outputSizeBytes = info.sizeBytes,
                    status = "SUCCESS",
                    timestamp = now
                )
            )
            entity.copy(id = id)
        }
    }

    suspend fun markDocumentOpened(doc: PdfDocumentEntity) {
        recentDao.upsert(
            RecentDocumentEntity(
                documentId = doc.id,
                title = doc.title,
                localPath = doc.localPath,
                pageCount = doc.pageCount,
                sizeBytes = doc.sizeBytes,
                lastOpenedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun toggleFavorite(doc: PdfDocumentEntity) {
        val next = !doc.isFavorite
        documentDao.setFavorite(doc.id, next)
        if (next) {
            favoriteDao.insert(
                FavoriteDocumentEntity(
                    documentId = doc.id,
                    title = doc.title,
                    localPath = doc.localPath
                )
            )
        } else {
            favoriteDao.remove(doc.id)
        }
    }

    suspend fun renameDocument(doc: PdfDocumentEntity, newTitle: String): Result<PdfDocumentEntity> {
        val file = File(doc.localPath)
        val renamed = fileManager.renameVaultFile(file, newTitle)
        return renamed.map { newFile ->
            val cleanTitle = newTitle.trim().ifBlank { doc.title }
            documentDao.updateFileDetails(
                id = doc.id,
                newTitle = cleanTitle,
                newFileName = newFile.name,
                newPath = newFile.absolutePath
            )
            recentDao.upsert(
                RecentDocumentEntity(
                    documentId = doc.id,
                    title = cleanTitle,
                    localPath = newFile.absolutePath,
                    pageCount = doc.pageCount,
                    sizeBytes = newFile.length()
                )
            )
            doc.copy(title = cleanTitle, fileName = newFile.name, localPath = newFile.absolutePath)
        }
    }

    suspend fun moveToTrash(doc: PdfDocumentEntity) {
        documentDao.softDelete(doc.id)
        recentDao.remove(doc.id)
    }

    suspend fun restoreFromTrash(doc: PdfDocumentEntity) {
        documentDao.restore(doc.id)
    }

    suspend fun deletePermanently(doc: PdfDocumentEntity) = withContext(Dispatchers.IO) {
        runCatching { File(doc.localPath).delete() }
        documentDao.deletePermanently(doc.id)
        favoriteDao.remove(doc.id)
        recentDao.remove(doc.id)
    }

    suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        val deleted = deletedDocuments.first()
        deleted.forEach { runCatching { File(it.localPath).delete() } }
        documentDao.purgeDeleted()
    }

    suspend fun logHistoryFailure(actionType: String, inputName: String, message: String) {
        historyDao.insert(
            ProcessingHistoryEntity(
                actionType = actionType,
                inputName = inputName,
                outputName = "-",
                details = message,
                status = "FAILED",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun logHistorySuccess(
        actionType: String,
        inputName: String,
        outputName: String,
        outputPath: String? = null,
        details: String = "",
        originalSizeBytes: Long = 0L,
        outputSizeBytes: Long = 0L
    ) {
        historyDao.insert(
            ProcessingHistoryEntity(
                actionType = actionType,
                inputName = inputName,
                outputName = outputName,
                outputPath = outputPath,
                details = details,
                originalSizeBytes = originalSizeBytes,
                outputSizeBytes = outputSizeBytes,
                status = "SUCCESS",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearHistory() = historyDao.clearHistory()

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        fileManager.cleanupTempFiles()
    }

    // Settings updates (synced across DataStore and UserSettings Room entity)
    suspend fun setThemeMode(mode: ThemeMode) {
        settingsDataStore.setThemeMode(mode)
        syncRoomSettings()
    }

    suspend fun setLanguage(language: AppLanguage) {
        settingsDataStore.setLanguage(language)
        syncRoomSettings()
    }

    suspend fun setDefaultPageSize(size: PageSizeOption) {
        settingsDataStore.setDefaultPageSize(size)
        syncRoomSettings()
    }

    suspend fun setDefaultOrientation(orientation: PageOrientation) {
        settingsDataStore.setDefaultOrientation(orientation)
        syncRoomSettings()
    }

    suspend fun setDefaultCompression(level: CompressionLevel) {
        settingsDataStore.setDefaultCompression(level)
        syncRoomSettings()
    }

    suspend fun setDefaultSaveFolderUri(uri: String) {
        settingsDataStore.setDefaultSaveFolderUri(uri)
        syncRoomSettings()
    }

    suspend fun setDefaultFontSize(size: Float) {
        settingsDataStore.setDefaultFontSize(size)
        syncRoomSettings()
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        settingsDataStore.setOnboardingCompleted(completed)
        syncRoomSettings()
    }

    suspend fun toggleFavoriteTool(toolId: String) {
        settingsDataStore.toggleFavoriteTool(toolId)
        syncRoomSettings()
    }

    private suspend fun syncRoomSettings() {
        val prefs = settingsDataStore.preferencesFlow.first()
        settingsDao.upsert(
            UserSettingsEntity(
                id = 1,
                themeMode = prefs.themeMode.key,
                languageCode = prefs.language.code,
                defaultPageSize = prefs.defaultPageSize.name,
                defaultOrientation = prefs.defaultOrientation.name,
                defaultCompression = prefs.defaultCompression.name,
                defaultSaveFolderUri = prefs.defaultSaveFolderUri,
                defaultFontSize = prefs.defaultFontSize,
                defaultMarginPt = prefs.defaultMarginPt,
                onboardingCompleted = prefs.onboardingCompleted,
                favoriteToolIdsCsv = prefs.favoriteToolIds.joinToString(","),
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}
