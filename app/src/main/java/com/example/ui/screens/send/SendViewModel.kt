package com.example.ui.screens.send

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.TransferEntity
import com.example.data.model.*
import com.example.data.repository.DeviceRepository
import com.example.data.repository.TransferRepository
import com.example.network.NetworkUtils
import com.example.network.P2PConnectivityManager
import com.example.network.RadioStateManager
import com.example.util.DeviceFileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SendViewModel(
    private val transferRepository: TransferRepository,
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    private var p2pManager: P2PConnectivityManager? = null

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

    private val _isWifiActive = MutableStateFlow(false)
    val isWifiActive: StateFlow<Boolean> = _isWifiActive.asStateFlow()

    private val _isBluetoothActive = MutableStateFlow(false)
    val isBluetoothActive: StateFlow<Boolean> = _isBluetoothActive.asStateFlow()

    private val _radioMessage = MutableStateFlow("Radios idle")
    val radioMessage: StateFlow<String> = _radioMessage.asStateFlow()

    private fun getOrCreateManager(context: Context): P2PConnectivityManager {
        if (p2pManager == null) {
            val mgr = P2PConnectivityManager(context.applicationContext)
            p2pManager = mgr

            viewModelScope.launch {
                mgr.radioStateManager.isWifiActive.collect { _isWifiActive.value = it }
            }
            viewModelScope.launch {
                mgr.radioStateManager.isBluetoothActive.collect { _isBluetoothActive.value = it }
            }
            viewModelScope.launch {
                mgr.radioStateManager.activeMessage.collect { _radioMessage.value = it }
            }
            viewModelScope.launch {
                mgr.discoveredDevices.collect { devList ->
                    val current = _discoveredDevices.value.toMutableList()
                    for (d in devList) {
                        if (current.none { it.ipAddress == d.ipAddress }) {
                            current.add(d)
                        }
                    }
                    _discoveredDevices.value = current
                }
            }
            viewModelScope.launch {
                mgr.isDiscovering.collect { _isSearching.value = it }
            }
        }
        return p2pManager!!
    }

    fun selectCategory(category: FileCategory, context: Context? = null) {
        _selectedCategory.value = category
        context?.let { loadRealDeviceFiles(it) }
    }

    fun loadRealDeviceFiles(context: Context) {
        getOrCreateManager(context)
        viewModelScope.launch(Dispatchers.IO) {
            val loadedFiles = when (_selectedCategory.value) {
                FileCategory.APK -> com.example.util.ApkExtractor.getInstalledSharedFiles(context)
                FileCategory.ALL -> {
                    val media = DeviceFileUtils.queryMediaStoreFiles(context, FileCategory.ALL)
                    val apks = com.example.util.ApkExtractor.getInstalledSharedFiles(context).take(15)
                    media + apks
                }
                else -> DeviceFileUtils.queryMediaStoreFiles(context, _selectedCategory.value)
            }
            val existingPicked = _availableFiles.value.filter { it.uri != null }
            val combined = (existingPicked + loadedFiles).distinctBy { it.id }
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

    /**
     * Initializes Sender engine and explicitly enables required radios for discovery/transfer
     */
    fun searchNearbyDevices(context: Context, onRadioActionNeeded: ((Intent) -> Unit)? = null) {
        val mgr = getOrCreateManager(context)
        // Explicitly enables required radios only when file transfer engine is initialized
        mgr.startSenderEngine(onRadioActionNeeded = onRadioActionNeeded)
    }

    fun explicitlyEnableRadios(context: Context, onActionNeeded: ((Intent) -> Unit)? = null) {
        val mgr = getOrCreateManager(context)
        mgr.radioStateManager.explicitlyEnableRequiredRadios(
            enableWifi = true,
            enableBluetooth = true,
            onActionRequired = onActionNeeded
        )
    }

    /**
     * Handles QR code scan: extracts IP, token, PIN, device name,
     * connects via socket, confirms handshake, and initiates stream transfer.
     */
    fun parseAndPairFromQr(context: Context, qrContent: String) {
        val mgr = getOrCreateManager(context)

        var ip = ""
        var port = 8888
        var devName = "Scanned Receiver"
        var token = ""
        var pin = ""

        val raw = qrContent.trim()
        if (raw.startsWith("NOVASHARE_P2P:") || raw.startsWith("NOVASHARE:")) {
            val parts = raw.split(";", ":")
            for (p in parts) {
                if (p.startsWith("IP=")) ip = p.substringAfter("IP=")
                if (p.startsWith("PORT=")) port = p.substringAfter("PORT=").toIntOrNull() ?: 8888
                if (p.startsWith("NAME=") || p.startsWith("DEVICE=")) devName = p.substringAfter("=")
                if (p.startsWith("TOKEN=")) token = p.substringAfter("TOKEN=")
                if (p.startsWith("PIN=")) pin = p.substringAfter("PIN=")
            }
        } else if (raw.contains("http://")) {
            ip = raw.substringAfter("http://").substringBefore(":").substringBefore("/")
        } else if (raw.contains(":")) {
            ip = raw.substringBefore(":")
            port = raw.substringAfter(":").toIntOrNull() ?: 8888
        } else {
            ip = raw
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
                mgr.parseAndConnectFromQr(qrContent, _selectedFiles.value) { session ->
                    _activeSession.value = session
                    if (session.status == TransferStatus.COMPLETED) {
                        recordCompletedTransfers(newDevice.name, ip, _selectedFiles.value, session.speedBytesPerSec)
                    }
                }
            }
        }
    }

    private fun recordCompletedTransfers(deviceName: String, targetIp: String, files: List<SharedFile>, speed: Long) {
        viewModelScope.launch {
            for (file in files) {
                val entity = TransferEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    direction = "SEND",
                    deviceName = deviceName,
                    deviceIp = targetIp,
                    fileName = file.name,
                    filePath = file.path,
                    fileSize = file.size,
                    fileCategory = file.category.name,
                    status = "COMPLETED",
                    speedBytesPerSec = speed,
                    durationMs = 1500,
                    isEncrypted = true,
                    checksumSha256 = "",
                    timestamp = System.currentTimeMillis()
                )
                transferRepository.recordTransfer(entity)
            }
        }
    }

    fun addManualDevice(context: Context, name: String, ipAddress: String) {
        val newDevice = Device(
            id = "manual_${System.currentTimeMillis()}",
            name = if (name.isBlank()) "Device $ipAddress" else name,
            ipAddress = ipAddress.trim(),
            port = 8888,
            type = DeviceType.ANDROID
        )
        val current = _discoveredDevices.value.toMutableList()
        current.removeAll { it.ipAddress == newDevice.ipAddress }
        current.add(0, newDevice)
        _discoveredDevices.value = current
    }

    fun initiateTransfer(context: Context, device: Device) {
        initiateTransferWithHandshake(
            context = context,
            targetIp = device.ipAddress,
            targetPort = device.port,
            qrToken = "",
            pin = "",
            deviceName = device.name
        )
    }

    private fun initiateTransferWithHandshake(
        context: Context,
        targetIp: String,
        targetPort: Int,
        qrToken: String,
        pin: String,
        deviceName: String
    ) {
        if (_selectedFiles.value.isEmpty()) return
        val mgr = getOrCreateManager(context)
        val files = _selectedFiles.value

        // Explicitly ensure radios are on when starting transfer
        mgr.radioStateManager.explicitlyEnableRequiredRadios()

        mgr.startHandshakeAndTransfer(
            targetIp = targetIp,
            targetPort = targetPort,
            qrToken = qrToken,
            pin = pin,
            files = files,
            onProgress = { session ->
                _activeSession.value = session

                if (session.status == TransferStatus.COMPLETED) {
                    recordCompletedTransfers(deviceName, targetIp, files, session.speedBytesPerSec)
                }
            }
        )
    }

    fun cancelTransfer() {
        p2pManager?.cancelActiveTransfer()
        _activeSession.value = null
    }

    override fun onCleared() {
        super.onCleared()
        p2pManager?.stopEngine(restoreOriginalRadios = false)
        p2pManager?.radioStateManager?.unregisterMonitoring()
    }
}
