package app.flickpanel.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.ha.Link

@Composable
internal fun GateControl(state: PanelUi, id: String, open: () -> Unit, modifier: Modifier) {
    val gate = state.entities[id]
    val pending = state.pending[id]
    val ready = state.link == Link.ONLINE && gate?.gateWithOpenControl == true && gate.state == "closed" && pending == null
    val label = when {
        state.link != Link.ONLINE || gate?.available != true -> "Gate unavailable"
        pending != null -> pending
        ready -> "Open gate"
        else -> gate.coverStatus()
    }
    Button(onClick = open, enabled = ready, modifier = modifier.heightIn(min = 48.dp)) { Text(label) }
}
