package com.example.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.NetworkUtils
import com.example.ui.components.StorageBar
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel
) {
    val context = LocalContext.current

    val deviceName by viewModel.deviceName.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val autoAccept by viewModel.autoAccept.collectAsState()
    val encryptionEnabled by viewModel.encryptionEnabled.collectAsState()

    val sentBytes by viewModel.totalSentBytes.collectAsState()
    val receivedBytes by viewModel.totalReceivedBytes.collectAsState()
    val completedCount by viewModel.totalCompletedTransfers.collectAsState()
    val storageStats by viewModel.deviceStorage.collectAsState()

    var showRenameDialog by remember { mutableStateOf(false) }
    var tempDeviceName by remember { mutableStateOf(deviceName) }
    var showLicenseDialog by remember { mutableStateOf(false) }
    var showReleaseNotesDialog by remember { mutableStateOf(false) }

    LaunchedEffect(deviceName) {
        tempDeviceName = deviceName
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings & Device",
                        fontWeight = FontWeight.ExtraBold,
                        color = NovaTextPrimary
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshStorageStats() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh Device Info", tint = NovaPrimary)
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Device Finder & Hardware Identity Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = NovaDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, NovaDarkOutline.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .clickable { showRenameDialog = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(NovaPrimaryContainer.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = "Device Finder",
                                tint = NovaPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Device Model",
                                    color = NovaTextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Tap to Rename",
                                    color = NovaPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = deviceName,
                                color = NovaTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                                color = NovaTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Name", tint = NovaPrimary)
                }
            }

            // 2. Real Physical Device Storage Card
            StorageBar(storage = storageStats)

            // 3. Real Live Transfer Analytics Card (Loaded from Room Database)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = NovaDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, NovaDarkOutline.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Transfer Analytics",
                            color = NovaTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Live Database",
                            color = NovaPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatBox(
                            title = "Total Sent",
                            value = NetworkUtils.formatFileSize(sentBytes),
                            color = NovaPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        StatBox(
                            title = "Total Received",
                            value = NetworkUtils.formatFileSize(receivedBytes),
                            color = NovaSuccess,
                            modifier = Modifier.weight(1f)
                        )
                        StatBox(
                            title = "Completed",
                            value = "$completedCount files",
                            color = NovaSecondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 4. Security & Connection Protocol
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = NovaDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, NovaDarkOutline.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Security & Transmission",
                        color = NovaTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    SettingToggleRow(
                        title = "End-to-End SHA-256 Checksums",
                        subtitle = "Verify cryptographic integrity for every transferred byte chunk",
                        checked = encryptionEnabled,
                        onCheckedChange = { viewModel.toggleEncryption() }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingToggleRow(
                        title = "Auto-Accept Trusted Peers",
                        subtitle = "Bypass manual PIN authorization for previously verified devices",
                        checked = autoAccept,
                        onCheckedChange = { viewModel.toggleAutoAccept() }
                    )
                }
            }

            // 5. Appearance & Real Theme Support
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = NovaDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, NovaDarkOutline.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Appearance & Theme",
                        color = NovaTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Select your preferred color mode for Nova Share",
                        color = NovaTextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(AppThemeMode.entries.size) { index ->
                            val mode = AppThemeMode.entries[index]
                            val isSelected = mode == themeMode
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = {
                                    Text(
                                        text = when (mode) {
                                            AppThemeMode.SYSTEM -> "System"
                                            AppThemeMode.DARK -> "Dark"
                                            AppThemeMode.AMOLED -> "AMOLED"
                                            AppThemeMode.LIGHT -> "Light"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NovaPrimary,
                                    selectedLabelColor = NovaOnPrimary,
                                    containerColor = NovaDarkSurfaceVariant,
                                    labelColor = NovaTextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = if (isSelected) NovaPrimary else NovaDarkOutline
                                )
                            )
                        }
                    }
                }
            }

            // 6. Premium Developer Profile Card (Suman M.)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = NovaDarkSurface),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(
                        colors = listOf(
                            NovaPrimary.copy(alpha = 0.7f),
                            NovaSecondary.copy(alpha = 0.7f)
                        )
                    )
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Monogram Avatar
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                Color(0xFF7C3AED),
                                                Color(0xFF00E5FF)
                                            )
                                        )
                                    )
                                    .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "SM",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Suman M.",
                                        color = NovaTextPrimary,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 17.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = "Verified Creator",
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = "Founder & Independent Android & Web Developer",
                                    color = NovaPrimary,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Architecting private, zero-compromise peer-to-peer mobile transfer technology and modern open-source Android software.",
                        color = NovaTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Clickable Social & Profile Badges (Horizontally scrollable for big text)
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            DeveloperActionChip(
                                label = "Portfolio",
                                icon = Icons.Default.Language,
                                onClick = { openExternalUrl(context, "https://sumanm.dev") }
                            )
                        }
                        item {
                            DeveloperActionChip(
                                label = "GitHub",
                                icon = Icons.Default.Code,
                                onClick = { openExternalUrl(context, "https://github.com/sumanm-dev") }
                            )
                        }
                        item {
                            DeveloperActionChip(
                                label = "Email",
                                icon = Icons.Default.Email,
                                onClick = { sendEmail(context, "sumanofficial.dev@gmail.com", "Nova Share Inquiry", "") }
                            )
                        }
                    }
                }
            }

            // 7. Redesigned Premium "Nova Share Open Source Info & App Details" Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = NovaDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, NovaDarkOutline.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Header with Branding & Badges
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                NovaPrimary,
                                                NovaSecondary
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Nova Share Icon",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Nova Share",
                                    color = NovaTextPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp
                                )
                                Text(
                                    text = "v2.4.0 • Build 240 (Production)",
                                    color = NovaTextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Open Source Tag
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = NovaPrimary.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NovaPrimary.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "Open Source",
                                color = NovaPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Free, open-source peer-to-peer file sharing for Android with end-to-end cryptographic checksum verification, SoftAP offline Hotspot auto-join, and zero cloud tracking.",
                        color = NovaTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Interactive Action Rows
                    HorizontalDivider(color = NovaDarkOutline.copy(alpha = 0.3f))

                    AboutActionRow(
                        title = "GitHub Repository",
                        subtitle = "View open source codebase & star the project",
                        icon = Icons.Default.Code,
                        onClick = { openExternalUrl(context, "https://github.com/sumanm-dev/NovaShare") }
                    )

                    HorizontalDivider(color = NovaDarkOutline.copy(alpha = 0.2f))

                    AboutActionRow(
                        title = "Release Notes & Changelog",
                        subtitle = "Check v2.4 upgrade features & improvements",
                        icon = Icons.Default.Update,
                        onClick = { showReleaseNotesDialog = true }
                    )

                    HorizontalDivider(color = NovaDarkOutline.copy(alpha = 0.2f))

                    AboutActionRow(
                        title = "MIT Open Source License",
                        subtitle = "Free for personal and commercial usage",
                        icon = Icons.Default.Description,
                        onClick = { showLicenseDialog = true }
                    )

                    HorizontalDivider(color = NovaDarkOutline.copy(alpha = 0.2f))

                    AboutActionRow(
                        title = "Report Bugs & Feedback",
                        subtitle = "sumanofficial.dev@gmail.com",
                        icon = Icons.Default.BugReport,
                        iconTint = NovaSecondary,
                        onClick = {
                            sendEmail(
                                context = context,
                                toEmail = "sumanofficial.dev@gmail.com",
                                subject = "Nova Share Bug Report / Feature Feedback",
                                body = "Device: $deviceName\nAndroid: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\nApp Version: v2.4.0\n\nDescribe the issue or feature request:\n"
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Support Banner
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                sendEmail(
                                    context = context,
                                    toEmail = "sumanofficial.dev@gmail.com",
                                    subject = "Nova Share Support Request",
                                    body = "Device: $deviceName\n"
                                )
                            },
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF1E1B4B),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4338CA))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "Support Email",
                                tint = Color(0xFFA5B4FC),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Found a bug or have an idea?",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Email: sumanofficial.dev@gmail.com",
                                    color = Color(0xFFA5B4FC),
                                    fontSize = 11.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Send Email",
                                tint = Color(0xFFA5B4FC),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "© 2026 Suman M. All rights reserved. Built with Kotlin & Jetpack Compose.",
                        color = NovaTextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = NovaDarkSurface,
            title = { Text(text = "Customize Device Name", fontWeight = FontWeight.Bold, color = NovaTextPrimary) },
            text = {
                Column {
                    Text(
                        text = "This name is visible to peers during Wi-Fi and Bluetooth discovery.",
                        color = NovaTextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
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
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setDeviceName(tempDeviceName)
                        showRenameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NovaPrimary)
                ) {
                    Text("Save Name", color = NovaOnPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = NovaTextSecondary)
                }
            }
        )
    }

    // Release Notes Dialog
    if (showReleaseNotesDialog) {
        AlertDialog(
            onDismissRequest = { showReleaseNotesDialog = false },
            containerColor = NovaDarkSurface,
            title = {
                Text("Nova Share v2.4.0 Highlights", fontWeight = FontWeight.Bold, color = NovaTextPrimary)
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ReleaseFeatureItem("End-to-End SHA-256", "Live cryptographic hash calculation ensures zero byte loss.")
                    ReleaseFeatureItem("Zero-Router Hotspot", "Direct SoftAP mode with QR code automatic Wi-Fi joining.")
                    ReleaseFeatureItem("Installed APK Extractor", "Extract and transfer installed apps without extra tools.")
                    ReleaseFeatureItem("Foreground Transfer Service", "Uninterrupted background transfers with live notifications.")
                    ReleaseFeatureItem("Instant ZXing QR Engine", "High-contrast QR generation with sub-second camera detection.")
                }
            },
            confirmButton = {
                Button(
                    onClick = { showReleaseNotesDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = NovaPrimary)
                ) {
                    Text("Got it", color = NovaOnPrimary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // MIT License Dialog
    if (showLicenseDialog) {
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            containerColor = NovaDarkSurface,
            title = {
                Text("MIT License", fontWeight = FontWeight.Bold, color = NovaTextPrimary)
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = """
                        Copyright (c) 2026 Suman M.
                        
                        Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
                        
                        The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
                        
                        THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
                        """.trimIndent(),
                        color = NovaTextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showLicenseDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = NovaPrimary)
                ) {
                    Text("Close", color = NovaOnPrimary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun ReleaseFeatureItem(title: String, description: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = NovaSuccess, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = title, color = NovaTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Text(text = description, color = NovaTextSecondary, fontSize = 11.sp, modifier = Modifier.padding(start = 22.dp))
    }
}

@Composable
private fun DeveloperActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = NovaDarkSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, NovaDarkOutline.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = NovaPrimary, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = NovaTextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AboutActionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color = NovaPrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(imageVector = icon, contentDescription = title, tint = iconTint, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, color = NovaTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(text = subtitle, color = NovaTextMuted, fontSize = 11.sp)
            }
        }
        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Open", tint = NovaTextMuted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun StatBox(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(
            text = value,
            color = color,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Text(
            text = title,
            color = NovaTextMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
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

private fun openExternalUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {}
}

private fun sendEmail(context: Context, toEmail: String, subject: String, body: String) {
    try {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$toEmail")
            putExtra(Intent.EXTRA_SUBJECT, subject)
            if (body.isNotBlank()) {
                putExtra(Intent.EXTRA_TEXT, body)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {}
}
