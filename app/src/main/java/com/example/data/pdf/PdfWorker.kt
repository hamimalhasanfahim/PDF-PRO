package com.example.data.pdf

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.PdfMasterApp
import com.example.domain.model.CompressionLevel
import java.io.File
import java.util.UUID

class PdfBackgroundWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as PdfMasterApp
        val container = app.container
        val opType = inputData.getString(KEY_OPERATION) ?: return Result.failure()

        return when (opType) {
            OP_COMPRESS -> {
                val path = inputData.getString(KEY_INPUT_PATH) ?: return Result.failure()
                val levelName = inputData.getString(KEY_COMPRESSION_LEVEL) ?: CompressionLevel.MEDIUM.name
                val quality = inputData.getInt(KEY_CUSTOM_QUALITY, 60)
                val maxDim = inputData.getInt(KEY_CUSTOM_MAX_DIM, 1500)
                val inFile = File(path)
                val outFile = container.fileStorageManager.createVaultFile("${inFile.nameWithoutExtension}_compressed")
                val res = container.compressionEngine.compressPdf(
                    inputFile = inFile,
                    outputFile = outFile,
                    level = CompressionLevel.fromName(levelName),
                    customQuality = quality,
                    customMaxDimPx = maxDim
                )
                res.fold(
                    onSuccess = { stats ->
                        val doc = container.repository.registerGeneratedPdf(
                            file = stats.outputFile,
                            title = stats.outputFile.nameWithoutExtension,
                            originTag = "compressed",
                            actionType = "Compress PDF (Background)",
                            inputName = inFile.name,
                            originalSizeBytes = stats.originalBytes,
                            details = "Reduced by ${stats.reductionPercent}%"
                        )
                        Result.success(
                            workDataOf(
                                KEY_OUTPUT_DOC_ID to doc.id,
                                KEY_OUTPUT_PATH to stats.outputFile.absolutePath,
                                KEY_ORIGINAL_BYTES to stats.originalBytes,
                                KEY_OUTPUT_BYTES to stats.compressedBytes
                            )
                        )
                    },
                    onFailure = {
                        container.repository.logHistoryFailure("Compress PDF", inFile.name, it.message.orEmpty())
                        Result.failure(workDataOf(KEY_ERROR to (it.message ?: "Compression failed")))
                    }
                )
            }
            else -> Result.failure()
        }
    }

    companion object {
        const val KEY_OPERATION = "operation"
        const val KEY_INPUT_PATH = "input_path"
        const val KEY_COMPRESSION_LEVEL = "compression_level"
        const val KEY_CUSTOM_QUALITY = "custom_quality"
        const val KEY_CUSTOM_MAX_DIM = "custom_max_dim"
        const val KEY_OUTPUT_DOC_ID = "output_doc_id"
        const val KEY_OUTPUT_PATH = "output_path"
        const val KEY_ORIGINAL_BYTES = "original_bytes"
        const val KEY_OUTPUT_BYTES = "output_bytes"
        const val KEY_ERROR = "error"

        const val OP_COMPRESS = "compress"

        fun enqueueCompression(
            context: Context,
            inputFile: File,
            level: CompressionLevel,
            customQuality: Int,
            customMaxDim: Int
        ): UUID {
            val request = OneTimeWorkRequestBuilder<PdfBackgroundWorker>()
                .setInputData(
                    Data.Builder()
                        .putString(KEY_OPERATION, OP_COMPRESS)
                        .putString(KEY_INPUT_PATH, inputFile.absolutePath)
                        .putString(KEY_COMPRESSION_LEVEL, level.name)
                        .putInt(KEY_CUSTOM_QUALITY, customQuality)
                        .putInt(KEY_CUSTOM_MAX_DIM, customMaxDim)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueue(request)
            return request.id
        }
    }
}
