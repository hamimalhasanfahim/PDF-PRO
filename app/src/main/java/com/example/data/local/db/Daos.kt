package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfDocumentDao {
    @Query("SELECT * FROM pdf_documents WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    fun observeActiveDocuments(): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents WHERE isDeleted = 0 AND (title LIKE '%' || :query || '%' OR fileName LIKE '%' || :query || '%') ORDER BY updatedAt DESC")
    fun searchActiveDocuments(query: String): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents WHERE isDeleted = 0 AND originTag IN ('created', 'scanned', 'converted') ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecentlyCreated(limit: Int = 10): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents WHERE isDeleted = 0 AND isFavorite = 1 ORDER BY updatedAt DESC")
    fun observeFavorites(): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun observeDeletedDocuments(): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PdfDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(doc: PdfDocumentEntity): Long

    @Update
    suspend fun update(doc: PdfDocumentEntity)

    @Query("UPDATE pdf_documents SET isFavorite = :favorite, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE pdf_documents SET isDeleted = 1, deletedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE pdf_documents SET isDeleted = 0, deletedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun restore(id: Long, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM pdf_documents WHERE id = :id")
    suspend fun deletePermanently(id: Long)

    @Query("DELETE FROM pdf_documents WHERE isDeleted = 1")
    suspend fun purgeDeleted()

    @Query("UPDATE pdf_documents SET title = :newTitle, fileName = :newFileName, localPath = :newPath, updatedAt = :now WHERE id = :id")
    suspend fun updateFileDetails(
        id: Long,
        newTitle: String,
        newFileName: String,
        newPath: String,
        now: Long = System.currentTimeMillis()
    )
}

@Dao
interface RecentDocumentDao {
    @Query("SELECT * FROM recent_documents ORDER BY lastOpenedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 15): Flow<List<RecentDocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: RecentDocumentEntity)

    @Query("DELETE FROM recent_documents WHERE documentId = :documentId")
    suspend fun remove(documentId: Long)

    @Query("DELETE FROM recent_documents")
    suspend fun clearAll()
}

@Dao
interface FavoriteDocumentDao {
    @Query("SELECT * FROM favorite_documents ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteDocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: FavoriteDocumentEntity)

    @Query("DELETE FROM favorite_documents WHERE documentId = :documentId")
    suspend fun remove(documentId: Long)
}

@Dao
interface ProcessingHistoryDao {
    @Query("SELECT * FROM processing_history ORDER BY timestamp DESC")
    fun observeHistory(): Flow<List<ProcessingHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ProcessingHistoryEntity): Long

    @Query("DELETE FROM processing_history")
    suspend fun clearHistory()
}

@Dao
interface UserSettingsDao {
    @Query("SELECT * FROM user_settings WHERE id = 1 LIMIT 1")
    fun observeSettings(): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): UserSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: UserSettingsEntity)
}
