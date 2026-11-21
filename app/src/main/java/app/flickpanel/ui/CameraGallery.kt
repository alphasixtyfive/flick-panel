package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.config.TileKind

@Composable
internal fun CameraGallery(state: PanelUi, model: PanelViewModel, columns: Int, modifier: Modifier) {
    val cameras = remember(state.config.favourites) {
        state.config.favourites.filter { it.kind == TileKind.CAMERA }.sortedByDescending { it.showOnHome }
    }
    if (cameras.isEmpty()) {
        Column(modifier) { EmptyState("No cameras pinned", "Add a camera in setup.") }
        return
    }
    LazyVerticalGrid(GridCells.Fixed(columns), modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(tileGap), verticalArrangement = Arrangement.spacedBy(tileGap),
        contentPadding = PaddingValues(bottom = 20.dp)) {
        items(cameras, key = { it.entity }) { camera ->
            CameraTile(camera, state, model, Modifier.fillMaxWidth(), widescreen = true)
        }
    }
}
