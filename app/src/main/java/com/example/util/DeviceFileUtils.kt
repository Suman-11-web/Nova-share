package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.data.model.FileCategory
import com.example.data.model.SharedFile

object DeviceFileUtils {

    fun parsePickedUris(context: Context, uris: List<Uri>): List<SharedFile> {
        val list = mutableListOf<SharedFile>()
        val resolver = context.contentResolver

        for (uri in uris) {
            try {
                var fileName = "File_${System.currentTimeMillis()}"
                var fileSize = 0L

                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                        if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                    }
                }

                val mimeType = resolver.getType(uri) ?: "application/octet-stream"
                val category = getFileCategoryFromMime(mimeType, fileName)

                list.add(
                    SharedFile(
                        name = fileName,
                        path = uri.toString(),
                        uri = uri,
                        size = fileSize,
                        category = category,
                        mimeType = mimeType,
                        dateModified = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return list
    }

    fun queryMediaStoreFiles(context: Context, categoryFilter: FileCategory = FileCategory.ALL): List<SharedFile> {
        val files = mutableListOf<SharedFile>()
        val resolver = context.contentResolver

        try {
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DATA
            )

            val uri = MediaStore.Files.getContentUri("external")
            val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"

            resolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val mimeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val dataColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)

                var count = 0
                while (cursor.moveToNext() && count < 200) {
                    val id = cursor.getLong(idColumn)
                    val name = if (nameColumn != -1) cursor.getString(nameColumn) else null
                    if (name.isNullOrEmpty()) continue

                    val size = if (sizeColumn != -1) cursor.getLong(sizeColumn) else 0L
                    if (size <= 0) continue

                    val mimeType = if (mimeColumn != -1) cursor.getString(mimeColumn) ?: "" else ""
                    val path = if (dataColumn != -1) cursor.getString(dataColumn) ?: "" else ""

                    val fileCategory = getFileCategoryFromMime(mimeType, name)
                    if (categoryFilter != FileCategory.ALL && fileCategory != categoryFilter) {
                        continue
                    }

                    files.add(
                        SharedFile(
                            id = id.toString(),
                            name = name,
                            path = path,
                            size = size,
                            category = fileCategory,
                            mimeType = mimeType
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return files
    }

    fun getFileCategoryFromMime(mimeType: String?, fileName: String): FileCategory {
        val mime = mimeType?.lowercase() ?: ""
        val ext = fileName.substringAfterLast('.', "").lowercase()

        return when {
            mime.startsWith("image/") || ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic") -> FileCategory.IMAGES
            mime.startsWith("video/") || ext in listOf("mp4", "mkv", "webm", "avi", "mov", "3gp") -> FileCategory.VIDEOS
            mime.startsWith("audio/") || ext in listOf("mp3", "m4a", "wav", "aac", "flac", "ogg") -> FileCategory.AUDIO
            mime.contains("pdf") || mime.contains("document") || mime.contains("word") || mime.contains("sheet") || ext in listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt") -> FileCategory.DOCUMENTS
            mime == "application/vnd.android.package-archive" || ext == "apk" -> FileCategory.APK
            mime.contains("zip") || mime.contains("rar") || mime.contains("compressed") || ext in listOf("zip", "rar", "7z", "tar", "gz") -> FileCategory.ARCHIVE
            mime.contains("vcard") || ext == "vcf" -> FileCategory.CONTACTS
            else -> FileCategory.ALL
        }
    }
}
