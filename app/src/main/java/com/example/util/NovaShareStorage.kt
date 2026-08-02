package com.example.util

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import java.io.File

object NovaShareStorage {

    /**
     * Returns the primary "Nova-share" directory in device main storage.
     * Main Path: /sdcard/Download/Nova-share or /sdcard/Nova-share
     */
    fun getNovaShareDirectory(context: Context? = null): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val novaShareFolder = File(downloadsDir, "Nova-share")
        if (!novaShareFolder.exists()) {
            novaShareFolder.mkdirs()
        }

        if (!novaShareFolder.exists()) {
            val primaryStorage = Environment.getExternalStorageDirectory()
            val fallbackFolder = File(primaryStorage, "Nova-share")
            if (!fallbackFolder.exists()) {
                fallbackFolder.mkdirs()
            }
            return fallbackFolder
        }

        return novaShareFolder
    }

    /**
     * Scan saved file with system MediaScanner so it immediately appears in Gallery & Downloads.
     */
    fun scanFile(context: Context, file: File) {
        try {
            MediaScannerConnection.scanFile(
                context.applicationContext,
                arrayOf(file.absolutePath),
                null
            ) { _, _ -> }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
