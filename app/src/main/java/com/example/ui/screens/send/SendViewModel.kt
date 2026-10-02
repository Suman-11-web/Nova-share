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
import com.example.network.TransferService
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

    private val _pickedFiles = MutableStateFlow<List<SharedFile>>(emptyList())
    val pickedFiles: StateFlow<List<SharedFile>> = _pickedFiles.asStateFlow()

    // Backward-compatible alias for existing references
    val availableFiles: StateFlow<List<SharedFile>> = _pickedFiles.asStateFlow()

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

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun clearUserMessage() {
        _userMessage.value = null
    }

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
            // When user taps Scan, scan recent downloaded/received files to suggest for sending
            val scanned = DeviceFileUtils.queryMediaStoreFiles(context, FileCategory.ALL).take(20)
            val current = _pickedFiles.value.toMutableList()
            for (f in scanned) {
                if (current.none { it.id == f.id || (it.name == f.name && it.size == f.size) }) {
                    current.add(f)
                }
            }
            _pickedFiles.value = current
            // Auto select newly scanned
            val selected = _selectedFiles.value.toMutableList()
            selected.addAll(scanned)
            _selectedFiles.value = selected.distinctBy { it.id }
        }
    }

    fun addPickedUris(context: Context, uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val parsedFiles = DeviceFileUtils.parsePickedUris(context, uris)
            val updated = (_pickedFiles.value + parsedFiles).distinctBy { it.id }
            _pickedFiles.value = updated

            val currentSelected = _selectedFiles.value.toMutableList()
            currentSelected.addAll(parsedFiles)
            _selectedFiles.value = currentSelected.distinctBy { it.id }
        }
    }

    fun removePickedFile(file: SharedFile) {
        _pickedFiles.value = _pickedFiles.value.filter { it.id != file.id }
        _selectedFiles.value = _selectedFiles.value.filter { it.id != file.id }
    }

    fun clearAllPickedFiles() {
        _pickedFiles.value = emptyList()
        _selectedFiles.value = emptyList()
    }

    fun selectAllPickedFiles() {
        _selectedFiles.value = _pickedFiles.value
    }

    fun deselectAllPickedFiles() {
        _selectedFiles.value = emptyList()
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
        val p2pPart = if (raw.contains(";;")) raw.substringAfter(";;") else raw
        if (p2pPart.startsWith("NOVASHARE_P2P:") || p2pPart.startsWith("NOVASHARE:")) {
            val payload = if (p2pPart.startsWith("NOVASHARE_P2P:v2;")) p2pPart.removePrefix("NOVASHARE_P2P:v2;")
            else if (p2pPart.startsWith("NOVASHARE_P2P:")) p2pPart.removePrefix("NOVASHARE_P2P:")
            else p2pPart.removePrefix("NOVASHARE:")

            val parts = payload.split(";")
            for (p in parts) {
                val kv = p.split("=", limit = 2)
                if (kv.size == 2) {
                    when (kv[0].trim().uppercase()) {
                        "IP" -> ip = kv[1].trim()
                        "PORT" -> port = kv[1].trim().toIntOrNull() ?: 8888
                        "NAME", "DEVICE" -> devName = kv[1].trim()
                        "TOKEN" -> token = kv[1].trim()
                        "PIN" -> pin = kv[1].trim()
                    }
                }
            }
        } else if (p2pPart.startsWith("novashare://")) {
            val uri = Uri.parse(p2pPart)
            ip = uri.getQueryParameter("ip") ?: ""
            port = uri.getQueryParameter("port")?.toIntOrNull() ?: 8888
            devName = uri.getQueryParameter("name") ?: "Scanned Receiver"
            token = uri.getQueryParameter("token") ?: ""
            pin = uri.getQueryParameter("pin") ?: ""
        } else if (p2pPart.contains("http://")) {
            ip = p2pPart.substringAfter("http://").substringBefore(":").substringBefore("/")
        } else if (p2pPart.contains(":")) {
            ip = p2pPart.substringBefore(":")
            port = p2pPart.substringAfter(":").toIntOrNull() ?: 8888
        } else {
            ip = p2pPart.trim()
        }

        if (ip.isNotBlank()) {
            val newDevice = Device(
                id = "qr_${System.currentTimeMillis()}",
                name = if (devName.isNotBlank() && devName != "Scanned Receiver") devName else "Receiver ($ip)",
                ipAddress = ip,
                port = port,
                type = DeviceType.ANDROID,
                qrToken = token,
                pairingPin = pin
            )
            val current = _discoveredDevices.value.toMutableList()
            current.removeAll { it.ipAddress == newDevice.ipAddress }
            current.add(0, newDevice)
            _discoveredDevices.value = current
            _selectedDevice.value = newDevice

            if (_selectedFiles.value.isNotEmpty()) {
                mgr.parseAndConnectFromQr(qrContent, _selectedFiles.value) { session ->
                    _activeSession.value = session
                    if (session.status == TransferStatus.COMPLETED) {
                        TransferService.showTransferCompleted(
                            context = context,
                            title = "Files Sent Successfully",
                            message = "Sent ${_selectedFiles.value.size} file(s) to ${newDevice.name}"
                        )
                        recordCompletedTransfers(newDevice.name, ip, _selectedFiles.value, session.speedBytesPerSec)
                    }
                }
            } else {
                _userMessage.value = "Paired with ${newDevice.name}! Select files below, then tap Send to transfer."
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
        _selectedDevice.value = newDevice
    }

    private val _selectedDevice = MutableStateFlow<Device?>(null)
    val selectedDevice: StateFlow<Device?> = _selectedDevice.asStateFlow()

    fun selectTargetDevice(device: Device) {
        _selectedDevice.value = device
    }

    fun initiateTransfer(context: Context, device: Device) {
        _selectedDevice.value = device
        if (_selectedFiles.value.isEmpty()) {
            _userMessage.value = "Select files above first to send to ${device.name}"
            return
        }
        initiateTransferWithHandshake(
            context = context,
            targetIp = device.ipAddress,
            targetPort = device.port,
            qrToken = device.qrToken,
            pin = device.pairingPin,
            deviceName = device.name
        )
    }

    fun initiateTransferToSelectedDevice(context: Context) {
        val device = _selectedDevice.value
        if (device == null) {
            _userMessage.value = "Please select or pair a receiver device first"
            return
        }
        initiateTransfer(context, device)
    }

    private fun initiateTransferWithHandshake(
        context: Context,
        targetIp: String,
        targetPort: Int,
        qrToken: String,
        pin: String,
        deviceName: String
    ) {
        if (_selectedFiles.value.isEmpty()) {
            _userMessage.value = "Select files above first"
            return
        }
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
                    TransferService.showTransferCompleted(
                        context = context,
                        title = "Files Sent Successfully",
                        message = "Sent ${files.size} file(s) to $deviceName"
                    )
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
