package com.example.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NovaDarkBackground
import com.example.ui.theme.NovaDarkOutline
import com.example.ui.theme.NovaOnPrimary
import com.example.ui.theme.NovaPrimary
import com.example.ui.theme.NovaTextPrimary
import com.example.ui.theme.NovaTextSecondary

enum class NavTab(val title: String, val icon: ImageVector) {
    SEND("Send", Icons.Default.FileUpload),
    RECEIVE("Receive", Icons.Default.FileDownload),
    WEB_SHARE("Web Share", Icons.Default.Language),
    HISTORY("History", Icons.Default.History),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun BottomNavBar(
    currentTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        containerColor = NovaDarkBackground,
        contentColor = NovaTextPrimary
    ) {
        NavTab.values().forEach { tab ->
            val isSelected = currentTab == tab
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.title,
                        tint = if (isSelected) NovaOnPrimary else NovaTextSecondary
                    )
                },
                label = {
                    Text(
                        text = tab.title,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) NovaTextPrimary else NovaTextSecondary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        softWrap = false
                    )
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = NovaPrimary,
                    selectedIconColor = NovaOnPrimary,
                    unselectedIconColor = NovaTextSecondary,
                    selectedTextColor = NovaTextPrimary,
                    unselectedTextColor = NovaTextSecondary
                )
            )
        }
    }
}
