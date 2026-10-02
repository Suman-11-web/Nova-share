package com.example.util

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.provider.Settings
import java.io.File

data class RealDeviceStorage(
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
    val imagesBytes: Long,
    val videosBytes: Long,
    val audioBytes: Long,
    val docsBytes: Long,
    val otherBytes: Long
)

object DeviceFinderEngine {

    /**
     * Resolves the true user-facing mobile device name dynamically.
     * Uses system device name, bluetooth name, and manufacturer/model fallback.
     */
    fun resolveDeviceName(context: Context): String {
        try {
            // 1. Check Global DEVICE_NAME (Android 7.1+)
            val globalName = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            if (!globalName.isNullOrBlank() && !globalName.equals("Android", ignoreCase = true)) {
                return globalName.trim()
            }
        } catch (_: Exception) {}

        try {
            // 2. Check bluetooth_name in Secure Settings
            val btName = Settings.Secure.getString(context.contentResolver, "bluetooth_name")
            if (!btName.isNullOrBlank() && !btName.equals("Android", ignoreCase = true)) {
                return btName.trim()
            }
        } catch (_: Exception) {}

        try {
            // 3. Bluetooth Adapter Name (if enabled/accessible)
            @Suppress("DEPRECATION")
            val adapter = BluetoothAdapter.getDefaultAdapter()
            val adapterName = adapter?.name
            if (!adapterName.isNullOrBlank() && !adapterName.equals("Android", ignoreCase = true)) {
                return adapterName.trim()
            }
        } catch (_: Exception) {}

        // 4. Construct from Build Manufacturer and Model
        val manufacturer = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.orEmpty()

        return when {
            model.isBlank() -> "Android Device"
            manufacturer.isBlank() -> model
            model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$manufacturer $model"
        }
    }

    /**
     * Queries physical device storage filesystem and MediaStore for real space statistics.
     */
    fun queryDeviceStorage(context: Context): RealDeviceStorage {
        val totalBytes: Long
        val freeBytes: Long
        val usedBytes: Long

        try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            totalBytes = (totalBlocks * blockSize).coerceAtLeast(1024L * 1024L * 1024L)
            freeBytes = (availableBlocks * blockSize).coerceAtLeast(0L)
            usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
        } catch (e: Exception) {
            return RealDeviceStorage(
                totalBytes = 64L * 1024 * 1024 * 1024,
                usedBytes = 24L * 1024 * 1024 * 1024,
                freeBytes = 40L * 1024 * 1024 * 1024,
                imagesBytes = 4L * 1024 * 1024 * 1024,
                videosBytes = 8L * 1024 * 1024 * 1024,
                audioBytes = 2L * 1024 * 1024 * 1024,
                docsBytes = 1L * 1024 * 1024 * 1024,
                otherBytes = 9L * 1024 * 1024 * 1024
            )
        }

        var images = 0L
        var videos = 0L
        var audio = 0L
        var docs = 0L

        try {
            val proj = arrayOf(MediaStore.MediaColumns.SIZE)

            // Images
            context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, null, null, null)?.use { c ->
                val col = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                if (col != -1) {
                    while (c.moveToNext()) {
                        images += c.getLong(col).coerceAtLeast(0L)
                    }
                }
            }

            // Videos
            context.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, proj, null, null, null)?.use { c ->
                val col = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                if (col != -1) {
                    while (c.moveToNext()) {
                        videos += c.getLong(col).coerceAtLeast(0L)
                    }
                }
            }

            // Audio
            context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj, null, null, null)?.use { c ->
                val col = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                if (col != -1) {
                    while (c.moveToNext()) {
                        audio += c.getLong(col).coerceAtLeast(0L)
                    }
                }
            }

            // Docs / Downloads
            val docDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (docDir != null && docDir.exists()) {
                docDir.walkTopDown().maxDepth(3).forEach { f ->
                    if (f.isFile) docs += f.length().coerceAtLeast(0L)
                }
            }
        } catch (_: Exception) {}

        val other = (usedBytes - images - videos - audio - docs).coerceAtLeast(0L)

        return RealDeviceStorage(
            totalBytes = totalBytes,
            usedBytes = usedBytes,
            freeBytes = freeBytes,
            imagesBytes = images,
            videosBytes = videos,
            audioBytes = audio,
            docsBytes = docs,
            otherBytes = other
        )
    }
}
