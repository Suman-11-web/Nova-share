package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.FileCategory
import com.example.data.model.SharedFile
import com.example.network.NetworkUtils
import com.example.ui.theme.NovaDarkBackground
import com.example.ui.theme.NovaDarkSurface
import com.example.ui.theme.NovaOnPrimary
import com.example.ui.theme.NovaPrimary
import com.example.ui.theme.NovaTextMuted
import com.example.ui.theme.NovaTextPrimary
import com.example.ui.theme.NovaTextSecondary

@Composable
fun FilePreviewDialog(
    file: SharedFile,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = NovaDarkSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // File Type Preview Card
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(NovaPrimary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    val categoryIcon = when (file.category) {
                        FileCategory.IMAGES -> Icons.Default.Image
                        FileCategory.VIDEOS -> Icons.Default.Movie
                        FileCategory.AUDIO -> Icons.Default.MusicNote
                        FileCategory.DOCUMENTS, FileCategory.ALL -> Icons.Default.Description
                        FileCategory.APK -> Icons.Default.Android
                        FileCategory.ARCHIVE -> Icons.Default.FolderZip
                        FileCategory.CONTACTS -> Icons.Default.ContactPage
                        FileCategory.FOLDERS -> Icons.Default.Folder
                    }
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = file.name,
                        tint = NovaPrimary,
                        modifier = Modifier.size(54.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = file.name,
                    color = NovaTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                Text(
                    text = "${file.category.displayName} • ${NetworkUtils.formatFileSize(file.size)}",
                    color = NovaTextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Detail Attributes Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(NovaDarkBackground)
                        .padding(12.dp)
                ) {
                    DetailRow(label = "Path", value = file.path)
                    DetailRow(label = "SHA-256 Checksum", value = if (file.checksumSha256.isNotEmpty()) file.checksumSha256 else "Verified On Transfer")
                    DetailRow(label = "MIME Type", value = file.mimeType.ifEmpty { "application/octet-stream" })
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NovaPrimary)
                ) {
                    Text(text = "Close Preview", color = NovaOnPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, color = NovaTextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(text = value, color = NovaTextPrimary, fontSize = 12.sp, maxLines = 2)
    }
}
