package com.example.data.pdf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.util.UUID

class FileStorageManager(private val context: Context) {

    val pdfVaultDir: File
        get() = File(context.filesDir, "pdfs").apply { if (!exists()) mkdirs() }

    val scansDir: File
        get() = File(context.filesDir, "scans").apply { if (!exists()) mkdirs() }

    val tempDir: File
        get() = File(context.cacheDir, "pdf_tmp").apply { if (!exists()) mkdirs() }

    fun createVaultFile(desiredName: String, extension: String = "pdf"): File {
        val sanitized = sanitizeFileName(desiredName)
        val base = sanitized.removeSuffix(".$extension")
        var candidate = File(pdfVaultDir, "$base.$extension")
        var counter = 1
        while (candidate.exists()) {
            candidate = File(pdfVaultDir, "${base}_$counter.$extension")
            counter++
        }
        return candidate
    }

    fun createTempFile(prefix: String, extension: String): File {
        return File(tempDir, "${prefix}_${UUID.randomUUID().toString().take(8)}.$extension")
    }

    fun cleanupTempFiles() {
        tempDir.listFiles()?.forEach { file ->
            runCatching {
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            }
        }
    }

    suspend fun importPdfFromUri(uri: Uri): Result<ImportedPdfInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = resolveDisplayName(uri) ?: "imported_${System.currentTimeMillis()}.pdf"
            val destFile = createVaultFile(displayName, "pdf")
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Cannot open selected file URI.")

            input.use { stream ->
                FileOutputStream(destFile).use { out ->
                    stream.copyTo(out)
                }
            }

            if (!isValidPdfFile(destFile)) {
                destFile.delete()
                throw IllegalArgumentException("Selected file is not a valid PDF document.")
            }

            val pageCountAndLock = inspectPdfQuick(destFile)
            ImportedPdfInfo(
                file = destFile,
                title = displayName.substringBeforeLast("."),
                pageCount = pageCountAndLock.first,
                isEncrypted = pageCountAndLock.second,
                sizeBytes = destFile.length()
            )
        }
    }

    suspend fun copyUriToTempFile(uri: Uri, fallbackExt: String = "bin"): Result<Pair<File, String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val name = resolveDisplayName(uri) ?: "input_${System.currentTimeMillis()}.$fallbackExt"
                val ext = name.substringAfterLast(".", fallbackExt)
                val temp = createTempFile("import", ext)
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Unable to read selected file.")
                input.use { stream ->
                    FileOutputStream(temp).use { out ->
                        stream.copyTo(out)
                    }
                }
                temp to name
            }
        }

    suspend fun exportFileToUri(sourceFile: File, targetUri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!sourceFile.exists()) {
                    throw IllegalStateException("Source file no longer exists.")
                }
                val out = context.contentResolver.openOutputStream(targetUri)
                    ?: throw IllegalStateException("Cannot write to destination URI.")
                out.use { output ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(output)
                    }
                }
                Unit
            }
        }

    suspend fun exportFileToFolderTree(
        sourceFile: File,
        treeUri: Uri,
        mimeType: String = "application/pdf"
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val treeDoc = DocumentFile.fromTreeUri(context, treeUri)
                ?: throw IllegalStateException("Selected folder is inaccessible.")
            val targetDoc = treeDoc.createFile(mimeType, sourceFile.name)
                ?: throw IllegalStateException("Failed to create file in selected folder.")
            exportFileToUri(sourceFile, targetDoc.uri).getOrThrow()
            targetDoc.uri
        }
    }

    fun renameVaultFile(currentFile: File, newTitle: String): Result<File> = runCatching {
        if (!currentFile.exists()) throw IllegalStateException("Original file is missing.")
        val ext = currentFile.extension.ifBlank { "pdf" }
        val sanitized = sanitizeFileName(newTitle).removeSuffix(".$ext")
        val target = File(currentFile.parentFile ?: pdfVaultDir, "$sanitized.$ext")
        if (target.absolutePath == currentFile.absolutePath) return@runCatching currentFile
        val finalTarget = if (target.exists()) createVaultFile(sanitized, ext) else target
        if (!currentFile.renameTo(finalTarget)) {
            currentFile.copyTo(finalTarget, overwrite = false)
            currentFile.delete()
        }
        finalTarget
    }

    fun isValidPdfFile(file: File): Boolean {
        if (!file.exists() || file.length() < 8) return false
        return runCatching {
            FileInputStream(file).use { fis ->
                val header = ByteArray(1024)
                val read = fis.read(header)
                if (read <= 0) return false
                val str = String(header, 0, read, Charsets.ISO_8859_1)
                str.contains("%PDF-")
            }
        }.getOrDefault(false)
    }

    fun inspectPdfQuick(file: File): Pair<Int, Boolean> {
        return try {
            PDDocument.load(file).use { doc ->
                doc.numberOfPages to doc.isEncrypted
            }
        } catch (e: Exception) {
            val msg = e.message.orEmpty().lowercase()
            if (msg.contains("password") || msg.contains("encrypt") || msg.contains("decrypt")) {
                1 to true
            } else {
                1 to false
            }
        }
    }

    fun shareFile(file: File, mimeType: String = "application/pdf") {
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Share ${file.name}").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun printPdfFile(activityContext: Context, file: File) {
        if (!file.exists()) return
        val printManager = activityContext.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: return
        val jobName = file.nameWithoutExtension
        printManager.print(jobName, PdfFilePrintAdapter(file), null)
    }

    fun resolveDisplayName(uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIdx != -1 && cursor.moveToFirst()) {
                return cursor.getString(nameIdx)
            }
        }
        return uri.lastPathSegment
    }

    fun getDirectorySizeBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun getDeviceAvailableBytes(): Long {
        return runCatching {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBytes
        }.getOrDefault(0L)
    }

    fun sanitizeFileName(raw: String): String {
        val cleaned = raw.trim()
            .replace(Regex("[^a-zA-Z0-9._\\- \\u0980-\\u09FF]"), "_")
            .replace(Regex("\\s+"), "_")
        return cleaned.ifBlank { "PDF_Document_${System.currentTimeMillis()}" }
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0L) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
                .coerceIn(0, units.lastIndex)
            return DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
        }
    }
}

data class ImportedPdfInfo(
    val file: File,
    val title: String,
    val pageCount: Int,
    val isEncrypted: Boolean,
    val sizeBytes: Long
)

private class PdfFilePrintAdapter(private val file: File) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(file.name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()
        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback
    ) {
        try {
            FileInputStream(file).use { input ->
                FileOutputStream(destination.fileDescriptor).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        if (cancellationSignal?.isCanceled == true) {
                            callback.onWriteCancelled()
                            return
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        }
    }
}
