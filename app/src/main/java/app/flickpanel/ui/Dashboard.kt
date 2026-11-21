package app.flickpanel.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flickpanel.Page
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.config.TileKind
import app.flickpanel.ha.Entity
import app.flickpanel.ha.Link

internal const val tabletWidthDp = 840

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Dashboard(state: PanelUi, model: PanelViewModel) {
    val colors = MaterialTheme.colorScheme
    var roomId by rememberSaveable { mutableStateOf<String?>(null) }
    val pages = rememberSaveableStateHolder()
    val favourites = state.config.favourites
    val weather = remember(favourites) { favourites.firstOrNull { it.kind == TileKind.WEATHER } }
    val homeTiles = remember(favourites, weather) { favourites.filter { it.showOnHome && it != weather } }
    val alerts = if (state.page == Page.FAVOURITES) state.activeHomeBadges() else emptyList()
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val wide = maxWidth >= tabletWidthDp.dp
        val compact = maxWidth < mediumWindowWidthDp.dp
        val short = maxHeight < 480.dp
        val fontScale = LocalDensity.current.fontScale
        val tileColumns = dashboardTileColumns(maxWidth.value, fontScale)
        val cameraColumns = minOf(tileColumns, if (maxWidth >= 1100.dp) 4 else if (wide) 3 else 2)
        val side = if (wide) panelWidePadding else if (maxWidth < 360.dp) 14.dp else 20.dp
        val headerWidth = minOf(maxWidth, panelMaxWidth) - side * 2
        val alertsInNavigation = alerts.isNotEmpty() && headerWidth >= 640.dp * fontScale.coerceAtLeast(1f)
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
            Row(
                Modifier.fillMaxWidth().padding(top = if (short) 4.dp else 10.dp)
                    .heightIn(min = if (short) 48.dp else 56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DashboardNavigation(state.page, short, model::selectPage, Modifier.weight(1f))
                if (alertsInNavigation) HomeWarnings(alerts, Modifier.widthIn(max = 230.dp))
            }
            pages.SaveableStateProvider(state.page.name) {
                val body = Modifier.fillMaxSize().panelEntrance(state.page, state.config.reduceMotion)
                    .padding(top = if (short) 10.dp else 16.dp)
                when (state.page) {
                    Page.CAMERAS -> CameraGallery(state, model, cameraColumns, body)
                    Page.ROOMS -> Rooms(state, model, roomId, { roomId = it }, body)
                    Page.FAVOURITES -> Favourites(
                        homeTiles, state, model, tileColumns, body,
                        alerts = if (alertsInNavigation) emptyList() else alerts,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DashboardHeader(
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
internal fun DashboardNavigation(current: Page, short: Boolean, selectPage: (Page) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(modifier) {
        val stacked = maxWidth < 400.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
        val style = if (short || stacked) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium
        val row = if (stacked) Modifier.fillMaxWidth() else Modifier
        Row(row.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (page in Page.entries) {
                val selected = current == page
                val color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant
                val icon = when (page) {
                    Page.FAVOURITES -> Icons.Rounded.FavoriteBorder
                    Page.ROOMS -> Icons.Rounded.GridView
                    Page.CAMERAS -> Icons.Rounded.Videocam
                }
                val width = if (stacked) Modifier.weight(1f) else Modifier
                val tab = width.clip(CircleShape).selectable(selected, role = Role.Tab, onClick = { selectPage(page) })
                    .background(if (selected) colors.secondaryContainer else Color.Transparent)
                    .heightIn(min = if (stacked) 56.dp else 48.dp)
                    .padding(horizontal = if (stacked) 4.dp else 12.dp, vertical = 6.dp)
                val label: @Composable () -> Unit = {
                    Text(page.label, style = style, color = color,
                        fontWeight = if (selected) FontWeight.SemiBold else style.fontWeight,
                        textAlign = if (stacked) TextAlign.Center else TextAlign.Start, maxLines = if (stacked) 2 else 1,
                        overflow = TextOverflow.Ellipsis)
                }
                if (stacked) {
                    Column(tab, horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
                        Icon(icon, null, Modifier.size(20.dp), tint = color)
                        label()
                    }
                } else {
                    Row(tab, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, Modifier.size(20.dp), tint = color)
                        label()
                    }
                }
            }
        }
    }
}
