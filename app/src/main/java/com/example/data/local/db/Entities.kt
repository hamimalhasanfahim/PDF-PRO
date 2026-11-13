package com.example.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_documents")
data class PdfDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val fileName: String,
    val localPath: String,
    val sourceUri: String? = null,
    val pageCount: Int = 1,
    val sizeBytes: Long = 0L,
    val isEncrypted: Boolean = false,
    val isFavorite: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val originTag: String = "created", // created, imported, scanned, edited, compressed, secured, converted
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recent_documents")
data class RecentDocumentEntity(
    @PrimaryKey val documentId: Long,
    val title: String,
    val localPath: String,
    val pageCount: Int,
    val sizeBytes: Long,
    val lastOpenedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorite_documents")
data class FavoriteDocumentEntity(
    @PrimaryKey val documentId: Long,
    val title: String,
    val localPath: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "processing_history")
data class ProcessingHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val actionType: String,
    val inputName: String,
    val outputName: String,
    val outputPath: String? = null,
    val details: String = "",
    val originalSizeBytes: Long = 0L,
    val outputSizeBytes: Long = 0L,
    val status: String = "SUCCESS", // SUCCESS, FAILED
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val themeMode: String = "system",
    val languageCode: String = "en",
    val defaultPageSize: String = "A4",
    val defaultOrientation: String = "PORTRAIT",
    val defaultCompression: String = "MEDIUM",
    val defaultSaveFolderUri: String = "",
    val defaultFontSize: Float = 14f,
    val defaultMarginPt: Float = 48f,
    val onboardingCompleted: Boolean = false,
    val favoriteToolIdsCsv: String = "create_text,scan_document,merge_pdf,compress_pdf",
    val updatedAt: Long = System.currentTimeMillis()
)
