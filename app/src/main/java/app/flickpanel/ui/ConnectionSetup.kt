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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.flickpanel.ConnectionField
import app.flickpanel.PanelUi
import app.flickpanel.validateConnectionInput

@Composable
internal fun ConnectionForm(
    state: PanelUi,
    connect: (String, String, String, String) -> Unit,
    modifier: Modifier = Modifier,
    onInput: () -> Unit,
) {
    var url by remember(state.serverAddress) { mutableStateOf(state.serverAddress.orEmpty()) }
    var token by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var errors by remember { mutableStateOf(emptyMap<ConnectionField, String>()) }
    val focus = LocalFocusManager.current
    val fields = remember { ConnectionField.entries.associateWith { FocusRequester() } }
    fun changed(field: ConnectionField) {
        errors = errors - field
        if (field == ConnectionField.PIN) errors = errors - ConnectionField.CONFIRMATION
        onInput()
    }
    val submit: () -> Unit = {
        if (!state.busy) {
            val result = validateConnectionInput(url, token, pin, confirm, needsPin = !state.configured)
            errors = result.errors
            val firstInvalid = errors.keys.firstOrNull()
            if (firstInvalid == null) {
                focus.clearFocus()
                connect(url, token, pin, confirm)
            } else {
                fields.getValue(firstInvalid).requestFocus()
            }
        }
    }
    Box(modifier.fillMaxWidth().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 540.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!state.configured) {
                Text("Flick Panel", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
                Text("Connect with your server address and a long-lived access token.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val addressError = errors[ConnectionField.ADDRESS]
            OutlinedTextField(url, {
                url = it
                changed(ConnectionField.ADDRESS)
            }, label = { Text("Home Assistant URL") },
                placeholder = { Text("https://home.example") }, singleLine = true, enabled = !state.busy,
                isError = addressError != null, supportingText = fieldError(addressError),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().focusRequester(fields.getValue(ConnectionField.ADDRESS)))
            val tokenError = errors[ConnectionField.TOKEN]
            OutlinedTextField(token, {
                token = it
                changed(ConnectionField.TOKEN)
            }, label = { Text("Long-lived access token") }, singleLine = true,
                enabled = !state.busy, visualTransformation = PasswordVisualTransformation(),
                isError = tokenError != null,
                supportingText = fieldError(tokenError) ?: {
                    Text("In Home Assistant, open your profile, then Security → Long-lived access tokens.")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password,
                    imeAction = if (state.configured) ImeAction.Done else ImeAction.Next),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(fields.getValue(ConnectionField.TOKEN)))
            if (!state.configured) {
                Text("Choose a 4 to 8 digit PIN to protect setup.", style = MaterialTheme.typography.titleSmall)
                PinField(pin, {
                    pin = it
                    changed(ConnectionField.PIN)
                }, "Setup PIN", fields.getValue(ConnectionField.PIN), errors[ConnectionField.PIN], enabled = !state.busy)
                PinField(confirm, {
                    confirm = it
                    changed(ConnectionField.CONFIRMATION)
                }, "Confirm PIN", fields.getValue(ConnectionField.CONFIRMATION), errors[ConnectionField.CONFIRMATION],
                    enabled = !state.busy, submit = submit)
            }
            ErrorText(state.error)
            Button(onClick = submit, enabled = !state.busy) {
                Text(if (state.busy) "Connecting…" else "Connect")
            }
        }
    }
}

@Composable
private fun PinField(
    value: String, change: (String) -> Unit, label: String, focus: FocusRequester, error: String?,
    enabled: Boolean, submit: (() -> Unit)? = null,
) {
    OutlinedTextField(value, { change(it.filter { c -> c in '0'..'9' }.take(8)) },
        label = { Text(label) }, singleLine = true, enabled = enabled, visualTransformation = PasswordVisualTransformation(),
        isError = error != null, supportingText = fieldError(error),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword,
            imeAction = if (submit == null) ImeAction.Next else ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit?.invoke() }),
        modifier = Modifier.fillMaxWidth().focusRequester(focus))
}

private fun fieldError(message: String?): (@Composable () -> Unit)? =
    if (message == null) null else ({ ErrorText(message) })

@Composable
internal fun ErrorText(message: String?, modifier: Modifier = Modifier) {
    if (message != null) {
        Text(message, modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error)
    }
}
