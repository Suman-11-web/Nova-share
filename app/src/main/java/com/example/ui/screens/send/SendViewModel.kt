package com.example.ui.screens.send

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.TransferEntity
import com.example.data.model.*
import com.example.data.repository.DeviceRepository
import com.example.data.repository.TransferRepository
import com.example.network.NetworkUtils
import com.example.network.P2PClient
import com.example.util.DeviceFileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SendViewModel(
    private val transferRepository: TransferRepository,
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    private var activeP2PClient: P2PClient? = null

    private val _selectedCategory = MutableStateFlow(FileCategory.ALL)
    val selectedCategory: StateFlow<FileCategory> = _selectedCategory.asStateFlow()

    private val _availableFiles = MutableStateFlow<List<SharedFile>>(emptyList())
    val availableFiles: StateFlow<List<SharedFile>> = _availableFiles.asStateFlow()

    private val _selectedFiles = MutableStateFlow<List<SharedFile>>(emptyList())
    val selectedFiles: StateFlow<List<SharedFile>> = _selectedFiles.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<Device>>(emptyList())
    val discoveredDevices: StateFlow<List<Device>> = _discoveredDevices.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _activeSession = MutableStateFlow<TransferSession?>(null)
    val activeSession: StateFlow<TransferSession?> = _activeSession.asStateFlow()

    fun selectCategory(category: FileCategory, context: Context? = null) {
        _selectedCategory.value = category
        context?.let { loadRealDeviceFiles(it) }
    }

    fun loadRealDeviceFiles(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val mediaFiles = DeviceFileUtils.queryMediaStoreFiles(context, _selectedCategory.value)
            val existingPicked = _availableFiles.value.filter { it.uri != null }
            val combined = (existingPicked + mediaFiles).distinctBy { it.id }
            _availableFiles.value = combined
        }
    }

    fun addPickedUris(context: Context, uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val parsedFiles = DeviceFileUtils.parsePickedUris(context, uris)
            val updated = (_availableFiles.value + parsedFiles).distinctBy { it.id }
            _availableFiles.value = updated

            val currentSelected = _selectedFiles.value.toMutableList()
            currentSelected.addAll(parsedFiles)
            _selectedFiles.value = currentSelected.distinctBy { it.id }
        }
    }

    fun toggleFileSelection(file: SharedFile) {
        val current = _selectedFiles.value.toMutableList()
        if (current.any { it.id == file.id }) {
            current.removeAll { it.id == file.id }
        } else {
            current.add(file)
        }
        _selectedFiles.value = current
    }

    fun clearSelection() {
        _selectedFiles.value = emptyList()
    }

    fun searchNearbyDevices(context: Context) {
        viewModelScope.launch {
            _isSearching.value = true
            val devices = mutableListOf<Device>()

            withContext(Dispatchers.IO) {
                try {
                    val localIp = NetworkUtils.getLocalIpAddress(context)
                    if (localIp != "127.0.0.1" && localIp.contains(".")) {
                        val subnet = localIp.substringBeforeLast(".")
                        val currentHost = localIp.substringAfterLast(".").toIntOrNull() ?: 0

                        val activeList = coroutineScope {
                            (1..254).filter { it != currentHost }.map { i ->
                                async(Dispatchers.IO) {
                                    val testIp = "$subnet.$i"
                                    try {
                                        val socket = java.net.Socket()
                                        socket.connect(java.net.InetSocketAddress(testIp, 8888), 120)
                                        socket.close()
                                        Device(
                                            id = "active_$testIp",
                                            name = "NovaShare Receiver ($testIp)",
                                            ipAddress = testIp,
                                            type = DeviceType.ANDROID
                                        )
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                            }.awaitAll().filterNotNull()
                        }
                        devices.addAll(activeList)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            _discoveredDevices.value = devices
            _isSearching.value = false
        }
    }

    fun parseAndPairFromQr(qrContent: String) {
        var ip = qrContent.trim()
        var devName = "Scanned Receiver"
        var port = 8888

        if (ip.contains("NOVASHARE:")) {
            val parts = ip.split(":")
            for (part in parts) {
                if (part.startsWith("IP=")) ip = part.substringAfter("IP=")
                if (part.startsWith("PORT=")) port = part.substringAfter("PORT=").toIntOrNull() ?: 8888
                if (part.startsWith("DEVICE=")) devName = part.substringAfter("DEVICE=")
            }
        } else if (ip.contains("IP=")) {
            ip = ip.substringAfter("IP=").substringBefore(":").substringBefore("?")
        } else if (ip.contains("http://")) {
            ip = ip.substringAfter("http://").substringBefore(":").substringBefore("/")
        } else if (ip.contains(":")) {
            ip = ip.substringBefore(":")
        }

        if (ip.isNotBlank()) {
            val newDevice = Device(
                id = "qr_${System.currentTimeMillis()}",
                name = "$devName ($ip)",
                ipAddress = ip,
                port = port,
                type = DeviceType.ANDROID
            )
            val current = _discoveredDevices.value.toMutableList()
            current.removeAll { it.ipAddress == newDevice.ipAddress }
            current.add(0, newDevice)
            _discoveredDevices.value = current

            if (_selectedFiles.value.isNotEmpty()) {
                initiateTransfer(newDevice)
            }
        }
    }

    fun addManualDevice(name: String, ipAddress: String) {
        val newDevice = Device(
            id = "manual_${System.currentTimeMillis()}",
            name = if (name.isBlank()) "Device $ipAddress" else name,
            ipAddress = ipAddress.trim(),
            type = DeviceType.ANDROID
        )
        val current = _discoveredDevices.value.toMutableList()
        current.removeAll { it.ipAddress == newDevice.ipAddress }
        current.add(0, newDevice)
        _discoveredDevices.value = current
    }

    fun initiateTransfer(device: Device) {
        if (_selectedFiles.value.isEmpty()) return

        val files = _selectedFiles.value
        activeP2PClient?.cancel()

        val client = P2PClient(
            senderDeviceName = NetworkUtils.getDeviceModelName(),
            targetIp = device.ipAddress,
            targetPort = device.port,
            filesToSend = files,
            onProgress = { session ->
                _activeSession.value = session

                if (session.status == TransferStatus.COMPLETED) {
                    viewModelScope.launch {
                        for (file in files) {
                            val entity = TransferEntity(
                                id = java.util.UUID.randomUUID().toString(),
                                direction = "SEND",
                                deviceName = device.name,
                                deviceIp = device.ipAddress,
                                fileName = file.name,
                                filePath = file.path,
                                fileSize = file.size,
                                fileCategory = file.category.name,
                                status = "COMPLETED",
                                speedBytesPerSec = session.speedBytesPerSec,
                                durationMs = 1200,
                                isEncrypted = true,
                                checksumSha256 = "",
                                timestamp = System.currentTimeMillis()
                            )
                            transferRepository.recordTransfer(entity)
                        }
                    }
                }
            }
        )
        activeP2PClient = client
        client.startTransfer()
    }

    fun cancelTransfer() {
        activeP2PClient?.cancel()
        _activeSession.value = null
    }
}
