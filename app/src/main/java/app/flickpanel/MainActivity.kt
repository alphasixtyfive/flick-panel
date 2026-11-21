package app.flickpanel

import android.app.ActivityManager
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
import app.flickpanel.ui.OfflineMapAssets
import app.flickpanel.ui.PanelScreen

class MainActivity : ComponentActivity() {
    private val model by viewModels<PanelViewModel>()
    private val offlineMaps = lazy(LazyThreadSafetyMode.NONE) {
        val lowMemory = getSystemService(ActivityManager::class.java)
            ?.let { it.isLowRamDevice || it.memoryClass <= 128 } == true
        OfflineMapAssets(assets, retainDetail = !lowMemory)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val systemBars = WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            val state by model.ui.collectAsStateWithLifecycle()
            val keepScreenOn = state.screenBlank || state.configured && state.keepsDisplayAwake
            val brightness = state.windowBrightness()
            LaunchedEffect(keepScreenOn) {
                if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            LaunchedEffect(brightness) {
                if (window.attributes.screenBrightness != brightness) {
                    window.attributes = window.attributes.apply { screenBrightness = brightness }
                }
            }
            LaunchedEffect(state.configured) {
                val types = WindowInsetsCompat.Type.systemBars()
                if (state.configured) systemBars.hide(types) else systemBars.show(types)
            }
            PanelScreen(state, model) { offlineMaps.value }
        }
    }

    override fun onStart() {
        super.onStart()
        model.visible(true)
    }

    override fun onStop() {
        model.visible(false)
        if (offlineMaps.isInitialized()) offlineMaps.value.clear()
        super.onStop()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        model.touch()
    }
}

private fun PanelUi.windowBrightness(): Float {
    if (screenBlank) return 0f
    val level = brightnessPercent?.div(100f)
    if (configured && clock && keepsDisplayAwake) {
        val idleMaximum = if (config.idleAlbum == null) .13f else .22f
        return minOf(level ?: idleMaximum, idleMaximum)
    }
    return level ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
}
