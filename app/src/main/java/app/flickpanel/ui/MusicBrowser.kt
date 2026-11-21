package app.flickpanel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelViewModel
import app.flickpanel.ha.MediaChoice
import app.flickpanel.ha.MediaTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun MusicBrowser(
    id: String, library: MusicLibraryState, model: PanelViewModel, wide: Boolean,
    playback: MusicPlayback, artwork: ImageBitmap?, transport: (MediaTransport) -> Unit, volume: SpeakerVolume?,
    showPlayer: () -> Unit, modifier: Modifier,
) {
    val root = library.content(null)
    val sources = remember(root) {
        root?.items.orEmpty().sortedBy {
            when {
                it.type == "music_assistant" && it.id == "playlists" -> 0
                it.type == "music_assistant" && it.id == "radio" -> 1
                else -> 2
            }
        }
    }
    val folder = library.path.lastOrNull()
    val content = if (folder == null) root?.copy(items = sources) else library.content(folder)
    val canGoUp = library.path.size > 1 || !wide && folder != null
    var retry by remember(id) { mutableIntStateOf(0) }
    var starting by remember(id) { mutableStateOf(false) }
    var playFailed by remember(folder) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun back() {
        if (starting) return
        if (canGoUp) library.up() else showPlayer()
    }

    fun play(item: MediaChoice) {
        if (!playback.ready || starting) return
        starting = true
        playFailed = false
        scope.launch {
            try {
                model.playMusic(id, item)
                showPlayer()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                playFailed = true
            } finally {
                starting = false
            }
        }
    }

    fun open(item: MediaChoice, fromRoot: Boolean = false) {
        if (starting) return
        if (item.expandable) library.enter(item, fromRoot) else play(item)
    }

    BackHandler(onBack = ::back)
    LaunchedEffect(id, retry) { library.load(null) { model.browseMusic(id, it) } }
    LaunchedEffect(id, folder, retry) {
        if (folder != null) library.load(folder) { model.browseMusic(id, it) }
    }
    LaunchedEffect(wide, sources) {
        if (wide && library.path.isEmpty()) {
            sources.firstOrNull(MediaChoice::expandable)?.let { library.enter(it, fromRoot = true) }
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            if (wide && sources.isNotEmpty()) {
                MusicLibraryNavigation(sources, library.path.firstOrNull(), enabled = !starting) {
                    open(it, fromRoot = true)
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(folder?.title ?: "Sources", Modifier.weight(1f).semantics { heading() },
                        style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (canGoUp) {
                        TextButton(onClick = ::back, enabled = !starting, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Back")
                        }
                    }
                    if (starting) {
                        CircularProgressIndicator(Modifier.padding(8.dp).size(22.dp), strokeWidth = 2.dp)
                    } else if (folder?.playable == true) {
                        TextButton(onClick = { play(folder) }, enabled = playback.ready, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
                            Text("Play all", Modifier.padding(start = 4.dp))
                        }
                    }
                }
                if (playFailed) {
                    Text("Couldn’t start playback", Modifier.padding(vertical = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                if (content == null || content.items.isEmpty()) {
                    MusicFolderStatus(content, Modifier.weight(1f)) { retry += 1 }
                } else {
                    val gallery = folder?.type == "music_assistant" && folder.id in setOf("albums", "artists")
                    LazyVerticalGrid(
                        columns = if (gallery) GridCells.Adaptive(144.dp) else GridCells.Fixed(if (wide) 2 else 1),
                        modifier = Modifier.weight(1f),
                        state = library.position(folder),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(if (gallery) 16.dp else 4.dp),
                    ) {
                        items(content.items) { item ->
                            MusicLibraryItem(item, model, gallery, enabled = !starting && (item.expandable || playback.ready),
                                canPlay = !starting && playback.ready, open = { open(item) }, play = { play(item) })
                        }
                    }
                }
            }
        }
        MusicPlaybackBar(playback, artwork, showPlayer, transport, volume, starting)
    }
}

@Composable
private fun MusicLibraryNavigation(
    sources: List<MediaChoice>, current: MediaChoice?, enabled: Boolean, open: (MediaChoice) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(Modifier.width(192.dp).fillMaxHeight().selectableGroup(),
        contentPadding = PaddingValues(top = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(sources) { source ->
            val selected = source == current
            Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(MaterialTheme.shapes.large)
                .background(if (selected) colors.secondaryContainer else Color.Transparent)
                .selectable(selected, enabled = enabled, role = Role.Tab, onClick = { open(source) })
                .padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                Text(source.title, style = MaterialTheme.typography.titleMedium,
                    color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun MusicLibraryItem(
    item: MediaChoice, model: PanelViewModel, gallery: Boolean, enabled: Boolean, canPlay: Boolean,
    open: () -> Unit, play: () -> Unit,
) {
    val artwork = rememberPanelImage(item.artwork, model::cachedArtwork, model::loadArtwork)
    val action = Modifier.clip(MaterialTheme.shapes.medium).clickable(
        enabled = enabled, role = Role.Button, onClickLabel = if (item.expandable) "Open" else "Play", onClick = open,
    )
    if (gallery) {
        Column(action.padding(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MusicArtwork(artwork, Modifier.fillMaxWidth().aspectRatio(1f), fallbackSize = 40.dp)
            Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    } else {
        Row(action.fillMaxWidth().heightIn(min = 60.dp).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MusicArtwork(artwork, Modifier.size(48.dp), fallbackSize = 24.dp)
            Text(item.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (item.expandable && item.playable) {
                IconButton(onClick = play, enabled = canPlay, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.PlayArrow, "Play " + item.title)
                }
            } else {
                Icon(if (item.expandable) Icons.AutoMirrored.Rounded.KeyboardArrowRight else Icons.Rounded.PlayArrow,
                    null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MusicFolderStatus(content: MusicFolder?, modifier: Modifier, retry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
        if (content == null) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        Text(when {
            content == null -> "Loading music…"
            content.failed -> "Couldn’t load music"
            else -> "No music here"
        }, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = if (content?.failed == true) colors.error else colors.onSurfaceVariant)
        if (content?.failed == true) TextButton(onClick = retry) { Text("Try again") }
    }
}
