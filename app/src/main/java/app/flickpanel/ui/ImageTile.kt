package app.flickpanel.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelViewModel
import kotlinx.coroutines.CancellationException

@Composable
internal fun ImageTile(id: String, title: String, state: String?, available: Boolean, model: PanelViewModel, modifier: Modifier) {
    val revision = state.orEmpty().take(120)
    var bitmap by remember(id) { mutableStateOf<Bitmap?>(model.cachedSnapshot(id, revision)) }
    var failed by remember(id, revision) { mutableStateOf(false) }
    LaunchedEffect(id, revision, available) {
        if (!available) return@LaunchedEffect
        try {
            val loaded = model.snapshot(id, revision)
            if (loaded != null) bitmap = loaded
            failed = loaded == null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        }
    }

    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    Surface(modifier, shape = panelCardShape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxSize().padding(tileContentPadding), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            TileTitle(title)
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                if (image != null) Image(image, null, Modifier.matchParentSize(), contentScale = ContentScale.Fit)
                else TileStatus(if (!available || failed) "Image unavailable" else "Loading image…")
            }
        }
    }
}
