package com.example.ui.screens.receive

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.network.NetworkUtils
import com.example.ui.components.QRCodeCanvas
import com.example.ui.components.TransferProgressCard
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveScreen(
    viewModel: ReceiveViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val hotspotPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val nearbyOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.NEARBY_WIFI_DEVICES] == true
        } else true

        val locationOk = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (nearbyOk && (locationOk || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)) {
            viewModel.toggleHotspot(context)
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Nearby Devices permission is required for Offline Hotspot mode")
            }
        }
    }

    var wifiEnabled by remember { mutableStateOf(NetworkUtils.isWifiEnabled(context)) }
    var bluetoothEnabled by remember { mutableStateOf(NetworkUtils.isBluetoothEnabled()) }

    LaunchedEffect(Unit) {
        viewModel.updateLocalIp(context)
        wifiEnabled = NetworkUtils.isWifiEnabled(context)
        bluetoothEnabled = NetworkUtils.isBluetoothEnabled()
    }

    val localIp by viewModel.localIp.collectAsState()
    val pairingPin by viewModel.pairingPin.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val incomingSession by viewModel.incomingSession.collectAsState()
    val pendingRequest by viewModel.pendingRequest.collectAsState()
    val isWifiActive by viewModel.isWifiActive.collectAsState()
    val isBluetoothActive by viewModel.isBluetoothActive.collectAsState()
    val isHotspotMode by viewModel.isHotspotMode.collectAsState()
    val hotspotInfo by viewModel.hotspotInfo.collectAsState()
    val radioMessage by viewModel.radioMessage.collectAsState()
    val qrDataString by viewModel.qrDataString.collectAsState()

    val qrData = if (qrDataString.isNotBlank()) qrDataString
    else "NOVASHARE:IP=$localIp:PORT=8888:PIN=$pairingPin:DEVICE=${NetworkUtils.getDeviceModelName()}"

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = "Receive Files", fontWeight = FontWeight.ExtraBold, color = NovaTextPrimary) },
                actions = {
                    IconButton(onClick = {
                        wifiEnabled = NetworkUtils.isWifiEnabled(context)
                        bluetoothEnabled = NetworkUtils.isBluetoothEnabled()
                        viewModel.updateLocalIp(context)
                    }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh Connectivity", tint = NovaPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NovaDarkBackground)
            )
        },
        containerColor = NovaDarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Connectivity Status Banner (Wi-Fi & Bluetooth)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = NovaDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, NovaDarkOutline.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Wi-Fi Status
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val isWifiOn = isWifiActive || wifiEnabled
                            Icon(
                                imageVector = if (isWifiOn) Icons.Default.Wifi else Icons.Default.WifiOff,
                                contentDescription = "Wi-Fi",
                                tint = if (isWifiOn) NovaPrimary else NovaError,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isWifiOn) "Wi-Fi On" else "Wi-Fi Off",
                                color = NovaTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Bluetooth Status
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val isBtOn = isBluetoothActive || bluetoothEnabled
                            Icon(
                                imageVector = if (isBtOn) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                                contentDescription = "Bluetooth",
                                tint = if (isBtOn) NovaSecondary else NovaError,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isBtOn) "Bluetooth On" else "Bluetooth Off",
                                color = NovaTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (!(isWifiActive || wifiEnabled) || !(isBluetoothActive || bluetoothEnabled)) {
                        Button(
                            onClick = {
                                viewModel.explicitlyEnableRadios(context) { intent ->
                                    try { context.startActivity(intent) } catch (_: Exception) {}
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NovaPrimary.copy(alpha = 0.2f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(text = "Turn On Radios", color = NovaPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Pairing QR Code Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = NovaDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, NovaDarkOutline.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Scan QR to Pair & Connect",
                        color = NovaTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Open Nova Share scanner on sender phone to connect automatically",
                        color = NovaTextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )

                    QRCodeCanvas(qrContent = qrData, size = 180.dp, backgroundColor = NovaDarkSurfaceVariant)

                    Spacer(modifier = Modifier.height(16.dp))

                    // IP & PIN display
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(NovaDarkBackground)
                            .border(1.dp, NovaDarkOutline.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Local Wi-Fi IP", color = NovaTextMuted, fontSize = 11.sp)
                            Text(text = localIp, color = NovaPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Security PIN", color = NovaTextMuted, fontSize = 11.sp)
                            Text(text = pairingPin, color = NovaSecondary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Incoming Progress Card
            incomingSession?.let { session ->
                TransferProgressCard(session = session)
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Radar Status Action Button
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = NovaDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, NovaDarkOutline.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.WifiTethering else Icons.Default.PortableWifiOff,
                            contentDescription = "Radar Status",
                            tint = if (isListening) NovaPrimary else NovaError,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isListening) "Receiver Active & Listening" else "Receiver Idle",
                                color = NovaTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (isListening) "Ready for incoming transfers" else "Tap to start receiver engine",
                                color = NovaTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Switch(
                        checked = isListening,
                        onCheckedChange = { viewModel.toggleListening(context) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NovaOnPrimary,
                            checkedTrackColor = NovaPrimary,
                            uncheckedThumbColor = NovaTextMuted,
                            uncheckedTrackColor = NovaDarkSurfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Offline Private Hotspot Mode Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (isHotspotMode) NovaPrimary.copy(alpha = 0.12f) else NovaDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isHotspotMode) NovaPrimary else NovaDarkOutline.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isHotspotMode) Icons.Default.WifiTethering else Icons.Default.WifiTetheringOff,
                            contentDescription = "Hotspot Mode",
                            tint = if (isHotspotMode) NovaPrimary else NovaTextMuted,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isHotspotMode) "Offline Hotspot Active" else "Zero-Router Hotspot Mode",
                                color = NovaTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (isHotspotMode && hotspotInfo.ssid.isNotBlank()) "SSID: ${hotspotInfo.ssid} (QR Auto-Joins)"
                                else "Transfer without existing Wi-Fi router",
                                color = if (isHotspotMode) NovaPrimary else NovaTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Switch(
                        checked = isHotspotMode,
                        onCheckedChange = { willEnable ->
                            if (willEnable) {
                                val neededPermissions = mutableListOf<String>()
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                                        neededPermissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
                                    }
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                        neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                    neededPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
                                }

                                if (neededPermissions.isNotEmpty()) {
                                    hotspotPermissionsLauncher.launch(neededPermissions.toTypedArray())
                                } else {
                                    viewModel.toggleHotspot(context)
                                }
                            } else {
                                viewModel.toggleHotspot(context)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NovaOnPrimary,
                            checkedTrackColor = NovaPrimary,
                            uncheckedThumbColor = NovaTextMuted,
                            uncheckedTrackColor = NovaDarkSurfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NovaDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, NovaDarkOutline.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderSpecial,
                        contentDescription = "Save Directory",
                        tint = NovaPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Save Location",
                            color = NovaTextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Main Storage / Download / Nova-share",
                            color = NovaTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // ACCEPT / DECLINE INCOMING TRANSFER REQUEST DIALOG
    pendingRequest?.let { req ->
        AlertDialog(
            onDismissRequest = { viewModel.declineTransfer() },
            icon = {
                Icon(
                    imageVector = Icons.Default.SendToMobile,
                    contentDescription = "Incoming Request",
                    tint = NovaPrimary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Incoming File Transfer",
                    color = NovaTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "${req.deviceName} wants to send you files over local Wi-Fi:",
                        color = NovaTextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = NovaDarkSurfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Total Files: ${req.files.size.coerceAtLeast(1)}",
                                    color = NovaPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = NetworkUtils.formatFileSize(req.totalBytes),
                                    color = NovaSecondary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            req.files.take(3).forEach { file ->
                                Text(
                                    text = "• ${file.name}",
                                    color = NovaTextPrimary,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                            if (req.files.size > 3) {
                                Text(
                                    text = "...and ${req.files.size - 3} more file(s)",
                                    color = NovaTextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.acceptTransfer() },
                    colors = ButtonDefaults.buttonColors(containerColor = NovaPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Accept", tint = NovaOnPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Accept & Download", color = NovaOnPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { viewModel.declineTransfer() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NovaError),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NovaError)
                ) {
                    Icon(imageVector = Icons.Default.Cancel, contentDescription = "Decline", tint = NovaError)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Decline", color = NovaError, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = NovaDarkSurface,
            shape = RoundedCornerShape(24.dp)
        )
    }
}
