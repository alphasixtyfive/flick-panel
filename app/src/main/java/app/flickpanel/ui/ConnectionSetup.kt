package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi

@Composable
internal fun ConnectionForm(
    state: PanelUi,
    connect: (String, String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var url by remember(state.serverAddress) { mutableStateOf(state.serverAddress.orEmpty()) }
    var token by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val canConnect = url.isNotBlank() && token.isNotBlank() && !state.busy
    val submit = {
        if (canConnect) {
            focus.clearFocus()
            connect(url, token, pin, confirm)
        }
    }
    Box(modifier.fillMaxWidth().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 540.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (state.configured) "Home Assistant connection" else "Flick Panel", style = MaterialTheme.typography.headlineMedium)
            if (!state.configured) Text("Connect with your server address and a long-lived access token.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(url, { url = it }, label = { Text("Home Assistant URL") },
                placeholder = { Text("https://home.example") }, singleLine = true, enabled = !state.busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(token, { token = it }, label = { Text("Long-lived access token") }, singleLine = true,
                enabled = !state.busy, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password,
                    imeAction = if (state.configured) ImeAction.Done else ImeAction.Next),
                keyboardActions = KeyboardActions(onDone = { submit() }), modifier = Modifier.fillMaxWidth())
            if (!state.configured) {
                Text("Choose a setup PIN to protect editing.", style = MaterialTheme.typography.titleSmall)
                PinField(pin, { pin = it }, "Setup PIN", enabled = !state.busy)
                PinField(confirm, { confirm = it }, "Confirm PIN", enabled = !state.busy, submit = submit)
            }
            ErrorText(state.error)
            Button(onClick = submit, enabled = canConnect) {
                Text(if (state.busy) "Connecting…" else "Connect")
            }
        }
    }
}

@Composable
private fun PinField(value: String, change: (String) -> Unit, label: String, enabled: Boolean, submit: (() -> Unit)? = null) {
    OutlinedTextField(value, { change(it.filter { c -> c in '0'..'9' }.take(8)) },
        label = { Text(label) }, singleLine = true, enabled = enabled, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword,
            imeAction = if (submit == null) ImeAction.Next else ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit?.invoke() }),
        modifier = Modifier.fillMaxWidth())
}

@Composable
internal fun UnlockEditor(state: PanelUi, unlock: (String) -> Unit, cancel: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(modifier = Modifier.widthIn(max = 304.dp), onDismissRequest = cancel, title = { Text("Unlock setup") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (pin.isEmpty()) "Enter PIN" else "●".repeat(pin.length),
                modifier = Modifier.padding(bottom = 7.dp).clearAndSetSemantics {
                    contentDescription = if (pin.isEmpty()) "Enter PIN" else "${pin.length} digits entered"
                }, style = MaterialTheme.typography.titleLarge)
            ErrorText(state.error)
            val keyPadding = PaddingValues(vertical = 6.dp)
            for (digits in listOf("123", "456", "789", " 0⌫")) {
                Row(Modifier.widthIn(max = 220.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (digit in digits) {
                        val key = Modifier.weight(1f).heightIn(min = 52.dp)
                        when (digit) {
                            ' ' -> Spacer(key)
                            '⌫' -> FilledTonalButton(onClick = { pin = pin.dropLast(1) }, modifier = key, contentPadding = keyPadding,
                                enabled = pin.isNotEmpty() && !state.busy) {
                                Icon(Icons.AutoMirrored.Rounded.Backspace, "Delete last digit")
                            }
                            else -> FilledTonalButton(onClick = { pin += digit }, modifier = key, contentPadding = keyPadding,
                                enabled = pin.length < 8 && !state.busy) {
                                Text(digit.toString(), style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
            }
        }
    }, confirmButton = {
        Button(onClick = {
            val submitted = pin
            pin = ""
            unlock(submitted)
        }, enabled = !state.busy && pin.length >= 4) {
            Text(if (state.busy) "Checking…" else "Unlock")
        }
    }, dismissButton = { TextButton(onClick = cancel) { Text("Cancel") } })
}

@Composable
internal fun ErrorText(message: String?, modifier: Modifier = Modifier) {
    if (message != null) {
        Text(message, modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error)
    }
}
