package com.example.ui.screens.receive

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.data.model.FileCategory
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
    val storedFiles by viewModel.storedFiles.collectAsState()
    var selectedStoredCategory by remember { mutableStateOf<FileCategory?>(null) }

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
                        viewModel.refreshStoredFiles(context)
                    }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh Connectivity", tint = NovaPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
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
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            // Wi-Fi Status
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val isWifiOn = isWifiActive || wifiEnabled
                                Icon(
                                    imageVector = if (isWifiOn) Icons.Default.Wifi else Icons.Default.WifiOff,
                                    contentDescription = "Wi-Fi",
                                    tint = if (isWifiOn) NovaPrimary else NovaError,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isWifiOn) "Wi-Fi On" else "Wi-Fi Off",
                                    color = NovaTextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }

                            // Bluetooth Status
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val isBtOn = isBluetoothActive || bluetoothEnabled
                                Icon(
                                    imageVector = if (isBtOn) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                                    contentDescription = "Bluetooth",
                                    tint = if (isBtOn) NovaSecondary else NovaError,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isBtOn) "BT On" else "BT Off",
                                    color = NovaTextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
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
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Turn On Radios",
                                    color = NovaPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.WifiTethering else Icons.Default.PortableWifiOff,
                            contentDescription = "Radar Status",
                            tint = if (isListening) NovaPrimary else NovaError,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isListening) "Receiver Active & Listening" else "Receiver Idle",
                                color = NovaTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isListening) "Ready for incoming transfers" else "Tap to start receiver engine",
                                color = NovaTextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

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
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isHotspotMode) Icons.Default.WifiTethering else Icons.Default.WifiTetheringOff,
                                contentDescription = "Hotspot Mode",
                                tint = if (isHotspotMode) NovaPrimary else NovaTextMuted,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isHotspotMode) "Offline Hotspot Active" else "Zero-Router Hotspot Mode",
                                    color = NovaTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isHotspotMode && hotspotInfo.ssid.isNotBlank()) "SSID: ${hotspotInfo.ssid} (QR Auto-Joins)"
                                    else if (hotspotInfo.statusMessage.isNotBlank() && hotspotInfo.statusMessage != "Hotspot Idle") hotspotInfo.statusMessage
                                    else "Transfer without existing Wi-Fi router",
                                    color = if (isHotspotMode) NovaPrimary else NovaTextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

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

                    // System Settings shortcut for Hotspot / Tethering
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                try {
                                    context.startActivity(viewModel.getHotspotSettingsIntent(context))
                                } catch (_: Exception) {}
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = NovaPrimary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Hotspot / Tethering Settings",
                                color = NovaPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // SECTION: Stored Received Files & Data
            val filteredStored = remember(storedFiles, selectedStoredCategory) {
                if (selectedStoredCategory == null || selectedStoredCategory == FileCategory.ALL) storedFiles
                else storedFiles.filter { it.category == selectedStoredCategory }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = "Received Files & Stored Data",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = NovaTextPrimary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = NovaSecondary.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "${storedFiles.size} items",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NovaSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            maxLines = 1
                        )
                    }
                }

                IconButton(onClick = { viewModel.refreshStoredFiles(context) }) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh Files", tint = NovaSecondary, modifier = Modifier.size(20.dp))
                }
            }

            // Category Filter for Received Stored Files (Horizontally Scrollable)
            if (storedFiles.isNotEmpty()) {
                val filterCategories: List<Pair<FileCategory?, String>> = listOf(
                    null to "All (${storedFiles.size})",
                    FileCategory.IMAGES to "Images (${storedFiles.count { it.category == FileCategory.IMAGES }})",
                    FileCategory.VIDEOS to "Videos (${storedFiles.count { it.category == FileCategory.VIDEOS }})",
                    FileCategory.DOCUMENTS to "Docs (${storedFiles.count { it.category == FileCategory.DOCUMENTS }})"
                )

                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filterCategories.size) { index ->
                        val pair = filterCategories[index]
                        val cat = pair.first
                        val label = pair.second
                        val isSelected = selectedStoredCategory == cat
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedStoredCategory = if (isSelected) null else cat },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NovaSecondary,
                                selectedLabelColor = Color.Black,
                                containerColor = NovaDarkSurfaceVariant,
                                labelColor = NovaTextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = NovaDarkOutline
                            )
                        )
                    }
                }
            }

            if (storedFiles.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = NovaDarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NovaDarkOutline.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "No received files",
                            tint = NovaSecondary.copy(alpha = 0.6f),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No Received Files Yet",
                            color = NovaTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Files, images, and data transferred to this phone will be strictly saved in Download/Nova-share and listed here.",
                            color = NovaTextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filteredStored.forEach { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.openStoredFile(context, item) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = NovaDarkSurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NovaDarkOutline.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val itemIcon = when (item.category) {
                                    FileCategory.IMAGES -> Icons.Default.Image
                                    FileCategory.VIDEOS -> Icons.Default.VideoLibrary
                                    FileCategory.AUDIO -> Icons.Default.MusicNote
                                    FileCategory.DOCUMENTS -> Icons.Default.Description
                                    FileCategory.APK -> Icons.Default.Android
                                    else -> Icons.Default.InsertDriveFile
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = NovaDarkSurfaceVariant,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = itemIcon,
                                            contentDescription = item.name,
                                            tint = NovaSecondary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        color = NovaTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${NetworkUtils.formatFileSize(item.size)} • ${java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(item.lastModified))}",
                                        color = NovaTextSecondary,
                                        fontSize = 11.sp
                                    )
                                }

                                // Open Button
                                IconButton(onClick = { viewModel.openStoredFile(context, item) }) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = "Open",
                                        tint = NovaPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Share Button
                                IconButton(onClick = { viewModel.shareStoredFile(context, item) }) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share",
                                        tint = NovaSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Delete Button
                                IconButton(onClick = { viewModel.deleteStoredFile(context, item) }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete",
                                        tint = NovaError,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Save Directory Indicator
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
                            text = "Strict Save Location",
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
                    Text(
                        text = "Accept",
                        color = NovaOnPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
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
                    Text(
                        text = "Decline",
                        color = NovaError,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            },
            containerColor = NovaDarkSurface,
            shape = RoundedCornerShape(24.dp)
        )
    }
}
