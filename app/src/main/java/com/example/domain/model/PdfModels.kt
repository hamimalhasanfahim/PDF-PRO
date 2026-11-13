package com.example.domain.model

import java.io.File

enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

enum class AppLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    BENGALI("bn", "বাংলা (Bengali)");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

enum class PageSizeOption(
    val label: String,
    val widthPt: Float,
    val heightPt: Float
) {
    A3("A3", 841.89f, 1190.55f),
    A4("A4", 595.28f, 841.89f),
    A5("A5", 419.53f, 595.28f),
    LETTER("Letter", 612f, 792f),
    LEGAL("Legal", 612f, 1008f),
    CUSTOM("Custom", 595.28f, 841.89f);

    companion object {
        fun fromLabel(label: String?): PageSizeOption =
            entries.firstOrNull { it.name == label || it.label == label } ?: A4
    }
}

enum class PageOrientation(val label: String) {
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape");

    companion object {
        fun fromName(name: String?): PageOrientation =
            entries.firstOrNull { it.name == name } ?: PORTRAIT
    }
}

enum class CompressionLevel(
    val label: String,
    val jpegQuality: Int,
    val maxImageDimensionPx: Int
) {
    HIGH("High Compression", 35, 1000),
    MEDIUM("Medium Compression", 60, 1500),
    LOW("Low Compression", 82, 2200),
    CUSTOM("Custom", 65, 1600);

    companion object {
        fun fromName(name: String?): CompressionLevel =
            entries.firstOrNull { it.name == name } ?: MEDIUM
    }
}

enum class ScanFilter(val label: String) {
    ORIGINAL("Original"),
    COLOR_ENHANCED("Color Boost"),
    GRAYSCALE("Grayscale"),
    BLACK_AND_WHITE("B&W Document")
}

enum class ToolCategory {
    CREATE,
    EDIT,
    ORGANIZE,
    CONVERT,
    SCAN_OCR,
    SECURITY,
    UTILITIES
}

enum class PdfToolId(val id: String, val category: ToolCategory) {
    CREATE_TEXT("create_text", ToolCategory.CREATE),
    CREATE_IMAGES("create_images", ToolCategory.CREATE),
    CREATE_BLANK("create_blank", ToolCategory.CREATE),
    SCAN_DOCUMENT("scan_document", ToolCategory.SCAN_OCR),
    OCR_EXTRACT("ocr_extract", ToolCategory.SCAN_OCR),
    EDIT_PDF("edit_pdf", ToolCategory.EDIT),
    MERGE_PDF("merge_pdf", ToolCategory.ORGANIZE),
    SPLIT_PDF("split_pdf", ToolCategory.ORGANIZE),
    ORGANIZE_PAGES("organize_pages", ToolCategory.ORGANIZE),
    EXTRACT_IMAGES("extract_images", ToolCategory.ORGANIZE),
    COMPRESS_PDF("compress_pdf", ToolCategory.CONVERT),
    PDF_TO_IMAGES("pdf_to_images", ToolCategory.CONVERT),
    OFFICE_CONVERT("office_convert", ToolCategory.CONVERT),
    PROTECT_PDF("protect_pdf", ToolCategory.SECURITY),
    UNLOCK_PDF("unlock_pdf", ToolCategory.SECURITY),
    WATERMARK_PDF("watermark_pdf", ToolCategory.SECURITY),
    SIGN_PDF("sign_pdf", ToolCategory.SECURITY),
    METADATA_BOOKMARKS("metadata_bookmarks", ToolCategory.UTILITIES),
    PAGE_NUMBERS("page_numbers", ToolCategory.UTILITIES),
    COMPARE_PDF("compare_pdf", ToolCategory.UTILITIES),
    SEARCH_PDF("search_pdf", ToolCategory.UTILITIES)
}

enum class EditorTool {
    VIEW,
    TEXT,
    IMAGE,
    SHAPE,
    HIGHLIGHT,
    UNDERLINE,
    STRIKETHROUGH,
    DRAW,
    COMMENT,
    SIGNATURE,
    DATE_STAMP,
    WATERMARK
}

enum class ShapeKind {
    RECTANGLE,
    CIRCLE,
    LINE,
    ARROW
}

sealed class PdfOverlayAnnotation {
    abstract val id: String
    abstract val pageIndex: Int

    data class TextOverlay(
        override val id: String,
        override val pageIndex: Int,
        val normX: Float,
        val normY: Float,
        val text: String,
        val fontSizePt: Float = 16f,
        val colorArgb: Int = 0xFF141726.toInt()
    ) : PdfOverlayAnnotation()

    data class ImageOverlay(
        override val id: String,
        override val pageIndex: Int,
        val normX: Float,
        val normY: Float,
        val normWidth: Float,
        val normHeight: Float,
        val imagePath: String
    ) : PdfOverlayAnnotation()

    data class ShapeOverlay(
        override val id: String,
        override val pageIndex: Int,
        val kind: ShapeKind,
        val normStartX: Float,
        val normStartY: Float,
        val normEndX: Float,
        val normEndY: Float,
        val colorArgb: Int = 0xFF7047EB.toInt(),
        val strokeWidthPt: Float = 2.5f
    ) : PdfOverlayAnnotation()

    data class MarkupOverlay(
        override val id: String,
        override val pageIndex: Int,
        val kind: EditorTool, // HIGHLIGHT, UNDERLINE, STRIKETHROUGH
        val normStartX: Float,
        val normStartY: Float,
        val normEndX: Float,
        val normEndY: Float,
        val colorArgb: Int
    ) : PdfOverlayAnnotation()

    data class FreehandOverlay(
        override val id: String,
        override val pageIndex: Int,
        val normPoints: List<Pair<Float, Float>>,
        val colorArgb: Int = 0xFF3478F6.toInt(),
        val strokeWidthPt: Float = 3f
    ) : PdfOverlayAnnotation()

    data class CommentOverlay(
        override val id: String,
        override val pageIndex: Int,
        val normX: Float,
        val normY: Float,
        val commentText: String,
        val author: String = "User"
    ) : PdfOverlayAnnotation()

    data class SignatureOverlay(
        override val id: String,
        override val pageIndex: Int,
        val normX: Float,
        val normY: Float,
        val normWidth: Float,
        val normHeight: Float,
        val signatureImagePath: String
    ) : PdfOverlayAnnotation()

    data class WatermarkOverlay(
        override val id: String,
        override val pageIndex: Int, // -1 means all pages
        val text: String,
        val opacity: Float = 0.22f,
        val angleDeg: Float = 45f,
        val colorArgb: Int = 0xFF7047EB.toInt()
    ) : PdfOverlayAnnotation()
}

data class PdfMetadataInfo(
    val title: String = "",
    val author: String = "",
    val subject: String = "",
    val keywords: String = "",
    val creator: String = "",
    val producer: String = "",
    val pageCount: Int = 0,
    val fileSizeBytes: Long = 0L,
    val isEncrypted: Boolean = false
)

data class PdfBookmarkItem(
    val title: String,
    val pageIndex: Int
)

data class CompressionStats(
    val originalBytes: Long,
    val compressedBytes: Long,
    val outputFile: File
) {
    val savedBytes: Long get() = (originalBytes - compressedBytes).coerceAtLeast(0L)
    val reductionPercent: Int
        get() = if (originalBytes > 0L) {
            (((originalBytes - compressedBytes).toDouble() / originalBytes.toDouble()) * 100.0)
                .toInt()
                .coerceIn(0, 99)
        } else 0
}

data class PageDiffItem(
    val pageNumber: Int,
    val addedLines: List<String>,
    val removedLines: List<String>,
    val isIdentical: Boolean
)

data class PdfDiffResult(
    val leftFileName: String,
    val rightFileName: String,
    val leftPageCount: Int,
    val rightPageCount: Int,
    val pageDiffs: List<PageDiffItem>
)

data class PdfSearchMatch(
    val pageIndex: Int,
    val snippet: String,
    val matchCountOnPage: Int
)

data class OcrResultData(
    val fullText: String,
    val pagesText: List<Pair<Int, String>>,
    val modelNotice: String? = null
)

data class ScannedPageItem(
    val id: String,
    val originalFile: File,
    val processedFile: File,
    val rotationDegrees: Int = 0,
    val filter: ScanFilter = ScanFilter.COLOR_ENHANCED,
    val brightness: Float = 0f, // -50..50
    val contrast: Float = 1.0f, // 0.5..2.0
    val corners: List<Pair<Float, Float>> = listOf(
        0.06f to 0.06f,
        0.94f to 0.06f,
        0.94f to 0.94f,
        0.06f to 0.94f
    )
)

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val defaultPageSize: PageSizeOption = PageSizeOption.A4,
    val defaultOrientation: PageOrientation = PageOrientation.PORTRAIT,
    val defaultCompression: CompressionLevel = CompressionLevel.MEDIUM,
    val defaultSaveFolderUri: String = "",
    val defaultFontSize: Float = 14f,
    val defaultMarginPt: Float = 48f,
    val onboardingCompleted: Boolean = false,
    val favoriteToolIds: Set<String> = setOf(
        PdfToolId.CREATE_TEXT.id,
        PdfToolId.SCAN_DOCUMENT.id,
        PdfToolId.MERGE_PDF.id,
        PdfToolId.COMPRESS_PDF.id
    )
)

data class StorageBreakdown(
    val totalVaultBytes: Long,
    val documentCount: Int,
    val deletedBytes: Long,
    val deletedCount: Int,
    val cacheBytes: Long,
    val deviceFreeBytes: Long
)
