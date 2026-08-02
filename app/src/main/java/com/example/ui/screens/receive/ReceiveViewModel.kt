package com.example.ui.screens.receive

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.TransferEntity
import com.example.data.model.*
import com.example.data.repository.TransferRepository
import com.example.network.NetworkUtils
import com.example.network.P2PServer
import com.example.util.NovaShareStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Random

class ReceiveViewModel(
    private val transferRepository: TransferRepository? = null
) : ViewModel() {

    private var p2pServer: P2PServer? = null

    private val _localIp = MutableStateFlow("127.0.0.1")
    val localIp: StateFlow<String> = _localIp.asStateFlow()

    private val _pairingPin = MutableStateFlow(generateRandomPin())
    val pairingPin: StateFlow<String> = _pairingPin.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _incomingSession = MutableStateFlow<TransferSession?>(null)
    val incomingSession: StateFlow<TransferSession?> = _incomingSession.asStateFlow()

    fun updateLocalIp(context: Context) {
        val ip = NetworkUtils.getLocalIpAddress(context)
        _localIp.value = ip
        if (!_isListening.value) {
            startListening(context)
        }
    }

    fun generateNewPin() {
        _pairingPin.value = generateRandomPin()
    }

    fun toggleListening(context: Context) {
        if (_isListening.value) {
            stopListening()
        } else {
            startListening(context)
        }
    }

    fun startListening(context: Context) {
        if (p2pServer != null) stopListening()

        val saveDir = NovaShareStorage.getNovaShareDirectory(context)
        p2pServer = P2PServer(
            context = context,
            port = 8888,
            outputDir = saveDir,
            onTransferUpdated = { session ->
                _incomingSession.value = session

                if (session.status == TransferStatus.COMPLETED) {
                    val fileName = session.files.firstOrNull()?.name ?: "Received_File"
                    val entity = TransferEntity(
                        id = session.id,
                        direction = "RECEIVE",
                        deviceName = session.deviceName,
                        deviceIp = session.deviceIp,
                        fileName = fileName,
                        filePath = "${saveDir.absolutePath}/$fileName",
                        fileSize = session.totalBytes,
                        fileCategory = session.files.firstOrNull()?.category?.name ?: "ALL",
                        status = "COMPLETED",
                        speedBytesPerSec = session.speedBytesPerSec,
                        durationMs = 1200,
                        isEncrypted = session.isEncrypted,
                        checksumSha256 = "",
                        timestamp = System.currentTimeMillis()
                    )
                    viewModelScope.launch {
                        transferRepository?.recordTransfer(entity)
                    }
                }
            }
        ).apply {
            start()
        }
        _isListening.value = true
    }

    fun stopListening() {
        p2pServer?.stop()
        p2pServer = null
        _isListening.value = false
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }

    private fun generateRandomPin(): String {
        return String.format("%06d", Random().nextInt(1000000))
    }
}
