package com.example.data.model

import android.net.Uri

enum class FileCategory(val displayName: String) {
    IMAGES("Images"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    DOCUMENTS("Documents"),
    APK("APKs"),
    ARCHIVE("Archives"),
    CONTACTS("Contacts"),
    FOLDERS("Folders"),
    ALL("All Files")
}

data class SharedFile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val path: String,
    val uri: Uri? = null,
    val size: Long,
    val category: FileCategory,
    val mimeType: String = "*/*",
    val isDirectory: Boolean = false,
    val checksumSha256: String = "",
    val thumbnailUri: String? = null,
    val dateModified: Long = System.currentTimeMillis()
)
