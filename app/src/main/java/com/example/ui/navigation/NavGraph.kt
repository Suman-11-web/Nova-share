package com.example.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.data.local.AppDatabase
import com.example.data.repository.DeviceRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.TransferRepository
import com.example.ui.components.AnimatedEntrySplashScreen
import com.example.ui.components.BottomNavBar
import com.example.ui.components.NavTab
import com.example.ui.screens.history.HistoryScreen
import com.example.ui.screens.history.HistoryViewModel
import com.example.ui.screens.receive.ReceiveScreen
import com.example.ui.screens.receive.ReceiveViewModel
import com.example.ui.screens.send.SendScreen
import com.example.ui.screens.send.SendViewModel
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.settings.SettingsViewModel
import com.example.ui.screens.webshare.WebShareScreen
import com.example.ui.screens.webshare.WebShareViewModel

@Composable
fun MainAppNavigation() {
    val context = LocalContext.current

    // Initialize Database & Repositories
    val database = remember { AppDatabase.getDatabase(context) }
    val transferRepo = remember { TransferRepository(database.transferDao()) }
    val deviceRepo = remember { DeviceRepository(database.deviceDao()) }
    val settingsRepo = remember { SettingsRepository(context) }

    // ViewModels
    val sendViewModel = remember { SendViewModel(transferRepo, deviceRepo) }
    val receiveViewModel = remember { ReceiveViewModel(transferRepo) }
    val webShareViewModel = remember { WebShareViewModel() }
    val historyViewModel = remember { HistoryViewModel(transferRepo) }
    val settingsViewModel = remember { SettingsViewModel(settingsRepo) }

    var showSplash by remember { mutableStateOf(true) }
    var currentTab by remember { mutableStateOf(NavTab.SEND) }

    if (showSplash) {
        AnimatedEntrySplashScreen(onSplashFinished = { showSplash = false })
    } else {
        Scaffold(
            bottomBar = {
                BottomNavBar(
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "tab_transition"
                ) { tab ->
                    when (tab) {
                        NavTab.SEND -> SendScreen(viewModel = sendViewModel)
                        NavTab.RECEIVE -> ReceiveScreen(viewModel = receiveViewModel)
                        NavTab.WEB_SHARE -> WebShareScreen(viewModel = webShareViewModel)
                        NavTab.HISTORY -> HistoryScreen(viewModel = historyViewModel)
                        NavTab.SETTINGS -> SettingsScreen(viewModel = settingsViewModel)
                    }
                }
            }
        }
    }
}
