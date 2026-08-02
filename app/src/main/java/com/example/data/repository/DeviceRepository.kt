package com.example.data.repository

import com.example.data.local.DeviceDao
import com.example.data.local.DeviceEntity
import kotlinx.coroutines.flow.Flow

class DeviceRepository(private val deviceDao: DeviceDao) {
    val allDevices: Flow<List<DeviceEntity>> = deviceDao.getAllDevices()
    val favoriteDevices: Flow<List<DeviceEntity>> = deviceDao.getFavoriteDevices()

    suspend fun saveDevice(device: DeviceEntity) {
        deviceDao.insertDevice(device)
    }

    suspend fun toggleFavorite(id: String, isFavorite: Boolean) {
        deviceDao.updateFavorite(id, isFavorite)
    }

    suspend fun toggleTrusted(id: String, isTrusted: Boolean) {
        deviceDao.updateTrusted(id, isTrusted)
    }

    suspend fun toggleBlocked(id: String, isBlocked: Boolean) {
        deviceDao.updateBlocked(id, isBlocked)
    }

    suspend fun deleteDevice(id: String) {
        deviceDao.deleteDevice(id)
    }
}
