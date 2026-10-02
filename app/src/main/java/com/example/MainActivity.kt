package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import com.example.data.repository.SettingsRepository
import com.example.ui.navigation.MainAppNavigation
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.NovaShareTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission result handled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Proactively request POST_NOTIFICATIONS on Android 13+ for transfer progress & request banners
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val settingsRepo = remember { SettingsRepository(applicationContext) }
            val themeMode by settingsRepo.themeMode.collectAsState(initial = AppThemeMode.SYSTEM)
            NovaShareTheme(themeMode = themeMode) {
                MainAppNavigation(settingsRepo = settingsRepo)
            }
        }
    }
}

