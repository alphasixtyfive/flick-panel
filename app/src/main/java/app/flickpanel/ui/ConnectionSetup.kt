package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
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
    modifier: Modifier = Modifier,
    onInput: () -> Unit = {},
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
            OutlinedTextField(url, {
                url = it
                onInput()
            }, label = { Text("Home Assistant URL") },
                placeholder = { Text("https://home.example") }, singleLine = true, enabled = !state.busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(token, {
                token = it
                onInput()
            }, label = { Text("Long-lived access token") }, singleLine = true,
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
internal fun ErrorText(message: String?, modifier: Modifier = Modifier) {
    if (message != null) {
        Text(message, modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error)
    }
}
