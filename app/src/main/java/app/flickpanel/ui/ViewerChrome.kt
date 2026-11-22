package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val viewerSurface = Color(0xe61b1e21)
internal val viewerHeaderShade = Brush.verticalGradient(
    0f to Color(0xb4000000), .54f to Color(0x8c000000), 1f to Color.Transparent,
)
internal val appBarTopPadding = 8.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ViewerTopAppBar(
    title: String, closeLabel: String, close: () -> Unit, modifier: Modifier = Modifier,
    status: String? = null,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentGutter: Dp = 16.dp,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier.padding(horizontal = contentGutter - 16.dp).padding(top = appBarTopPadding, end = 12.dp),
        title = { Text(title, Modifier.semantics { heading() }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        actions = {
            if (status != null) {
                Text(
                    status, Modifier.padding(start = 8.dp, end = 16.dp).widthIn(max = 144.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            actions()
            ViewerCloseButton(closeLabel, close)
        },
        windowInsets = windowInsets,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor),
    )
}

@Composable
internal fun ViewerCloseButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        enabled = enabled,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = colors.surfaceContainerHigh,
            contentColor = colors.onSurface,
        ),
    ) {
        Icon(Icons.Rounded.Close, label)
    }
}

@Composable
internal fun PanelActionFooter(gutter: Dp = 16.dp, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = gutter, end = gutter, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

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
