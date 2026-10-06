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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lymow.toolkit.companion.data.ApiException
import com.lymow.toolkit.companion.data.Telemetry
import com.lymow.toolkit.companion.data.ToolkitApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Native live status + quick actions, straight from /api/telemetry. */
@Composable
fun HomeScreen(api: ToolkitApi, onAuthRequired: () -> Unit) {
    val scope = rememberCoroutineScope()

    var tele by remember { mutableStateOf<Telemetry?>(null) }
    var loading by remember { mutableStateOf(true) }
    var unreachable by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<String?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        try {
            tele = api.telemetry()
            unreachable = false
        } catch (e: ApiException) {
            if (e.code == 401) onAuthRequired() else unreachable = true
        } catch (_: Exception) {
            unreachable = true
        }
        loading = false
    }

    // Poll while visible: fast when the mower is working, slower when idle.
    LaunchedEffect(Unit) {
        while (true) {
            refresh()
            delay(if (tele?.isMowing == true) 5_000 else 15_000)
        }
    }

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
            IconButton(onClick = { scope.launch { loading = true; refresh() } }, enabled = !loading) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        // Fault banner
        tele?.takeIf { it.hasFault }?.let { t ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        t.errorCodes.joinToString(", ").ifBlank { t.statusLabel },
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        // Hero status card
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (tele?.isDocked == true) Icons.Default.Home else Icons.Default.Grass,
                    contentDescription = null,
                    tint = when {
                        tele?.hasFault == true -> MaterialTheme.colorScheme.error
                        tele?.isMowing == true -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.size(16.dp))
                Column {
                    when {
                        loading && tele == null ->
                            Text("Checking status…", style = MaterialTheme.typography.titleLarge)
                        unreachable -> {
                            Text("Offline", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Toolkit server not reachable",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        else -> {
                            Text(tele?.statusLabel ?: "—", style = MaterialTheme.typography.titleLarge)
                            Text(
                                buildString {
                                    if (tele?.online == false) append("Link stale · ")
                                    if (tele?.asleep == true) append("Asleep · ")
                                    tele?.cleanPercent?.let { append("This mow $it%") }
                                }.trimEnd(' ', '·'),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                tele?.battery?.let { pct ->
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
        tele?.let { t ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                t.rtkLabel?.let { InfoChip(Icons.Default.SatelliteAlt, "RTK: $it") }
                t.wifiLabel?.let { InfoChip(Icons.Default.Wifi, it) }
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
                    if (tele?.isPaused == true) {
                        ActionButton("Resume", Icons.Default.PlayArrow, Modifier.weight(1f)) {
                            pendingAction = "resume"
                        }
                    } else {
                        ActionButton(
                            "Mow", Icons.Default.PlayArrow, Modifier.weight(1f),
                            enabled = tele != null && !tele!!.isMowing,
                        ) { pendingAction = "mow" }
                    }
                    ActionButton(
                        "Pause", Icons.Default.Pause, Modifier.weight(1f),
                        enabled = tele?.isMowing == true,
                    ) { pendingAction = "pause" }
                    ActionButton(
                        "Dock", Icons.Default.Home, Modifier.weight(1f),
                        enabled = tele != null && tele?.isDocked != true,
                    ) { pendingAction = "dock" }
                }
                actionMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    // Confirmation dialog for blade actions
    pendingAction?.let { action ->
        val verb = when (action) {
            "mow" -> "Start mowing"; "pause" -> "Pause"; "dock" -> "Send to dock"
            else -> action.replaceFirstChar { c -> c.uppercase() }
        }
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("$verb the mower?") },
            text = {
                Text("Make sure the area is clear of people, pets and obstacles before continuing.")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingAction = null
                    actionMessage = null
                    scope.launch {
                        try {
                            if (action == "mow") api.startMow() else api.sendCommand(action)
                            actionMessage = "“$verb” sent."
                            delay(1_500)
                            refresh()
                        } catch (e: ApiException) {
                            if (e.code == 401) onAuthRequired()
                            else actionMessage = e.message ?: "Command failed"
                        } catch (e: Exception) {
                            actionMessage = "Command failed: ${e.message ?: "no answer"}"
                        }
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
private fun InfoChip(icon: ImageVector, label: String) {
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
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
