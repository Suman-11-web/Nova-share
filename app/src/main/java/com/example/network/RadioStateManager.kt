package com.example.network

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RadioActivationStatus(
    val wifiEnabled: Boolean,
    val bluetoothEnabled: Boolean,
    val isReadyForTransfer: Boolean,
    val requiresUserAction: Boolean,
    val userActionIntent: Intent? = null,
    val statusMessage: String
)

class RadioStateManager(private val appContext: Context) {

    private val wifiManager = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private val _isWifiActive = MutableStateFlow(checkWifiEnabled())
    val isWifiActive: StateFlow<Boolean> = _isWifiActive.asStateFlow()

    private val _isBluetoothActive = MutableStateFlow(checkBluetoothEnabled())
    val isBluetoothActive: StateFlow<Boolean> = _isBluetoothActive.asStateFlow()

    private val _isRadioReady = MutableStateFlow(checkRadioReady())
    val isRadioReady: StateFlow<Boolean> = _isRadioReady.asStateFlow()

    private val _activeMessage = MutableStateFlow("Radios idle")
    val activeMessage: StateFlow<String> = _activeMessage.asStateFlow()

    // Hardware state history to manage radio lifetime
    private var wasWifiOriginallyEnabled = false
    private var wasBluetoothOriginallyEnabled = false
    private var wifiEnabledByEngine = false
    private var bluetoothEnabledByEngine = false
    private var isMonitoringRegistered = false

    private val radioStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshStates()
        }
    }

    init {
        registerMonitoring()
        refreshStates()
    }

    fun registerMonitoring() {
        if (isMonitoringRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            }
            appContext.registerReceiver(radioStateReceiver, filter)
            isMonitoringRegistered = true
        } catch (e: Exception) {
            Log.e("RadioStateManager", "Failed to register radio receiver", e)
        }
    }

    fun unregisterMonitoring() {
        if (!isMonitoringRegistered) return
        try {
            appContext.unregisterReceiver(radioStateReceiver)
            isMonitoringRegistered = false
        } catch (e: Exception) {
            Log.e("RadioStateManager", "Failed to unregister radio receiver", e)
        }
    }

    fun refreshStates() {
        val wifi = checkWifiEnabled()
        val bt = checkBluetoothEnabled()
        _isWifiActive.value = wifi
        _isBluetoothActive.value = bt
        val ready = wifi || bt || isNetworkConnected()
        _isRadioReady.value = ready
        _activeMessage.value = when {
            wifi && bt -> "Wi-Fi & Bluetooth are active and ready"
            wifi -> "Wi-Fi active for high-speed P2P transfer"
            bt -> "Bluetooth active for device discovery"
            ready -> "Local connection active"
            else -> "Radios disabled - initialize engine to activate"
        }
    }

    /**
     * Explicitly enables required radios ONLY when the file transfer engine is initialized.
     * Manages hardware state responsibly.
     */
    fun explicitlyEnableRequiredRadios(
        enableWifi: Boolean = true,
        enableBluetooth: Boolean = true,
        onActionRequired: ((Intent) -> Unit)? = null
    ): RadioActivationStatus {
        wasWifiOriginallyEnabled = checkWifiEnabled()
        wasBluetoothOriginallyEnabled = checkBluetoothEnabled()

        var wifiActivated = wasWifiOriginallyEnabled
        var btActivated = wasBluetoothOriginallyEnabled
        var intentNeeded: Intent? = null
        val messages = mutableListOf<String>()

        // 1. Explicit Wi-Fi management
        if (enableWifi && !wifiActivated) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                try {
                    @Suppress("DEPRECATION")
                    val success = wifiManager?.setWifiEnabled(true) ?: false
                    if (success) {
                        wifiActivated = true
                        wifiEnabledByEngine = true
                        messages.add("Wi-Fi enabled automatically by engine")
                    }
                } catch (e: Exception) {
                    Log.w("RadioStateManager", "Could not enable Wi-Fi programmatically", e)
                }
            } else {
                // Android 10+ requires system UI interaction for direct Wi-Fi enable
                try {
                    val panelIntent = Intent(Settings.Panel.ACTION_WIFI).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    intentNeeded = panelIntent
                    messages.add("Wi-Fi toggle panel triggered")
                } catch (_: Exception) {
                    val fallbackIntent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    intentNeeded = fallbackIntent
                    messages.add("Wi-Fi settings opened")
                }
            }
        } else if (wifiActivated) {
            messages.add("Wi-Fi radio already active")
        }

        // 2. Explicit Bluetooth management
        if (enableBluetooth && !btActivated) {
            bluetoothAdapter?.let { adapter ->
                val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ContextCompat.checkSelfPermission(
                        appContext,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                } else {
                    ContextCompat.checkSelfPermission(
                        appContext,
                        Manifest.permission.BLUETOOTH_ADMIN
                    ) == PackageManager.PERMISSION_GRANTED
                }

                if (hasPermission) {
                    try {
                        @Suppress("DEPRECATION")
                        val success = adapter.enable()
                        if (success) {
                            btActivated = true
                            bluetoothEnabledByEngine = true
                            messages.add("Bluetooth radio enabled by engine")
                        } else {
                            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            if (intentNeeded == null) intentNeeded = enableBtIntent
                        }
                    } catch (e: Exception) {
                        Log.w("RadioStateManager", "Bluetooth programmatic enable failed", e)
                    }
                } else {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (intentNeeded == null) intentNeeded = enableBtIntent
                }
            }
        } else if (btActivated) {
            messages.add("Bluetooth radio active")
        }

        refreshStates()

        if (intentNeeded != null && onActionRequired != null) {
            onActionRequired(intentNeeded)
        }

        val ready = checkWifiEnabled() || checkBluetoothEnabled() || isNetworkConnected()
        return RadioActivationStatus(
            wifiEnabled = checkWifiEnabled(),
            bluetoothEnabled = checkBluetoothEnabled(),
            isReadyForTransfer = ready,
            requiresUserAction = intentNeeded != null && !ready,
            userActionIntent = intentNeeded,
            statusMessage = messages.joinToString("; ")
        )
    }

    /**
     * Clean up and restore radio states when engine is shut down or idle.
     */
    fun releaseRadiosOnEngineShutdown(restoreOriginalState: Boolean = false) {
        if (restoreOriginalState) {
            if (wifiEnabledByEngine && !wasWifiOriginallyEnabled) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    try {
                        @Suppress("DEPRECATION")
                        wifiManager?.setWifiEnabled(false)
                    } catch (_: Exception) {}
                }
                wifiEnabledByEngine = false
            }

            if (bluetoothEnabledByEngine && !wasBluetoothOriginallyEnabled) {
                val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ContextCompat.checkSelfPermission(
                        appContext,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }
                if (hasPermission) {
                    try {
                        @Suppress("DEPRECATION")
                        bluetoothAdapter?.disable()
                    } catch (_: Exception) {}
                }
                bluetoothEnabledByEngine = false
            }
        }
        refreshStates()
    }

    private fun checkWifiEnabled(): Boolean {
        return try {
            wifiManager?.isWifiEnabled ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun checkBluetoothEnabled(): Boolean {
        return try {
            bluetoothAdapter?.isEnabled ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun checkRadioReady(): Boolean {
        return checkWifiEnabled() || checkBluetoothEnabled() || isNetworkConnected()
    }

    private fun isNetworkConnected(): Boolean {
        return try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (_: Exception) {
            false
        }
    }
}
