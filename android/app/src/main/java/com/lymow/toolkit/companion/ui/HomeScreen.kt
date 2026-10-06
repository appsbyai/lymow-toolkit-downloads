package com.lymow.toolkit.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lymow.toolkit.companion.data.MowerStatus
import com.lymow.toolkit.companion.data.SettingsStore
import com.lymow.toolkit.companion.data.ToolkitClient
import kotlinx.coroutines.launch

/** Native at-a-glance status plus quick actions, with the full web UI one tap away. */
@Composable
fun HomeScreen(store: SettingsStore, onOpenDashboard: () -> Unit) {
    val config by store.config.collectAsState(initial = null)
    val url = config?.serverUrl.orEmpty()
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf<MowerStatus?>(null) }
    var loading by remember { mutableStateOf(true) }
    var pendingAction by remember { mutableStateOf<String?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        if (url.isBlank()) return
        scope.launch {
            loading = true
            status = ToolkitClient(url).fetchStatus()
            loading = false
        }
    }

    LaunchedEffect(url) { refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Your mower", style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = { refresh() }, enabled = !loading) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        // Hero status card
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Grass,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.size(16.dp))
                Column {
                    when {
                        loading -> Text("Checking status…", style = MaterialTheme.typography.titleLarge)
                        status?.reachable != true -> {
                            Text("Offline", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Toolkit server not reachable",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        status?.nativeApi == true -> {
                            Text(
                                status?.state?.replaceFirstChar { it.uppercase() } ?: "Connected",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            status?.batteryPercent?.let {
                                Text(
                                    "Battery $it%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        else -> {
                            Text("Connected", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Live details in the Dashboard tab",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                status?.batteryPercent?.let { pct ->
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { pct / 100f },
                            modifier = Modifier.size(52.dp),
                        )
                        Text("$pct%", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Signal chips
        if (status?.nativeApi == true) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                status?.rtkFix?.let { Chip(Icons.Default.SatelliteAlt, "RTK: $it") }
                status?.wifiDbm?.let { Chip(Icons.Default.Wifi, "$it dBm") }
                status?.nextMow?.let { Chip(Icons.Default.Schedule, "Next: $it") }
            }
        }

        // Quick actions — real blades, so every action is confirmed first.
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Quick actions", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Controls a real machine with spinning blades — keep people and pets clear.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("Start", Icons.Default.PlayArrow, Modifier.weight(1f)) {
                        pendingAction = "start"
                    }
                    ActionButton("Pause", Icons.Default.Pause, Modifier.weight(1f)) {
                        pendingAction = "pause"
                    }
                    ActionButton("Dock", Icons.Default.PowerSettingsNew, Modifier.weight(1f)) {
                        pendingAction = "dock"
                    }
                }
                actionMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Gateway to the full dashboard
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Full dashboard", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Live map, scheduling, mow-history calendar, freshness and RTK heat maps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onOpenDashboard, modifier = Modifier.fillMaxWidth()) {
                    Text("Open dashboard")
                }
            }
        }
    }

    // Confirmation dialog for blade actions
    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("${action.replaceFirstChar { c -> c.uppercase() }} the mower?") },
            text = {
                Text("Make sure the area is clear of people, pets and obstacles before continuing.")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingAction = null
                    actionMessage = null
                    scope.launch {
                        val ok = ToolkitClient(url).sendAction(action)
                        actionMessage = if (ok) {
                            "“${action.replaceFirstChar { c -> c.uppercase() }}” sent."
                        } else {
                            "The Toolkit did not accept the command — use the Dashboard tab."
                        }
                        refresh()
                    }
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun Chip(icon: ImageVector, label: String) {
    AssistChip(
        onClick = {},
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
    )
}

@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FilledTonalButton(onClick = onClick, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
