package com.mdlimonhossain.stumps.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mdlimonhossain.stumps.ui.designsystem.ListItemCard

/**
 * The Settings screen — matches the reference app's short list (Profile Edit / Manage Devices /
 * Logout). "Manage Devices" opens ThisDeviceScreen — see that file's doc comment for why it only
 * shows THIS device rather than a real cross-device session list.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenProfileEdit: () -> Unit, onOpenManageDevices: () -> Unit, onSignOut: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineLarge)
        TextButton(onClick = onBack) { Text("← ফিরে যাও") }
        Spacer(Modifier.height(16.dp))

        SettingsRow(title = "Profile Edit", onClick = onOpenProfileEdit)
        Spacer(Modifier.height(8.dp))
        SettingsRow(title = "Manage Devices", onClick = onOpenManageDevices)
        Spacer(Modifier.height(8.dp))
        SettingsRow(title = "Logout", titleColor = MaterialTheme.colorScheme.error, onClick = onSignOut)
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    titleColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    onClick: (() -> Unit)?
) {
    ListItemCard(
        title = title,
        subtitle = subtitle,
        titleColor = titleColor,
        onClick = onClick,
        trailing = { if (onClick != null) Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }
    )
}
