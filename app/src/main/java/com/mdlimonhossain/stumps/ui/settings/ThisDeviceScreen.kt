package com.mdlimonhossain.stumps.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mdlimonhossain.stumps.ui.designsystem.AppCard

/**
 * Stands in for the reference app's "Manage Devices" screen — but only shows THIS device,
 * rather than a list of every device signed into the account. That's not a shortcut we took;
 * it's a real limit of what's possible here: Firebase Authentication (the plain client-side SDK
 * this app uses) has no API for one device to ask "what other devices are signed into this
 * account?" or to remotely sign another one out. Doing that for real needs either the Firebase
 * Admin SDK running on a server we control, or Google's paid Identity Platform upgrade — neither
 * of which exists in this project. Rather than fake a device list that can't actually do
 * anything, this screen is honest about showing only what a device CAN know about itself.
 */
@Composable
fun ThisDeviceScreen(onBack: () -> Unit, onSignOut: () -> Unit) {
    val context = LocalContext.current
    val appVersion = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        TextButton(onClick = onBack) { Text("← ফিরে যাও") }
        Spacer(Modifier.height(8.dp))
        Text(text = "এই ডিভাইস", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Firebase-এর ফ্রি Authentication সিস্টেম দিয়ে অন্য কোনো ডিভাইস থেকে দূর থেকে লগআউট করানো সম্ভব না (এর জন্য আলাদা সার্ভার লাগে) — তাই এখানে শুধু এই ডিভাইসের তথ্যই দেখানো হচ্ছে।",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        AppCard(modifier = Modifier.fillMaxWidth()) {
            DeviceInfoRow("মডেল", "${Build.MANUFACTURER} ${Build.MODEL}")
            HorizontalDivider()
            DeviceInfoRow("Android ভার্সন", "Android ${Build.VERSION.RELEASE}")
            HorizontalDivider()
            DeviceInfoRow("Stumps ভার্সন", appVersion)
        }

        Spacer(Modifier.height(24.dp))
        Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("এই ডিভাইস থেকে Logout") }
    }
}

@Composable
private fun DeviceInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(text = label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontWeight = FontWeight.Medium)
    }
}
