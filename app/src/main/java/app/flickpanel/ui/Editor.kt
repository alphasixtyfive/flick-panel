package app.flickpanel.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.config.HomeBadge
import app.flickpanel.config.PanelBackupAction
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.Area
import app.flickpanel.ha.Entity

private data class RoomTileTarget(val entity: String, val label: String)

private sealed class SetupDestination(val title: String, val allowsSaving: Boolean = false) {
    data class Panel(val page: PanelPage) : SetupDestination(page.label, allowsSaving = !page.readOnly)
    data class Room(val area: Area, val tile: RoomTileTarget? = null) :
        SetupDestination(tile?.label ?: area.name, allowsSaving = true)
    data class Picker(val source: SetupPicker) : SetupDestination(source.title)
    data class Format(val tile: TileFormat) : SetupDestination(if (tile.current == null) "Configure tile" else "Edit tile")
    data class Badge(val badge: HomeBadge) : SetupDestination("Warning badge")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditPanel(state: PanelUi, model: PanelViewModel, backup: (PanelBackupAction) -> Unit) {
    var draft by remember(state.config) { mutableStateOf(state.config) }
    var section by remember { mutableStateOf(SetupSection.FAVOURITES) }
    var destinations by remember { mutableStateOf(emptyList<SetupDestination>()) }
    var choosingPhotos by remember { mutableStateOf(false) }
    var confirmingDiscard by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var removed by remember { mutableStateOf<Pair<Int, TileSpec>?>(null) }
    val editorPages = rememberSaveableStateHolder()
    val current = destinations.lastOrNull()
    val dirty = draft != state.config
    fun navigateTo(destination: SetupDestination) { destinations = destinations + destination }
    fun backToPrevious() { destinations = destinations.dropLast(1) }
    fun choose(source: SetupPicker) {
        search = ""
        navigateTo(SetupDestination.Picker(source))
    }
    val editTile: (TileSpec) -> Unit = { tile ->
        val entity = state.entity(tile.entity) ?: Entity(tile.entity, "")
        navigateTo(SetupDestination.Format(TileFormat(entity, tile, (entity.kinds() + tile.kind).distinct())))
    }
    val finish = {
        if (!state.busy) {
            if (dirty) confirmingDiscard = true else model.relock()
        }
    }
    val detailTitle = if (state.connectionEditor) "Connection" else current?.title

    SetupLayout(
        theme = draft.theme,
        highContrast = draft.highContrast,
        reduceMotion = draft.reduceMotion,
        destination = if (state.connectionEditor) "connection" else current ?: section,
        section = section,
        openSection = {
            destinations = emptyList()
            model.cancelConnectionChange()
            section = it
        },
        detailTitle = detailTitle,
        showSave = dirty && !state.connectionEditor && current?.allowsSaving != false,
        busy = state.busy,
        back = {
            if (!state.busy) {
                when {
                    state.connectionEditor -> model.cancelConnectionChange()
                    current != null -> backToPrevious()
                    else -> finish()
                }
            }
        },
        close = finish,
        save = { model.save(draft) },
    ) {
        if (state.connectionEditor) {
            ConnectionForm(state, model::connect)
        } else when (current) {
            is SetupDestination.Format -> FormatPanel(current.tile, state) { spec ->
                val original = current.tile.current
                val tiles = draft.favourites
                draft = draft.copy(favourites = if (original == null) tiles + spec
                    else tiles.map { if (it.entity == original.entity) spec else it })
                destinations = destinations.dropLast(if (original == null) 2 else 1)
            }
            is SetupDestination.Picker -> when (val source = current.source) {
                SetupPicker.TILE, SetupPicker.APP -> Column {
                    SingleChoiceSegmentedButtonRow(Modifier.padding(horizontal = setupPadding)
                        .widthIn(max = 360.dp).fillMaxWidth()) {
                        val sources = listOf(SetupPicker.TILE to "Home Assistant", SetupPicker.APP to "Android apps")
                        sources.forEachIndexed { index, (option, label) ->
                            SegmentedButton(
                                selected = source == option,
                                onClick = { destinations = destinations.dropLast(1) + SetupDestination.Picker(option) },
                                shape = SegmentedButtonDefaults.itemShape(index, sources.size),
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) { Text(label) }
                        }
                    }
                    if (source == SetupPicker.APP) {
                        val added = remember(draft.favourites) { draft.favourites.mapNotNull { it.appPackage }.toSet() }
                        AppPicker(added) { tile ->
                            draft = draft.copy(favourites = draft.favourites + tile)
                            backToPrevious()
                        }
                    } else {
                        editorPages.SaveableStateProvider("device-picker") {
                            EntityPicker(source, draft, state, search, { search = it }, model::loadCatalogue) { entity ->
                                val featured = entity.domain != "camera" ||
                                    draft.favourites.none { it.kind == TileKind.CAMERA && it.showOnHome }
                                navigateTo(SetupDestination.Format(TileFormat(entity, null, entity.kinds(), featured)))
                            }
                        }
                    }
                }
                SetupPicker.MESSAGE, SetupPicker.PRESENCE, SetupPicker.BADGE -> EntityPicker(
                    source, draft, state, search, { search = it }, model::loadCatalogue,
                ) { entity ->
                    backToPrevious()
                    when (source) {
                        SetupPicker.MESSAGE -> draft = draft.copy(messageEntity = entity.id)
                        SetupPicker.PRESENCE -> draft = draft.copy(presenceEntity = entity.id)
                        else -> {
                            val states = if (entity.domain == "sensor") emptyList() else listOf("on")
                            navigateTo(SetupDestination.Badge(HomeBadge(entity.id, null, states)))
                        }
                    }
                }
            }
            is SetupDestination.Badge -> WarningBadgeEditor(current.badge, state) { saved ->
                val existing = draft.homeBadges
                val next = if (existing.none { it.entity == saved.entity }) existing + saved
                    else existing.map { if (it.entity == saved.entity) saved else it }
                draft = draft.copy(homeBadges = next)
                backToPrevious()
            }
            is SetupDestination.Room -> Column(Modifier.fillMaxWidth()) {
                ErrorText(state.error, Modifier.padding(horizontal = setupPadding))
                RoomControlsEditor(
                    room = current.area,
                    draft = draft,
                    state = state,
                    selectedTile = current.tile?.entity,
                    openTile = { tile -> navigateTo(current.copy(tile = RoomTileTarget(tile.entity, state.label(tile)))) },
                    update = { controls -> draft = draft.copy(roomTiles = draft.roomTiles + (current.area.id to controls)) },
                    reset = { draft = draft.copy(roomTiles = draft.roomTiles - current.area.id) },
                )
            }
            is SetupDestination.Panel, null -> editorPages.SaveableStateProvider(section.name) {
                Column(Modifier.fillMaxWidth()) {
                    ErrorText(state.error, Modifier.padding(horizontal = setupPadding))
                    when (section) {
                        SetupSection.FAVOURITES -> FavouriteEditor(
                            draft, state,
                            add = {
                                editorPages.removeState("device-picker")
                                choose(SetupPicker.TILE)
                            },
                            edit = editTile,
                            resize = { selected ->
                                draft = draft.copy(favourites = draft.favourites.map { tile ->
                                    if (tile.entity == selected.entity && tile.kind.resizable) {
                                        tile.copy(wideOnTablet = !tile.wideOnTablet)
                                    } else tile
                                })
                            },
                            remove = { tile ->
                                val index = draft.favourites.indexOf(tile)
                                if (index >= 0) {
                                    removed = index to tile
                                    draft = draft.copy(favourites = draft.favourites - tile)
                                }
                            },
                            move = { id, delta ->
                                val tiles = draft.favourites.toMutableList()
                                val group = tiles.firstOrNull { it.entity == id }?.kind?.homeGroup
                                val onHome = tiles.indices.filter { tiles[it].showOnHome && tiles[it].kind.homeGroup == group }
                                val position = onHome.indexOfFirst { tiles[it].entity == id }
                                if (position >= 0 && position + delta in onHome.indices) {
                                    java.util.Collections.swap(tiles, onHome[position], onHome[position + delta])
                                    draft = draft.copy(favourites = tiles)
                                }
                            },
                            undo = removed?.let { (index, tile) ->
                                {
                                    val tiles = draft.favourites.toMutableList()
                                    if (tiles.none { it.entity == tile.entity }) {
                                        tiles.add(index.coerceIn(0, tiles.size), tile)
                                        draft = draft.copy(favourites = tiles)
                                    }
                                    removed = null
                                }
                            },
                        )
                        SetupSection.ROOMS -> RoomSetup(draft, state, customize = { navigateTo(SetupDestination.Room(it)) }) { draft = it }
                        SetupSection.CAMERAS -> CameraSetup(draft, state, editTile) { draft = it }
                        SetupSection.PANEL -> PanelSettings(
                            page = (current as? SetupDestination.Panel)?.page,
                            draft = draft,
                            state = state,
                            open = { navigateTo(SetupDestination.Panel(it)) },
                            change = { draft = it },
                            choosePhotos = { choosingPhotos = true },
                            chooseMessage = { choose(SetupPicker.MESSAGE) },
                            choosePresence = { choose(SetupPicker.PRESENCE) },
                            chooseBadge = { choose(SetupPicker.BADGE) },
                            editBadge = { navigateTo(SetupDestination.Badge(it)) },
                            changeConnection = model::changeConnection,
                            linkPanel = model::linkPanel,
                            unlinkPanel = model::unlinkPanel,
                            backup = backup,
                        )
                    }
                }
            }
        }
    }
    if (confirmingDiscard) AlertDialog(onDismissRequest = { confirmingDiscard = false },
        title = { Text("Discard changes?") },
        confirmButton = { TextButton(onClick = model::relock) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { confirmingDiscard = false }) { Text("Keep editing") } })
    if (choosingPhotos) PhotoAlbumPicker(draft.idleAlbum?.id, model::browsePhotos, { album ->
        draft = draft.copy(idleAlbum = album)
        choosingPhotos = false
    }, { choosingPhotos = false })
}
