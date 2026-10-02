package com.example.ui.screens.webshare

import android.net.Uri
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.NetworkUtils
import com.example.ui.components.QRCodeCanvas
import com.example.ui.theme.*
import com.example.util.DeviceFileUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebShareScreen(
    viewModel: WebShareViewModel
) {
    val context = LocalContext.current
    val isServerRunning by viewModel.isServerRunning.collectAsState()
    val webUrl by viewModel.webUrl.collectAsState()
    val webSharedFiles by viewModel.webSharedFiles.collectAsState()

    // System file picker launcher for Web Share
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val parsed = DeviceFileUtils.parsePickedUris(context, uris)
            viewModel.addSharedFiles(parsed)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Web Share (Phone ↔ PC)", fontWeight = FontWeight.ExtraBold, color = NovaTextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Server Toggle Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = NovaDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, NovaDarkOutline.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Computer,
                        contentDescription = "Web Portal",
                        tint = if (isServerRunning) NovaPrimary else NovaTextMuted,
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isServerRunning) "Web Portal Running" else "Web Portal Offline",
                        color = NovaTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Connect any browser on PC, Mac, Linux, or iOS without installing software",
                        color = NovaTextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )

                    Button(
                        onClick = { viewModel.toggleWebServer(context) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isServerRunning) NovaError else NovaPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isServerRunning) "Stop Web Server" else "Start Web Portal Server",
                            fontWeight = FontWeight.Bold,
                            color = if (isServerRunning) Color.White else NovaOnPrimary
                        )
                    }
                }
            }

            if (isServerRunning) {
                Spacer(modifier = Modifier.height(16.dp))

                // Connection Info & QR Code
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = NovaDarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NovaDarkOutline.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Scan or Open Address in Browser",
                            color = NovaTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        QRCodeCanvas(qrContent = webUrl, size = 150.dp, backgroundColor = NovaDarkSurfaceVariant)

                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = NovaDarkBackground
                        ) {
                            Text(
                                text = webUrl,
                                color = NovaPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Shared Files Header & Add Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Files Shared on Web Portal (${webSharedFiles.size})",
                    color = NovaTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                Button(
                    onClick = { pickerLauncher.launch("*/*") },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NovaPrimary),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Files", tint = NovaOnPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Add Files", fontSize = 11.sp, color = NovaOnPrimary, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (webSharedFiles.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NovaDarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NovaDarkOutline.copy(alpha = 0.3f))
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Default.CloudUpload, contentDescription = "Empty", tint = NovaTextMuted, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "No files published on Web Portal", color = NovaTextPrimary, fontWeight = FontWeight.Bold)
                            Text(text = "Tap 'Add Files' to allow connected PC browsers to download real device files", color = NovaTextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(webSharedFiles) { file ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = NovaDarkSurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NovaDarkOutline.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
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
                                        fontSize = 11.sp
                                    )
                                }
                                IconButton(onClick = { viewModel.removeSharedFile(file.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Remove File",
                                        tint = NovaError
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
