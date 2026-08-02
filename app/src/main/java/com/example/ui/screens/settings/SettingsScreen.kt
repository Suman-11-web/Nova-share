package com.example.ui.screens.settings

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.NetworkUtils
import com.example.ui.components.StorageBar
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.NovaDarkBackground
import com.example.ui.theme.NovaDarkOutline
import com.example.ui.theme.NovaDarkSurface
import com.example.ui.theme.NovaDarkSurfaceVariant
import com.example.ui.theme.NovaOnPrimary
import com.example.ui.theme.NovaPrimary
import com.example.ui.theme.NovaSecondary
import com.example.ui.theme.NovaSuccess
import com.example.ui.theme.NovaTextMuted
import com.example.ui.theme.NovaTextPrimary
import com.example.ui.theme.NovaTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel
) {
    val deviceName by viewModel.deviceName.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val autoAccept by viewModel.autoAccept.collectAsState()
    val encryptionEnabled by viewModel.encryptionEnabled.collectAsState()
    val bandwidthLimit by viewModel.bandwidthLimit.collectAsState()

    val sentBytes by viewModel.totalSentBytes.collectAsState()
    val receivedBytes by viewModel.totalReceivedBytes.collectAsState()
    val completedCount by viewModel.totalCompletedTransfers.collectAsState()

    var showRenameDialog by remember { mutableStateOf(false) }
    var tempDeviceName by remember { mutableStateOf(deviceName) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Settings & Statistics", fontWeight = FontWeight.ExtraBold, color = NovaTextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NovaDarkBackground)
            )
        },
        containerColor = NovaDarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Transfer Analytics Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = NovaDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, NovaDarkOutline.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Transfer Analytics",
                        color = NovaTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatBox(title = "Total Sent", value = NetworkUtils.formatFileSize(sentBytes), color = NovaPrimary)
                        StatBox(title = "Total Received", value = NetworkUtils.formatFileSize(receivedBytes), color = NovaSuccess)
                        StatBox(title = "Transfers", value = "$completedCount", color = NovaSecondary)
                    }
                }
            }

            // Storage Breakdown Bar
            StorageBar()

            // Device Name Configuration Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = NovaDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, NovaDarkOutline.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .clickable { showRenameDialog = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Rename", tint = NovaPrimary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "Device Name", color = NovaTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(text = deviceName, color = NovaTextSecondary, fontSize = 12.sp)
                        }
                    }
                    Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Edit", tint = NovaTextMuted)
                }
            }

            // Security Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = NovaDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, NovaDarkOutline.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Security & Verification", color = NovaTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    SettingToggleRow(
                        title = "AES-256 P2P Encryption",
                        subtitle = "Encrypt all file chunks over local socket connections",
                        checked = encryptionEnabled,
                        onCheckedChange = { viewModel.toggleEncryption() }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingToggleRow(
                        title = "Auto-Accept Trusted Devices",
                        subtitle = "Skip PIN prompts for devices saved as trusted",
                        checked = autoAccept,
                        onCheckedChange = { viewModel.toggleAutoAccept() }
                    )
                }
            }

            // Appearance Theme Selector Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = NovaDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, NovaDarkOutline.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Appearance & Theme", color = NovaTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AppThemeMode.values().forEach { mode ->
                            val isSelected = mode == themeMode
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(text = mode.name, fontSize = 11.sp) },
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
                }
            }

            // Diagnostics & App Info
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = NovaDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, NovaDarkOutline.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Nova Share Open Source Info", color = NovaTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Version 1.0.0 (Build 2026)", color = NovaTextSecondary, fontSize = 12.sp)
                    Text(text = "License: MIT • GitHub Actions CI/CD Enabled", color = NovaTextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = NovaDarkSurface,
            title = { Text(text = "Rename Device", fontWeight = FontWeight.Bold, color = NovaTextPrimary) },
            text = {
                OutlinedTextField(
                    value = tempDeviceName,
                    onValueChange = { tempDeviceName = it },
                    label = { Text("Device Name", color = NovaTextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NovaPrimary,
                        unfocusedBorderColor = NovaDarkOutline,
                        focusedTextColor = NovaTextPrimary,
                        unfocusedTextColor = NovaTextPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setDeviceName(tempDeviceName)
                        showRenameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NovaPrimary)
                ) {
                    Text("Save", color = NovaOnPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = NovaTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun StatBox(title: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Text(text = title, color = NovaTextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = NovaTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(text = subtitle, color = NovaTextMuted, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NovaOnPrimary,
                checkedTrackColor = NovaPrimary,
                uncheckedThumbColor = NovaTextMuted,
                uncheckedTrackColor = NovaDarkSurfaceVariant
            )
        )
    }
}
