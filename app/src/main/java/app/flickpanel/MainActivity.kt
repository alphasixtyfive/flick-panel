package app.flickpanel

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.flickpanel.config.PanelBackupAction
import app.flickpanel.ui.OfflineMapAssets
import app.flickpanel.ui.PanelScreen

class MainActivity : ComponentActivity() {
    private val model by viewModels<PanelViewModel>()
    private val backupWriter = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        model.backupDocument(PanelBackupAction.BACK_UP, uri, ::showBackupMessage)
    }
    private val backupReader = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        model.backupDocument(PanelBackupAction.RESTORE, uri, ::showBackupMessage)
    }
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
            val brightness = state.displayBrightness?.div(100f) ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
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
            PanelScreen(state, model, ::chooseBackupFile) { offlineMaps.value }
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

    private fun chooseBackupFile(action: PanelBackupAction) {
        if (!model.beginBackup(action)) return
        try {
            when (action) {
                PanelBackupAction.BACK_UP -> backupWriter.launch("Flick-Panel-backup.json")
                PanelBackupAction.RESTORE -> backupReader.launch(arrayOf("application/json", "application/octet-stream"))
            }
        } catch (_: ActivityNotFoundException) {
            model.backupDocument(action, null, ::showBackupMessage)
            showBackupMessage("Android's file picker is unavailable")
        }
    }

    private fun showBackupMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
