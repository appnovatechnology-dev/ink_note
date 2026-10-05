package com.example.inknote.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File

object DownloadUtils {

    /**
     * Saves a file directly to the device's public Downloads directory.
     * Uses MediaStore.Downloads on Android 10+ (API 29+) and direct File I/O on older APIs or fallback.
     */
    fun saveFileToDownloads(
        context: Context,
        sourceFile: File,
        targetFileName: String,
        mimeType: String
    ): Uri? {
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            android.util.Log.e("DownloadUtils", "Source file does not exist or is empty: ${sourceFile.absolutePath}")
            return null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // First attempt with sub-directory: Environment.DIRECTORY_DOWNLOADS + "/Inkstead/"
            val uri = trySaveToMediaStore(context, sourceFile, targetFileName, mimeType, Environment.DIRECTORY_DOWNLOADS + "/Inkstead/")
                ?: trySaveToMediaStore(context, sourceFile, targetFileName, mimeType, Environment.DIRECTORY_DOWNLOADS + "/")
            if (uri != null) {
                return uri
            }
        }

        // Fallback: Direct public storage I/O
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val targetFile = File(downloadsDir, targetFileName)
            sourceFile.copyTo(targetFile, overwrite = true)
            android.util.Log.d("DownloadUtils", "Saved via fallback file copy to: ${targetFile.absolutePath}")
            Uri.fromFile(targetFile)
        } catch (e: Exception) {
            android.util.Log.e("DownloadUtils", "Failed to save file to Downloads", e)
            null
        }
    }

    private fun trySaveToMediaStore(
        context: Context,
        sourceFile: File,
        targetFileName: String,
        mimeType: String,
        relativePath: String
    ): Uri? {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, targetFileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null

            val writeSuccess = resolver.openOutputStream(uri)?.use { outStream ->
                sourceFile.inputStream().use { inStream ->
                    inStream.copyTo(outStream)
                }
                true
            } ?: false

            if (writeSuccess) {
                values.clear()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(uri, values, null, null)
                android.util.Log.d("DownloadUtils", "Successfully saved to MediaStore: $uri")
                uri
            } else {
                resolver.delete(uri, null, null)
                null
            }
        } catch (e: Exception) {
            android.util.Log.w("DownloadUtils", "trySaveToMediaStore failed for $relativePath: ${e.message}")
            null
        }
    }

    /**
     * Resolves the exact display name of a file from a Uri (e.g. from Storage Access Framework / Photo Picker).
     */
    fun getOriginalFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            try {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            result = it.getString(index)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (result.isNullOrBlank()) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "Imported_Document"
    }
}
