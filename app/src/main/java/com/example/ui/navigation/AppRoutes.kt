package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object OnboardingRoute

@Serializable
data object HomeRoute

@Serializable
data class DocumentsRoute(val initialTab: Int = 0)

@Serializable
data object ToolsHubRoute

@Serializable
data class CreatePdfRoute(val initialMode: String = "text")

@Serializable
data class PdfViewerEditorRoute(
    val docId: Long = -1L,
    val startInEditMode: Boolean = false
)

@Serializable
data class OrganizerRoute(
    val initialTool: String = "merge",
    val docId: Long = -1L
)

@Serializable
data class ConverterRoute(
    val initialMode: String = "pdf_to_jpg",
    val docId: Long = -1L
)

@Serializable
data class CompressorRoute(val docId: Long = -1L)

@Serializable
data object ScannerRoute

@Serializable
data class OcrRoute(val docId: Long = -1L)

@Serializable
data class SecurityRoute(
    val initialTab: String = "protect",
    val docId: Long = -1L
)

@Serializable
data class UtilitiesRoute(
    val initialTab: String = "compare",
    val docId: Long = -1L
)

@Serializable
data object SettingsRoute
