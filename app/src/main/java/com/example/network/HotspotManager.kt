package com.example.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HotspotInfo(
    val isActive: Boolean,
    val ssid: String = "",
    val passphrase: String = "",
    val gatewayIp: String = "192.168.43.1",
    val statusMessage: String = "Hotspot Idle",
    val isFallbackAvailable: Boolean = false
)

class HotspotManager(private val context: Context) {

    companion object {
        const val TAG = "NovaHotspotManager"
    }

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var hotspotReservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var activeNetworkCallback: ConnectivityManager.NetworkCallback? = null

    private val _hotspotInfo = MutableStateFlow(HotspotInfo(isActive = false))
    val hotspotInfo: StateFlow<HotspotInfo> = _hotspotInfo.asStateFlow()

    private val _isConnectingToHotspot = MutableStateFlow(false)
    val isConnectingToHotspot: StateFlow<Boolean> = _isConnectingToHotspot.asStateFlow()

    /**
     * Returns an intent to open the device's Tethering & Hotspot settings
     */
    fun getHotspotSettingsIntent(): android.content.Intent {
        val tetherIntent = android.content.Intent("android.settings.TETHER_SETTINGS").apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return if (tetherIntent.resolveActivity(context.packageManager) != null) {
            tetherIntent
        } else {
            android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
    }

    /**
     * Checks if standard Android Wi-Fi Hotspot / Tethering interface is currently active
     */
    fun isSystemTetheringActive(): Boolean {
        return try {
            val ip = NetworkUtils.getLocalIpAddress(context)
            ip.startsWith("192.168.43.") || ip.startsWith("192.168.49.") || ip.startsWith("192.168.50.")
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Receiver side: Starts an isolated, high-speed private Wi-Fi SoftAP on the device.
     */
    fun startLocalOnlyHotspot(
        onSuccess: (ssid: String, password: String, ip: String) -> Unit = { _, _, _ -> },
        onError: (String) -> Unit = {}
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            val msg = "Local Hotspot requires Android 8.0+"
            _hotspotInfo.value = HotspotInfo(isActive = false, statusMessage = msg, isFallbackAvailable = true)
            onError(msg)
            return
        }

        if (wifiManager == null) {
            val msg = "Wi-Fi is not supported on this device"
            _hotspotInfo.value = HotspotInfo(isActive = false, statusMessage = msg, isFallbackAvailable = true)
            onError(msg)
            return
        }

        // On Android, Wi-Fi hardware MUST be enabled for LocalOnlyHotspot to start.
        if (!wifiManager.isWifiEnabled) {
            try {
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = true
            } catch (e: Exception) {
                Log.w(TAG, "Could not automatically turn on Wi-Fi for hotspot: ${e.message}")
            }
        }

        // Validate runtime permissions before calling system API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val nearbyGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED

            if (!nearbyGranted) {
                val msg = "Nearby Devices permission is required to start Local Hotspot"
                Log.w(TAG, msg)
                _hotspotInfo.value = HotspotInfo(isActive = false, statusMessage = msg, isFallbackAvailable = true)
                onError(msg)
                return
            }
        }

        val locationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && !locationGranted) {
            val msg = "Location permission is required to start Local Hotspot"
            Log.w(TAG, msg)
            _hotspotInfo.value = HotspotInfo(isActive = false, statusMessage = msg, isFallbackAvailable = true)
            onError(msg)
            return
        }

        // Check if system tethering hotspot is already running
        if (isSystemTetheringActive()) {
            val gatewayIp = NetworkUtils.getLocalIpAddress(context)
            val info = HotspotInfo(
                isActive = true,
                ssid = "System_Hotspot",
                passphrase = "",
                gatewayIp = gatewayIp,
                statusMessage = "System Hotspot Active ($gatewayIp)",
                isFallbackAvailable = false
            )
            _hotspotInfo.value = info
            onSuccess(info.ssid, info.passphrase, info.gatewayIp)
            return
        }

        if (hotspotReservation != null) {
            val info = _hotspotInfo.value
            onSuccess(info.ssid, info.passphrase, info.gatewayIp)
            return
        }

        try {
            wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation?) {
                    super.onStarted(reservation)
                    hotspotReservation = reservation
                    if (reservation == null) {
                        _hotspotInfo.value = HotspotInfo(isActive = false, statusMessage = "Reservation was null", isFallbackAvailable = true)
                        onError("Reservation was null")
                        return
                    }

                    var ssid = "NovaShare_Direct"
                    var password = ""

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            val config = reservation.softApConfiguration
                            ssid = config.ssid ?: ssid
                            password = config.passphrase ?: ""
                        } catch (_: Exception) {
                            @Suppress("DEPRECATION")
                            val config = reservation.wifiConfiguration
                            ssid = config?.SSID ?: ssid
                            password = config?.preSharedKey ?: ""
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        val config = reservation.wifiConfiguration
                        ssid = config?.SSID ?: ssid
                        password = config?.preSharedKey ?: ""
                    }

                    val gatewayIp = NetworkUtils.getLocalIpAddress(context).let {
                        if (it != "127.0.0.1" && it != "192.168.1.100") it else "192.168.43.1"
                    }

                    Log.d(TAG, "Local Hotspot started: SSID=$ssid, Password=$password, IP=$gatewayIp")
                    _hotspotInfo.value = HotspotInfo(
                        isActive = true,
                        ssid = ssid,
                        passphrase = password,
                        gatewayIp = gatewayIp,
                        statusMessage = "Offline Private Hotspot Active",
                        isFallbackAvailable = false
                    )
                    mainHandler.post { onSuccess(ssid, password, gatewayIp) }
                }

                override fun onStopped() {
                    super.onStopped()
                    hotspotReservation = null
                    _hotspotInfo.value = HotspotInfo(isActive = false, statusMessage = "Hotspot Stopped")
                    Log.d(TAG, "Local Hotspot stopped")
                }

                override fun onFailed(reason: Int) {
                    super.onFailed(reason)
                    hotspotReservation = null
                    val reasonStr = when (reason) {
                        ERROR_NO_CHANNEL -> "No frequency channel available"
                        ERROR_GENERIC -> "Generic hotspot failure (Try system hotspot settings)"
                        ERROR_INCOMPATIBLE_MODE -> "Incompatible Wi-Fi mode (Try system hotspot settings)"
                        ERROR_TETHERING_DISALLOWED -> "Tethering disallowed by device policy"
                        else -> "Hotspot failed with code $reason"
                    }
                    _hotspotInfo.value = HotspotInfo(isActive = false, statusMessage = reasonStr, isFallbackAvailable = true)
                    mainHandler.post { onError(reasonStr) }
                }
            }, mainHandler)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting Local Hotspot - nearby devices/location permission missing", e)
            val msg = "Permission denied: Nearby Devices / Location required"
            _hotspotInfo.value = HotspotInfo(isActive = false, statusMessage = msg, isFallbackAvailable = true)
            onError(msg)
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting Local Hotspot", e)
            val msg = e.message ?: "Could not start hotspot"
            _hotspotInfo.value = HotspotInfo(isActive = false, statusMessage = msg, isFallbackAvailable = true)
            onError(msg)
        }
    }

    fun stopLocalHotspot() {
        try {
            hotspotReservation?.close()
            hotspotReservation = null
            _hotspotInfo.value = HotspotInfo(isActive = false, statusMessage = "Hotspot Idle")
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping hotspot", e)
        }
    }

    /**
     * Sender side: Automatically connects to Receiver's Hotspot without user manually entering Wi-Fi settings!
     */
    fun connectToHotspot(
        ssid: String,
        passphrase: String,
        onConnected: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (ssid.isBlank()) {
            onError("SSID cannot be blank")
            return
        }

        _isConnectingToHotspot.value = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                // Unbind any previous network
                disconnectFromHotspot()

                val specifier = WifiNetworkSpecifier.Builder()
                    .setSsid(ssid)
                    .apply {
                        if (passphrase.isNotBlank()) {
                            setWpa2Passphrase(passphrase)
                        }
                    }
                    .build()

                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .setNetworkSpecifier(specifier)
                    .build()

                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        super.onAvailable(network)
                        Log.d(TAG, "Connected to Hotspot: $ssid. Binding process to network.")
                        connectivityManager?.bindProcessToNetwork(network)
                        _isConnectingToHotspot.value = false
                        mainHandler.post { onConnected() }
                    }

                    override fun onUnavailable() {
                        super.onUnavailable()
                        _isConnectingToHotspot.value = false
                        mainHandler.post { onError("Hotspot network unavailable") }
                    }

                    override fun onLost(network: Network) {
                        super.onLost(network)
                        Log.d(TAG, "Lost connection to Hotspot: $ssid")
                        disconnectFromHotspot()
                    }
                }

                activeNetworkCallback = callback
                connectivityManager?.requestNetwork(request, callback, 15000)
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to Hotspot via NetworkSpecifier", e)
                _isConnectingToHotspot.value = false
                onError(e.message ?: "Failed to connect to hotspot")
            }
        } else {
            // Android 9 and below
            try {
                @Suppress("DEPRECATION")
                val wifiConfig = WifiConfiguration().apply {
                    SSID = "\"$ssid\""
                    if (passphrase.isNotBlank()) {
                        preSharedKey = "\"$passphrase\""
                    } else {
                        allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                    }
                }
                @Suppress("DEPRECATION")
                val netId = wifiManager?.addNetwork(wifiConfig) ?: -1
                if (netId != -1) {
                    @Suppress("DEPRECATION")
                    wifiManager?.disconnect()
                    @Suppress("DEPRECATION")
                    wifiManager?.enableNetwork(netId, true)
                    @Suppress("DEPRECATION")
                    wifiManager?.reconnect()
                    _isConnectingToHotspot.value = false
                    mainHandler.postDelayed({ onConnected() }, 2000)
                } else {
                    _isConnectingToHotspot.value = false
                    onError("Could not configure Wi-Fi network")
                }
            } catch (e: Exception) {
                _isConnectingToHotspot.value = false
                onError(e.message ?: "Error on legacy Wi-Fi connect")
            }
        }
    }

    fun disconnectFromHotspot() {
        try {
            activeNetworkCallback?.let {
                connectivityManager?.unregisterNetworkCallback(it)
                activeNetworkCallback = null
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                connectivityManager?.bindProcessToNetwork(null)
            }
            _isConnectingToHotspot.value = false
        } catch (e: Exception) {
            Log.w(TAG, "Error disconnecting hotspot callback", e)
        }
    }
}
