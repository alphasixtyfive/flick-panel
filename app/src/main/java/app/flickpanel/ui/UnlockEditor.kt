package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.flickpanel.PanelUi

private val keypadRows = listOf("123", "456", "789", " 0⌫")
private val keypadWidth = 322.dp

@Composable
internal fun UnlockEditor(state: PanelUi, unlock: (String) -> Unit, cancel: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    val submit = {
        val submitted = pin
        pin = ""
        unlock(submitted)
    }
    Dialog(onDismissRequest = cancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
            val sideBySide = maxWidth >= 420.dp * fontScale && maxHeight < 480.dp
            val compact = maxHeight < 360.dp
            Surface(Modifier.widthIn(max = if (sideBySide) 640.dp else 448.dp).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.extraLarge) {
                if (sideBySide) {
                    Row(Modifier.verticalScroll(rememberScrollState()).padding(
                        horizontal = if (compact) 20.dp else 28.dp,
                        vertical = if (compact) 12.dp else 28.dp,
                    ),
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 20.dp else 32.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 22.dp)) {
                            PinPrompt(pin.length, state.error)
                            PinActions(state.busy, pin.length >= 4, cancel, submit)
                        }
                        PinKeys(pin.length, state.busy, if (compact) 48.dp else 52.dp, { pin += it },
                            { pin = pin.dropLast(1) }, Modifier.weight(1f), compact = compact)
                    }
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(
                        horizontal = if (compact) 18.dp else 28.dp, vertical = if (compact) 16.dp else 32.dp,
                    ), verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        PinPrompt(pin.length, state.error)
                        PinKeys(pin.length, state.busy, if (compact) 48.dp else 62.dp, { pin += it }, { pin = pin.dropLast(1) },
                            Modifier.widthIn(max = keypadWidth).fillMaxWidth(), compact = compact)
                        PinActions(state.busy, pin.length >= 4, cancel, submit, Modifier.widthIn(max = keypadWidth))
                    }
                }
            }
        }
    }
}

@Composable
private fun PinPrompt(length: Int, error: String?) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Unlock setup", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center)
        Box(Modifier.heightIn(min = 38.dp), contentAlignment = Alignment.Center) {
            Text(if (length == 0) "Enter PIN" else "•".repeat(length),
                Modifier.clearAndSetSemantics { contentDescription = if (length == 0) "Enter PIN" else "$length digits entered" },
                style = if (length == 0) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.headlineSmall,
                letterSpacing = if (length == 0) 0.sp else 5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (error != null) ErrorText(error)
    }
}

@Composable
private fun PinKeys(
    length: Int, busy: Boolean, keyHeight: Dp, add: (Char) -> Unit, delete: () -> Unit, modifier: Modifier,
    compact: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp)) {
        for (digits in keypadRows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (digit in digits) {
                    val key = Modifier.weight(1f).heightIn(min = keyHeight)
                    if (digit == ' ') {
                        Spacer(key)
                    } else {
                        val deleting = digit == '⌫'
                        FilledTonalButton(
                            onClick = { if (deleting) delete() else add(digit) },
                            modifier = key, contentPadding = PaddingValues(horizontal = 4.dp, vertical = if (compact) 2.dp else 8.dp),
                            shape = MaterialTheme.shapes.medium,
                            enabled = !busy && if (deleting) length > 0 else length < 8,
                        ) {
                            if (deleting) Icon(Icons.AutoMirrored.Rounded.Backspace, "Delete last digit")
                            else Text(digit.toString(), style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinActions(busy: Boolean, valid: Boolean, cancel: () -> Unit, submit: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = cancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
        Button(onClick = submit, enabled = valid && !busy, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(if (busy) "Checking…" else "Unlock")
        }
    }
}
