package com.example.ui.screens.webshare

import android.content.Context
import androidx.lifecycle.ViewModel
import com.example.data.model.FileCategory
import com.example.data.model.SharedFile
import com.example.network.NetworkUtils
import com.example.network.WebServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WebShareViewModel : ViewModel() {

    private var webServer: WebServer? = null

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _webUrl = MutableStateFlow("http://192.168.1.100:8080")
    val webUrl: StateFlow<String> = _webUrl.asStateFlow()

    private val _webSharedFiles = MutableStateFlow<List<SharedFile>>(emptyList())
    val webSharedFiles: StateFlow<List<SharedFile>> = _webSharedFiles.asStateFlow()

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
            webServer = WebServer(context = context, port = 8080).apply {
                setSharedFiles(_webSharedFiles.value)
                start()
            }
            _isServerRunning.value = true
        }
    }
}
