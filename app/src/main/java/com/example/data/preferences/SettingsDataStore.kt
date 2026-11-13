package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.AppLanguage
import com.example.domain.model.CompressionLevel
import com.example.domain.model.PageOrientation
import com.example.domain.model.PageSizeOption
import com.example.domain.model.PdfToolId
import com.example.domain.model.ThemeMode
import com.example.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pdf_master_preferences"
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val DEFAULT_PAGE_SIZE = stringPreferencesKey("default_page_size")
        val DEFAULT_ORIENTATION = stringPreferencesKey("default_orientation")
        val DEFAULT_COMPRESSION = stringPreferencesKey("default_compression")
        val DEFAULT_SAVE_FOLDER_URI = stringPreferencesKey("default_save_folder_uri")
        val DEFAULT_FONT_SIZE = floatPreferencesKey("default_font_size")
        val DEFAULT_MARGIN_PT = floatPreferencesKey("default_margin_pt")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val FAVORITE_TOOLS = stringPreferencesKey("favorite_tools_csv")
    }

    private val defaultFavoritesCsv = listOf(
        PdfToolId.CREATE_TEXT.id,
        PdfToolId.SCAN_DOCUMENT.id,
        PdfToolId.MERGE_PDF.id,
        PdfToolId.COMPRESS_PDF.id
    ).joinToString(",")

    val preferencesFlow: Flow<UserPreferences> = context.settingsDataStore.data.map { prefs ->
        val favCsv = prefs[Keys.FAVORITE_TOOLS] ?: defaultFavoritesCsv
        UserPreferences(
            themeMode = ThemeMode.fromKey(prefs[Keys.THEME_MODE]),
            language = AppLanguage.fromCode(prefs[Keys.LANGUAGE_CODE]),
            defaultPageSize = PageSizeOption.fromLabel(prefs[Keys.DEFAULT_PAGE_SIZE]),
            defaultOrientation = PageOrientation.fromName(prefs[Keys.DEFAULT_ORIENTATION]),
            defaultCompression = CompressionLevel.fromName(prefs[Keys.DEFAULT_COMPRESSION]),
            defaultSaveFolderUri = prefs[Keys.DEFAULT_SAVE_FOLDER_URI] ?: "",
            defaultFontSize = prefs[Keys.DEFAULT_FONT_SIZE] ?: 14f,
            defaultMarginPt = prefs[Keys.DEFAULT_MARGIN_PT] ?: 48f,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            favoriteToolIds = favCsv.split(",").filter { it.isNotBlank() }.toSet()
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.key }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.settingsDataStore.edit { it[Keys.LANGUAGE_CODE] = language.code }
    }

    suspend fun setDefaultPageSize(size: PageSizeOption) {
        context.settingsDataStore.edit { it[Keys.DEFAULT_PAGE_SIZE] = size.name }
    }

    suspend fun setDefaultOrientation(orientation: PageOrientation) {
        context.settingsDataStore.edit { it[Keys.DEFAULT_ORIENTATION] = orientation.name }
    }

    suspend fun setDefaultCompression(level: CompressionLevel) {
        context.settingsDataStore.edit { it[Keys.DEFAULT_COMPRESSION] = level.name }
    }

    suspend fun setDefaultSaveFolderUri(uri: String) {
        context.settingsDataStore.edit { it[Keys.DEFAULT_SAVE_FOLDER_URI] = uri }
    }

    suspend fun setDefaultFontSize(size: Float) {
        context.settingsDataStore.edit { it[Keys.DEFAULT_FONT_SIZE] = size }
    }

    suspend fun setDefaultMarginPt(margin: Float) {
        context.settingsDataStore.edit { it[Keys.DEFAULT_MARGIN_PT] = margin }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.settingsDataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun toggleFavoriteTool(toolId: String) {
        context.settingsDataStore.edit { prefs ->
            val current = (prefs[Keys.FAVORITE_TOOLS] ?: defaultFavoritesCsv)
                .split(",")
                .filter { it.isNotBlank() }
                .toMutableSet()
            if (current.contains(toolId)) current.remove(toolId) else current.add(toolId)
            prefs[Keys.FAVORITE_TOOLS] = current.joinToString(",")
        }
    }
}
