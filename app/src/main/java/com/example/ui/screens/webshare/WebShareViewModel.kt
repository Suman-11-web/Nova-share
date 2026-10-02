package com.example.ui.screens.webshare

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.TransferEntity
import com.example.data.model.FileCategory
import com.example.data.model.SharedFile
import com.example.data.repository.TransferRepository
import com.example.network.NetworkUtils
import com.example.network.WebServer
import com.example.util.DeviceFileUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class WebShareViewModel(
    private val transferRepository: TransferRepository? = null
) : ViewModel() {

    private var webServer: WebServer? = null

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _webUrl = MutableStateFlow("http://192.168.1.100:8080")
    val webUrl: StateFlow<String> = _webUrl.asStateFlow()

    private val _webSharedFiles = MutableStateFlow<List<SharedFile>>(emptyList())
    val webSharedFiles: StateFlow<List<SharedFile>> = _webSharedFiles.asStateFlow()

    private val _webUploadedFiles = MutableStateFlow<List<SharedFile>>(emptyList())
    val webUploadedFiles: StateFlow<List<SharedFile>> = _webUploadedFiles.asStateFlow()

    private val _uploadNotification = MutableStateFlow<String?>(null)
    val uploadNotification: StateFlow<String?> = _uploadNotification.asStateFlow()

    fun clearUploadNotification() {
        _uploadNotification.value = null
    }

    fun addSharedFiles(files: List<SharedFile>) {
        val current = _webSharedFiles.value.toMutableList()
        current.addAll(files)
        _webSharedFiles.value = current
        webServer?.setSharedFiles(current)
    }

    fun removeSharedFile(fileId: String) {
        val current = _webSharedFiles.value.filter { it.id != fileId }
        _webSharedFiles.value = current
        webServer?.setSharedFiles(current)
    }

    fun toggleWebServer(context: Context) {
        if (_isServerRunning.value) {
            webServer?.stop()
            webServer = null
            _isServerRunning.value = false
        } else {
            val ip = NetworkUtils.getLocalIpAddress(context)
            _webUrl.value = "http://$ip:8080"
            webServer = WebServer(
                context = context,
                port = 8080,
                onFileUploaded = { name, size, file ->
                    handleIncomingWebFile(context, name, size, file)
                }
            ).apply {
                setSharedFiles(_webSharedFiles.value)
                start()
            }
            _isServerRunning.value = true
        }
    }

    private fun handleIncomingWebFile(context: Context, name: String, size: Long, file: File) {
        val cat = DeviceFileUtils.getFileCategoryFromMime("*/*", name)
        val sharedFile = SharedFile(
            id = UUID.randomUUID().toString(),
            name = name,
            path = file.absolutePath,
            size = size,
            category = cat
        )
        val currentUploaded = _webUploadedFiles.value.toMutableList()
        currentUploaded.add(0, sharedFile)
        _webUploadedFiles.value = currentUploaded
        _uploadNotification.value = "Received '$name' (${NetworkUtils.formatFileSize(size)}) from Web Portal"

        val repo = transferRepository ?: TransferRepository(AppDatabase.getDatabase(context).transferDao())
        viewModelScope.launch {
            repo.recordTransfer(
                TransferEntity(
                    id = UUID.randomUUID().toString(),
                    direction = "RECEIVE",
                    deviceName = "Web Browser (PC)",
                    deviceIp = _webUrl.value,
                    fileName = name,
                    filePath = file.absolutePath,
                    fileSize = size,
                    fileCategory = cat.name,
                    status = "COMPLETED",
                    speedBytesPerSec = 15_000_000,
                    durationMs = 800,
                    isEncrypted = false,
                    checksumSha256 = "",
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        webServer?.stop()
        webServer = null
    }
}
