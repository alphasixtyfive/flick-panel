package app.flickpanel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.flickpanel.Page
import app.flickpanel.PanelUi
import app.flickpanel.PanelViewModel
import app.flickpanel.config.TileKind
import app.flickpanel.ha.Link
import app.flickpanel.ha.PersonLocation
import app.flickpanel.ha.personStatus
import app.flickpanel.ha.readContent

@Composable
internal fun PanelScreen(state: PanelUi, model: PanelViewModel) {
    PanelColors(state.config.theme, state.config.highContrast) {
        val snacks = remember { SnackbarHostState() }
        val dashboard = rememberSaveableStateHolder()
        val error = state.error
        LaunchedEffect(error, state.editor, state.unlocking, state.configured) {
            if (error != null && state.configured && !state.editor && !state.unlocking) {
                snacks.showSnackbar(error)
                model.clearError()
            }
        }
        val camera = state.camera
        val music = state.musicOpen
        val agenda = state.config.favourites.firstOrNull { it.entity == state.agendaOpen && it.kind.isAgenda }
        val content = state.config.favourites.firstOrNull { it.entity == state.contentOpen && it.kind.hasContentScreen }
        when {
            state.screenBlank -> {
                Box(Modifier.fillMaxSize().background(Color.Black).clickable(
                    interactionSource = null, indication = null, onClickLabel = "Wake panel",
                    role = Role.Button, onClick = model::wake,
                ))
                BackHandler(onBack = model::wake)
            }
            camera != null -> {
                val tile = state.config.favourites.firstOrNull { it.entity == camera }
                val title = tile?.let(state::label)
                val streamCamera = tile?.cameraStream?.takeIf { state.entities[it]?.available == true } ?: camera
                val features = state.entity(streamCamera)?.attributes?.optLong("supported_features", -1L) ?: -1L
                Box(Modifier.fillMaxSize()) {
                    key(camera) {
                        CameraViewer(camera, title, canStream = features and 2L != 0L, openedAt = state.cameraOpenedAt,
                            gateId = tile?.cameraGate, state = state, model = model, streamCamera = streamCamera)
                    }
                    SnackbarHost(snacks, Modifier.align(Alignment.BottomCenter).padding(bottom = 84.dp))
                }
                BackHandler(onBack = model::closeCamera)
            }
            music != null -> Box(Modifier.fillMaxSize()) {
                MusicPlayerScreen(music, state, model)
                SnackbarHost(snacks, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
            }
            agenda != null -> {
                OpenAgenda(agenda, state, model)
                BackHandler(onBack = model::closeAgenda)
            }
            content != null -> {
                val title = state.label(content)
                when (content.kind) {
                    TileKind.PERSON -> {
                        val live = state.entities[content.entity]?.takeIf { state.link == Link.ONLINE }
                        PersonMapScreen(title, live?.let(PersonLocation::from), close = model::closeContent, status = live?.personStatus())
                    }
                    TileKind.READER -> ReaderScreen(title,
                        readContent(state.entity(content.entity), content.readerAttribute), model::closeContent)
                    else -> Unit
                }
                BackHandler(onBack = model::closeContent)
            }
            else -> dashboard.SaveableStateProvider("dashboard") {
                val insets = if (state.configured) WindowInsets.displayCutout else ScaffoldDefaults.contentWindowInsets
                Scaffold(snackbarHost = { SnackbarHost(snacks) }, contentWindowInsets = insets) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        when {
                            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                            !state.configured -> ConnectionForm(state, model::connect)
                            state.clock -> AmbientClock(state.message, state.config.idleAlbum?.id, state.config.reduceMotion, model)
                            else -> Dashboard(state, model)
                        }
                    }
                }
            }
        }
        if (state.unlocking) UnlockEditor(state, model::unlock, model::relock)
        if (state.editor) EditPanel(state, model)
        BackHandler(state.configured && state.page != Page.FAVOURITES && !state.clock && !state.screenBlank &&
            !state.editor && !state.unlocking && !state.hasViewer) {
            model.selectPage(Page.FAVOURITES)
        }
    }
}
