package app.flickpanel

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.flickpanel.ui.PanelScreen

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
            PanelScreen(state, model)
        }
    }

    override fun onStart() {
        super.onStart()
        model.visible(true)
    }

    override fun onStop() {
        model.visible(false)
        super.onStop()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        model.touch()
    }
}
