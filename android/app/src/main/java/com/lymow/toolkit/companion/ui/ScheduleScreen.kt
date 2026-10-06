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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lymow.toolkit.companion.data.ApiException
import com.lymow.toolkit.companion.data.ScheduleTask
import com.lymow.toolkit.companion.data.ToolkitApi
import kotlinx.coroutines.launch

private val FREQS = listOf("weekly", "once", "even", "odd")
private val DAY_LABELS = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")

/** Native scheduler: list, enable/disable, run now, skip, delete, create. */
@Composable
fun ScheduleScreen(api: ToolkitApi, onAuthRequired: () -> Unit) {
    val scope = rememberCoroutineScope()

    var tasks by remember { mutableStateOf<List<ScheduleTask>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<ScheduleTask?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<ScheduleTask?>(null) }

    suspend fun load() {
        try {
            tasks = api.schedules()
            error = null
        } catch (e: ApiException) {
            if (e.code == 401) onAuthRequired() else error = e.message
        } catch (_: Exception) {
            error = "Could not load the schedules."
        }
        loading = false
    }

    fun act(action: suspend () -> Unit, done: String) {
        scope.launch {
            message = null
            try {
                action()
                message = done
            } catch (e: ApiException) {
                if (e.code == 401) onAuthRequired() else message = e.message
            } catch (_: Exception) {
                message = "That did not work — try again."
            }
            load()
        }
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editing = ScheduleTask()
                    showEditor = true
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New schedule") },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Schedules", style = MaterialTheme.typography.headlineSmall)
                IconButton(onClick = { scope.launch { loading = true; load() } }, enabled = !loading) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
            message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
            }

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error)
                }
                tasks.isNullOrEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No schedules yet — tap “New schedule”.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tasks!!, key = { it.id }) { task ->
                        ScheduleCard(
                            task = task,
                            onToggle = { enabled ->
                                act({ api.saveSchedule(task.copy(enabled = enabled)) },
                                    if (enabled) "Enabled." else "Paused.")
                            },
                            onRun = { act({ api.runScheduleNow(task.id) }, "Mow started.") },
                            onSkip = { act({ api.skipSchedule(task.id) }, "Next run skipped.") },
                            onDelete = { confirmDelete = task },
                        )
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }

    if (showEditor && editing != null) {
        ScheduleEditorDialog(
            initial = editing!!,
            onDismiss = { showEditor = false },
            onSave = { task ->
                showEditor = false
                act({ api.saveSchedule(task) }, "Schedule saved.")
            },
        )
    }

    confirmDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this schedule?") },
            text = { Text("“${task.label.ifBlank { task.freqText }} · ${task.timeText}” will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    act({ api.deleteSchedule(task.id) }, "Schedule deleted.")
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ScheduleCard(
    task: ScheduleTask,
    onToggle: (Boolean) -> Unit,
    onRun: () -> Unit,
    onSkip: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    task.label.ifBlank { "Mow" },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "${task.freqText} · ${task.timeText}" +
                        (task.zoneNames.takeIf { it.isNotEmpty() }
                            ?.let { " · ${it.joinToString(", ")}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                task.lastRunDate?.let {
                    Text(
                        "Last ran $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onRun) { Icon(Icons.Default.PlayArrow, "Run now") }
            IconButton(onClick = onSkip) { Icon(Icons.Default.SkipNext, "Skip next") }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
            }
            Switch(checked = task.enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun ScheduleEditorDialog(
    initial: ScheduleTask,
    onDismiss: () -> Unit,
    onSave: (ScheduleTask) -> Unit,
) {
    var label by remember { mutableStateOf(initial.label) }
    var freq by remember { mutableStateOf(initial.freq) }
    var date by remember { mutableStateOf(initial.date ?: "") }
    var days by remember { mutableStateOf(initial.days.toSet()) }
    var hour by remember { mutableIntStateOf(initial.timeMin / 60) }
    var minute by remember { mutableIntStateOf(initial.timeMin % 60) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id.isBlank()) "New schedule" else "Edit schedule") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("Repeat", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FREQS.forEach { f ->
                        FilterChip(
                            selected = freq == f,
                            onClick = { freq = f },
                            label = { Text(f.replaceFirstChar { c -> c.uppercase() }) },
                        )
                    }
                }
                if (freq == "once") {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        placeholder = { Text("2026-06-15") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (freq == "weekly") {
                    Spacer(Modifier.height(12.dp))
                    Text("Days", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DAY_LABELS.forEachIndexed { i, d ->
                            FilterChip(
                                selected = i in days,
                                onClick = {
                                    days = if (i in days) days - i else days + i
                                },
                                label = { Text(d, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Start time", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = hour.toString(),
                        onValueChange = { hour = it.toIntOrNull()?.coerceIn(0, 23) ?: 0 },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(80.dp),
                    )
                    Text(" : ", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = minute.toString().padStart(2, '0'),
                        onValueChange = { minute = it.toIntOrNull()?.coerceIn(0, 59) ?: 0 },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(80.dp),
                    )
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    freq == "once" && !Regex("\\d{4}-\\d{2}-\\d{2}").matches(date) ->
                        error = "Enter the date as YYYY-MM-DD."
                    freq == "weekly" && days.isEmpty() ->
                        error = "Pick at least one day."
                    else -> onSave(
                        initial.copy(
                            label = label.trim(),
                            freq = freq,
                            date = if (freq == "once") date else null,
                            days = days.sorted(),
                            timeMin = hour * 60 + minute,
                        ),
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
