package com.lymow.toolkit.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.lymow.toolkit.companion.data.SettingsStore
import com.lymow.toolkit.companion.data.ThemeMode
import com.lymow.toolkit.companion.data.ToolkitApi
import kotlinx.coroutines.launch

private const val KOFI_URL = "https://ko-fi.com/lymow_toolkit"
private const val COMMUNITY_URL = "https://www.facebook.com/share/g/1Jc7YfPStf/"
private const val RELEASES_URL =
    "https://github.com/AppGuy77/lymow-toolkit-downloads/releases/latest"

@Composable
fun SettingsScreen(store: SettingsStore, api: ToolkitApi, onServerForgotten: () -> Unit) {
    val config by store.config.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var confirmForget by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        // Server & session
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Server", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    config?.serverUrl.orEmpty().ifBlank { "Not connected" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { confirmSignOut = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text("Sign out of the dashboard")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { confirmForget = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text("Forget server")
                }
            }
        }

        // Appearance
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                val modes = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
                val labels = listOf("System", "Light", "Dark")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = config?.themeMode == mode,
                            onClick = { scope.launch { store.setThemeMode(mode) } },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = modes.size,
                            ),
                        ) { Text(labels[index]) }
                    }
                }
            }
        }

        // Community & support
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Community & support", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                LinkRow("Latest Toolkit release", RELEASES_URL, uriHandler::openUri)
                LinkRow("Facebook group", COMMUNITY_URL, uriHandler::openUri)
                LinkRow("Support on Ko-fi", KOFI_URL, uriHandler::openUri)
            }
        }

        // Safety
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.padding(6.dp))
                Text(
                    "This app controls a real machine with spinning blades. Always supervise " +
                        "your mower and keep people, pets and hands clear. Independent " +
                        "community project — not affiliated with or endorsed by Lymow.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("The session on this phone ends; the server address is kept.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    scope.launch {
                        api.logout()
                        onServerForgotten()
                    }
                }) { Text("Sign out") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") }
            },
        )
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Forget this server?") },
            text = { Text("The saved address and session will be removed from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmForget = false
                    scope.launch {
                        api.logout()
                        store.forgetServer()
                        onServerForgotten()
                    }
                }) { Text("Forget") }
            },
            dismissButton = {
                TextButton(onClick = { confirmForget = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun LinkRow(label: String, url: String, open: (String) -> Unit) {
    TextButton(onClick = { open(url) }, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label)
            Icon(Icons.Default.OpenInNew, contentDescription = null)
        }
    }
}
