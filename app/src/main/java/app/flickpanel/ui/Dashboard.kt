package app.flickpanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flickpanel.ActiveHomeBadge
import app.flickpanel.Page
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.config.TileKind
import app.flickpanel.ha.Entity
import app.flickpanel.ha.Link

internal const val tabletWidthDp = 840
internal val dashboardPillHeight = 48.dp
private val inlineTabsWidth = 400.dp
private val inlineAlertWidth = 230.dp
private val navigationGap = 16.dp

@Composable
internal fun Dashboard(state: PanelUi, model: PanelViewModel, closePreview: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val preview = closePreview != null
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
        val narrow = maxWidth < 360.dp
        val side = if (wide) panelWidePadding else if (narrow) 14.dp else 20.dp
        val headerWidth = minOf(maxWidth, panelMaxWidth) - side * 2
        val tabsWidth = inlineTabsWidth * fontScale.coerceAtLeast(1f)
        val fullAlert = headerWidth >= tabsWidth + inlineAlertWidth + navigationGap
        val alertIcon = short && !fullAlert && headerWidth >= tabsWidth + dashboardPillHeight + navigationGap
        val alertsInNavigation = alerts.isNotEmpty() && (fullAlert || alertIcon)
        val alertsInHeader = short && alerts.isNotEmpty() && !alertsInNavigation
        Column(Modifier.widthIn(max = panelMaxWidth).fillMaxSize().padding(horizontal = side)) {
            DashboardHeader(
                if (preview) "Preview" else "Home",
                weather?.let { state.entity(it.entity) },
                wide = wide,
                short = short,
                stacked = compact && !short && (fontScale >= 1.3f || narrow),
                alerts = if (alertsInHeader) alerts else emptyList(),
                preview = preview,
                headerAction = closePreview ?: model::showUnlock,
                idle = if (preview) null else model::showIdleScreen,
            )
            state.message?.let { message ->
                Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Notifications, null, tint = colors.primary, modifier = Modifier.size(21.dp))
                    Text(message, style = MaterialTheme.typography.titleMedium, maxLines = if (short) 1 else 2,
                        overflow = TextOverflow.Ellipsis)
                }
            }
            ConnectionStatus(state.link, state.serverAddress != null, preview, model::retryConnection, model::showConnectionUnlock)
            Row(
                Modifier.fillMaxWidth().padding(top = if (short) 10.dp else 24.dp)
                    .heightIn(min = if (short) dashboardPillHeight else 56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(navigationGap),
            ) {
                DashboardNavigation(state.page, short, if (preview) null else model::selectPage, Modifier.weight(1f))
                if (alertsInNavigation) HomeWarnings(
                    alerts, Modifier.widthIn(max = inlineAlertWidth), prominent = !short, iconOnly = alertIcon,
                )
            }
            pages.SaveableStateProvider(state.page.name) {
                val body = Modifier.fillMaxSize().panelEntrance(state.page, state.config.reduceMotion)
                    .padding(top = if (short) 10.dp else 16.dp)
                when (state.page) {
                    Page.CAMERAS -> CameraGallery(state, model, cameraColumns, body)
                    Page.ROOMS -> Rooms(state, model, roomId, { roomId = it }, body)
                    Page.FAVOURITES -> Favourites(
                        homeTiles, state, model, tileColumns, body,
                        alerts = if (alertsInNavigation || alertsInHeader) emptyList() else alerts,
                        preview = preview,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectionStatus(
    link: Link, hasConnection: Boolean, preview: Boolean, retry: () -> Unit, openConnection: () -> Unit,
) {
    val status = when (link) {
        Link.OFFLINE -> if (hasConnection) "Home Assistant is offline" else "Home Assistant connection needs setup"
        Link.CONNECTING -> "Connecting to Home Assistant…"
        Link.AUTH_REJECTED -> "Home Assistant rejected the token"
        Link.ONLINE -> return
    }
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            status, Modifier.align(Alignment.CenterVertically).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (link) {
            Link.OFFLINE -> TextButton(onClick = if (hasConnection) retry else openConnection, enabled = !preview,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(if (hasConnection) "Retry" else "Set up connection") }
            Link.AUTH_REJECTED -> TextButton(onClick = openConnection, enabled = !preview,
                modifier = Modifier.heightIn(min = 48.dp)) { Text("Replace token") }
            else -> Unit
        }
    }
}

@Composable
private fun DashboardHeader(
    title: String, weather: Entity?, wide: Boolean, short: Boolean, stacked: Boolean,
    alerts: List<ActiveHomeBadge>, preview: Boolean, headerAction: () -> Unit, idle: (() -> Unit)?,
) {
    val padding = if (short) 6.dp else if (wide) 22.dp else 18.dp
    val prominent = wide && !short
    val homeGesture = if (preview) Modifier else Modifier
        .pointerInput(headerAction) { detectTapGestures(onLongPress = { headerAction() }) }
        .semantics {
            onLongClick("Unlock setup") {
                headerAction()
                true
            }
        }
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
            modifier = modifier.heightIn(min = 48.dp).then(homeGesture).semantics { heading() },
        )
    }
    val details: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(if (prominent) 34.dp else 11.dp),
            verticalAlignment = Alignment.CenterVertically) {
            WeatherSummary(weather, compact = !prominent, prominent = prominent)
            HeaderClock(prominent, idle)
        }
    }
    val action: @Composable () -> Unit = {
        IconButton(onClick = headerAction, modifier = Modifier.size(dashboardPillHeight)) {
            if (preview) Icon(Icons.Rounded.Close, "Close Favourites preview")
            else Icon(Icons.Rounded.Settings, "Settings")
        }
    }
    if (stacked) {
        Column(Modifier.fillMaxWidth().padding(top = padding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                titleContent(Modifier.weight(1f))
                action()
            }
            Box(Modifier.align(Alignment.End)) { details() }
        }
    } else {
        Box(Modifier.fillMaxWidth().padding(top = padding)) {
            Row(
                Modifier.fillMaxWidth().padding(end = dashboardPillHeight + 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                titleContent(Modifier.weight(1f))
                if (alerts.isNotEmpty()) HomeWarnings(alerts, iconOnly = true)
                details()
            }
            Box(Modifier.align(Alignment.CenterEnd)) { action() }
        }
    }
}

@Composable
private fun DashboardNavigation(current: Page, short: Boolean, selectPage: ((Page) -> Unit)?, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val stacked = maxWidth < inlineTabsWidth * density.fontScale.coerceAtLeast(1f)
        val style = if (short || stacked) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium
        val gap = 6.dp
        val compactPadding = 6.dp
        val tabWidths = if (stacked) {
            rememberNavigationLabelWidths(Page.entries.map(Page::label), style.copy(fontWeight = FontWeight.SemiBold), compactPadding)
        } else emptyList()
        val scrollable = stacked && tabWidths.fold(gap * (Page.entries.size - 1)) { sum, width -> sum + width } > maxWidth
        val row = when {
            scrollable -> Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            stacked -> Modifier.fillMaxWidth()
            else -> Modifier
        }
        Row(row.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            for ((index, page) in Page.entries.withIndex()) {
                val selected = current == page
                val color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant
                val icon = when (page) {
                    Page.FAVOURITES -> Icons.Rounded.FavoriteBorder
                    Page.ROOMS -> Icons.Rounded.GridView
                    Page.CAMERAS -> Icons.Rounded.Videocam
                }
                val width = when {
                    scrollable -> Modifier.width(tabWidths[index])
                    stacked -> Modifier.weight(tabWidths[index].value)
                    else -> Modifier
                }
                val tab = width.clip(CircleShape)
                    .selectable(selected, enabled = selectPage != null, role = Role.Tab, onClick = { selectPage?.invoke(page) })
                    .background(if (selected) colors.secondaryContainer else Color.Transparent)
                    .heightIn(min = if (stacked) 56.dp else dashboardPillHeight)
                    .padding(horizontal = if (stacked) compactPadding else 12.dp, vertical = 6.dp)
                val label: @Composable () -> Unit = {
                    Text(page.label, style = style, color = color,
                        fontWeight = if (selected) FontWeight.SemiBold else style.fontWeight,
                        maxLines = 1,
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
