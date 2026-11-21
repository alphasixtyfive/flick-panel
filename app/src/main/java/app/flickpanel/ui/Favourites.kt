package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec

@Composable
internal fun Favourites(
    tiles: List<TileSpec>, state: PanelUi, model: PanelViewModel, columns: Int, modifier: Modifier, showReadings: Boolean,
) {
    val badges = state.activeHomeBadges()
    Column(modifier.padding(bottom = 12.dp)) {
        if (badges.isNotEmpty()) HomeWarnings(badges)
        if (tiles.isEmpty() && badges.isEmpty()) {
            EmptyState("No favourites yet", "Hold Home to unlock setup and add tiles.")
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val fontScale = LocalDensity.current.fontScale
            val height = maxHeight
            val groups = remember(tiles, columns) {
                homeTileGroups(tiles, columns, compact = columns == 4, resizeTiles = true)
            }
            val gates = if (columns == 4) {
                tiles.asSequence().filter { it.kind == TileKind.COVER && state.entity(it.entity)?.isGate == true }
                    .map(TileSpec::entity).toSet()
            } else emptySet()
            val pages = remember(groups, showReadings, height, fontScale, gates) {
                homePages(homeBands(groups, fontScale, height, showReadings, gates), height)
            }
            if (pages.isEmpty()) return@BoxWithConstraints
            var requestedPage by rememberSaveable { mutableIntStateOf(0) }
            val current = requestedPage.coerceIn(pages.indices)
            LaunchedEffect(pages.size) { requestedPage = current }
            val showPager = pages.size > 1
            val boardHeight = if (showPager) (height - homePagerHeight).coerceAtLeast(0.dp) else height
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(tileGap)) {
                    for (band in pages[current]) key(band.lanes.first().first().tiles.first().entity) {
                        FavouriteBand(band, minOf(band.height, boardHeight), groups, state, model)
                    }
                }
                if (showPager) FavouritesPager(current, pages.size) { requestedPage = it }
            }
        }
    }
}

@Composable
private fun FavouriteBand(
    band: HomeBand, height: Dp, groups: HomeTileGroups, state: PanelUi, model: PanelViewModel,
) {
    Row(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(tileGap)) {
        for (lane in band.lanes) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(tileGap)) {
                for (row in lane) {
                    TileRow(row.tiles, state, model, row.columns, groups.compact, groups.resizeTiles,
                        shortcutRow = row.shortcuts, homeAgenda = row.expandedAgenda, modifier = Modifier.height(row.height))
                }
            }
        }
    }
}

@Composable
private fun FavouritesPager(page: Int, count: Int, select: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(homePagerHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        FilledTonalIconButton(onClick = { select(page - 1) }, enabled = page > 0) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Previous favourites page")
        }
        Text("${page + 1} of $count", style = MaterialTheme.typography.labelLarge)
        FilledTonalIconButton(onClick = { select(page + 1) }, enabled = page + 1 < count) {
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Next favourites page")
        }
    }
}
