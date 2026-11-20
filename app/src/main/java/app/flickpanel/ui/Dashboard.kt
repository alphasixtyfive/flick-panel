package app.flickpanel.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.flickpanel.Page
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.config.TileKind
import app.flickpanel.ha.Link
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

internal const val tabletWidthDp = 840

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormat = DateTimeFormatter.ofPattern("EEEE d MMMM")

@Composable
private fun clockTime(): ZonedDateTime {
    val now by produceState(ZonedDateTime.now()) {
        while (isActive) {
            value = ZonedDateTime.now()
            delay(60_025 - System.currentTimeMillis() % 60_000)
        }
    }
    return now
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AmbientClock(message: String?, albumId: String?, model: PanelViewModel) {
    val now = clockTime()
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xff10151b))
        .clickable(interactionSource = null, indication = null, onClickLabel = "Open home controls", onClick = model::wake)) {
        IdlePhotos(albumId, model::browsePhotos, model::loadPhoto, Modifier.matchParentSize())
        if (message != null) {
            Text(message, color = Color(0xffc1d1e1), fontSize = 23.sp, textAlign = TextAlign.Center,
                maxLines = 2, modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 34.dp, vertical = 34.dp))
        }
        val size = (maxWidth.value * .18f).coerceIn(68f, 160f).sp
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(now.format(clockFormat), fontSize = size, lineHeight = size,
                fontWeight = FontWeight.Medium, color = Color(0xffdce7f3))
            Text(now.format(dateFormat), style = MaterialTheme.typography.titleLarge,
                color = Color(0xffa9baca), modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Dashboard(state: PanelUi, model: PanelViewModel) {
    val colors = MaterialTheme.colorScheme
    val now = clockTime()
    val weather = state.config.favourites.firstOrNull { it.kind == TileKind.WEATHER }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val wide = maxWidth >= tabletWidthDp.dp
        val compactNavigation = maxWidth < 600.dp
        val cameraColumns = if (maxWidth >= 1100.dp) 4 else if (wide) 3 else 2
        val side = if (wide) panelWidePadding else 20.dp
        Column(Modifier.widthIn(max = panelMaxWidth).fillMaxSize().padding(horizontal = side)) {
            Row(Modifier.fillMaxWidth().padding(top = if (wide) 34.dp else 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                val title = if (state.page == Page.ROOMS) "Rooms" else "Home"
                Text(title, style = if (wide) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f, fill = false).combinedClickable(
                        interactionSource = null, indication = null, onClick = {},
                        onLongClickLabel = "Unlock setup", onLongClick = model::showUnlock,
                    )
                        .heightIn(min = 48.dp).semantics { heading() })
                Row(horizontalArrangement = Arrangement.spacedBy(if (wide) 34.dp else 15.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (weather != null) {
                        WeatherSummary(state.entity(weather.entity), compact = !wide, prominent = wide)
                    }
                    Box(Modifier.heightIn(min = 48.dp).clickable(interactionSource = null, indication = null,
                        onClickLabel = "Show idle screen", role = Role.Button, onClick = model::showIdleScreen),
                        contentAlignment = Alignment.Center) {
                        Text(now.format(clockFormat),
                            style = if (wide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleLarge)
                    }
                }
            }
            state.message?.let { message ->
                Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Notifications, null, tint = colors.primary, modifier = Modifier.size(21.dp))
                    Text(message, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            val status = when (state.link) {
                Link.OFFLINE -> "Home Assistant is offline"
                Link.CONNECTING -> "Connecting to Home Assistant…"
                Link.AUTH_REJECTED -> "Home Assistant rejected the token"
                Link.ONLINE -> null
            }
            if (status != null) Text(status, style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            DashboardNavigation(state.page, wide, compactNavigation, model::selectPage)
            key(state.page) {
                val body = Modifier.fillMaxSize().padding(top = 22.dp)
                when (state.page) {
                    Page.CAMERAS -> CameraGallery(state, model, cameraColumns, body)
                    Page.ROOMS -> Rooms(state, model, wide, body)
                    Page.FAVOURITES -> Column(body.verticalScroll(rememberScrollState()).padding(bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(tileGap)) { Favourites(state, model, wide) }
                }
            }
        }
    }
}

@Composable
private fun DashboardNavigation(current: Page, wide: Boolean, compact: Boolean, selectPage: (Page) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val scroll = if (compact) Modifier else Modifier.horizontalScroll(rememberScrollState())
    Row(Modifier.fillMaxWidth().padding(top = if (wide) 28.dp else 20.dp)
        .then(scroll).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (page in Page.entries) {
            val icon = when (page) {
                Page.FAVOURITES -> Icons.Rounded.FavoriteBorder
                Page.ROOMS -> Icons.Rounded.MeetingRoom
                Page.CAMERAS -> Icons.Rounded.Videocam
            }
            val selected = current == page
            Surface(selected = selected, onClick = { selectPage(page) }, shape = CircleShape,
                color = if (selected) colors.primaryContainer else colors.surfaceContainer,
                contentColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                modifier = (if (compact) Modifier.weight(1f) else Modifier).semantics { role = Role.Tab }) {
                if (compact) {
                    Column(Modifier.heightIn(min = 56.dp).padding(horizontal = 4.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
                        Icon(icon, null, Modifier.size(20.dp))
                        Text(page.label, style = MaterialTheme.typography.labelLarge,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, Modifier.size(24.dp))
                        Text(page.label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun Favourites(state: PanelUi, model: PanelViewModel, wide: Boolean) {
    val tiles = remember(state.config.favourites) {
        val headerWeather = state.config.favourites.firstOrNull { it.kind == TileKind.WEATHER }
        state.config.favourites.filter { it.showOnHome && it != headerWeather }
    }
    if (tiles.isEmpty()) {
        EmptyState("No favourites yet", "Hold Home to unlock setup and add tiles.")
        return
    }
    FavouritesTileGrid(tiles, state, model, wide)
}

@Composable
private fun CameraGallery(state: PanelUi, model: PanelViewModel, columns: Int, modifier: Modifier) {
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

@Composable
internal fun EmptyState(title: String, note: String?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 42.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        note?.let { Text(it, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
