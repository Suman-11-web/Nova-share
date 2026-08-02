package com.example.ui.screens.send

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.example.data.model.FileCategory
import com.example.data.model.SharedFile
import com.example.network.NetworkUtils
import com.example.ui.components.CameraQrScannerDialog
import com.example.ui.components.DeviceAvatar
import com.example.ui.components.FilePreviewDialog
import com.example.ui.components.TransferProgressCard
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    viewModel: SendViewModel
) {
    val context = LocalContext.current

    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val availableFiles by viewModel.availableFiles.collectAsState()
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()

    var previewFile by remember { mutableStateOf<SharedFile?>(null) }
    var showIpDialog by remember { mutableStateOf(false) }
    var showCameraScanner by remember { mutableStateOf(false) }
    var showBluetoothWifiDialog by remember { mutableStateOf(false) }
    var manualIpInput by remember { mutableStateOf("") }
    var manualNameInput by remember { mutableStateOf("") }

    // Launcher for system file picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addPickedUris(context, uris)
        }
    }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showCameraScanner = true
        }
    }

    // Permissions launcher for Nearby Search (Location, Bluetooth, Wi-Fi)
    val nearbyPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Open Bluetooth/Wi-Fi dialog regardless to allow user to confirm adapter states
        showBluetoothWifiDialog = true
    }

    LaunchedEffect(Unit) {
        viewModel.loadRealDeviceFiles(context)
    }

    val filteredFiles = remember(selectedCategory, availableFiles) {
        if (selectedCategory == FileCategory.ALL) availableFiles
        else availableFiles.filter { it.category == selectedCategory }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Send Files",
                        fontWeight = FontWeight.ExtraBold,
                        color = NovaTextPrimary
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.loadRealDeviceFiles(context) }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh Storage", tint = NovaPrimary)
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
                .padding(horizontal = 16.dp)
        ) {
            // Action Bar: Pick Files from Device Storage
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { filePickerLauncher.launch("*/*") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = NovaPrimary),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(imageVector = Icons.Default.AddCircle, contentDescription = "Browse Files", tint = NovaOnPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Pick Files from Device", color = NovaOnPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = { viewModel.loadRealDeviceFiles(context) },
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NovaDarkOutline)
                ) {
                    Icon(imageVector = Icons.Default.Folder, contentDescription = "Scan Storage", tint = NovaSecondary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Scan", color = NovaTextPrimary, fontSize = 12.sp)
                }
            }

            // Category Filter Bar
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                items(FileCategory.values()) { category ->
                    val isSelected = category == selectedCategory
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectCategory(category, context) },
                        label = { Text(text = category.displayName, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NovaPrimary,
                            selectedLabelColor = NovaOnPrimary,
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

            // File Selection List / Empty state
            if (filteredFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "No files",
                            tint = NovaTextMuted,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No files loaded yet",
                            color = NovaTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Tap 'Pick Files from Device' above to select real files from your storage",
                            color = NovaTextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredFiles) { file ->
                        val isSelected = selectedFiles.any { it.id == file.id }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleFileSelection(file) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) NovaPrimary.copy(alpha = 0.15f) else NovaDarkSurface
                            ),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, NovaPrimary) else androidx.compose.foundation.BorderStroke(1.dp, NovaDarkOutline.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { viewModel.toggleFileSelection(file) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = NovaPrimary,
                                        checkmarkColor = NovaOnPrimary
                                    )
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.name,
                                        color = NovaTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "${file.category.displayName} • ${NetworkUtils.formatFileSize(file.size)}",
                                        color = NovaTextSecondary,
                                        fontSize = 12.sp
                                    )
                                }

                                IconButton(onClick = { previewFile = file }) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = "Preview",
                                        tint = NovaPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Active Transfer Progress Section
            activeSession?.let { session ->
                Spacer(modifier = Modifier.height(8.dp))
                TransferProgressCard(session = session)
            }

            // Nearby Receivers Section
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = NovaDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, NovaDarkOutline.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Nearby Receivers (${discoveredDevices.size})",
                                color = NovaTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            if (selectedFiles.isNotEmpty()) {
                                Text(
                                    text = "${selectedFiles.size} file(s) selected (${NetworkUtils.formatFileSize(selectedFiles.sumOf { it.size })})",
                                    color = NovaPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Scan QR Camera Button
                            Button(
                                onClick = {
                                    val cameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                                    if (cameraPermission == PackageManager.PERMISSION_GRANTED) {
                                        showCameraScanner = true
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NovaSecondary)
                            ) {
                                Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = "Scan QR", tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Scan QR", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                            }

                            // Search Nearby Button (Triggers Bluetooth/Wi-Fi check & permissions)
                            Button(
                                onClick = {
                                    val neededPermissions = mutableListOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        neededPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
                                        neededPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
                                        neededPermissions.add("android.permission.NEARBY_WIFI_DEVICES")
                                    }

                                    val ungranted = neededPermissions.filter {
                                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                                    }

                                    if (ungranted.isNotEmpty()) {
                                        nearbyPermissionsLauncher.launch(ungranted.toTypedArray())
                                    } else {
                                        showBluetoothWifiDialog = true
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NovaPrimary)
                            ) {
                                if (isSearching) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = NovaOnPrimary, strokeWidth = 2.dp)
                                } else {
                                    Icon(imageVector = Icons.Default.Radar, contentDescription = "Search Nearby", tint = NovaOnPrimary, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = if (isSearching) "Scanning..." else "Search Nearby", fontSize = 11.sp, color = NovaOnPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (discoveredDevices.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(NovaDarkSurfaceVariant)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.WifiFind, contentDescription = "No devices", tint = NovaTextMuted)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "No active receivers detected. Tap 'Scan QR' to pair with camera or 'Search Nearby' to scan network.",
                                color = NovaTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(discoveredDevices) { device ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { viewModel.initiateTransfer(device) }
                                        .padding(8.dp)
                                ) {
                                    DeviceAvatar(deviceType = device.type, size = 52.dp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = device.name,
                                        color = NovaTextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = device.ipAddress,
                                        color = NovaTextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    // Bluetooth & Wi-Fi Enable Prompt Dialog
    if (showBluetoothWifiDialog) {
        AlertDialog(
            onDismissRequest = { showBluetoothWifiDialog = false },
            icon = { Icon(imageVector = Icons.Default.BluetoothSearching, contentDescription = "Enable Bluetooth", tint = NovaPrimary, modifier = Modifier.size(32.dp)) },
            title = { Text(text = "Enable Bluetooth & Wi-Fi", color = NovaTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "NovaShare uses Bluetooth and local Wi-Fi to scan for nearby receiving phones. Please make sure both Bluetooth and Wi-Fi are turned ON.",
                        color = NovaTextSecondary,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBluetoothWifiDialog = false
                        viewModel.searchNearbyDevices(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NovaPrimary)
                ) {
                    Text("Scan Active Network", color = NovaOnPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBluetoothWifiDialog = false }) {
                    Text("Cancel", color = NovaTextSecondary)
                }
            },
            containerColor = NovaDarkSurface
        )
    }

    // Camera QR Scanner Dialog
    if (showCameraScanner) {
        CameraQrScannerDialog(
            onDismiss = { showCameraScanner = false },
            onQrScanned = { qrResult ->
                viewModel.parseAndPairFromQr(qrResult)
                showCameraScanner = false
            }
        )
    }

    // Manual IP Connection Dialog
    if (showIpDialog) {
        AlertDialog(
            onDismissRequest = { showIpDialog = false },
            title = { Text(text = "Connect to Receiver IP", color = NovaTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(text = "Enter the IP address shown on the receiver screen:", color = NovaTextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = manualIpInput,
                        onValueChange = { manualIpInput = it },
                        label = { Text("Receiver IP (e.g. 192.168.1.102)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NovaPrimary,
                            unfocusedBorderColor = NovaDarkOutline
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manualNameInput,
                        onValueChange = { manualNameInput = it },
                        label = { Text("Device Name (Optional)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NovaPrimary,
                            unfocusedBorderColor = NovaDarkOutline
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (manualIpInput.isNotBlank()) {
                            viewModel.addManualDevice(manualNameInput, manualIpInput)
                            showIpDialog = false
                            manualIpInput = ""
                            manualNameInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NovaPrimary)
                ) {
                    Text("Add Receiver", color = NovaOnPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showIpDialog = false }) {
                    Text("Cancel", color = NovaTextSecondary)
                }
            },
            containerColor = NovaDarkSurface
        )
    }

    // Preview Dialog
    previewFile?.let { file ->
        FilePreviewDialog(file = file, onDismiss = { previewFile = null })
    }
}
