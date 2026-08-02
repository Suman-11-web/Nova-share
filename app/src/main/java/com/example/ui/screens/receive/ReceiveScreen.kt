package com.example.ui.screens.receive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import com.example.ui.components.QRCodeCanvas
import com.example.ui.components.TransferProgressCard
import com.example.ui.screens.receive.ReceiveViewModel
import com.example.ui.theme.NovaDarkBackground
import com.example.ui.theme.NovaDarkOutline
import com.example.ui.theme.NovaDarkSurface
import com.example.ui.theme.NovaDarkSurfaceVariant
import com.example.ui.theme.NovaError
import com.example.ui.theme.NovaOnPrimary
import com.example.ui.theme.NovaPrimary
import com.example.ui.theme.NovaSecondary
import com.example.ui.theme.NovaTextMuted
import com.example.ui.theme.NovaTextPrimary
import com.example.ui.theme.NovaTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveScreen(
    viewModel: ReceiveViewModel
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.updateLocalIp(context)
    }

    val localIp by viewModel.localIp.collectAsState()
    val pairingPin by viewModel.pairingPin.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val incomingSession by viewModel.incomingSession.collectAsState()

    val qrData = "NOVASHARE:IP=$localIp:PORT=8888:PIN=$pairingPin"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Receive Files", fontWeight = FontWeight.ExtraBold, color = NovaTextPrimary) },
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
            // Pairing QR Code Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = NovaDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, NovaDarkOutline.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Scan QR to Pair & Send",
                        color = NovaTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Open Nova Share on sender phone or camera to connect",
                        color = NovaTextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                    )

                    QRCodeCanvas(qrContent = qrData, size = 180.dp, backgroundColor = NovaDarkSurfaceVariant)

                    Spacer(modifier = Modifier.height(20.dp))

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

            Spacer(modifier = Modifier.height(16.dp))

            // Incoming Progress Card
            incomingSession?.let { session ->
                TransferProgressCard(session = session)
                Spacer(modifier = Modifier.height(16.dp))
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

            Spacer(modifier = Modifier.weight(1f))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NovaDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, NovaDarkOutline.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderSpecial,
                        contentDescription = "Save Directory",
                        tint = NovaPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Save Location",
                            color = NovaTextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Main Storage / Download / Nova-share",
                            color = NovaTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
