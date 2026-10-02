package com.example.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.SettingsRepository
import com.example.data.repository.TransferRepository
import com.example.ui.theme.AppThemeMode
import com.example.util.DeviceFinderEngine
import com.example.util.RealDeviceStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val transferRepository: TransferRepository,
    context: Context
) : ViewModel() {

    private val appContext = context.applicationContext

    private val _deviceName = MutableStateFlow(DeviceFinderEngine.resolveDeviceName(appContext))
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    private val _themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _autoAccept = MutableStateFlow(true)
    val autoAccept: StateFlow<Boolean> = _autoAccept.asStateFlow()

    private val _encryptionEnabled = MutableStateFlow(true)
    val encryptionEnabled: StateFlow<Boolean> = _encryptionEnabled.asStateFlow()

    private val _bandwidthLimit = MutableStateFlow(0) // 0 means unlimited
    val bandwidthLimit: StateFlow<Int> = _bandwidthLimit.asStateFlow()

    // Real Live Analytics stats from Room Database
    private val _totalSentBytes = MutableStateFlow(0L)
    val totalSentBytes: StateFlow<Long> = _totalSentBytes.asStateFlow()

    private val _totalReceivedBytes = MutableStateFlow(0L)
    val totalReceivedBytes: StateFlow<Long> = _totalReceivedBytes.asStateFlow()

    private val _totalCompletedTransfers = MutableStateFlow(0)
    val totalCompletedTransfers: StateFlow<Int> = _totalCompletedTransfers.asStateFlow()

    // Real Physical Device Storage Breakdown
    private val _deviceStorage = MutableStateFlow<RealDeviceStorage?>(null)
    val deviceStorage: StateFlow<RealDeviceStorage?> = _deviceStorage.asStateFlow()

    init {
        // Collect persistent settings
        viewModelScope.launch {
            settingsRepository.deviceName.collect { name ->
                _deviceName.value = name
            }
        }
        viewModelScope.launch {
            settingsRepository.themeMode.collect { mode ->
                _themeMode.value = mode
            }
        }
        viewModelScope.launch {
            settingsRepository.autoAcceptTrusted.collect { enabled ->
                _autoAccept.value = enabled
            }
        }
        viewModelScope.launch {
            settingsRepository.isEncryptionEnabled.collect { enabled ->
                _encryptionEnabled.value = enabled
            }
        }
        viewModelScope.launch {
            settingsRepository.bandwidthLimitMb.collect { limit ->
                _bandwidthLimit.value = limit
            }
        }

        // Collect REAL Room transfer analytics
        viewModelScope.launch {
            transferRepository.totalBytesSent.collect { sent ->
                _totalSentBytes.value = sent ?: 0L
            }
        }
        viewModelScope.launch {
            transferRepository.totalBytesReceived.collect { received ->
                _totalReceivedBytes.value = received ?: 0L
            }
        }
        viewModelScope.launch {
            transferRepository.totalCompletedCount.collect { count ->
                _totalCompletedTransfers.value = count
            }
        }

        // Query real physical device storage stats
        refreshStorageStats()
    }

    fun refreshStorageStats() {
        viewModelScope.launch(Dispatchers.IO) {
            val stats = DeviceFinderEngine.queryDeviceStorage(appContext)
            _deviceStorage.value = stats
        }
    }

    fun setDeviceName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotBlank()) {
            _deviceName.value = trimmed
            viewModelScope.launch { settingsRepository.setDeviceName(trimmed) }
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun toggleAutoAccept() {
        val newval = !_autoAccept.value
        _autoAccept.value = newval
        viewModelScope.launch { settingsRepository.setAutoAcceptTrusted(newval) }
    }

    fun toggleEncryption() {
        val newval = !_encryptionEnabled.value
        _encryptionEnabled.value = newval
        viewModelScope.launch { settingsRepository.setEncryptionEnabled(newval) }
    }

    fun setBandwidthLimit(limitMb: Int) {
        _bandwidthLimit.value = limitMb
        viewModelScope.launch { settingsRepository.setBandwidthLimitMb(limitMb) }
    }
}
