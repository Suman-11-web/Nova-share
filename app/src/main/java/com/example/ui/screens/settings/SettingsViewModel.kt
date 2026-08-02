package com.example.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.SettingsRepository
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _deviceName = MutableStateFlow("Pixel 8 Pro")
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    private val _themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _autoAccept = MutableStateFlow(true)
    val autoAccept: StateFlow<Boolean> = _autoAccept.asStateFlow()

    private val _encryptionEnabled = MutableStateFlow(true)
    val encryptionEnabled: StateFlow<Boolean> = _encryptionEnabled.asStateFlow()

    private val _bandwidthLimit = MutableStateFlow(0) // 0 means unlimited
    val bandwidthLimit: StateFlow<Int> = _bandwidthLimit.asStateFlow()

    // Analytics stats
    val totalSentBytes = MutableStateFlow(142L * 1024 * 1024 * 1024)
    val totalReceivedBytes = MutableStateFlow(89L * 1024 * 1024 * 1024)
    val totalCompletedTransfers = MutableStateFlow(128)

    fun setDeviceName(name: String) {
        _deviceName.value = name
        viewModelScope.launch { settingsRepository.setDeviceName(name) }
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
