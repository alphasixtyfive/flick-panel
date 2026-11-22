package app.flickpanel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.ha.Link
import app.flickpanel.ha.MediaTransport

@Composable
internal fun MusicPlayerScreen(id: String, state: PanelUi, model: PanelViewModel) {
    val entity = state.entities[id]
    val label = state.entityName(id)
    val playback = MusicPlayback(label, entity, state.link == Link.ONLINE && entity?.available == true, id in state.pending)
    val artwork = rememberPanelImage(playback.artworkUrl, model::cachedArtwork, model::loadArtwork)
    val library = remember(id) { MusicLibraryState() }
    var browsing by rememberSaveable(id) { mutableStateOf(false) }
    val transport: (MediaTransport) -> Unit = { model.mediaTransport(id, it) }
    val volume = if (entity == null) null else playback.volume?.let { level ->
        SpeakerVolume(
            level, playback.muted, playback.ready, playback.pending,
            change = { model.setVolume(entity, it) }, mute = { model.setSpeakerMuted(entity, it) },
        )
    }

    BackHandler(enabled = !browsing, onBack = model::closeMusic)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize().panelEntrance(browsing, state.config.reduceMotion)) {
            if (browsing) {
                MusicLibraryScreen(id, library, model, playback, artwork, transport, volume) { browsing = false }
            } else {
                NowPlaying(
                    playback, artwork, browse = if (playback.canBrowse) ({ browsing = true }) else null,
                    close = model::closeMusic,
                    transport = transport, seek = { model.seekMusic(id, it) }, volume = volume,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun MusicLibraryScreen(
    id: String, library: MusicLibraryState, model: PanelViewModel, playback: MusicPlayback,
    artwork: ImageBitmap?, transport: (MediaTransport) -> Unit, volume: SpeakerVolume?, showPlayer: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val wide = minOf(maxWidth, 1080.dp) >= 760.dp * fontScale
        val sidePadding = if (wide) 28.dp else 16.dp
        Column(Modifier.widthIn(max = 1080.dp).fillMaxSize()) {
            ViewerTopAppBar("Music library", "Close music", model::closeMusic,
                windowInsets = WindowInsets(0, 0, 0, 0), contentGutter = sidePadding)
            MusicBrowser(
                id, library, model, wide, playback, artwork, transport, volume, showPlayer,
                Modifier.weight(1f).padding(horizontal = sidePadding).padding(top = 8.dp, bottom = 12.dp),
            )
        }
    }
}
