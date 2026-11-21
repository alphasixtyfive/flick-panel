package app.flickpanel.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

internal val viewerSurface = Color(0xe61b1e21)

@Composable
internal fun ViewerIconButton(
    icon: ImageVector, label: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true,
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = viewerSurface,
            contentColor = Color.White,
            disabledContainerColor = Color(0xb91b1e21),
            disabledContentColor = Color(0xffa1adb9),
        ),
    ) {
        Icon(icon, label)
    }
}
