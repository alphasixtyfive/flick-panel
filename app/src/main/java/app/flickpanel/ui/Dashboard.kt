package app.flickpanel.ui

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
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
import app.flickpanel.ha.Entity
import app.flickpanel.ha.Link
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

internal const val tabletWidthDp = 840

@Composable
private fun clockFormatter(): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    val use24Hour = DateFormat.is24HourFormat(LocalContext.current)
    return remember(locale, use24Hour) {
        val pattern = DateFormat.getBestDateTimePattern(locale, if (use24Hour) "Hm" else "hma")
        DateTimeFormatter.ofPattern(pattern, locale)
    }
}

@Composable
private fun dateFormatter(): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale)
    }
}

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
internal fun AmbientClock(message: String?, albumId: String?, reduceMotion: Boolean, model: PanelViewModel) {
    val now = clockTime()
    val time = now.format(clockFormatter())
    val date = now.format(dateFormatter())
    val shadow = if (albumId == null) null else Shadow(Color(0x66000000), Offset(0f, 2f), blurRadius = 10f)
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xff10151b))
        .clickable(interactionSource = null, indication = null, onClickLabel = "Open home controls", onClick = model::wake)) {
        val short = maxHeight < 480.dp
        val captionInset = if (short || maxWidth < mediumWindowWidthDp.dp) 14.dp else 34.dp
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        IdlePhotos(albumId, model::browsePhotos, model::loadPhoto, reduceMotion, Modifier.matchParentSize())
        if (message != null) {
            Text(message, color = Color(0xffeef4fa), textAlign = TextAlign.Center,
                style = (if (short) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge).copy(shadow = shadow),
                maxLines = if (short) 1 else 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.TopCenter).padding(captionInset))
        }
        val availableClock = minOf((maxWidth - 24.dp).value * .18f, maxHeight.value * .35f)
        val size = minOf(160f, availableClock / fontScale).sp
        Column(Modifier.align(Alignment.Center).padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(time, fontSize = size, lineHeight = size, maxLines = 1,
                fontWeight = FontWeight.Medium, color = Color(0xfff1f6fb), style = MaterialTheme.typography.displayMedium.copy(shadow = shadow))
            Text(date, style = MaterialTheme.typography.titleLarge.copy(shadow = shadow),
                color = Color(0xffedf3fa), modifier = Modifier.padding(top = if (short) 4.dp else 12.dp),
                textAlign = TextAlign.Center, maxLines = if (short) 1 else 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Dashboard(state: PanelUi, model: PanelViewModel) {
    val colors = MaterialTheme.colorScheme
    var roomId by rememberSaveable { mutableStateOf<String?>(null) }
    val weather = state.config.favourites.firstOrNull { it.kind == TileKind.WEATHER }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val wide = maxWidth >= tabletWidthDp.dp
        val compact = maxWidth < mediumWindowWidthDp.dp
        val short = maxHeight < 480.dp
        val fontScale = LocalDensity.current.fontScale
        val tileColumns = dashboardTileColumns(maxWidth.value, fontScale)
        val cameraColumns = minOf(tileColumns, if (maxWidth >= 1100.dp) 4 else if (wide) 3 else 2)
        val side = if (wide) panelWidePadding else if (maxWidth < 360.dp) 14.dp else 20.dp
        Column(Modifier.widthIn(max = panelMaxWidth).fillMaxSize().padding(horizontal = side)) {
            DashboardHeader(
                "Home",
                weather?.let { state.entity(it.entity) },
                wide = wide,
                short = short,
                stacked = compact && !short && fontScale >= 1.3f,
                unlock = model::showUnlock,
                idle = model::showIdleScreen,
            )
            state.message?.let { message ->
                Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Notifications, null, tint = colors.primary, modifier = Modifier.size(21.dp))
                    Text(message, style = MaterialTheme.typography.titleMedium, maxLines = if (short) 1 else 2,
                        overflow = TextOverflow.Ellipsis)
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
            DashboardNavigation(state.page, short, model::selectPage)
            key(state.page) {
                val body = Modifier.fillMaxSize().padding(top = if (short) 10.dp else 16.dp)
                when (state.page) {
                    Page.CAMERAS -> CameraGallery(state, model, cameraColumns, body)
                    Page.ROOMS -> Rooms(state, model, tileColumns, roomId, { roomId = it }, body)
                    Page.FAVOURITES -> Favourites(state, model, tileColumns, resizeTiles = !compact, modifier = body)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DashboardHeader(
    title: String, weather: Entity?, wide: Boolean, short: Boolean, stacked: Boolean,
    unlock: () -> Unit, idle: () -> Unit,
) {
    val padding = if (short) 6.dp else if (wide) 22.dp else 18.dp
    val prominent = wide && !short
    val titleContent: @Composable (Modifier) -> Unit = { modifier ->
        Text(
            title,
            style = when {
                short -> MaterialTheme.typography.headlineSmall
                wide -> MaterialTheme.typography.displayMedium
                stacked -> MaterialTheme.typography.headlineMedium
                else -> MaterialTheme.typography.displaySmall
            },
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.combinedClickable(
                interactionSource = null, indication = null, onClick = {},
                onLongClickLabel = "Unlock setup", onLongClick = unlock,
            ).heightIn(min = 48.dp).semantics { heading() },
        )
    }
    val details: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(if (prominent) 34.dp else 11.dp),
            verticalAlignment = Alignment.CenterVertically) {
            WeatherSummary(weather, compact = !prominent, prominent = prominent)
            HeaderClock(prominent, idle)
        }
    }
    if (stacked) {
        Column(Modifier.fillMaxWidth().padding(top = padding)) {
            titleContent(Modifier.fillMaxWidth())
            Box(Modifier.align(Alignment.End)) { details() }
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(top = padding), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            titleContent(Modifier.weight(1f))
            details()
        }
    }
}

@Composable
private fun HeaderClock(wide: Boolean, showIdleScreen: () -> Unit) {
    Box(Modifier.heightIn(min = 48.dp).clickable(interactionSource = null, indication = null,
        onClickLabel = "Show idle screen", role = Role.Button, onClick = showIdleScreen),
        contentAlignment = Alignment.Center) {
        val style = if (wide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleLarge
        Text(clockTime().format(clockFormatter()), style = style.copy(fontFeatureSettings = "'tnum'"), maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardNavigation(current: Page, short: Boolean, selectPage: (Page) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tabLabel = if (short) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium
    val tabs: @Composable () -> Unit = {
        for (page in Page.entries) {
            val selected = current == page
            Tab(
                selected = selected,
                onClick = { selectPage(page) },
                selectedContentColor = colors.primary,
                unselectedContentColor = colors.onSurfaceVariant,
                modifier = Modifier.heightIn(min = if (short) 48.dp else 56.dp),
            ) {
                Text(page.label, Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                    style = tabLabel, fontWeight = if (selected) FontWeight.SemiBold else tabLabel.fontWeight,
                    textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    BoxWithConstraints(
        Modifier.padding(top = if (short) 4.dp else 10.dp).widthIn(max = 420.dp).fillMaxWidth(),
    ) {
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        if (maxWidth < 284.dp * fontScale) {
            PrimaryScrollableTabRow(
                selectedTabIndex = current.ordinal, edgePadding = 0.dp,
                containerColor = Color.Transparent, contentColor = colors.primary,
                indicator = {}, divider = {}, tabs = tabs,
            )
        } else {
            PrimaryTabRow(
                selectedTabIndex = current.ordinal,
                containerColor = Color.Transparent, contentColor = colors.primary,
                indicator = {}, divider = {}, tabs = tabs,
            )
        }
    }
}

@Composable
private fun Favourites(state: PanelUi, model: PanelViewModel, columns: Int, resizeTiles: Boolean, modifier: Modifier) {
    val tiles = remember(state.config.favourites) {
        val headerWeather = state.config.favourites.firstOrNull { it.kind == TileKind.WEATHER }
        state.config.favourites.filter { it.showOnHome && it != headerWeather }
    }
    val badges = state.activeHomeBadges()
    if (tiles.isEmpty() && badges.isEmpty()) {
        Column(modifier) { EmptyState("No favourites yet", "Hold Home to unlock setup and add tiles.") }
        return
    }
    FavouritesTileGrid(tiles, state, model, columns, resizeTiles, modifier, badges)
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
