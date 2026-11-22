package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.Area
import app.flickpanel.ha.Link
import app.flickpanel.ha.VacuumAction
import app.flickpanel.ha.supportsVacuum
import app.flickpanel.ha.vacuumBatteryPercent
import app.flickpanel.ha.vacuumReady
import app.flickpanel.ha.vacuumStatus
import kotlinx.coroutines.CancellationException

@Composable
internal fun OpenVacuum(tile: TileSpec, state: PanelUi, model: PanelViewModel) {
    val online = state.link == Link.ONLINE
    val vacuum = state.entities[tile.entity]?.takeIf { online && it.available }
    val battery = tile.vacuumBattery?.let(state.entities::get).takeIf { online }
    val supportsRooms = vacuum?.supportsVacuum(VacuumAction.CLEAN_AREA) == true
    val pending = tile.entity in state.pending
    var roomResult by remember(tile.entity) { mutableStateOf<Result<List<Area>>?>(null) }
    var retry by remember(tile.entity) { mutableIntStateOf(0) }
    var selected by rememberSaveable(tile.entity) { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(model, tile.entity, supportsRooms, retry) {
        roomResult = null
        if (!supportsRooms) return@LaunchedEffect
        roomResult = try {
            val mapped = model.loadVacuumRooms(tile.entity)
            selected = selected.filter { id -> mapped.any { it.id == id } }
            Result.success(mapped)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }
    LaunchedEffect(vacuum?.state) {
        if (vacuum != null && !vacuum.vacuumReady) selected = emptyList()
    }

    val rooms = roomResult?.getOrNull().takeIf { supportsRooms }
    val roomsFailed = supportsRooms && roomResult?.isFailure == true
    val canSelect = vacuum?.vacuumReady == true && !pending
    val mapId = checkNotNull(tile.vacuumMap)
    val map = state.entities[mapId]
    VacuumLayout(
        title = state.label(tile),
        status = vacuumStatus(vacuum),
        battery = vacuumBatteryPercent(vacuum, battery),
        close = model::closeVacuum,
        map = { modifier ->
            VacuumMap(mapId, map?.state, online && map?.available == true, model, modifier)
        },
        controls = { modifier ->
            VacuumRoomsPane(
                rooms = rooms.orEmpty(),
                selected = selected,
                message = when {
                    vacuum == null -> "Vacuum unavailable"
                    !supportsRooms -> "Room cleaning unavailable"
                    roomsFailed -> "Rooms unavailable"
                    rooms == null -> "Loading rooms…"
                    rooms.isEmpty() -> "Map rooms in Home Assistant"
                    else -> null
                },
                retry = { retry += 1 }.takeIf { roomsFailed },
                clear = if (canSelect && selected.isNotEmpty()) ({ selected = emptyList() }) else null,
                enabled = canSelect && rooms != null,
                select = { id -> selected = if (id in selected) selected - id else selected + id },
                modifier = modifier,
            ) {
                VacuumActions(vacuum, selected.size, pending, rooms != null) { action ->
                    model.controlVacuum(tile.entity, action, selected)
                }
            }
        },
    )
}

@Composable
internal fun VacuumLayout(
    title: String, status: String, battery: Int?, close: () -> Unit,
    map: @Composable (Modifier) -> Unit, controls: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val sideBySide = maxWidth >= mediumWindowWidthDp.dp
        val expanded = maxWidth >= tabletWidthDp.dp
        val short = maxHeight < 480.dp
        val gutter = if (expanded) 32.dp else 16.dp
        val gap = if (expanded) 24.dp else 12.dp
        val insetsHandled = WindowInsets(0, 0, 0, 0)

        Scaffold(
            contentWindowInsets = insetsHandled,
            topBar = {
                ViewerTopAppBar(
                    title = title,
                    closeLabel = "Close vacuum",
                    close = close,
                    contentGutter = gutter,
                    windowInsets = insetsHandled,
                    actions = {
                        Row(Modifier.padding(start = 8.dp, end = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            val statusWidth = if (battery == null) 144.dp else 100.dp
                            TileStatus(status, Modifier.widthIn(max = statusWidth), maxLines = 1)
                            battery?.let { VacuumBattery(it) }
                        }
                    },
                )
            },
        ) { bars ->
            val body = Modifier.fillMaxSize().padding(bars).padding(horizontal = gutter)
                .padding(top = if (expanded && !short) 12.dp else 8.dp,
                    bottom = if (expanded && !short) 24.dp else 16.dp)
            if (sideBySide) {
                Row(body, horizontalArrangement = Arrangement.spacedBy(gap)) {
                    val roomsShare = if (expanded) .3f else .5f
                    map(Modifier.weight(1f - roomsShare).fillMaxHeight())
                    controls(Modifier.weight(roomsShare).fillMaxHeight())
                }
            } else {
                BoxWithConstraints(body) {
                    val mapHeight = (maxHeight * .35f).coerceAtMost(232.dp)
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                        map(Modifier.fillMaxWidth().height(mapHeight))
                        controls(Modifier.fillMaxWidth().weight(1f))
                    }
                }
            }
        }
    }
}
