package com.lymow.toolkit.companion.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.lymow.toolkit.companion.data.SettingsStore
import com.lymow.toolkit.companion.data.ToolkitClient
import kotlinx.coroutines.launch

/**
 * First-run (and re-connect) screen. The whole app keys off one thing: the
 * address of the Lymow Toolkit server running on the user's network, e.g.
 * http://192.168.1.50:8787
 */
@Composable
fun ConnectScreen(store: SettingsStore, onConnected: () -> Unit) {
    val config by store.config.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8787") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var prefilled by remember { mutableStateOf(false) }

    // Prefill once from stored config when returning to this screen.
    val cfg = config
    if (!prefilled && cfg != null && cfg.serverUrl.isNotBlank()) {
        prefilled = true
        val withoutScheme = cfg.serverUrl.substringAfter("://")
        host = withoutScheme.substringBefore(":").substringBefore("/")
        port = Regex(":(\\d+)").find(withoutScheme)?.groupValues?.get(1) ?: "8787"
        password = cfg.password
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Grass,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("Lymow Companion", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Connect to your Lymow Toolkit server",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = host,
            onValueChange = { host = it; error = null },
            label = { Text("Server address") },
            placeholder = { Text("192.168.1.50") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = port,
            onValueChange = { port = it.filter(Char::isDigit); error = null },
            label = { Text("Port") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Dashboard password (optional)") },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        AnimatedVisibility(visible = error != null) {
            Text(
                error.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                val url = ToolkitClient.normalize(host, port)
                if (url.isEmpty()) {
                    error = "Enter the address of your Toolkit server."
                    return@Button
                }
                connecting = true
                error = null
                scope.launch {
                    val reachable = ToolkitClient(url).probe()
                    if (reachable) {
                        store.saveServer(url, password)
                        connecting = false
                        onConnected()
                    } else {
                        connecting = false
                        error = "No answer at $url — check the address and that the Toolkit is running."
                    }
                }
            },
            enabled = !connecting && host.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.size(12.dp))
                Text("Connecting…")
            } else {
                Text("Connect")
            }
        }

        Spacer(Modifier.height(28.dp))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    "The address is shown when you launch the Toolkit — it is the same " +
                        "address you open in a browser, e.g. http://192.168.1.50:8787. " +
                        "Your phone must be on the same network.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
