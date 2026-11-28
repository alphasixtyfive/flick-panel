package app.flickpanel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.flickpanel.Page
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.config.HomeBadge
import app.flickpanel.config.PanelBackupAction
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import app.flickpanel.ha.Area
import app.flickpanel.ha.Entity
import kotlinx.coroutines.launch

private data class RoomTileTarget(val entity: String, val label: String)

private sealed class SetupDestination(val title: String, val allowsSaving: Boolean = false) {
    data class Panel(val page: PanelPage) : SetupDestination(page.label, allowsSaving = !page.readOnly)
    data class Room(val area: Area, val tile: RoomTileTarget? = null) :
        SetupDestination(tile?.label ?: area.name, allowsSaving = true)
    data class Picker(val source: SetupPicker) : SetupDestination(source.title)
    data class Format(val tile: TileFormat) : SetupDestination(if (tile.current == null) "Configure tile" else "Edit tile")
    data class Badge(val badge: HomeBadge) : SetupDestination("Warning badge")
}

@Composable
internal fun EditPanel(state: PanelUi, model: PanelViewModel, backup: (PanelBackupAction) -> Unit) {
    val restorePreview by model.restorePreview.collectAsStateWithLifecycle()
    val draft by remember(state.config) { derivedStateOf { model.setupDraft.value ?: state.config } }
    var section by rememberSaveable {
        mutableStateOf(if (state.connectionEditor) SetupSection.PANEL else SetupSection.FAVOURITES)
    }
    var destinations by remember {
        mutableStateOf<List<SetupDestination>>(
            if (state.connectionEditor) listOf(SetupDestination.Panel(PanelPage.HOME_ASSISTANT)) else emptyList(),
        )
    }
    var previewingFavourites by rememberSaveable { mutableStateOf(false) }
    var choosingPhotos by remember { mutableStateOf(false) }
    var confirmingDiscard by remember { mutableStateOf(false) }
    var confirmingExport by remember { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var removed by remember { mutableStateOf<Pair<Int, TileSpec>?>(null) }
    val editorPages = rememberSaveableStateHolder()
    val feedback = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val current = destinations.lastOrNull()
    val dirty = draft != state.config
    if (previewingFavourites) {
        val close = {
            previewingFavourites = false
            model.touch()
        }
        BackHandler(onBack = close)
        PanelColors(draft.theme, draft.highContrast) {
            Surface(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
                    Dashboard(
                        state.copy(layout = state.layout.copy(config = draft), page = Page.FAVOURITES),
                        model, closePreview = close,
                    )
                }
            }
        }
        return
    }
    fun navigateTo(destination: SetupDestination) {
        model.touch()
        destinations = destinations + destination
    }
    fun backToPrevious() {
        model.touch()
        destinations = destinations.dropLast(1)
    }
    fun updateSearch(value: String) {
        model.touch()
        search = value
    }
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
    val detailTitle = if (state.connectionEditor) "Home Assistant connection" else current?.title
    val picker = (current as? SetupDestination.Picker)?.source

    SetupLayout(
        theme = draft.theme,
        highContrast = draft.highContrast,
        reduceMotion = draft.reduceMotion,
        destination = if (state.connectionEditor) "connection" else current ?: section,
        section = section,
        openSection = {
            model.touch()
            destinations = emptyList()
            model.cancelConnectionChange()
            section = it
        },
        detailTitle = detailTitle,
        detailWidth = if (picker == SetupPicker.TILE || picker == SetupPicker.APP) 880.dp else 760.dp,
        toolbarAction = if (picker == SetupPicker.TILE) {
            {
                TextButton(
                    onClick = { navigateTo(SetupDestination.Picker(SetupPicker.APP)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Rounded.Apps, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Apps")
                }
            }
        } else null,
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
        save = { model.save(draft) { scope.launch { feedback.showSnackbar("Changes saved") } } },
        feedback = feedback,
    ) {
        if (state.connectionEditor) {
            ConnectionForm(state, model::connect, onInput = {
                model.touch()
                model.clearError()
            })
        } else when (current) {
            is SetupDestination.Format -> FormatPanel(current.tile, state, model) { spec ->
                val original = current.tile.current
                val tiles = draft.favourites
                model.updateSetupDraft(draft.copy(favourites = if (original == null) tiles + spec
                    else tiles.map { if (it.entity == original.entity) spec else it }))
                destinations = destinations.dropLast(if (original == null) 2 else 1)
            }
            is SetupDestination.Picker -> when (val source = current.source) {
                SetupPicker.TILE -> editorPages.SaveableStateProvider("device-picker") {
                    EntityPicker(source, draft, state, search, ::updateSearch, model::loadCatalogue) { entity ->
                        val featured = entity.domain != "camera" ||
                            draft.favourites.none { it.kind == TileKind.CAMERA && it.showOnHome }
                        navigateTo(SetupDestination.Format(TileFormat(entity, null, entity.kinds(), featured)))
                    }
                }
                SetupPicker.APP -> {
                    val added = remember(draft.favourites) { draft.favourites.mapNotNull { it.appPackage }.toSet() }
                    AppPicker(added, model::touch) { tile ->
                        model.updateSetupDraft(draft.copy(favourites = draft.favourites + tile))
                        destinations = destinations.dropLast(2)
                    }
                }
                SetupPicker.MESSAGE, SetupPicker.SCREENSAVER_MESSAGE, SetupPicker.BADGE -> EntityPicker(
                    source, draft, state, search, ::updateSearch, model::loadCatalogue,
                ) { entity ->
                    backToPrevious()
                    when (source) {
                        SetupPicker.MESSAGE -> model.updateSetupDraft(draft.copy(messageEntity = entity.id))
                        SetupPicker.SCREENSAVER_MESSAGE -> model.updateSetupDraft(draft.copy(screensaverMessageEntity = entity.id))
                        else -> {
                            val states = if (entity.domain == "sensor") emptyList() else listOf("on")
                            navigateTo(SetupDestination.Badge(HomeBadge(entity.id, null, states)))
                        }
                    }
                }
            }
            is SetupDestination.Badge -> WarningBadgeEditor(current.badge, state, model::touch) { saved ->
                val existing = draft.homeBadges
                val next = if (existing.none { it.entity == saved.entity }) existing + saved
                    else existing.map { if (it.entity == saved.entity) saved else it }
                model.updateSetupDraft(draft.copy(homeBadges = next))
                backToPrevious()
            }
            is SetupDestination.Room -> Column(Modifier.fillMaxWidth()) {
                ErrorText(state.error, Modifier.padding(horizontal = setupPadding))
                RoomControlsEditor(
                    room = current.area,
                    draft = draft,
                    state = state,
                    selectedTile = current.tile?.entity,
                    onInput = model::touch,
                    openTile = { tile -> navigateTo(current.copy(tile = RoomTileTarget(tile.entity, state.label(tile)))) },
                    update = { controls ->
                        model.updateSetupDraft(draft.copy(roomTiles = draft.roomTiles + (current.area.id to controls)))
                    },
                    reset = { model.updateSetupDraft(draft.copy(roomTiles = draft.roomTiles - current.area.id)) },
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
                            preview = {
                                model.touch()
                                previewingFavourites = true
                            },
                            edit = editTile,
                            resize = { selected ->
                                model.updateSetupDraft(draft.copy(favourites = draft.favourites.map { tile ->
                                    if (tile.entity == selected.entity && tile.kind.resizable) {
                                        tile.copy(wideOnTablet = !tile.wideOnTablet)
                                    } else tile
                                }))
                            },
                            remove = { tile ->
                                val index = draft.favourites.indexOf(tile)
                                if (index >= 0) {
                                    removed = index to tile
                                    model.updateSetupDraft(draft.copy(favourites = draft.favourites - tile))
                                }
                            },
                            move = { id, delta ->
                                val tiles = draft.favourites.toMutableList()
                                val group = tiles.firstOrNull { it.entity == id }?.kind?.homeGroup
                                val onHome = tiles.indices.filter { tiles[it].showOnHome && tiles[it].kind.homeGroup == group }
                                val position = onHome.indexOfFirst { tiles[it].entity == id }
                                if (position >= 0 && position + delta in onHome.indices) {
                                    java.util.Collections.swap(tiles, onHome[position], onHome[position + delta])
                                    model.updateSetupDraft(draft.copy(favourites = tiles))
                                }
                            },
                            undo = removed?.let { (index, tile) ->
                                {
                                    val tiles = draft.favourites.toMutableList()
                                    if (tiles.none { it.entity == tile.entity }) {
                                        tiles.add(index.coerceIn(0, tiles.size), tile)
                                        model.updateSetupDraft(draft.copy(favourites = tiles))
                                    }
                                    removed = null
                                }
                            },
                        )
                        SetupSection.ROOMS -> RoomSetup(
                            draft, state, customize = { navigateTo(SetupDestination.Room(it)) }, update = model::updateSetupDraft,
                        )
                        SetupSection.CAMERAS -> CameraSetup(draft, state, editTile, model::updateSetupDraft)
                        SetupSection.PANEL -> PanelSettings(
                            page = (current as? SetupDestination.Panel)?.page,
                            draft = draft,
                            state = state,
                            open = { navigateTo(SetupDestination.Panel(it)) },
                            change = model::updateSetupDraft,
                            choosePhotos = { choosingPhotos = true },
                            chooseMessage = { choose(SetupPicker.MESSAGE) },
                            chooseScreensaverMessage = { choose(SetupPicker.SCREENSAVER_MESSAGE) },
                            chooseBadge = { choose(SetupPicker.BADGE) },
                            editBadge = { navigateTo(SetupDestination.Badge(it)) },
                            onInput = model::touch,
                            changeConnection = model::changeConnection,
                            linkPanel = model::linkPanel,
                            unlinkPanel = model::unlinkPanel,
                            backup = { action ->
                                if (action == PanelBackupAction.BACK_UP && dirty) confirmingExport = true
                                else model.beginBackup(action, openFilePicker = backup)
                            },
                        )
                    }
                }
            }
        }
    }
    if (confirmingDiscard) AlertDialog(onDismissRequest = { confirmingDiscard = false },
        title = { Text("Discard changes?") },
        confirmButton = { TextButton(onClick = model::discardSetup) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { confirmingDiscard = false }) { Text("Keep editing") } })
    if (confirmingExport) AlertDialog(
        onDismissRequest = { confirmingExport = false },
        title = { Text("Include unsaved changes?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Save your edits and include them in the backup, or export the last saved layout.")
                TextButton(onClick = {
                    confirmingExport = false
                    model.beginBackup(PanelBackupAction.BACK_UP, openFilePicker = backup)
                }) { Text("Export last saved") }
            }
        },
        dismissButton = { TextButton(onClick = { confirmingExport = false }) { Text("Cancel") } },
        confirmButton = {
            TextButton(onClick = {
                confirmingExport = false
                model.beginBackup(PanelBackupAction.BACK_UP, saveChanges = true, openFilePicker = backup)
            }) { Text("Save and export") }
        },
    )
    restorePreview?.let { config ->
        AlertDialog(
            onDismissRequest = { if (!state.busy) model.cancelRestore() },
            title = { Text("Restore this backup?") },
            text = {
                val favourites = config.favourites.count { it.showOnHome }
                val rooms = config.roomTiles.size
                val cameras = config.favourites.count { it.kind == TileKind.CAMERA }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((label, count) in listOf("Favourites" to favourites, "Customised rooms" to rooms, "Saved cameras" to cameras)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label)
                            Text(count.toString())
                        }
                    }
                    Text(if (dirty) "This replaces the saved layout and your unsaved edits."
                        else "This replaces the saved layout and panel preferences.")
                    ErrorText(state.error)
                }
            },
            dismissButton = {
                TextButton(enabled = !state.busy, onClick = model::cancelRestore) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(enabled = !state.busy, onClick = {
                    model.restoreBackup { scope.launch { feedback.showSnackbar("Backup restored") } }
                }) { Text("Restore layout") }
            },
        )
    }
    if (choosingPhotos) PhotoAlbumPicker(draft.idleAlbum?.id, model::browsePhotos, { album ->
        model.updateSetupDraft(draft.copy(idleAlbum = album))
        choosingPhotos = false
    }, { choosingPhotos = false })
}
