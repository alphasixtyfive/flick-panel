package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.flickpanel.config.TileSpec
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PeopleGrid(
    people: List<TileSpec>,
    resizeTiles: Boolean,
    modifier: Modifier = Modifier,
    tile: @Composable (TileSpec, Modifier) -> Unit,
) {
    if (people.isEmpty()) return

    val density = LocalDensity.current
    val type = MaterialTheme.typography
    val cardHeight = with(density) {
        maxOf(86.dp, type.titleMedium.lineHeight.toDp() + type.bodyMedium.lineHeight.toDp() + 30.dp)
    }

    BoxWithConstraints(modifier) {
        val minWidth = 162.dp * density.fontScale.coerceAtLeast(1f)
        val columns = if (maxWidth >= minWidth * 2 + tileGap) 2 else 1
        val pages = remember(people, columns, resizeTiles) { peopleGridPages(people, columns, resizeTiles) }
        val pager = rememberPagerState { pages.size }
        val scope = rememberCoroutineScope()
        val rowsPerPage = pages.first().size
        Column(Modifier.fillMaxWidth()) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxWidth(),
                pageSpacing = tileGap,
            ) { page ->
                val rows = pages[page]
                Column(verticalArrangement = Arrangement.spacedBy(tileGap)) {
                    for (row in rows) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(tileGap)) {
                            var occupied = 0
                            for (person in row) key(person.entity) {
                                val span = tileSpan(person, columns, compact = true, resizeTiles)
                                occupied += span
                                tile(person, Modifier.weight(span.toFloat()).height(cardHeight))
                            }
                            if (occupied < columns) Spacer(Modifier.weight((columns - occupied).toFloat()))
                        }
                    }
                    if (rows.size < rowsPerPage) Spacer(Modifier.height(cardHeight))
                }
            }
            if (pages.size > 1) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (pager.currentPage > 0) {
                        TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, Modifier.size(20.dp))
                            Text("Previous people", Modifier.padding(start = 4.dp))
                        }
                    }
                    if (pager.currentPage < pages.lastIndex) {
                        TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                            Text("More people", Modifier.padding(end = 4.dp))
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}
