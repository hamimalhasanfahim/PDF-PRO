package com.example.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PdfDocumentEntity::class,
        RecentDocumentEntity::class,
        FavoriteDocumentEntity::class,
        ProcessingHistoryEntity::class,
        UserSettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PdfMasterDatabase : RoomDatabase() {
    abstract fun pdfDocumentDao(): PdfDocumentDao
    abstract fun recentDocumentDao(): RecentDocumentDao
    abstract fun favoriteDocumentDao(): FavoriteDocumentDao
    abstract fun processingHistoryDao(): ProcessingHistoryDao
    abstract fun userSettingsDao(): UserSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: PdfMasterDatabase? = null

        fun getInstance(context: Context): PdfMasterDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PdfMasterDatabase::class.java,
                    "pdf_master_pro.db"
                ).fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
