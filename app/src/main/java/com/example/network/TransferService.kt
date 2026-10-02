package com.example.network

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class TransferService : Service() {

    companion object {
        const val CHANNEL_ID = "nova_transfer_channel"
        const val REQUEST_CHANNEL_ID = "nova_request_channel"
        const val NOTIFICATION_ID = 2001
        const val REQUEST_NOTIFICATION_ID = 2002
        const val COMPLETED_NOTIFICATION_ID = 2003

        const val ACTION_START = "com.example.action.START_TRANSFER"
        const val ACTION_UPDATE = "com.example.action.UPDATE_PROGRESS"
        const val ACTION_STOP = "com.example.action.STOP_TRANSFER"
        const val ACTION_CANCEL = "com.example.action.CANCEL_TRANSFER"
        const val ACTION_SHOW_REQUEST = "com.example.action.SHOW_REQUEST"
        const val ACTION_DISMISS_REQUEST = "com.example.action.DISMISS_REQUEST"
        const val ACTION_ACCEPT_TRANSFER = "com.example.action.ACCEPT_TRANSFER"
        const val ACTION_DECLINE_TRANSFER = "com.example.action.DECLINE_TRANSFER"
        const val ACTION_SHOW_COMPLETED = "com.example.action.SHOW_COMPLETED"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_SPEED = "extra_speed"
        const val EXTRA_ETA = "extra_eta"
        const val EXTRA_SENDER_NAME = "extra_sender_name"
        const val EXTRA_FILES_COUNT = "extra_files_count"
        const val EXTRA_TOTAL_SIZE = "extra_total_size"
        const val EXTRA_COMPLETED_MSG = "extra_completed_msg"

        var onCancelRequested: (() -> Unit)? = null
        var onAcceptRequested: (() -> Unit)? = null
        var onDeclineRequested: (() -> Unit)? = null

        fun start(context: Context, title: String) {
            val intent = Intent(context, TransferService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateProgress(context: Context, title: String, progress: Int, speed: String, eta: String) {
            val intent = Intent(context, TransferService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_PROGRESS, progress)
                putExtra(EXTRA_SPEED, speed)
                putExtra(EXTRA_ETA, eta)
            }
            context.startService(intent)
        }

        fun showTransferRequest(context: Context, senderName: String, filesCount: Int, totalSizeFormatted: String) {
            val intent = Intent(context, TransferService::class.java).apply {
                action = ACTION_SHOW_REQUEST
                putExtra(EXTRA_SENDER_NAME, senderName)
                putExtra(EXTRA_FILES_COUNT, filesCount)
                putExtra(EXTRA_TOTAL_SIZE, totalSizeFormatted)
            }
            context.startService(intent)
        }

        fun dismissTransferRequest(context: Context) {
            val intent = Intent(context, TransferService::class.java).apply {
                action = ACTION_DISMISS_REQUEST
            }
            context.startService(intent)
        }

        fun showTransferCompleted(context: Context, title: String, message: String) {
            val intent = Intent(context, TransferService::class.java).apply {
                action = ACTION_SHOW_COMPLETED
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_COMPLETED_MSG, message)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TransferService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var notificationManager: NotificationManager? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        createNotificationChannel()
        acquireLocks()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val transferChannel = NotificationChannel(
                CHANNEL_ID,
                "NovaShare File Transfer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live progress, speed, and background locks during active transfers"
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(transferChannel)

            val requestChannel = NotificationChannel(
                REQUEST_CHANNEL_ID,
                "NovaShare Transfer Requests",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts you when a nearby device sends files or requests pairing"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager?.createNotificationChannel(requestChannel)
        }
    }

    private fun acquireLocks() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NovaShare:TransferWakeLock")?.apply {
                setReferenceCounted(false)
                acquire(60 * 60 * 1000L) // 1 hour max safeguard
            }

            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiLockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL
            }
            wifiLock = wifiManager?.createWifiLock(wifiLockMode, "NovaShare:WifiLock")?.apply {
                setReferenceCounted(false)
                acquire()
            }
            Log.d("TransferService", "Acquired WakeLock and WifiLock for background transfer performance")
        } catch (e: Exception) {
            Log.w("TransferService", "Failed to acquire locks", e)
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
            wifiLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
            wifiLock = null
            Log.d("TransferService", "Released WakeLock and WifiLock")
        } catch (e: Exception) {
            Log.w("TransferService", "Failed to release locks", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "File Transferring"
                val notification = buildNotification(title, 0, "Initializing transfer...", "")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }

            ACTION_UPDATE -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Transferring"
                val progress = intent.getIntExtra(EXTRA_PROGRESS, 0)
                val speed = intent.getStringExtra(EXTRA_SPEED) ?: ""
                val eta = intent.getStringExtra(EXTRA_ETA) ?: ""
                val notification = buildNotification(title, progress, speed, eta)
                notificationManager?.notify(NOTIFICATION_ID, notification)
            }

            ACTION_SHOW_REQUEST -> {
                val senderName = intent.getStringExtra(EXTRA_SENDER_NAME) ?: "Nearby Device"
                val filesCount = intent.getIntExtra(EXTRA_FILES_COUNT, 1)
                val totalSize = intent.getStringExtra(EXTRA_TOTAL_SIZE) ?: ""
                val requestNotif = buildRequestNotification(senderName, filesCount, totalSize)
                notificationManager?.notify(REQUEST_NOTIFICATION_ID, requestNotif)
            }

            ACTION_DISMISS_REQUEST -> {
                notificationManager?.cancel(REQUEST_NOTIFICATION_ID)
            }

            ACTION_ACCEPT_TRANSFER -> {
                notificationManager?.cancel(REQUEST_NOTIFICATION_ID)
                onAcceptRequested?.invoke()
            }

            ACTION_DECLINE_TRANSFER -> {
                notificationManager?.cancel(REQUEST_NOTIFICATION_ID)
                onDeclineRequested?.invoke()
            }

            ACTION_SHOW_COMPLETED -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Transfer Complete"
                val message = intent.getStringExtra(EXTRA_COMPLETED_MSG) ?: "File transfer succeeded"
                val completedNotif = buildCompletedNotification(title, message)
                notificationManager?.notify(COMPLETED_NOTIFICATION_ID, completedNotif)
            }

            ACTION_CANCEL -> {
                onCancelRequested?.invoke()
                stopForegroundAndService()
            }

            ACTION_STOP -> {
                stopForegroundAndService()
            }
        }

        return START_NOT_STICKY
    }

    private fun buildRequestNotification(senderName: String, filesCount: Int, totalSize: String): Notification {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            201,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val acceptIntent = Intent(this, TransferService::class.java).apply {
            action = ACTION_ACCEPT_TRANSFER
        }
        val acceptPendingIntent = PendingIntent.getService(
            this,
            202,
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val declineIntent = Intent(this, TransferService::class.java).apply {
            action = ACTION_DECLINE_TRANSFER
        }
        val declinePendingIntent = PendingIntent.getService(
            this,
            203,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val sizeText = if (totalSize.isNotBlank()) " ($totalSize)" else ""
        return NotificationCompat.Builder(this, REQUEST_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("NovaShare: Transfer Request")
            .setContentText("$senderName wants to send $filesCount file(s)$sizeText")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .addAction(android.R.drawable.checkbox_on_background, "Accept", acceptPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePendingIntent)
            .build()
    }

    private fun buildCompletedNotification(title: String, message: String): Notification {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            204,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    private fun stopForegroundAndService() {
        releaseLocks()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun buildNotification(title: String, progress: Int, speed: String, eta: String): Notification {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val cancelIntent = Intent(this, TransferService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val detailText = if (speed.isNotBlank()) {
            if (eta.isNotBlank()) "$speed • ETA: $eta" else speed
        } else {
            "In progress ($progress%)"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(title)
            .setContentText(detailText)
            .setContentIntent(pendingIntent)
            .setProgress(100, progress, progress <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        releaseLocks()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
