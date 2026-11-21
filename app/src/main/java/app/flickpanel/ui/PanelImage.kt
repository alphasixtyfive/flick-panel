package app.flickpanel.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException

@Composable
internal fun rememberPanelImage(
    reference: String?, cached: (String) -> Bitmap?, load: suspend (String) -> Bitmap?,
): ImageBitmap? {
    if (reference == null) return null
    var bitmap by remember(reference, cached) { mutableStateOf(cached(reference)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(reference, load, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (bitmap != null) return@repeatOnLifecycle
            bitmap = try {
                load(reference)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
    }
    return remember(bitmap) { bitmap?.asImageBitmap() }
}
