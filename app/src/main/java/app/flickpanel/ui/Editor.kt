package app.flickpanel.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.config.HomeBadge
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.Area
import app.flickpanel.ha.Entity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditPanel(state: PanelUi, model: PanelViewModel) {
    var draft by remember(state.config) { mutableStateOf(state.config) }
    var section by remember { mutableStateOf(SetupSection.FAVOURITES) }
    var picker by remember { mutableStateOf<SetupPicker?>(null) }
    var format by remember { mutableStateOf<TileFormat?>(null) }
    var room by remember { mutableStateOf<Area?>(null) }
    var badge by remember { mutableStateOf<HomeBadge?>(null) }
    var panelPage by remember { mutableStateOf<PanelPage?>(null) }
    var choosingPhotos by remember { mutableStateOf(false) }
    var confirmingDiscard by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var removed by remember { mutableStateOf<Pair<Int, TileSpec>?>(null) }
    val editorPages = rememberSaveableStateHolder()
    val activePicker = picker
    val activeFormat = format
    val activeRoom = room
    val activeBadge = badge
    val adding = activePicker == SetupPicker.TILE || activePicker == SetupPicker.APP
    val dirty = draft != state.config
    val editTile: (TileSpec) -> Unit = { tile ->
        val entity = state.entity(tile.entity) ?: Entity(tile.entity, "")
        format = TileFormat(entity, tile, (entity.kinds() + tile.kind).distinct())
    }
    val finish = {
        if (!state.busy) {
            if (dirty) confirmingDiscard = true else model.relock()
        }
    }
    val detailTitle = when {
        state.connectionEditor -> "Connection"
        activeFormat != null -> if (activeFormat.current == null) "Add tile" else "Edit tile"
        activePicker != null -> activePicker.title
        activeBadge != null -> "Warning badge"
        activeRoom != null -> activeRoom.name
        else -> panelPage?.label
    }

    SetupLayout(
        theme = draft.theme,
        highContrast = draft.highContrast,
        reduceMotion = draft.reduceMotion,
        destination = activeFormat?.entity?.id ?: activePicker ?: activeRoom?.id ?: activeBadge?.entity ?: detailTitle ?: section,
        section = section,
        openSection = {
            format = null
            picker = null
            badge = null
            panelPage = null
            room = null
            model.cancelConnectionChange()
            section = it
        },
        detailTitle = detailTitle,
        canSave = activeFormat == null && activePicker == null && activeBadge == null && !state.connectionEditor,
        dirty = dirty,
        busy = state.busy,
        back = {
            if (!state.busy) {
                when {
                    activeFormat != null -> format = null
                    activePicker != null -> picker = null
                    activeBadge != null -> badge = null
                    state.connectionEditor -> model.cancelConnectionChange()
                    activeRoom != null -> room = null
                    panelPage != null -> panelPage = when (panelPage) {
                        PanelPage.REMOTE_CONTROL, PanelPage.HOME_BADGES -> PanelPage.HOME_ASSISTANT
                        else -> null
                    }
                    else -> finish()
                }
            }
        },
        close = finish,
        save = { if (dirty) model.save(draft) else model.relock() },
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
            adding -> Column {
                SecondaryTabRow(selectedTabIndex = if (activePicker == SetupPicker.APP) 1 else 0) {
                    Tab(selected = activePicker == SetupPicker.TILE, onClick = { picker = SetupPicker.TILE },
                        text = { Text("Home Assistant") })
                    Tab(selected = activePicker == SetupPicker.APP, onClick = { picker = SetupPicker.APP },
                        text = { Text("Android apps") })
                }
                if (activePicker == SetupPicker.APP) {
                    val added = remember(draft.favourites) { draft.favourites.mapNotNull { it.appPackage }.toSet() }
                    AppPicker(added) { tile ->
                        draft = draft.copy(favourites = draft.favourites + tile)
                        picker = null
                    }
                } else {
                    editorPages.SaveableStateProvider("device-picker") {
                        EntityPicker(SetupPicker.TILE, draft, state, search, { search = it }, model::loadCatalogue) { entity ->
                            val featured = entity.domain != "camera" ||
                                draft.favourites.none { it.kind == TileKind.CAMERA && it.showOnHome }
                            format = TileFormat(entity, null, entity.kinds(), featured)
                        }
                    }
                }
            }
            activePicker != null -> EntityPicker(activePicker, draft, state, search, { search = it }, model::loadCatalogue) { entity ->
                draft = when (activePicker) {
                    SetupPicker.MESSAGE -> draft.copy(messageEntity = entity.id)
                    SetupPicker.PRESENCE -> draft.copy(presenceEntity = entity.id)
                    SetupPicker.BADGE -> {
                        val states = if (entity.domain == "sensor") emptyList() else listOf("on")
                        badge = HomeBadge(entity.id, null, states)
                        draft
                    }
                    else -> draft
                }
                picker = null
            }
            activeBadge != null -> WarningBadgeEditor(activeBadge, state) { saved ->
                val existing = draft.homeBadges
                val next = if (existing.none { it.entity == saved.entity }) existing + saved
                    else existing.map { if (it.entity == saved.entity) saved else it }
                draft = draft.copy(homeBadges = next)
                badge = null
            }
            activeRoom != null -> RoomControlsEditor(
                room = activeRoom,
                draft = draft,
                state = state,
                update = { controls -> draft = draft.copy(roomTiles = draft.roomTiles + (activeRoom.id to controls)) },
                reset = { draft = draft.copy(roomTiles = draft.roomTiles - activeRoom.id) },
            )
            else -> editorPages.SaveableStateProvider(section.name) {
                Column(Modifier.fillMaxWidth()) {
                    ErrorText(state.error, Modifier.padding(horizontal = setupPadding))
                    when (section) {
                        SetupSection.FAVOURITES -> FavouriteEditor(
                            draft, state,
                            add = {
                                search = ""
                                editorPages.removeState("device-picker")
                                picker = SetupPicker.TILE
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
                                    if (tiles.size < 48 && tiles.none { it.entity == tile.entity }) {
                                        tiles.add(index.coerceIn(0, tiles.size), tile)
                                        draft = draft.copy(favourites = tiles)
                                    }
                                    removed = null
                                }
                            },
                        )
                        SetupSection.ROOMS -> RoomSetup(draft, state, customize = { room = it }) { draft = it }
                        SetupSection.CAMERAS -> CameraSetup(draft, state, editTile) { draft = it }
                        SetupSection.PANEL -> PanelSettings(
                            page = panelPage,
                            draft = draft,
                            state = state,
                            open = { panelPage = it },
                            change = { draft = it },
                            choosePhotos = { choosingPhotos = true },
                            chooseMessage = { search = ""; picker = SetupPicker.MESSAGE },
                            choosePresence = { search = ""; picker = SetupPicker.PRESENCE },
                            chooseBadge = { search = ""; picker = SetupPicker.BADGE },
                            editBadge = { badge = it },
                            changeConnection = model::changeConnection,
                            linkPanel = model::linkPanel,
                            unlinkPanel = model::unlinkPanel,
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
