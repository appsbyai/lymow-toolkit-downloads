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
import com.lymow.toolkit.companion.data.LoginResult
import com.lymow.toolkit.companion.data.SettingsStore
import com.lymow.toolkit.companion.data.ToolkitApi
import kotlinx.coroutines.launch

private enum class Step { SERVER, SETUP_PASSWORD, LOGIN, TOTP }

/**
 * Sign-in flow, fully native:
 * server address → (first run: create the dashboard password) → password →
 * optional 2FA code → session cookie stored on-device.
 */
@Composable
fun ConnectScreen(store: SettingsStore, onConnected: () -> Unit) {
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(Step.SERVER) }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8787") }
    var api by remember { mutableStateOf<ToolkitApi?>(null) }

    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var totpCode by remember { mutableStateOf("") }
    var totpStep by remember { mutableStateOf("") }

    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun fail(message: String) {
        busy = false
        error = message
    }

    fun connect(url: String) {
        busy = true
        error = null
        val client = ToolkitApi(url, store)
        scope.launch {
            try {
                if (!client.probe()) {
                    fail("No answer at $url — check the address and that the Toolkit is running.")
                    return@launch
                }
                val status = client.accessStatus()
                api = client
                busy = false
                step = if (status.configured) Step.LOGIN else Step.SETUP_PASSWORD
            } catch (e: Exception) {
                fail("Could not reach the Toolkit: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun finish(url: String) {
        scope.launch {
            store.saveServer(url)
            busy = false
            onConnected()
        }
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
            when (step) {
                Step.SERVER -> "Connect to your Lymow Toolkit server"
                Step.SETUP_PASSWORD -> "First run — create the dashboard password"
                Step.LOGIN -> "Sign in to the dashboard"
                Step.TOTP -> "Enter your 2FA code"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))

        when (step) {
            Step.SERVER -> {
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
                Spacer(Modifier.height(24.dp))
                PrimaryButton("Find server", busy, host.isNotBlank()) {
                    val url = ToolkitApi.normalize(host, port)
                    if (url.isEmpty()) error = "Enter the address of your Toolkit server."
                    else connect(url)
                }
            }

            Step.SETUP_PASSWORD -> {
                PasswordField(password, { password = it; error = null }, "New password (min 4 chars)", showPassword) { showPassword = !showPassword }
                Spacer(Modifier.height(12.dp))
                PasswordField(password2, { password2 = it; error = null }, "Repeat password", showPassword) { showPassword = !showPassword }
                Spacer(Modifier.height(24.dp))
                PrimaryButton("Create password", busy, password.length >= 4) {
                    if (password != password2) {
                        error = "The passwords don't match."
                        return@PrimaryButton
                    }
                    busy = true
                    error = null
                    scope.launch {
                        when (val r = api!!.setupPassword(password)) {
                            is LoginResult.Ok -> finish(api!!.baseUrlForDisplay())
                            is LoginResult.Failure -> fail(r.message)
                            else -> fail("Unexpected answer from the Toolkit.")
                        }
                    }
                }
            }

            Step.LOGIN -> {
                PasswordField(password, { password = it; error = null }, "Dashboard password", showPassword) { showPassword = !showPassword }
                Spacer(Modifier.height(24.dp))
                PrimaryButton("Sign in", busy, password.isNotBlank()) {
                    busy = true
                    error = null
                    scope.launch {
                        when (val r = api!!.login(password)) {
                            is LoginResult.Ok -> finish(api!!.baseUrlForDisplay())
                            is LoginResult.NeedTotp -> {
                                totpStep = r.step
                                busy = false
                                step = Step.TOTP
                            }
                            is LoginResult.Failure -> fail(r.message)
                        }
                    }
                }
            }

            Step.TOTP -> {
                OutlinedTextField(
                    value = totpCode,
                    onValueChange = { totpCode = it.filter(Char::isDigit).take(9); error = null },
                    label = { Text("Authenticator code (or recovery code)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(24.dp))
                PrimaryButton("Verify", busy, totpCode.isNotBlank()) {
                    busy = true
                    error = null
                    scope.launch {
                        when (val r = api!!.totp(totpStep, totpCode)) {
                            is LoginResult.Ok -> finish(api!!.baseUrlForDisplay())
                            is LoginResult.Failure -> fail(r.message)
                            else -> fail("Unexpected answer from the Toolkit.")
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = error != null) {
            Text(
                error.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        if (step == Step.SERVER) {
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
}

@Composable
private fun PrimaryButton(
    label: String,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = { if (!busy) onClick() },
        enabled = !busy && enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.size(12.dp))
            Text("Working…")
        } else {
            Text(label)
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggle: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggle) {
                Icon(
                    if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password",
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
