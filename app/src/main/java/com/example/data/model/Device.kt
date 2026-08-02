package com.example.data.model

enum class DeviceType {
    ANDROID, WINDOWS, MACOS, LINUX, BROWSER
}

data class Device(
    val id: String,
    val name: String,
    val ipAddress: String,
    val port: Int = 8080,
    val type: DeviceType = DeviceType.ANDROID,
    val avatarId: Int = 1,
    val isTrusted: Boolean = false,
    val isBlocked: Boolean = false,
    val isFavorite: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
)

enum class TransferDirection {
    SEND, RECEIVE
}

enum class TransferStatus {
    PENDING, CONNECTING, WAITING_FOR_ACCEPTANCE, TRANSFERRING, PAUSED, COMPLETED, FAILED, CANCELLED, DECLINED
}

data class TransferSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    val direction: TransferDirection,
    val deviceName: String,
    val deviceIp: String,
    val files: List<SharedFile>,
    val totalBytes: Long,
    var bytesTransferred: Long = 0,
    var speedBytesPerSec: Long = 0,
    var etaSeconds: Long = 0,
    var status: TransferStatus = TransferStatus.PENDING,
    var isEncrypted: Boolean = true,
    var pinCode: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
