package app.flickpanel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
            val wide = maxWidth >= tabletWidthDp.dp
            val cardMinimum = if (wide) 194.dp else 154.dp
            val speakerInline = maxWidth >= 620.dp
            Column(
                Modifier.widthIn(max = panelMaxWidth).fillMaxSize()
                    .padding(horizontal = if (wide) panelWidePadding else 20.dp)
                    .padding(top = if (wide) 34.dp else 18.dp),
            ) {
                MusicHeader(
                    collection = current?.title,
                    player = player,
                    speakerInline = speakerInline,
                    starting = starting,
                    back = ::back,
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
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        items(shelves) { shelf ->
                            MusicShelf(
                                shelf.title,
                                cache[shelf],
                                cardMinimum,
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
                        columns = GridCells.Adaptive(cardMinimum),
                        modifier = Modifier.weight(1f),
                        state = gridScroll,
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(results) { item ->
                            MusicCard(item, model, !starting, open = { open(item) }, play = { play(item) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicHeader(
    collection: String?,
    player: String?,
    speakerInline: Boolean,
    starting: Boolean,
    back: () -> Unit,
    playFolder: (() -> Unit)?,
) {
    val type = MaterialTheme.typography
    val speaker = player?.trim()?.takeIf { it.isNotEmpty() }
    val title = collection ?: "Browse"
    Column(
        Modifier.fillMaxWidth().padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FilledTonalIconButton(onClick = back, enabled = !starting, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, if (collection == null) "Back to player" else "Back")
            }
            Text(
                title, Modifier.weight(1f).semantics { heading() }, style = type.displaySmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (speaker != null && speakerInline) {
                MusicSpeaker(speaker, Modifier.widthIn(max = 192.dp))
            }
            if (playFolder != null) {
                FilledTonalButton(onClick = playFolder, enabled = !starting, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
                    Text("Play", Modifier.padding(start = 6.dp))
                }
            }
            if (starting) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp).semantics {
                        contentDescription = "Starting playback"
                    }, strokeWidth = 2.dp)
                }
            }
        }
        if (speaker != null && !speakerInline) {
            MusicSpeaker(speaker, Modifier.padding(start = 62.dp))
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
    cardMinimum: Dp,
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(22.dp))
            }
        }

        if (choices.isNullOrEmpty()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 132.dp),
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
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val gap = 14.dp
                val columns = ((maxWidth + gap) / (cardMinimum + gap)).toInt().coerceAtLeast(1)
                val cardWidth = (maxWidth - gap * (columns - 1)) / columns
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (item in choices.take(columns)) {
                        MusicCard(
                            item, model, enabled, open = { open(item) }, play = { play(item) },
                            modifier = Modifier.width(cardWidth),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MusicSources(choices: List<MediaChoice>, enabled: Boolean, open: (MediaChoice) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("More to browse", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            for (item in choices) {
                Surface(
                    onClick = { open(item) },
                    enabled = enabled,
                    modifier = Modifier.widthIn(min = 152.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = colors.surfaceContainer,
                ) {
                    Box(
                        Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            item.title, style = MaterialTheme.typography.labelLarge,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
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
private fun MusicCard(
    item: MediaChoice,
    model: PanelViewModel,
    enabled: Boolean,
    open: () -> Unit,
    play: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val artwork = rememberPanelImage(item.artwork, model::cachedArtwork, model::loadArtwork)
    val buttonContainer = if (artwork == null) colors.secondaryContainer else Color(0xde17191c)
    val buttonContent = if (artwork == null) colors.onSecondaryContainer else Color.White
    Surface(modifier, shape = panelCardShape, color = colors.surfaceContainer) {
        Box {
            if (artwork != null) {
                Image(artwork, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(
                            0.26f to Color.Transparent,
                            0.51f to Color(0xa5000000),
                            1f to Color(0xe9000000),
                        )
                    ),
                )
            }
            Column(
                Modifier.fillMaxWidth().heightIn(min = 132.dp).clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = if (item.expandable) "Open folder" else "Play",
                    onClick = if (item.expandable) open else play,
                ).padding(start = 16.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.CenterEnd) {
                    if (item.expandable && item.playable) {
                        FilledTonalIconButton(
                            onClick = play,
                            enabled = enabled,
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = buttonContainer,
                                contentColor = buttonContent,
                            ),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, "Play ${item.title}", Modifier.size(26.dp))
                        }
                    } else {
                        val filled = item.playable || artwork != null
                        Box(
                            Modifier.size(48.dp).background(
                                if (filled) buttonContainer else Color.Transparent, CircleShape
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (item.playable) Icons.Rounded.PlayArrow else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                null, Modifier.size(26.dp),
                                tint = if (filled) buttonContent else colors.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    item.title, Modifier.fillMaxWidth().padding(end = 4.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (artwork == null) colors.onSurface else Color.White,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
