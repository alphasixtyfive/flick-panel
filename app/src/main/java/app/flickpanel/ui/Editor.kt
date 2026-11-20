package app.flickpanel.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.config.PanelConfig
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.Entity
import app.flickpanel.ha.Link

@Composable
internal fun ConnectionForm(
    state: PanelUi,
    connect: (String, String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    Box(modifier.fillMaxWidth().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 540.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (state.configured) "Home Assistant connection" else "Flick Panel", style = MaterialTheme.typography.headlineMedium)
            Text("Connect to Home Assistant with your server address and a long-lived access token.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(url, { url = it }, label = { Text("Home Assistant URL") },
                placeholder = { Text("https://home.example") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(token, { token = it }, label = { Text("Long-lived access token") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
            if (!state.configured) {
                Text("Choose a setup PIN to protect editing.", style = MaterialTheme.typography.titleSmall)
                PinField(pin, { pin = it }, "Setup PIN")
                PinField(confirm, { confirm = it }, "Confirm PIN")
            }
            ErrorText(state.error)
            Button(onClick = { connect(url, token, pin, confirm) },
                enabled = url.isNotBlank() && token.isNotBlank() && !state.busy) {
                Text(if (state.busy) "Connecting…" else "Connect")
            }
        }
    }
}

@Composable
private fun PinField(value: String, change: (String) -> Unit, label: String) {
    OutlinedTextField(value, { change(it.filter { c -> c in '0'..'9' }.take(8)) },
        label = { Text(label) }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
}

@Composable
internal fun UnlockEditor(state: PanelUi, unlock: (String) -> Unit, cancel: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(modifier = Modifier.widthIn(max = 304.dp), onDismissRequest = cancel, title = { Text("Unlock setup") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (pin.isEmpty()) "Enter PIN" else "●".repeat(pin.length),
                modifier = Modifier.padding(bottom = 7.dp).clearAndSetSemantics {
                    contentDescription = if (pin.isEmpty()) "Enter PIN" else "${pin.length} digits entered"
                }, style = MaterialTheme.typography.titleLarge)
            ErrorText(state.error)
            for (digits in listOf("123", "456", "789", " 0⌫")) {
                Row(Modifier.widthIn(max = 220.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (digit in digits) {
                        val key = Modifier.weight(1f).heightIn(min = 52.dp)
                        when (digit) {
                            ' ' -> Spacer(key)
                            '⌫' -> FilledTonalButton(onClick = { pin = pin.dropLast(1) }, modifier = key,
                                enabled = pin.isNotEmpty() && !state.busy) {
                                Icon(Icons.AutoMirrored.Rounded.Backspace, "Delete last digit")
                            }
                            else -> FilledTonalButton(onClick = { pin += digit }, modifier = key,
                                enabled = pin.length < 8 && !state.busy) {
                                Text(digit.toString(), style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
            }
        }
    }, confirmButton = {
        Button(onClick = {
            val submitted = pin
            pin = ""
            unlock(submitted)
        }, enabled = !state.busy && pin.length >= 4) {
            Text(if (state.busy) "Checking…" else "Unlock")
        }
    }, dismissButton = { TextButton(onClick = cancel) { Text("Cancel") } })
}

private enum class Source(val title: String) {
    TILE("Add tile"), APP("Choose an app"), MESSAGE("Choose a message"), PRESENCE("Choose a presence sensor")
}

private data class Format(val entity: Entity, val current: TileSpec?, val kinds: List<TileKind>)

@Composable
internal fun EditPanel(state: PanelUi, model: PanelViewModel) {
    var draft by remember(state.config) { mutableStateOf(state.config) }
    var section by remember { mutableStateOf(SetupSection.FAVOURITES) }
    var picker by remember { mutableStateOf<Source?>(null) }
    var format by remember { mutableStateOf<Format?>(null) }
    var panelPage by remember { mutableStateOf<PanelPage?>(null) }
    var choosingPhotos by remember { mutableStateOf(false) }
    val activePicker = picker
    val activeFormat = format
    val detailTitle = when {
        state.connectionEditor -> "Connection"
        activeFormat != null -> if (activeFormat.current == null) "Add tile" else "Edit tile"
        activePicker != null -> activePicker.title
        else -> panelPage?.label
    }

    SetupLayout(
        theme = draft.theme,
        section = section,
        openSection = {
            format = null
            picker = null
            panelPage = null
            model.cancelConnectionChange()
            section = it
        },
        detailTitle = detailTitle,
        canSave = activeFormat == null && activePicker == null && !state.connectionEditor,
        busy = state.busy,
        back = {
            if (!state.busy) {
                when {
                    activeFormat != null -> format = null
                    activePicker != null -> picker = null
                    state.connectionEditor -> model.cancelConnectionChange()
                    panelPage != null -> panelPage = null
                    else -> model.relock()
                }
            }
        },
        close = model::relock,
        save = { model.save(draft) },
    ) {
        when {
            state.connectionEditor -> ConnectionForm(state, model::connect)
            activeFormat != null -> FormatPanel(activeFormat, state) { spec ->
                val current = activeFormat.current
                val tiles = draft.favourites
                draft = draft.copy(favourites = if (current == null) tiles + spec
                    else tiles.map { if (it.entity == current.entity) spec else it })
                format = null
                picker = null
            }
            activePicker == Source.APP -> AppPicker(draft.favourites.mapNotNull { it.appPackage }.toSet()) { tile ->
                draft = draft.copy(favourites = draft.favourites + tile)
                picker = null
            }
            activePicker != null -> EntityPicker(activePicker, draft, state, model::loadCatalogue) { entity ->
                when (activePicker) {
                    Source.TILE -> {
                        val kinds = entity.kinds()
                        if (kinds.size == 1 && kinds.single() != TileKind.IMAGE) {
                            val kind = kinds.single()
                            val featured = kind != TileKind.CAMERA || draft.favourites.none { it.kind == TileKind.CAMERA && it.showOnHome }
                            draft = draft.copy(favourites = draft.favourites + TileSpec(entity.id, kind, showOnHome = featured))
                            picker = null
                        } else {
                            format = Format(entity, null, kinds)
                        }
                    }
                    Source.MESSAGE -> { draft = draft.copy(messageEntity = entity.id); picker = null }
                    Source.PRESENCE -> { draft = draft.copy(presenceEntity = entity.id); picker = null }
                    Source.APP -> Unit
                }
            }
            else -> Column(Modifier.fillMaxWidth()) {
                ErrorText(state.error, Modifier.padding(horizontal = setupPadding))
                when (section) {
                    SetupSection.FAVOURITES -> FavouriteEditor(
                        draft, state,
                        add = { picker = Source.TILE },
                        addApp = { picker = Source.APP },
                        edit = { tile ->
                            val entity = state.entity(tile.entity) ?: Entity(tile.entity, "")
                            format = Format(entity, tile, (entity.kinds() + tile.kind).distinct())
                        },
                        remove = { tile -> draft = draft.copy(favourites = draft.favourites - tile) },
                        move = { id, delta ->
                            val tiles = draft.favourites.toMutableList()
                            val onHome = tiles.indices.filter { tiles[it].showOnHome }
                            val position = onHome.indexOfFirst { tiles[it].entity == id }
                            if (position >= 0 && position + delta in onHome.indices) {
                                java.util.Collections.swap(tiles, onHome[position], onHome[position + delta])
                                draft = draft.copy(favourites = tiles)
                            }
                        },
                    )
                    SetupSection.ROOMS -> RoomSetup(draft, state) { draft = it }
                    SetupSection.CAMERAS -> CameraSetup(draft, state) { draft = it }
                    SetupSection.PANEL -> PanelSettings(
                        page = panelPage,
                        draft = draft,
                        state = state,
                        open = { panelPage = it },
                        change = { draft = it },
                        choosePhotos = { choosingPhotos = true },
                        chooseMessage = { picker = Source.MESSAGE },
                        choosePresence = { picker = Source.PRESENCE },
                        changeConnection = model::changeConnection,
                    )
                }
            }
        }
    }
    if (choosingPhotos) PhotoAlbumPicker(draft.idleAlbum?.id, model::browsePhotos, { album ->
        draft = draft.copy(idleAlbum = album)
        choosingPhotos = false
    }, { choosingPhotos = false })
}

@Composable
private fun FavouriteEditor(
    draft: PanelConfig, state: PanelUi, add: () -> Unit, addApp: () -> Unit, edit: (TileSpec) -> Unit,
    remove: (TileSpec) -> Unit, move: (String, Int) -> Unit
) {
    val homeTiles = draft.favourites.filter { it.showOnHome }
    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = setupPadding),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 13.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = add, enabled = draft.favourites.size < 48, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.Add, null, Modifier.padding(end = 7.dp))
                    Text("Add tile")
                }
                TextButton(onClick = addApp, enabled = draft.favourites.size < 48, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Add app")
                }
            }
        }
        items(homeTiles, key = { it.entity }) { tile ->
            val after = homeTiles.lastOrNull()?.entity != tile.entity
            val before = homeTiles.firstOrNull()?.entity != tile.entity
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    ReorderGrip(state.label(tile), before, after, { move(tile.entity, it) })
                    Surface(onClick = { edit(tile) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.Center) {
                            Text(state.label(tile), style = MaterialTheme.typography.titleMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(tile.kind.label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    TileOptions(state.label(tile), edit = { edit(tile) }, remove = { remove(tile) })
                }
            }
        }
    }
}

@Composable
private fun TileOptions(label: String, edit: () -> Unit, remove: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Rounded.MoreVert, "Options for $label") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Edit tile") }, onClick = { expanded = false; edit() })
            DropdownMenuItem(text = { Text("Remove tile", color = MaterialTheme.colorScheme.error) },
                onClick = { expanded = false; remove() })
        }
    }
}

@Composable
private fun RoomSetup(draft: PanelConfig, state: PanelUi, update: (PanelConfig) -> Unit) {
    var search by remember { mutableStateOf("") }
    val rooms = state.catalogue.rooms
    val chosen = draft.roomIds ?: rooms.map { it.id }
    val visible = remember(rooms, chosen, search) {
        val byId = rooms.associateBy { it.id }
        (chosen.mapNotNull(byId::get) + rooms.filterNot { it.id in chosen })
            .filter { it.name.contains(search.trim(), ignoreCase = true) }
    }
    Column(Modifier.padding(horizontal = setupPadding, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Rooms on this panel", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
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
private fun CameraSetup(draft: PanelConfig, state: PanelUi, update: (PanelConfig) -> Unit) {
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
                        Checkbox(checked = tile != null, onCheckedChange = { inGallery() }, enabled = tile != null || canAdd)
                        Surface(onClick = inGallery, enabled = tile != null || canAdd,
                            modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Text(name, Modifier.padding(vertical = 15.dp, horizontal = 5.dp),
                                style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

@Composable
private fun EntityPicker(source: Source, draft: PanelConfig, state: PanelUi, refresh: () -> Unit, onSelect: (Entity) -> Unit) {
    var query by remember(source) { mutableStateOf("") }
    val catalog = state.catalogue
    val areas = remember(catalog.areas) { catalog.areas.associate { it.id to it.name } }
    val matches = remember(catalog, draft.favourites, source, query) {
        val present = draft.favourites.mapTo(hashSetOf()) { it.entity }
        val term = query.trim()
        catalog.entries.filter { entry ->
            val entity = entry.entity
            val area = areas[entry.area].orEmpty()
            val matchesName = term.isEmpty() || entity.name.contains(term, true) ||
                entity.id.contains(term, true) || area.contains(term, true)
            matchesName && entry.visible && when (source) {
                Source.TILE -> entity.id !in present && (!entry.diagnostic || term.isNotEmpty())
                Source.APP -> false
                Source.MESSAGE -> entity.domain in setOf("sensor", "input_text")
                Source.PRESENCE -> entity.domain == "binary_sensor"
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = setupPadding)) {
        OutlinedTextField(query, { query = it }, label = { Text("Find a device or sensor") }, singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, null) }, modifier = Modifier.fillMaxWidth())
        if (state.loadingCatalogue) CircularProgressIndicator(Modifier.padding(15.dp))
        else if (matches.isEmpty()) {
            Text(if (state.link != Link.ONLINE) "Home Assistant is reconnecting" else "No matching entities",
                Modifier.padding(vertical = 17.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (catalog.entries.isEmpty()) TextButton(onClick = refresh, enabled = state.link == Link.ONLINE) { Text("Refresh devices") }
        }
        LazyColumn(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            items(matches, key = { it.entity.id }) { entry ->
                val entity = entry.entity
                Surface(onClick = { onSelect(entity) }, shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer) {
                    ListItem(headlineContent = { Text(entity.name, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(areas[entry.area] ?: entity.id, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormatPanel(format: Format, state: PanelUi, apply: (TileSpec) -> Unit) {
    var kind by remember(format) { mutableStateOf(format.current?.kind ?: format.kinds.first()) }
    var label by remember(format) { mutableStateOf(format.current?.label.orEmpty()) }
    var status by remember(format) { mutableStateOf(format.current?.statusEntity) }
    var showOnHome by remember(format) { mutableStateOf(format.current?.showOnHome ?: true) }
    var wideOnTablet by remember(format) { mutableStateOf(format.current?.wideOnTablet ?: false) }
    var cameraGate by remember(format) { mutableStateOf(format.current?.cameraGate) }
    var timersOpen by remember { mutableStateOf(false) }
    var gatesOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(setupPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(format.entity.name, style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(label, { label = it.take(72) }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Custom label") }, placeholder = { Text(format.entity.name) }, singleLine = true)
        Text("Display as", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            for (option in format.kinds) FilterChip(selected = kind == option,
                onClick = { kind = option }, label = { Text(option.label) })
        }
        if (kind.resizable) {
            Text("Width on tablets", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !wideOnTablet, onClick = { wideOnTablet = false }, label = { Text("Standard") })
                FilterChip(selected = wideOnTablet, onClick = { wideOnTablet = true }, label = { Text("Wide") })
            }
        }
        if (kind == TileKind.ACTION) {
            Box {
                FilledTonalButton(onClick = { timersOpen = true }) {
                    Text(status?.let { state.entity(it)?.name ?: it } ?: "Link a status timer")
                }
                DropdownMenu(timersOpen, onDismissRequest = { timersOpen = false }) {
                    DropdownMenuItem(text = { Text("No status timer") }, onClick = { status = null; timersOpen = false })
                    for (timer in state.catalogue.entries.filter { it.visible && it.entity.domain == "timer" }) {
                        DropdownMenuItem(text = { Text(timer.entity.name) },
                            onClick = { status = timer.entity.id; timersOpen = false })
                    }
                }
            }
        }
        if (kind == TileKind.CAMERA) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Pin to Favourites")
                Switch(checked = showOnHome, onCheckedChange = { showOnHome = it })
            }
            Box {
                FilledTonalButton(onClick = { gatesOpen = true }) {
                    Text(cameraGate?.let { state.entity(it)?.name ?: it } ?: "Link a gate control")
                }
                DropdownMenu(gatesOpen, onDismissRequest = { gatesOpen = false }) {
                    DropdownMenuItem(text = { Text("No gate control") }, onClick = { cameraGate = null; gatesOpen = false })
                    for (gate in state.catalogue.entries.filter { it.visible && it.entity.gateWithOpenControl }) {
                        DropdownMenuItem(text = { Text(gate.entity.name) },
                            onClick = { cameraGate = gate.entity.id; gatesOpen = false })
                    }
                }
            }
        }
        Button(onClick = { apply(TileSpec(format.entity.id, kind, label.trim().takeIf(String::isNotEmpty),
            status.takeIf { kind == TileKind.ACTION }, showOnHome = kind != TileKind.CAMERA || showOnHome,
            wideOnTablet = kind.resizable && wideOnTablet, cameraGate = cameraGate.takeIf { kind == TileKind.CAMERA })) }) {
            Text(if (format.current == null) "Add" else "Apply")
        }
    }
}

@Composable
private fun ErrorText(message: String?, modifier: Modifier = Modifier) {
    if (message != null) Text(message, modifier, color = MaterialTheme.colorScheme.error)
}
