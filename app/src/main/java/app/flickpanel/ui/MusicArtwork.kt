package app.flickpanel.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun MusicArtwork(
    artwork: ImageBitmap?, modifier: Modifier, fallbackSize: Dp = 28.dp, shape: Shape = MaterialTheme.shapes.medium,
) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.clip(shape).background(colors.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        if (artwork == null) Icon(Icons.Rounded.MusicNote, null, Modifier.size(fallbackSize), tint = colors.primary)
        else Image(artwork, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
    }
}
