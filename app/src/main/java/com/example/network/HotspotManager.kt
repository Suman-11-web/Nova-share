package com.example.network

import android.content.Context
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HotspotInfo(
    val isActive: Boolean,
    val ssid: String = "",
    val passphrase: String = "",
    val gatewayIp: String = "192.168.43.1",
    val statusMessage: String = "Hotspot Idle"
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
     * Receiver side: Starts an isolated, high-speed private Wi-Fi SoftAP on the device.
     */
    fun startLocalOnlyHotspot(
        onSuccess: (ssid: String, password: String, ip: String) -> Unit = { _, _, _ -> },
        onError: (String) -> Unit = {}
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            val msg = "Local Hotspot requires Android 8.0+"
            _hotspotInfo.value = HotspotInfo(isActive = false, statusMessage = msg)
            onError(msg)
            return
        }

        if (hotspotReservation != null) {
            val info = _hotspotInfo.value
            onSuccess(info.ssid, info.passphrase, info.gatewayIp)
            return
        }

        try {
            wifiManager?.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation?) {
                    super.onStarted(reservation)
                    hotspotReservation = reservation
                    if (reservation == null) {
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
                        statusMessage = "Offline Private Hotspot Active"
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
                        ERROR_GENERIC -> "Generic hotspot failure"
                        ERROR_INCOMPATIBLE_MODE -> "Incompatible Wi-Fi mode"
                        ERROR_TETHERING_DISALLOWED -> "Tethering disallowed by device policy"
                        else -> "Failed with code $reason"
                    }
                    _hotspotInfo.value = HotspotInfo(isActive = false, statusMessage = reasonStr)
                    mainHandler.post { onError(reasonStr) }
                }
            }, mainHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting Local Hotspot", e)
            val msg = e.message ?: "Could not start hotspot"
            _hotspotInfo.value = HotspotInfo(isActive = false, statusMessage = msg)
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
