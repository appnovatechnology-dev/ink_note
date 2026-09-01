package com.example.inknote.pdf

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object DocumentDownloadManager {

    suspend fun saveToDownloads(
        context: Context,
        sourceFile: File,
        targetFileName: String,
        mimeType: String = "application/pdf"
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            if (!sourceFile.exists()) {
                return@withContext Result.failure(Exception("Source file does not exist"))
            }

            val sanitizedFileName = if (targetFileName.contains('.')) {
                targetFileName
            } else {
                when {
                    mimeType.contains("pdf") -> "$targetFileName.pdf"
                    mimeType.contains("audio") || mimeType.contains("m4a") || mimeType.contains("mp4") -> "$targetFileName.m4a"
                    else -> "$targetFileName.bin"
                }
            }

            val contentResolver = context.contentResolver

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, sanitizedFileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return@withContext Result.failure(Exception("Failed to create download record"))

                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    sourceFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                contentResolver.update(uri, contentValues, null, null)
                Result.success(uri)
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val targetFile = File(downloadsDir, sanitizedFileName)
                sourceFile.inputStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Result.success(Uri.fromFile(targetFile))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
