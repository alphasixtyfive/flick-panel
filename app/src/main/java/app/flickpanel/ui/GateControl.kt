package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.ha.Link

@Composable
internal fun GateControl(state: PanelUi, id: String, open: () -> Unit, modifier: Modifier) {
    val gate = state.entities[id]
    val ready = state.link == Link.ONLINE && gate?.gateWithOpenControl == true && gate.state == "closed" && id !in state.pending
    val label = when {
        state.link != Link.ONLINE || gate?.available != true -> "Gate unavailable"
        id in state.pending -> "Opening gate…"
        gate.state == "open" -> "Gate open"
        gate.state == "opening" -> "Gate opening…"
        gate.state == "closing" -> "Gate closing…"
        else -> "Open gate"
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
        state.error?.let {
            Surface(Modifier.widthIn(max = 224.dp), shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer) {
                Text(it, Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
        Button(onClick = open, enabled = ready, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
    }
}
