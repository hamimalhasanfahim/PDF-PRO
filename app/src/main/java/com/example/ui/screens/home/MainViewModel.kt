package com.example.ui.screens.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.db.PdfDocumentEntity
import com.example.data.local.db.ProcessingHistoryEntity
import com.example.data.local.db.RecentDocumentEntity
import com.example.data.repository.DocumentRepository
import com.example.domain.model.AppLanguage
import com.example.domain.model.CompressionLevel
import com.example.domain.model.PageOrientation
import com.example.domain.model.PageSizeOption
import com.example.domain.model.PdfToolId
import com.example.domain.model.StorageBreakdown
import com.example.domain.model.ThemeMode
import com.example.domain.model.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(
    private val repository: DocumentRepository
) : ViewModel() {

    val userPreferences: StateFlow<UserPreferences> = repository.userPreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    private val _docSearchQuery = MutableStateFlow("")
    val docSearchQuery: StateFlow<String> = _docSearchQuery.asStateFlow()

    val activeDocuments: StateFlow<List<PdfDocumentEntity>> = _docSearchQuery
        .flatMapLatest { q -> repository.searchDocuments(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allActiveDocuments: StateFlow<List<PdfDocumentEntity>> = repository.activeDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentDocuments: StateFlow<List<RecentDocumentEntity>> = repository.recentDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyCreated: StateFlow<List<PdfDocumentEntity>> = repository.recentlyCreated
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteDocuments: StateFlow<List<PdfDocumentEntity>> = repository.favoriteDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deletedDocuments: StateFlow<List<PdfDocumentEntity>> = repository.deletedDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val processingHistory: StateFlow<List<ProcessingHistoryEntity>> = repository.processingHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val storageBreakdown: StateFlow<StorageBreakdown> = repository.storageBreakdown
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            StorageBreakdown(0L, 0, 0L, 0, 0L, 0L)
        )

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun updateDocSearchQuery(query: String) {
        _docSearchQuery.value = query
    }

    fun importPdf(uri: Uri, onImported: ((PdfDocumentEntity) -> Unit)? = null) {
        viewModelScope.launch {
            val res = repository.importPdf(uri)
            res.fold(
                onSuccess = { doc ->
                    _statusMessage.value = "Imported ${doc.title}"
                    onImported?.invoke(doc)
                },
                onFailure = { err ->
                    _statusMessage.value = err.message ?: "Failed to import PDF"
                }
            )
        }
    }

    fun markOpened(doc: PdfDocumentEntity) {
        viewModelScope.launch { repository.markDocumentOpened(doc) }
    }

    fun toggleFavorite(doc: PdfDocumentEntity) {
        viewModelScope.launch { repository.toggleFavorite(doc) }
    }

    fun renameDocument(doc: PdfDocumentEntity, newTitle: String) {
        viewModelScope.launch {
            repository.renameDocument(doc, newTitle).fold(
                onSuccess = { _statusMessage.value = "Renamed to ${it.title}" },
                onFailure = { _statusMessage.value = it.message ?: "Rename failed" }
            )
        }
    }

    fun moveToTrash(doc: PdfDocumentEntity) {
        viewModelScope.launch {
            repository.moveToTrash(doc)
            _statusMessage.value = "Moved ${doc.title} to Recently Deleted"
        }
    }

    fun restoreFromTrash(doc: PdfDocumentEntity) {
        viewModelScope.launch {
            repository.restoreFromTrash(doc)
            _statusMessage.value = "Restored ${doc.title}"
        }
    }

    fun deletePermanently(doc: PdfDocumentEntity) {
        viewModelScope.launch {
            repository.deletePermanently(doc)
            _statusMessage.value = "Permanently deleted ${doc.title}"
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repository.emptyTrash()
            _statusMessage.value = "Emptied Recently Deleted"
        }
    }

    fun exportDocumentToUri(doc: PdfDocumentEntity, targetUri: Uri) {
        viewModelScope.launch {
            repository.fileManager.exportFileToUri(File(doc.localPath), targetUri).fold(
                onSuccess = { _statusMessage.value = "Exported ${doc.fileName}" },
                onFailure = { _statusMessage.value = it.message ?: "Export failed" }
            )
        }
    }

    fun shareDocument(doc: PdfDocumentEntity) {
        val f = File(doc.localPath)
        if (!f.exists()) {
            _statusMessage.value = "File is missing or inaccessible."
            return
        }
        repository.fileManager.shareFile(f)
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { repository.setLanguage(language) }
    }

    fun setDefaultPageSize(size: PageSizeOption) {
        viewModelScope.launch { repository.setDefaultPageSize(size) }
    }

    fun setDefaultOrientation(orientation: PageOrientation) {
        viewModelScope.launch { repository.setDefaultOrientation(orientation) }
    }

    fun setDefaultCompression(level: CompressionLevel) {
        viewModelScope.launch { repository.setDefaultCompression(level) }
    }

    fun setDefaultSaveFolderUri(uri: String) {
        viewModelScope.launch { repository.setDefaultSaveFolderUri(uri) }
    }

    fun setDefaultFontSize(size: Float) {
        viewModelScope.launch { repository.setDefaultFontSize(size) }
    }

    fun completeOnboarding() {
        viewModelScope.launch { repository.setOnboardingCompleted(true) }
    }

    fun toggleFavoriteTool(toolId: PdfToolId) {
        viewModelScope.launch { repository.toggleFavoriteTool(toolId.id) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _statusMessage.value = "Processing history cleared"
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            repository.clearCache()
            _statusMessage.value = "Temporary files cleaned"
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(container.repository) as T
        }
    }
}
