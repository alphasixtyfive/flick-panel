package app.flickpanel.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.RoomLayout
import app.flickpanel.config.PanelConfig
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.Area

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FavouriteEditor(
    draft: PanelConfig, state: PanelUi, add: () -> Unit, edit: (TileSpec) -> Unit,
    resize: (TileSpec) -> Unit, remove: (TileSpec) -> Unit, move: (String, Int) -> Unit, undo: (() -> Unit)?,
) {
    val groups = remember(draft.favourites) {
        draft.favourites.filter { it.showOnHome }.groupBy { it.kind.homeGroup }.toSortedMap()
    }
    Column(Modifier.fillMaxSize().padding(horizontal = setupPadding)) {
        FlowRow(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            FilledTonalButton(onClick = add, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Rounded.Add, null, Modifier.padding(end = 7.dp))
                Text("Add tile")
            }
            if (undo != null) TextButton(onClick = undo) { Text("Undo removal") }
        }
        if (groups.isEmpty()) {
            Text("Add something you use on this panel.", Modifier.padding(top = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 14.dp)) {
            for ((group, tiles) in groups) {
                item(key = "group:${group.name}") {
                    Text(group.label, Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp).semantics { heading() },
                        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
                itemsIndexed(tiles, key = { _, tile -> tile.entity }) { index, tile ->
                    val before = index > 0
                    val after = index < tiles.lastIndex
                    val top = if (before) 4.dp else 20.dp
                    val bottom = if (after) 4.dp else 20.dp
                    val label = state.label(tile)
                    val shape = remember(top, bottom) { RoundedCornerShape(top, top, bottom, bottom) }
                    Surface(
                        (if (draft.reduceMotion) Modifier else Modifier.animateItem()).fillMaxWidth().padding(bottom = 2.dp),
                        shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ReorderGrip(label, before, after, { move(tile.entity, it) })
                            Column(Modifier.weight(1f).heightIn(min = 60.dp).clip(MaterialTheme.shapes.medium)
                                .clickable(role = Role.Button, onClickLabel = "Edit $label") { edit(tile) }
                                .padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.Center) {
                                Text(label, style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(if (tile.wideOnTablet) "${tile.kind.label} · Wide" else tile.kind.label,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            }
                            TileOptions(label, tile, edit = { edit(tile) }, resize = { resize(tile) }, remove = { remove(tile) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TileOptions(label: String, tile: TileSpec, edit: () -> Unit, resize: () -> Unit, remove: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Rounded.MoreVert, "Options for $label") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Edit tile") }, onClick = { expanded = false; edit() })
            if (tile.kind.resizable) {
                DropdownMenuItem(
                    text = { Text(if (tile.wideOnTablet) "Make standard" else "Make wide") },
                    onClick = { expanded = false; resize() },
                )
            }
            DropdownMenuItem(text = { Text("Remove tile", color = MaterialTheme.colorScheme.error) },
                onClick = { expanded = false; remove() })
        }
    }
}

@Composable
internal fun RoomSetup(
    draft: PanelConfig, state: PanelUi, customize: (Area) -> Unit, update: (PanelConfig) -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val rooms = state.catalogue.rooms
    val selectedRooms = remember(draft, state.catalogue) { RoomLayout(draft, state.catalogue).selectedRooms }
    val chosen = remember(selectedRooms) { selectedRooms.map(Area::id) }
    val positions = remember(chosen) { chosen.withIndex().associate { (index, id) -> id to index } }
    val unselectedRooms = remember(rooms, positions) { rooms.filterNot { it.id in positions } }
    val groups = remember(selectedRooms, unselectedRooms, search) {
        val query = search.trim()
        listOf("On this panel" to selectedRooms, "Available rooms" to unselectedRooms)
            .map { (title, choices) -> title to choices.filter { it.name.contains(query, ignoreCase = true) } }
            .filter { (_, choices) -> choices.isNotEmpty() }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = setupPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (rooms.isNotEmpty()) SetupSearchField(search, { search = it }, "Find a room", Modifier.fillMaxWidth())
        SetupSwitchRow("Heating controls", null, draft.showRoomHeating) { update(draft.copy(showRoomHeating = it)) }
        if (state.loadingCatalogue) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        else if (rooms.isEmpty()) Text("No rooms available")
        else if (groups.isEmpty()) Text("No matching rooms", Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
                for ((title, choices) in groups) {
                    item(key = title) {
                        Text(title, Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp).semantics { heading() },
                            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    items(choices, key = Area::id) { room ->
                        val position = positions[room.id] ?: -1
                        val selected = position >= 0
                        Surface(
                            selected = selected,
                            onClick = { update(draft.copy(roomIds = if (selected) chosen - room.id else chosen + room.id)) },
                            shape = MaterialTheme.shapes.medium,
                            color = if (selected) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent,
                        ) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.padding(end = 16.dp))
                                Text(room.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (selected) {
                                    ReorderGrip(room.name, position > 0, position < chosen.lastIndex) { delta ->
                                        val next = chosen.toMutableList()
                                        if (position + delta in next.indices) {
                                            java.util.Collections.swap(next, position, position + delta)
                                            update(draft.copy(roomIds = next))
                                        }
                                    }
                                    IconButton(onClick = { customize(room) }) {
                                        Icon(Icons.Rounded.Edit, "Customize ${room.name}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CameraSetup(draft: PanelConfig, state: PanelUi, edit: (TileSpec) -> Unit, update: (PanelConfig) -> Unit) {
    val cameras = remember(state.catalogue) {
        state.catalogue.entries.filter { it.visible && it.entity.domain == "camera" }.map { it.entity }
    }
    val saved = remember(draft.favourites) { draft.favourites.associateBy { it.entity } }
    Column(Modifier.fillMaxSize().padding(horizontal = setupPadding, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Star a camera to pin it to Favourites.", Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = {
                val missing = cameras.filter { it.id !in saved }
                update(draft.copy(favourites = draft.favourites + missing.map { TileSpec(it.id, TileKind.CAMERA, showOnHome = false) }))
            }, enabled = cameras.any { it.id !in saved }) { Text("Select all") }
        }
        if (state.loadingCatalogue) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        else if (cameras.isEmpty()) Text("No cameras available")
        else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(cameras, key = { it.id }) { camera ->
                val tile = saved[camera.id]
                val name = tile?.label ?: camera.name
                val inGallery = {
                    val next = if (tile != null) draft.favourites - tile
                    else draft.favourites + TileSpec(camera.id, TileKind.CAMERA, showOnHome = false)
                    update(draft.copy(favourites = next))
                }
                val featured = tile?.showOnHome == true
                Row(Modifier.fillMaxWidth().heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).heightIn(min = 58.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .toggleable(tile != null, role = Role.Checkbox, onValueChange = { inGallery() })
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = tile != null, onCheckedChange = null)
                        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = {
                        val next = if (tile != null) draft.favourites.map {
                            if (it.entity == tile.entity) it.copy(showOnHome = !featured) else it
                        } else draft.favourites + TileSpec(camera.id, TileKind.CAMERA)
                        update(draft.copy(favourites = next))
                    }) {
                        Icon(if (featured) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            if (featured) "Remove $name from Home" else "Show $name on Home",
                            tint = if (featured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (tile != null) IconButton(onClick = { edit(tile) }) {
                        Icon(Icons.Rounded.Edit, "Edit $name")
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReorderGrip(label: String, before: Boolean, after: Boolean, reorder: (Int) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val latest by rememberUpdatedState(reorder)
    Box {
        IconButton(onClick = { menu = true }, modifier = Modifier.pointerInput(label) {
            var travel = 0f
            val threshold = 53.dp.toPx()
            detectDragGesturesAfterLongPress(onDragStart = { travel = 0f }) { change, drag ->
                change.consume()
                travel += drag.y
                if (travel > threshold) { latest(1); travel -= threshold }
                else if (travel < -threshold) { latest(-1); travel += threshold }
            }
        }) { Icon(Icons.Rounded.DragIndicator, "Reorder $label") }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; reorder(-1) }, enabled = before)
            DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; reorder(1) }, enabled = after)
        }
    }
}
