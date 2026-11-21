package app.flickpanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flickpanel.ha.PersonLocation
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class MapCredits(val label: String, val link: String) {
    NATURAL_EARTH("Made with Natural Earth", "https://www.naturalearthdata.com"),
    OPEN_STREET_MAP("© OpenStreetMap contributors", "https://www.openstreetmap.org/copyright"),
}

@Composable
internal fun PersonMapScreen(title: String, location: PersonLocation?, close: () -> Unit, status: String? = null) {
    val colours = mapColours()
    Box(Modifier.fillMaxSize().background(colours.water), contentAlignment = Alignment.Center) {
        if (location == null) {
            EmptyState("Location unavailable", null)
            MapViewerControls(title, status, close)
        } else {
            LoadPersonMap(title, status, location, colours, close)
        }
    }
}

@Composable
private fun LoadPersonMap(title: String, status: String?, location: PersonLocation, colours: MapColours, close: () -> Unit) {
    val assets = LocalContext.current.assets
    var basemap by remember(assets) { mutableStateOf<OfflineBasemap?>(null) }
    var failed by remember(assets) { mutableStateOf(false) }
    LaunchedEffect(assets) {
        try {
            basemap = withContext(Dispatchers.IO) { loadOfflineBasemap(assets) }
        } catch (_: IOException) {
            failed = true
        }
    }

    val map = basemap
    val pin = location.mapPoint()
    val region = map?.regions?.firstOrNull { it.bounds.contains(pin) }
    var detail by remember(region) { mutableStateOf<OfflineDetail?>(null) }
    var detailReady by remember(region) { mutableStateOf(region == null) }
    LaunchedEffect(region) {
        if (region != null) {
            detail = try {
                withContext(Dispatchers.IO) { loadOfflineDetail(assets, region) }
            } catch (_: IOException) {
                null
            }
            detailReady = true
        }
    }

    if (map != null && detailReady) {
        InteractivePersonMap(title, status, map, detail, location, colours, close)
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (failed) EmptyState("Map unavailable", null) else CircularProgressIndicator()
            MapViewerControls(title, status, close)
        }
    }
}

@Composable
internal fun InteractivePersonMap(
    title: String, status: String?, map: OfflineBasemap, detail: OfflineDetail?,
    location: PersonLocation, colours: MapColours, close: () -> Unit,
) {
    val pin = location.mapPoint()
    val minimumSpan = if (detail == null) .24f else .006f
    val preferredSpan = if (detail == null) .35f else .016f
    val openingSpan = maxOf(preferredSpan, ((location.accuracyMetres ?: 0.0) * 4 / metresPerLatitudeDegree).toFloat())
        .coerceAtMost(maximumMapSpan)
    var viewport by remember(detail) { mutableStateOf(MapViewport(pin, openingSpan)) }
    var followsPerson by remember(detail) { mutableStateOf(true) }
    LaunchedEffect(pin, followsPerson) {
        if (followsPerson) viewport = viewport.copy(centre = pin)
    }
    PersonMapCanvas(map, detail, location, viewport, minimumSpan, colours) { origin, centroid, pan, zoom, limit ->
        followsPerson = false
        viewport = viewport.transform(origin, centroid, pan, zoom, limit)
    }
    MapViewerControls(
        title = title,
        status = status,
        close = close,
        attribution = if (detail == null) MapCredits.NATURAL_EARTH else MapCredits.OPEN_STREET_MAP,
    ) {
        ViewerIconButton(Icons.Rounded.Add, "Zoom in", enabled = viewport.latitudeSpan > minimumSpan,
            onClick = { viewport = viewport.copy(latitudeSpan = (viewport.latitudeSpan / 1.6f).coerceAtLeast(minimumSpan)) })
        ViewerIconButton(Icons.Rounded.Remove, "Zoom out", enabled = viewport.latitudeSpan < maximumMapSpan,
            onClick = { viewport = viewport.copy(latitudeSpan = (viewport.latitudeSpan * 1.6f).coerceAtMost(maximumMapSpan)) })
        ViewerIconButton(Icons.Rounded.MyLocation, "Centre on person", onClick = {
            followsPerson = true
            viewport = MapViewport(pin, openingSpan)
        })
    }
}

@Composable
private fun MapViewerControls(
    title: String, status: String?, close: () -> Unit, attribution: MapCredits? = null,
    tools: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
        val short = maxHeight < 340.dp
        val inset = if (short || maxWidth < mediumWindowWidthDp.dp) 16.dp else 24.dp
        ViewerIconButton(Icons.Rounded.Close, "Close map", close, Modifier.align(Alignment.TopEnd).padding(inset).size(56.dp))
        if (tools != null) {
            if (short) {
                Row(Modifier.align(Alignment.TopStart).padding(inset), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tools()
                }
            } else {
                Column(Modifier.align(Alignment.BottomEnd).padding(inset), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tools()
                }
            }
        }
        val controlsWidth = if (tools == null || short) 0.dp else 62.dp
        Surface(
            Modifier.align(Alignment.BottomStart).padding(inset)
                .widthIn(max = minOf(400.dp, (maxWidth - inset * 2 - controlsWidth).coerceAtLeast(0.dp))),
            shape = panelCardShape,
            color = viewerSurface,
            contentColor = Color.White,
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (status != null) {
                    Text(status, style = MaterialTheme.typography.bodyMedium, color = Color(0xffe1e7ed), maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                }
                if (attribution != null) {
                    val uri = LocalUriHandler.current
                    Text(attribution.label, Modifier.clickable(onClickLabel = "Map data credits") { uri.openUri(attribution.link) },
                        style = MaterialTheme.typography.labelSmall, color = Color(0xffccd5de))
                }
            }
        }
    }
}
