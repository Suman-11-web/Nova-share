package com.example.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import com.example.data.model.FileCategory
import com.example.data.model.SharedFile
import java.io.File
import java.io.FileOutputStream

data class InstalledAppItem(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val apkSize: Long,
    val apkPath: String,
    val isSystemApp: Boolean,
    val iconUri: String? = null
)

object ApkExtractor {

    private const val TAG = "NovaApkExtractor"

    fun getInstalledApps(context: Context, includeSystemApps: Boolean = false): List<InstalledAppItem> {
        val apps = mutableListOf<InstalledAppItem>()
        val pm = context.packageManager

        try {
            val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
            for (pkg in packages) {
                val appInfo = pkg.applicationInfo ?: continue

                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (isSystem && !includeSystemApps) {
                    continue
                }

                val sourceDir = appInfo.sourceDir
                if (sourceDir.isNullOrBlank()) continue
                val apkFile = File(sourceDir)
                if (!apkFile.exists() || !apkFile.canRead()) continue

                val label = pm.getApplicationLabel(appInfo).toString()
                val versionName = pkg.versionName ?: "1.0"
                val size = apkFile.length()

                apps.add(
                    InstalledAppItem(
                        appName = label,
                        packageName = pkg.packageName,
                        versionName = versionName,
                        apkSize = size,
                        apkPath = sourceDir,
                        isSystemApp = isSystem,
                        iconUri = null
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying installed packages", e)
        }

        return apps.sortedBy { it.appName.lowercase() }
    }

    fun toSharedFile(app: InstalledAppItem): SharedFile {
        val cleanName = app.appName.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
        val fileName = "${cleanName}_v${app.versionName}.apk"
        return SharedFile(
            id = "apk_${app.packageName}",
            name = fileName,
            path = app.apkPath,
            size = app.apkSize,
            category = FileCategory.APK,
            mimeType = "application/vnd.android.package-archive",
            dateModified = System.currentTimeMillis()
        )
    }

    fun getInstalledSharedFiles(context: Context, includeSystem: Boolean = false): List<SharedFile> {
        return getInstalledApps(context, includeSystem).map { toSharedFile(it) }
    }
}
