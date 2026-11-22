package app.flickpanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.flickpanel.ha.PersonLocation
import java.io.IOException

internal enum class MapCredits(val label: String, val link: String) {
    NATURAL_EARTH("Made with Natural Earth", "https://www.naturalearthdata.com/about/terms-of-use/"),
    OPEN_STREET_MAP("© OpenStreetMap contributors", "https://www.openstreetmap.org/copyright"),
}

@Composable
internal fun PersonMapScreen(
    maps: OfflineMapAssets, title: String, location: PersonLocation?, close: () -> Unit, status: String? = null,
) {
    val colours = mapColours()
    BoxWithConstraints(
        Modifier.fillMaxSize().clipToBounds().background(colours.water),
        contentAlignment = Alignment.Center,
    ) {
        if (location == null) EmptyState("Location unavailable", null)
        else LoadPersonMap(maps, location, colours)
        val inset = if (maxHeight < 480.dp || maxWidth < 600.dp) 16.dp else 24.dp
        val statusWidth = minOf(144.dp, maxWidth / 3)
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().background(viewerHeaderShade)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(start = inset, top = if (maxHeight < 300.dp) 12.dp else inset, end = inset, bottom = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, Modifier.weight(1f).semantics { heading() }, color = Color.White,
                style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (status != null) {
                Text(status, Modifier.widthIn(max = statusWidth), color = Color.White,
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ViewerIconButton(Icons.Rounded.Close, "Close map", close, Modifier.size(48.dp))
        }
    }
}

@Composable
private fun LoadPersonMap(maps: OfflineMapAssets, location: PersonLocation, colours: MapColours) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var basemap by remember(maps) { mutableStateOf(maps.cachedBasemap) }
    var failed by remember(maps) { mutableStateOf(false) }
    LaunchedEffect(maps, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (basemap != null) return@repeatOnLifecycle
            failed = false
            try {
                basemap = maps.basemap()
            } catch (_: IOException) {
                failed = true
            }
        }
    }

    val map = basemap
    val pin = location.mapPoint()
    val region = map?.regions?.firstOrNull { it.bounds.contains(pin) }
    val cachedDetail = remember(maps, region) { region?.let(maps::cachedDetail) }
    var detail by remember(maps, region) { mutableStateOf(cachedDetail) }
    var detailReady by remember(maps, region) { mutableStateOf(region == null || cachedDetail != null) }
    LaunchedEffect(maps, region, lifecycle) {
        if (region == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (!detailReady) {
                detail = try {
                    maps.detail(region)
                } catch (_: IOException) {
                    null
                }
                detailReady = true
            }
        }
    }

    if (map != null && detailReady) {
        InteractivePersonMap(map, detail, location, colours)
    } else {
        if (failed) EmptyState("Map unavailable", null) else CircularProgressIndicator()
    }
}

@Composable
private fun InteractivePersonMap(
    map: OfflineBasemap, detail: OfflineDetail?, location: PersonLocation, colours: MapColours,
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
    MapViewerControls(if (detail == null) MapCredits.NATURAL_EARTH else MapCredits.OPEN_STREET_MAP) {
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
private fun MapViewerControls(attribution: MapCredits, tools: @Composable () -> Unit) {
    val openLink = rememberOpenWebLink()
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val short = maxHeight < 340.dp
        val inset = if (short || maxWidth < mediumWindowWidthDp.dp) 16.dp else 24.dp
        val toolbar = Modifier.align(Alignment.BottomEnd).padding(inset)
        if (short && maxWidth >= 360.dp) {
            Row(toolbar, horizontalArrangement = Arrangement.spacedBy(6.dp)) { tools() }
        } else {
            Column(toolbar, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                tools()
            }
        }
        val reserved = if (short && maxWidth >= 360.dp) 168.dp else 60.dp
        Surface(
            Modifier.align(Alignment.BottomStart).padding(inset)
                .widthIn(max = minOf(320.dp, (maxWidth - inset * 2 - reserved).coerceAtLeast(0.dp)))
                .heightIn(min = 48.dp)
                .clickable(onClickLabel = "Map data credits") { openLink(attribution.link) },
            shape = panelCardShape,
            color = viewerSurface,
            contentColor = Color.White,
        ) {
            Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.CenterStart) {
                Text(attribution.label, style = MaterialTheme.typography.labelSmall, color = Color(0xffccd5de))
            }
        }
    }
}
