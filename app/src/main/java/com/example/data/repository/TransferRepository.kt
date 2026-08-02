package com.example.data.repository

import com.example.data.local.TransferDao
import com.example.data.local.TransferEntity
import kotlinx.coroutines.flow.Flow

class TransferRepository(private val transferDao: TransferDao) {
    val allTransfers: Flow<List<TransferEntity>> = transferDao.getAllTransfers()
    val totalBytesSent: Flow<Long?> = transferDao.getTotalBytesSent()
    val totalBytesReceived: Flow<Long?> = transferDao.getTotalBytesReceived()
    val totalCompletedCount: Flow<Int> = transferDao.getTotalCompletedCount()

    suspend fun recordTransfer(transfer: TransferEntity) {
        transferDao.insertTransfer(transfer)
    }

    suspend fun deleteTransfer(id: String) {
        transferDao.deleteTransferById(id)
    }

    suspend fun clearHistory() {
        transferDao.clearHistory()
    }
}
