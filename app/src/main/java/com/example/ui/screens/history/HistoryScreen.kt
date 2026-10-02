package com.example.ui.screens.history

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.NetworkUtils
import com.example.ui.theme.NovaDarkBackground
import com.example.ui.theme.NovaDarkOutline
import com.example.ui.theme.NovaDarkSurface
import com.example.ui.theme.NovaDarkSurfaceVariant
import com.example.ui.theme.NovaError
import com.example.ui.theme.NovaPrimary
import com.example.ui.theme.NovaSuccess
import com.example.ui.theme.NovaTextMuted
import com.example.ui.theme.NovaTextPrimary
import com.example.ui.theme.NovaTextSecondary
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel
) {
    val historyLogs by viewModel.historyLogs.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val filteredLogs = remember(historyLogs, searchQuery) {
        if (searchQuery.isEmpty()) historyLogs
        else historyLogs.filter {
            it.fileName.contains(searchQuery, ignoreCase = true) ||
                    it.deviceName.contains(searchQuery, ignoreCase = true)
        }
    }

    val dateFormatter = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Transfer History", fontWeight = FontWeight.ExtraBold, color = NovaTextPrimary) },
                actions = {
                    if (historyLogs.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearAllHistory() }) {
                            Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Clear All", tint = NovaError)
                        }
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
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(text = "Search logs by file or device name...", color = NovaTextMuted) },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = NovaPrimary) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NovaPrimary,
                    unfocusedBorderColor = NovaDarkOutline,
                    focusedContainerColor = NovaDarkSurface,
                    unfocusedContainerColor = NovaDarkSurface,
                    focusedTextColor = NovaTextPrimary,
                    unfocusedTextColor = NovaTextPrimary
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.History, contentDescription = "Empty History", tint = NovaTextMuted, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "No transfer history found", color = NovaTextSecondary, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredLogs) { log ->
                        val isSend = log.direction == "SEND"
                        val directionIcon = if (isSend) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = NovaDarkSurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NovaDarkOutline.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = directionIcon,
                                            contentDescription = log.direction,
                                            tint = if (isSend) NovaPrimary else NovaSuccess,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = log.fileName,
                                            color = NovaTextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteItem(log.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = "Delete Log", tint = NovaTextMuted)
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${if (isSend) "To" else "From"} ${log.deviceName} • ${NetworkUtils.formatFileSize(log.fileSize)}",
                                        color = NovaTextSecondary,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = dateFormatter.format(Date(log.timestamp)),
                                        color = NovaTextMuted,
                                        fontSize = 11.sp
                                    )
                                }

                                if (log.checksumSha256.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "SHA-256 Checksum Verified",
                                        color = NovaSuccess,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
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
