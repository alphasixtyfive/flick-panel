package app.flickpanel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.flickpanel.ui.AmbientClock
import app.flickpanel.ui.CameraViewer
import app.flickpanel.ui.ConnectionForm
import app.flickpanel.ui.Dashboard
import app.flickpanel.ui.EditPanel
import app.flickpanel.ui.PanelColors
import app.flickpanel.ui.UnlockEditor

class MainActivity : ComponentActivity() {
    private val model by viewModels<PanelViewModel>()
    private var charging by mutableStateOf(false)
    private val battery = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ContextCompat.registerReceiver(this, battery, IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED)
        setContent {
            val state by model.ui.collectAsStateWithLifecycle()
            SideEffect {
                val wall = state.config.wallMode && charging && state.configured
                if (wall) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val brightness = if (wall && state.clock) .13f else -1f
                if (window.attributes.screenBrightness != brightness) {
                    window.attributes = window.attributes.apply { screenBrightness = brightness }
                }
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    if (state.configured) hide(WindowInsetsCompat.Type.systemBars())
                    else show(WindowInsetsCompat.Type.systemBars())
                }
            }
            PanelColors(state.config.theme) {
                val snacks = remember { SnackbarHostState() }
                val error = state.error
                LaunchedEffect(error, state.editor, state.unlocking, state.configured) {
                    if (error != null && state.configured && !state.editor && !state.unlocking) {
                        snacks.showSnackbar(error)
                        model.clearError()
                    }
                }
                Scaffold(snackbarHost = { SnackbarHost(snacks) }) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        when {
                            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                            !state.configured -> ConnectionForm(state, model::connect)
                            state.clock -> AmbientClock(state.message, model::wake)
                            else -> Dashboard(state, model)
                        }
                    }
                }
                if (state.unlocking) UnlockEditor(state, model::unlock, model::relock)
                if (state.editor) EditPanel(state, model)
                state.camera?.let { CameraViewer(it, state.entity(it)?.name, model) }
                BackHandler(state.configured && state.page != Page.FAVOURITES && !state.clock &&
                    !state.editor && !state.unlocking && state.camera == null) { model.selectPage(Page.FAVOURITES) }
            }
        }
    }

    override fun onStart() { super.onStart(); model.visible(true) }
    override fun onStop() { model.visible(false); super.onStop() }
    override fun onUserInteraction() { super.onUserInteraction(); model.touch() }
    override fun onDestroy() { unregisterReceiver(battery); super.onDestroy() }
}
