package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfer_history ORDER BY timestamp DESC")
    fun getAllTransfers(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfer_history WHERE status = :status ORDER BY timestamp DESC")
    fun getTransfersByStatus(status: String): Flow<List<TransferEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransfer(transfer: TransferEntity)

    @Query("DELETE FROM transfer_history WHERE id = :id")
    suspend fun deleteTransferById(id: String)

    @Query("DELETE FROM transfer_history")
    suspend fun clearHistory()

    @Query("SELECT SUM(fileSize) FROM transfer_history WHERE status = 'COMPLETED' AND direction = 'SEND'")
    fun getTotalBytesSent(): Flow<Long?>

    @Query("SELECT SUM(fileSize) FROM transfer_history WHERE status = 'COMPLETED' AND direction = 'RECEIVE'")
    fun getTotalBytesReceived(): Flow<Long?>

    @Query("SELECT COUNT(*) FROM transfer_history WHERE status = 'COMPLETED'")
    fun getTotalCompletedCount(): Flow<Int>
}

@Dao
interface DeviceDao {
    @Query("SELECT * FROM saved_devices ORDER BY lastSeen DESC")
    fun getAllDevices(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM saved_devices WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavoriteDevices(): Flow<List<DeviceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: DeviceEntity)

    @Query("UPDATE saved_devices SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: String, isFavorite: Boolean)

    @Query("UPDATE saved_devices SET isTrusted = :isTrusted WHERE id = :id")
    suspend fun updateTrusted(id: String, isTrusted: Boolean)

    @Query("UPDATE saved_devices SET isBlocked = :isBlocked WHERE id = :id")
    suspend fun updateBlocked(id: String, isBlocked: Boolean)

    @Query("DELETE FROM saved_devices WHERE id = :id")
    suspend fun deleteDevice(id: String)
}
