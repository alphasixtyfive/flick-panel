package app.flickpanel.ui

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

@Composable
internal fun rememberCameraAudioFocus(requested: Boolean, interrupted: () -> Unit): Boolean {
    val context = LocalContext.current
    val onInterrupted by rememberUpdatedState(interrupted)
    var focused by remember { mutableStateOf(false) }
    LifecycleResumeEffect(requested) {
        val manager = checkNotNull(context.getSystemService(AudioManager::class.java))
        var active = true
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setOnAudioFocusChangeListener { change ->
                if (active && focused && change < 0) {
                    focused = false
                    onInterrupted()
                }
            }
            .build()
        val granted = requested && manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        focused = granted
        if (requested && !granted) onInterrupted()

        onPauseOrDispose {
            active = false
            focused = false
            if (granted) manager.abandonAudioFocusRequest(request)
        }
    }
    return requested && focused
}
