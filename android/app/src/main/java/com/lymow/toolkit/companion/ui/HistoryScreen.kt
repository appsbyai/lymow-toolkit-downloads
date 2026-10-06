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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lymow.toolkit.companion.data.ApiException
import com.lymow.toolkit.companion.data.MowRecord
import com.lymow.toolkit.companion.data.MowTotals
import com.lymow.toolkit.companion.data.ToolkitApi
import com.lymow.toolkit.companion.data.ZoneFreshness
import kotlinx.coroutines.launch

/** Native mow history: lifetime totals, recent mows and per-zone freshness. */
@Composable
fun HistoryScreen(api: ToolkitApi, onAuthRequired: () -> Unit) {
    val scope = rememberCoroutineScope()

    var totals by remember { mutableStateOf<MowTotals?>(null) }
    var records by remember { mutableStateOf<List<MowRecord>?>(null) }
    var zones by remember { mutableStateOf<List<ZoneFreshness>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        try {
            totals = runCatching { api.mowTotals() }.getOrNull()
            records = api.mowHistory()
            zones = runCatching { api.zoneFreshness() }.getOrDefault(emptyList())
            error = null
        } catch (e: ApiException) {
            if (e.code == 401) onAuthRequired() else error = e.message
        } catch (_: Exception) {
            error = "Could not load the history."
        }
        loading = false
    }

    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("History", style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = { scope.launch { loading = true; load() } }, enabled = !loading) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }
        Spacer(Modifier.height(8.dp))

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error ?: "", color = MaterialTheme.colorScheme.error)
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Lifetime totals
                totals?.let { t ->
                    if (t.totalAreaM2 != null || t.totalTimeMin != null || t.mowCount != null) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                ) {
                                    t.totalAreaM2?.let {
                                        Stat("%,.0f m²".format(it), "total area")
                                    }
                                    t.totalTimeMin?.let {
                                        Stat("%.1f h".format(it / 60.0), "mow time")
                                    }
                                    t.mowCount?.let { Stat("$it", "mows") }
                                }
                            }
                        }
                    }
                }

                // Per-zone freshness
                if (zones.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        Text("Zone freshness", style = MaterialTheme.typography.titleMedium)
                    }
                    items(zones) { z ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(z.name, Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    z.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when (z.level) {
                                        0 -> MaterialTheme.colorScheme.primary
                                        2 -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }

                // Recent mows
                if (!records.isNullOrEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        Text("Recent mows", style = MaterialTheme.typography.titleMedium)
                    }
                    items(records!!) { r ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(r.title, style = MaterialTheme.typography.bodyMedium)
                                val meta = listOfNotNull(
                                    r.durationMin?.let { "$it min" },
                                    r.areaM2?.let { "%,.0f m²".format(it) },
                                    r.subtitle.ifBlank { null },
                                ).joinToString(" · ")
                                if (meta.isNotBlank()) {
                                    Text(
                                        meta,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                if (records.isNullOrEmpty() && zones.isEmpty() && totals == null) {
                    item {
                        Text(
                            "No history yet — it builds up after the first mows.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
