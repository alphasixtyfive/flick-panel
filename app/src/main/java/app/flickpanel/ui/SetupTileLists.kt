package app.flickpanel.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.config.PanelConfig
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FavouriteEditor(
    draft: PanelConfig, state: PanelUi, add: () -> Unit, edit: (TileSpec) -> Unit,
    resize: (TileSpec) -> Unit, remove: (TileSpec) -> Unit, move: (String, Int) -> Unit, undo: (() -> Unit)?,
) {
    val homeTiles = remember(draft.favourites) { draft.favourites.filter { it.showOnHome } }
    Column(Modifier.fillMaxSize().padding(horizontal = setupPadding)) {
        FlowRow(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            FilledTonalButton(onClick = add, enabled = draft.favourites.size < 48, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Rounded.Add, null, Modifier.padding(end = 7.dp))
                Text("Add tile")
            }
            if (undo != null) TextButton(onClick = undo) { Text("Undo removal") }
        }
        if (homeTiles.isEmpty()) {
            Text("Add something you use on this panel.", Modifier.padding(top = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 14.dp)) {
            items(homeTiles, key = { it.entity }) { tile ->
                val after = homeTiles.lastOrNull()?.entity != tile.entity
                val before = homeTiles.firstOrNull()?.entity != tile.entity
                Surface(modifier = if (draft.reduceMotion) Modifier else Modifier.animateItem(),
                    shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        ReorderGrip(state.label(tile), before, after, { move(tile.entity, it) })
                        Surface(onClick = { edit(tile) }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainer) {
                            Column(Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.Center) {
                                Text(state.label(tile), style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (tile.wideOnTablet) "${tile.kind.label} · Wide" else tile.kind.label,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        TileOptions(state.label(tile), tile, edit = { edit(tile) }, resize = { resize(tile) }, remove = { remove(tile) })
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RoomSetup(draft: PanelConfig, state: PanelUi, update: (PanelConfig) -> Unit) {
    var search by remember { mutableStateOf("") }
    val rooms = state.catalogue.rooms
    val chosen = draft.roomIds ?: rooms.map { it.id }
    val visible = remember(rooms, chosen, search) {
        val byId = rooms.associateBy { it.id }
        (chosen.mapNotNull(byId::get) + rooms.filterNot { it.id in chosen })
            .filter { it.name.contains(search.trim(), ignoreCase = true) }
    }
    Column(Modifier.padding(horizontal = setupPadding, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Rooms on this panel", style = MaterialTheme.typography.titleMedium)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { update(draft.copy(roomIds = null)) }, enabled = draft.roomIds != null) { Text("Show all") }
            TextButton(onClick = { update(draft.copy(roomIds = emptyList())) }, enabled = chosen.isNotEmpty()) { Text("Clear selection") }
        }
        if (state.loadingCatalogue) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        else if (rooms.isEmpty()) Text("No rooms available")
        else {
            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Find a room") })
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(visible, key = { it.id }) { room ->
                    val position = chosen.indexOf(room.id)
                    val selected = position >= 0
                    val toggle = {
                        val next = if (selected) chosen - room.id else chosen + room.id
                        update(draft.copy(roomIds = next))
                    }
                    Surface(selected = selected, onClick = toggle, enabled = selected || chosen.size < 48,
                        shape = panelCardShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.padding(end = 10.dp))
                            Text(room.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (selected) ReorderGrip(room.name, position > 0, position < chosen.lastIndex) { delta ->
                                val next = chosen.toMutableList()
                                if (position + delta in next.indices) {
                                    java.util.Collections.swap(next, position, position + delta)
                                    update(draft.copy(roomIds = next))
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
    val saved = draft.favourites.associateBy { it.entity }
    Column(Modifier.padding(horizontal = setupPadding, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Star a camera to show it on Home.", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = {
                val missing = cameras.filter { it.id !in saved }.take(48 - draft.favourites.size)
                update(draft.copy(favourites = draft.favourites + missing.map { TileSpec(it.id, TileKind.CAMERA, showOnHome = false) }))
            }, enabled = draft.favourites.size < 48 && cameras.any { it.id !in saved }) { Text("Select all") }
        }
        if (state.loadingCatalogue) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        else if (cameras.isEmpty()) Text("No cameras available")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(cameras, key = { it.id }) { camera ->
                val tile = saved[camera.id]
                val canAdd = draft.favourites.size < 48
                val name = tile?.label ?: camera.name
                val inGallery = {
                    val next = if (tile != null) draft.favourites - tile
                    else if (canAdd) draft.favourites + TileSpec(camera.id, TileKind.CAMERA, showOnHome = false)
                    else draft.favourites
                    update(draft.copy(favourites = next))
                }
                val featured = tile?.showOnHome == true
                Surface(shape = panelCardShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 5.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).heightIn(min = 58.dp)
                            .toggleable(tile != null, enabled = tile != null || canAdd, role = Role.Checkbox,
                                onValueChange = { inGallery() })
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = tile != null, onCheckedChange = null, enabled = tile != null || canAdd)
                            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = {
                            val next = draft.favourites.map {
                                if (it.kind == TileKind.CAMERA) it.copy(showOnHome = it.entity == camera.id && !featured)
                                else it
                            }
                            update(draft.copy(favourites = if (tile != null) next
                                else next + TileSpec(camera.id, TileKind.CAMERA)))
                        }, enabled = tile != null || canAdd) {
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
}

@Composable
private fun ReorderGrip(label: String, before: Boolean, after: Boolean, reorder: (Int) -> Unit) {
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
