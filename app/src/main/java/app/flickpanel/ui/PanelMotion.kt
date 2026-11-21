package app.flickpanel.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
internal fun Modifier.panelEntrance(destination: Any, reduceMotion: Boolean): Modifier {
    if (reduceMotion) return this
    val entrance = remember(destination) { Animatable(0f) }
    LaunchedEffect(entrance) {
        entrance.animateTo(1f, tween(durationMillis = 180, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        compositingStrategy = CompositingStrategy.ModulateAlpha
        alpha = .9f + .1f * entrance.value
        translationY = 5.dp.toPx() * (1f - entrance.value)
    }
}
