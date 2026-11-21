package app.flickpanel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelViewModel
import app.flickpanel.ha.MediaChoice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun MusicBrowser(id: String, player: String?, model: PanelViewModel, close: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var folders by remember(id) { mutableStateOf(emptyList<MediaChoice>()) }
    val current = folders.lastOrNull()
    val cache = remember(id) { mutableStateMapOf<MediaChoice?, List<MediaChoice>>() }
    val failures = remember(id) { mutableStateMapOf<MediaChoice?, Boolean>() }
    val attempts = remember(id) { mutableStateMapOf<MediaChoice?, Int>() }
    val library = cache[null].orEmpty()
    val shelves = remember(library) {
        listOf("playlists", "radio").mapNotNull { section ->
            library.firstOrNull { it.expandable && it.type == "music_assistant" && it.id == section }
        }
    }
    val gridScrolls = remember(id) { mutableMapOf<MediaChoice?, LazyGridState>() }
    val gridScroll = remember(id, current) { gridScrolls.getOrPut(current) { LazyGridState() } }
    val overviewScroll = remember(id) { LazyListState() }
    val results = cache[current]
    var playFailed by remember(id, current) { mutableStateOf(false) }
    var starting by remember(id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun back() {
        if (starting) return
        if (folders.isEmpty()) close() else folders = folders.dropLast(1)
    }

    fun retry(folder: MediaChoice?) {
        failures.remove(folder)
        attempts[folder] = (attempts[folder] ?: 0) + 1
    }

    suspend fun load(folder: MediaChoice?) {
        if (cache[folder] != null) return
        failures.remove(folder)
        try {
            cache[folder] = model.browseMusic(id, folder)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failures[folder] = true
        }
    }

    BackHandler(onBack = ::back)
    LaunchedEffect(id, current, attempts[current]) {
        if (current == null || current !in shelves) load(current)
    }
    for (shelf in shelves) key(shelf) {
        LaunchedEffect(id, attempts[shelf]) { load(shelf) }
    }

    fun play(item: MediaChoice) {
        if (starting) return
        starting = true
        playFailed = false
        scope.launch {
            try {
                model.playMusic(id, item)
                close()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                playFailed = true
            } finally {
                starting = false
            }
        }
    }

    fun open(item: MediaChoice, shelf: MediaChoice? = null) {
        if (starting) return
        if (item.expandable) {
            folders = if (shelf == null) folders + item else listOf(shelf, item)
        } else {
            play(item)
        }
    }

    Surface(Modifier.fillMaxSize(), color = colors.surface) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            val wide = maxWidth >= 680.dp
            val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
            val libraryColumns = if (minOf(maxWidth, musicScreenWidth) >= 700.dp * fontScale) 2 else 1
            Column(
                Modifier.widthIn(max = musicScreenWidth).fillMaxSize()
                    .padding(horizontal = if (wide) 28.dp else 20.dp)
                    .padding(top = 24.dp, bottom = 20.dp),
            ) {
                MusicHeader(
                    collection = current?.title,
                    starting = starting,
                    playFolder = current?.takeIf { it.playable }?.let { folder -> { play(folder) } },
                )
                if (playFailed) {
                    Text(
                        "Couldn\u2019t start playback",
                        Modifier.padding(vertical = 6.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.error,
                    )
                }

                if (current == null && shelves.isNotEmpty()) {
                    val more = remember(library, shelves) { library.filterNot { it in shelves } }
                    LazyColumn(
                        Modifier.weight(1f),
                        state = overviewScroll,
                        contentPadding = PaddingValues(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(shelves) { shelf ->
                            MusicShelf(
                                shelf.title,
                                cache[shelf],
                                libraryColumns,
                                model,
                                failed = failures[shelf] == true,
                                enabled = !starting,
                                viewAll = { open(shelf) },
                                open = { open(it, shelf) },
                                play = ::play,
                                retry = { retry(shelf) },
                            )
                        }
                        if (more.isNotEmpty()) {
                            item { MusicSources(more, !starting) { open(it) } }
                        }
                    }
                } else if (results.isNullOrEmpty()) {
                    MusicBrowserStatus(results == null, failures[current] == true, Modifier.weight(1f)) {
                        retry(current)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(libraryColumns),
                        modifier = Modifier.weight(1f),
                        state = gridScroll,
                        contentPadding = PaddingValues(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(results) { item ->
                            MusicEntry(item, model, !starting, open = { open(item) }, play = { play(item) })
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    FilledTonalButton(onClick = ::back, enabled = !starting, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp))
                        Text(if (current == null) "Back to player" else "Back", Modifier.padding(start = 8.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    player?.trim()?.takeIf { it.isNotEmpty() }?.let { speaker ->
                        MusicSpeaker(speaker, Modifier.widthIn(max = 192.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicHeader(
    collection: String?,
    starting: Boolean,
    playFolder: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            collection ?: "Browse", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineMedium,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (starting) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp).semantics {
                    contentDescription = "Starting playback"
                }, strokeWidth = 2.dp)
            }
        } else if (playFolder != null) {
            FilledTonalButton(onClick = playFolder, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
                Text("Play", Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun MusicSpeaker(speaker: String, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Speaker, null, Modifier.size(21.dp), tint = color)
        Text(
            speaker, style = MaterialTheme.typography.titleMedium, color = color,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MusicShelf(
    title: String,
    choices: List<MediaChoice>?,
    columns: Int,
    model: PanelViewModel,
    failed: Boolean,
    enabled: Boolean,
    viewAll: () -> Unit,
    open: (MediaChoice) -> Unit,
    play: (MediaChoice) -> Unit,
    retry: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, Modifier.weight(1f).semantics { heading() }, style = type.titleLarge)
            TextButton(
                onClick = viewAll,
                enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "See all $title" },
            ) {
                Text("See all", style = type.labelLarge)
            }
        }

        if (choices.isNullOrEmpty()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    failed -> {
                        Text("Couldn\u2019t load $title", color = colors.error, style = type.bodyMedium)
                        TextButton(onClick = retry, enabled = enabled) { Text("Try again") }
                    }
                    choices == null -> CircularProgressIndicator(
                        Modifier.size(22.dp).semantics { contentDescription = "Loading $title" },
                        strokeWidth = 2.dp,
                    )
                    else -> Text("No music here", color = colors.onSurfaceVariant, style = type.bodyMedium)
                }
            }
        } else {
            for (row in choices.take(if (columns == 2) 4 else 3).chunked(columns)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (item in row) {
                        MusicEntry(
                            item, model, enabled, open = { open(item) }, play = { play(item) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size < columns) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MusicSources(choices: List<MediaChoice>, enabled: Boolean, open: (MediaChoice) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Library", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (item in choices) {
                FilledTonalButton(
                    onClick = { open(item) },
                    enabled = enabled,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(item.title, style = MaterialTheme.typography.labelLarge, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun MusicBrowserStatus(pending: Boolean, failed: Boolean, modifier: Modifier, retry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (pending && !failed) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        Text(
            when {
                failed -> "Couldn\u2019t load music"
                pending -> "Loading music\u2026"
                else -> "No music here"
            },
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyLarge,
            color = if (failed) colors.error else colors.onSurfaceVariant,
        )
        if (failed) FilledTonalButton(onClick = retry) { Text("Try again") }
    }
}

@Composable
private fun MusicEntry(
    item: MediaChoice,
    model: PanelViewModel,
    enabled: Boolean,
    open: () -> Unit,
    play: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val artwork = rememberPanelImage(item.artwork, model::cachedArtwork, model::loadArtwork)
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = colors.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = if (item.expandable) "Open folder" else "Play",
                onClick = if (item.expandable) open else play,
            ).padding(start = 8.dp, top = 7.dp, end = 10.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MusicArtwork(artwork, Modifier.size(48.dp), 23.dp)
            Text(item.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (item.expandable && item.playable) {
                IconButton(onClick = play, enabled = enabled, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.PlayArrow, "Play ${item.title}", Modifier.size(26.dp))
                }
            } else {
                Icon(
                    if (item.expandable) Icons.AutoMirrored.Rounded.KeyboardArrowRight else Icons.Rounded.PlayArrow,
                    null, Modifier.size(22.dp), tint = colors.onSurfaceVariant,
                )
            }
        }
    }
}
