package app.flickpanel.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

@Composable
internal fun Modifier.panelEntrance(destination: Any, reduceMotion: Boolean): Modifier {
    if (reduceMotion) return this
    val opacity = remember(destination) { Animatable(.83f) }
    LaunchedEffect(opacity) {
        opacity.animateTo(1f, tween(durationMillis = 150, easing = FastOutSlowInEasing))
    }
    return graphicsLayer { alpha = opacity.value }
}
