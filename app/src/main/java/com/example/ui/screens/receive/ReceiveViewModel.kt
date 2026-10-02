package com.example.ui.screens.receive

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.TransferEntity
import com.example.data.model.*
import com.example.data.repository.TransferRepository
import com.example.network.NetworkUtils
import com.example.network.P2PConnectivityManager
import com.example.network.RadioStateManager
import com.example.network.TransferService
import com.example.util.NovaShareStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StoredFile(
    val file: java.io.File,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val category: FileCategory
)

class ReceiveViewModel(
    private val transferRepository: TransferRepository? = null
) : ViewModel() {

    private var p2pManager: P2PConnectivityManager? = null
    private var pendingDecisionCallback: ((Boolean) -> Unit)? = null

    private val _storedFiles = MutableStateFlow<List<StoredFile>>(emptyList())
    val storedFiles: StateFlow<List<StoredFile>> = _storedFiles.asStateFlow()

    private val _localIp = MutableStateFlow("127.0.0.1")
    val localIp: StateFlow<String> = _localIp.asStateFlow()

    private val _pairingPin = MutableStateFlow("123456")
    val pairingPin: StateFlow<String> = _pairingPin.asStateFlow()

    private val _qrDataString = MutableStateFlow("")
    val qrDataString: StateFlow<String> = _qrDataString.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _incomingSession = MutableStateFlow<TransferSession?>(null)
    val incomingSession: StateFlow<TransferSession?> = _incomingSession.asStateFlow()

    private val _pendingRequest = MutableStateFlow<TransferSession?>(null)
    val pendingRequest: StateFlow<TransferSession?> = _pendingRequest.asStateFlow()

    private val _isWifiActive = MutableStateFlow(false)
    val isWifiActive: StateFlow<Boolean> = _isWifiActive.asStateFlow()

    private val _isBluetoothActive = MutableStateFlow(false)
    val isBluetoothActive: StateFlow<Boolean> = _isBluetoothActive.asStateFlow()

    private val _isHotspotMode = MutableStateFlow(false)
    val isHotspotMode: StateFlow<Boolean> = _isHotspotMode.asStateFlow()

    private val _hotspotInfo = MutableStateFlow(com.example.network.HotspotInfo(isActive = false))
    val hotspotInfo: StateFlow<com.example.network.HotspotInfo> = _hotspotInfo.asStateFlow()

    private val _radioMessage = MutableStateFlow("Radios idle")
    val radioMessage: StateFlow<String> = _radioMessage.asStateFlow()

    private fun getOrCreateManager(context: Context): P2PConnectivityManager {
        if (p2pManager == null) {
            p2pManager = P2PConnectivityManager(context.applicationContext)
            // Hook up reactive radio states
            p2pManager?.let { mgr ->
                viewModelScope.launch {
                    mgr.radioStateManager.isWifiActive.collect { _isWifiActive.value = it }
                }
                viewModelScope.launch {
                    mgr.radioStateManager.isBluetoothActive.collect { _isBluetoothActive.value = it }
                }
                viewModelScope.launch {
                    mgr.isHotspotMode.collect { 
                        _isHotspotMode.value = it
                        refreshQrString(context)
                    }
                }
                viewModelScope.launch {
                    mgr.hotspotManager.hotspotInfo.collect { 
                        _hotspotInfo.value = it
                        refreshQrString(context)
                    }
                }
                viewModelScope.launch {
                    mgr.radioStateManager.activeMessage.collect { _radioMessage.value = it }
                }
                viewModelScope.launch {
                    mgr.currentPin.collect {
                        _pairingPin.value = it
                        refreshQrString(context)
                    }
                }
            }
        }
        return p2pManager!!
    }

    fun updateLocalIp(context: Context) {
        val ip = NetworkUtils.getLocalIpAddress(context)
        _localIp.value = ip
        val mgr = getOrCreateManager(context)
        mgr.radioStateManager.refreshStates()
        refreshQrString(context)
        refreshStoredFiles(context)

        if (!_isListening.value) {
            startListening(context)
        }
    }

    private fun refreshQrString(context: Context) {
        p2pManager?.let { mgr ->
            _qrDataString.value = mgr.generateQrPayload()
        } ?: run {
            val ip = NetworkUtils.getLocalIpAddress(context)
            val dev = NetworkUtils.getDeviceModelName()
            _qrDataString.value = "NOVASHARE_P2P:v2;IP=$ip;PORT=8888;NAME=$dev;TOKEN=tok_${System.currentTimeMillis()};PIN=${_pairingPin.value}"
        }
    }

    fun generateNewPin() {
        p2pManager?.rotateSessionCredentials()
    }

    fun toggleHotspot(context: Context) {
        val mgr = getOrCreateManager(context)
        mgr.toggleHotspotMode {
            refreshQrString(context)
        }
    }

    fun explicitlyEnableRadios(context: Context, onActionNeeded: ((Intent) -> Unit)? = null) {
        val mgr = getOrCreateManager(context)
        mgr.radioStateManager.explicitlyEnableRequiredRadios(
            enableWifi = true,
            enableBluetooth = true,
            onActionRequired = onActionNeeded
        )
        updateLocalIp(context)
    }

    fun toggleListening(context: Context) {
        if (_isListening.value) {
            stopListening()
        } else {
            startListening(context)
        }
    }

    fun startListening(context: Context, onRadioActionNeeded: ((Intent) -> Unit)? = null) {
        val mgr = getOrCreateManager(context)
        val saveDir = NovaShareStorage.getNovaShareDirectory(context)

        mgr.onTransferRequested = { session, decisionCallback ->
            pendingDecisionCallback = decisionCallback
            _pendingRequest.value = session
            _incomingSession.value = session

            // Show real heads-up system notification with Accept & Decline actions
            TransferService.showTransferRequest(
                context = context,
                senderName = session.deviceName,
                filesCount = session.files.size,
                totalSizeFormatted = NetworkUtils.formatFileSize(session.totalBytes)
            )
        }

        TransferService.onAcceptRequested = {
            acceptTransfer()
        }
        TransferService.onDeclineRequested = {
            declineTransfer()
        }

        mgr.onTransferSessionUpdated = { session ->
            _incomingSession.value = session

            if (session.status == TransferStatus.COMPLETED) {
                TransferService.dismissTransferRequest(context)
                TransferService.showTransferCompleted(
                    context = context,
                    title = "Files Received Successfully",
                    message = "Received ${session.files.size} file(s) from ${session.deviceName}"
                )

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
                    durationMs = 1500,
                    isEncrypted = session.isEncrypted,
                    checksumSha256 = "",
                    timestamp = System.currentTimeMillis()
                )
                viewModelScope.launch {
                    transferRepository?.recordTransfer(entity)
                    refreshStoredFiles(context)
                }
            } else if (session.status == TransferStatus.DECLINED || session.status == TransferStatus.FAILED) {
                TransferService.dismissTransferRequest(context)
            }
        }

        // Explicitly enables required radios only when file transfer engine is initialized
        mgr.startReceiverEngine(onRadioActionNeeded = onRadioActionNeeded)
        _isListening.value = true
        refreshQrString(context)
        refreshStoredFiles(context)
    }

    fun refreshStoredFiles(context: Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val saveDir = NovaShareStorage.getNovaShareDirectory(context)
            val filesList: List<java.io.File> = saveDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") } ?: emptyList()
            val list = filesList.sortedByDescending { it.lastModified() }.map { file ->
                val ext = file.extension.lowercase()
                val cat = when {
                    ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "svg") -> FileCategory.IMAGES
                    ext in listOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "flv") -> FileCategory.VIDEOS
                    ext in listOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "opus") -> FileCategory.AUDIO
                    ext in listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "epub") -> FileCategory.DOCUMENTS
                    ext == "apk" -> FileCategory.APK
                    else -> FileCategory.ALL
                }
                StoredFile(
                    file = file,
                    name = file.name,
                    size = file.length(),
                    lastModified = file.lastModified(),
                    category = cat
                )
            }
            _storedFiles.value = list
        }
    }

    fun deleteStoredFile(context: Context, storedFile: StoredFile) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (storedFile.file.exists()) {
                    storedFile.file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            refreshStoredFiles(context)
        }
    }

    fun openStoredFile(context: Context, storedFile: StoredFile) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                storedFile.file
            )
            val mime = android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(storedFile.file.extension.lowercase()) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Could not open file: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun shareStoredFile(context: Context, storedFile: StoredFile) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                storedFile.file
            )
            val mime = android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(storedFile.file.extension.lowercase()) ?: "*/*"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share ${storedFile.name}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Could not share file: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun getHotspotSettingsIntent(context: Context): Intent {
        return getOrCreateManager(context).hotspotManager.getHotspotSettingsIntent()
    }

    fun acceptTransfer() {
        p2pManager?.let { mgr: P2PConnectivityManager ->
            TransferService.dismissTransferRequest(mgr.context)
        }
        pendingDecisionCallback?.invoke(true)
        pendingDecisionCallback = null
        _pendingRequest.value = null
    }

    fun declineTransfer() {
        p2pManager?.let { mgr: P2PConnectivityManager ->
            TransferService.dismissTransferRequest(mgr.context)
        }
        pendingDecisionCallback?.invoke(false)
        pendingDecisionCallback = null
        _pendingRequest.value = null
        _incomingSession.value = null
    }

    fun stopListening() {
        p2pManager?.stopEngine(restoreOriginalRadios = false)
        _isListening.value = false
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
        p2pManager?.radioStateManager?.unregisterMonitoring()
    }
}
