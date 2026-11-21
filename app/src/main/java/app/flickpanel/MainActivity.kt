package app.flickpanel

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.flickpanel.ui.AmbientClock
import app.flickpanel.ui.CameraViewer
import app.flickpanel.ui.ConnectionForm
import app.flickpanel.ui.Dashboard
import app.flickpanel.ui.EditPanel
import app.flickpanel.ui.MusicBrowser
import app.flickpanel.ui.OpenAgenda
import app.flickpanel.ui.PanelColors
import app.flickpanel.ui.PersonMapScreen
import app.flickpanel.ui.ReaderScreen
import app.flickpanel.ui.UnlockEditor
import app.flickpanel.config.TileKind
import app.flickpanel.ha.Link
import app.flickpanel.ha.PersonLocation
import app.flickpanel.ha.readContent

class MainActivity : ComponentActivity() {
    private val model by viewModels<PanelViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by model.ui.collectAsStateWithLifecycle()
            val wall = state.config.wallMode && state.configured
            val hasPhotos = state.config.idleAlbum != null
            val blank = state.screenBlank
            LaunchedEffect(state.configured, state.clock, wall, hasPhotos, blank) {
                if (wall || blank) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val brightness = when {
                    blank -> 0f
                    wall && state.clock -> if (hasPhotos) .22f else .13f
                    else -> -1f
                }
                if (window.attributes.screenBrightness != brightness) {
                    window.attributes = window.attributes.apply { screenBrightness = brightness }
                }
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    if (state.configured) hide(WindowInsetsCompat.Type.systemBars())
                    else show(WindowInsetsCompat.Type.systemBars())
                }
            }
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
                val music = state.musicBrowser
                val content = state.config.favourites.firstOrNull { it.entity == state.contentOpen && it.kind.hasContentScreen }
                when {
                    blank -> {
                        Box(Modifier.fillMaxSize().background(Color.Black).clickable(
                            interactionSource = null, indication = null, onClickLabel = "Wake panel",
                            role = Role.Button, onClick = model::wake,
                        ))
                        BackHandler(onBack = model::wake)
                    }
                    camera != null -> {
                        val tile = state.config.favourites.firstOrNull { it.entity == camera }
                        val title = tile?.let(state::label)
                        val features = state.entity(camera)?.attributes?.optLong("supported_features", -1L) ?: -1L
                        Box(Modifier.fillMaxSize()) {
                            CameraViewer(camera, title, canStream = features and 2L != 0L, openedAt = state.cameraOpenedAt,
                                gateId = tile?.cameraGate, state = state, model = model)
                            SnackbarHost(snacks, Modifier.align(Alignment.BottomCenter).padding(bottom = 84.dp))
                        }
                        BackHandler(onBack = model::closeCamera)
                    }
                    music != null -> MusicBrowser(music, state.entity(music)?.name, model)
                    content != null -> {
                        val title = state.label(content)
                        when (content.kind) {
                            TileKind.PERSON -> {
                                val live = state.entities[content.entity]?.takeIf { state.link == Link.ONLINE }
                                PersonMapScreen(title, live?.let(PersonLocation::from), model::closeContent)
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
                state.config.favourites.firstOrNull { it.entity == state.agendaOpen && it.kind.isAgenda }?.let { tile ->
                    OpenAgenda(tile, state, model)
                }
                BackHandler(state.configured && state.page != Page.FAVOURITES && !state.clock && !state.screenBlank &&
                    !state.editor && !state.unlocking && !state.hasViewer) {
                    model.selectPage(Page.FAVOURITES)
                }
            }
        }
    }

    override fun onStart() { super.onStart(); model.visible(true) }
    override fun onStop() { model.visible(false); super.onStop() }
    override fun onUserInteraction() { super.onUserInteraction(); model.touch() }
}
