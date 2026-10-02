package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.example.data.repository.SettingsRepository
import com.example.ui.navigation.MainAppNavigation
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.NovaShareTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsRepo = remember { SettingsRepository(applicationContext) }
            val themeMode by settingsRepo.themeMode.collectAsState(initial = AppThemeMode.SYSTEM)
            NovaShareTheme(themeMode = themeMode) {
                MainAppNavigation(settingsRepo = settingsRepo)
            }
        }
    }
}
