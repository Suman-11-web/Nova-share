package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfer_history")
data class TransferEntity(
    @PrimaryKey val id: String,
    val direction: String, // SEND or RECEIVE
    val deviceName: String,
    val deviceIp: String,
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val fileCategory: String,
    val status: String, // COMPLETED, FAILED, CANCELLED
    val speedBytesPerSec: Long,
    val durationMs: Long,
    val isEncrypted: Boolean,
    val checksumSha256: String,
    val timestamp: Long
)

@Entity(tableName = "saved_devices")
data class DeviceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val ipAddress: String,
    val port: Int,
    val type: String,
    val avatarId: Int,
    val isTrusted: Boolean,
    val isBlocked: Boolean,
    val isFavorite: Boolean,
    val lastSeen: Long
)
