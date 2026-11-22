package app.flickpanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.flickpanel.config.TileAccent

@Composable
internal fun tileContainerColour(accent: TileAccent?, active: Boolean = false): Color {
    val colors = MaterialTheme.colorScheme
    val surface = colors.surfaceContainer
    if (accent == null) return if (active) colors.primaryContainer else surface
    return remember(accent, active, colors) {
        tonedTileContainer(accent, surface, colors.onSurface, colors.onSurfaceVariant, active)
    }
}

internal fun tonedTileContainer(
    accent: TileAccent, surface: Color, label: Color, secondaryLabel: Color, active: Boolean,
): Color {
    val tint = accentInk(accent, surface, label)
    var opacity = .43f
    repeat(5) {
        val container = tint.copy(alpha = opacity).compositeOver(surface)
        if (contrastRatio(label, container) >= 4.5f && contrastRatio(secondaryLabel, container) >= 4.5f &&
            contrastRatio(tint, container) >= 3f
        ) {
            return if (active) container else tint.copy(alpha = opacity * .36f).compositeOver(surface)
        }
        opacity *= .8f
    }
    return surface
}

@Composable
internal fun tileAccentColour(
    accent: TileAccent?,
    surface: Color = MaterialTheme.colorScheme.surfaceContainer,
    fallback: Color = MaterialTheme.colorScheme.primary,
    filledSurface: Color = surface,
): Color {
    if (accent == null) return fallback
    return remember(accent, surface, fallback, filledSurface) {
        accentInk(accent, surface, fallback, filledSurface)
    }
}

internal fun accentInk(accent: TileAccent, surface: Color, fallback: Color, filledSurface: Color = surface): Color {
    val (light, dark) = when (accent) {
        TileAccent.BLUE -> Color(0xFF8AB8FF) to Color(0xFF1A5FAB)
        TileAccent.TEAL -> Color(0xFF65DBD0) to Color(0xFF006B64)
        TileAccent.GREEN -> Color(0xFFA3D578) to Color(0xFF466C16)
        TileAccent.AMBER -> Color(0xFFFFC266) to Color(0xFF8B5603)
        TileAccent.CORAL -> Color(0xFFEF9E87) to Color(0xFF984231)
        TileAccent.ROSE -> Color(0xFFFFA1AF) to Color(0xFF9B3654)
    }
    fun contrast(tint: Color) = minOf(contrastRatio(tint, surface), contrastRatio(tint, filledSurface))
    val best = if (contrast(light) >= contrast(dark)) light else dark
    return if (contrast(best) >= 3f) best else fallback
}

internal fun contrastRatio(ink: Color, surface: Color): Float {
    val foreground = ink.luminance() + .05f
    val background = surface.luminance() + .05f
    return maxOf(foreground, background) / minOf(foreground, background)
}

internal data class TileIconColours(val circle: Color, val ink: Color)

internal fun tileIconColours(surface: Color, preferredInk: Color): TileIconColours {
    val ink = preferredInk.takeIf { contrastRatio(it, surface) >= 3f } ?: contrastInk(surface)
    val tint = ink.copy(alpha = .09f).compositeOver(surface)
    if (contrastRatio(ink, tint) >= 3f) return TileIconColours(tint, ink)

    // Shade away from the glyph when tinting towards it would cost too much contrast.
    val shade = if (ink.luminance() > surface.luminance()) Color.Black else Color.White
    return TileIconColours(shade.copy(alpha = .09f).compositeOver(surface), ink)
}

@Composable
internal fun TileIconBadge(
    icon: ImageVector, surface: Color, tint: Color, size: Dp = tileIconSize, description: String? = null,
) {
    TileIconBadge(surface, tint, size) { ink ->
        Icon(icon, description, Modifier.size((size * .75f).coerceAtMost(24.dp)), tint = ink)
    }
}

@Composable
internal fun TileIconBadge(
    surface: Color, tint: Color, size: Dp = tileIconSize, content: @Composable (Color) -> Unit,
) {
    val colours = remember(surface, tint) { tileIconColours(surface, tint) }
    Box(Modifier.size(size).background(colours.circle, CircleShape), contentAlignment = Alignment.Center) {
        content(colours.ink)
    }
}

@Composable
internal fun AccentedTileTitle(
    label: String, icon: ImageVector?, accent: TileAccent?, modifier: Modifier = Modifier, maxLines: Int = 2,
    customIcon: ImageVector? = null,
    surface: Color = tileContainerColour(accent),
    filledSurface: Color = surface,
) {
    val symbol = customIcon ?: icon
    if (symbol == null || accent == null && customIcon == null) {
        TileTitle(label, modifier, maxLines = maxLines)
        return
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TileIconBadge(symbol, surface, tileAccentColour(accent, surface, filledSurface = filledSurface), size = 24.dp)
        TileTitle(label, Modifier.weight(1f), maxLines = maxLines)
    }
}

@Composable
internal fun TileNameField(name: String, defaultName: String, change: (String) -> Unit) {
    OutlinedTextField(name, { change(it.take(72)) }, modifier = Modifier.fillMaxWidth(),
        label = { Text("Tile name") }, placeholder = { Text(defaultName, maxLines = 1) }, singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done))
}

@Composable
internal fun TileEditorPreview(label: String, detail: String, icon: ImageVector, accent: TileAccent?) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        val previewSurface = if (accent == null) colors.secondaryContainer else tileContainerColour(accent)
        val fallback = if (accent == null) colors.onSecondaryContainer else colors.onSurface
        TileIconBadge(icon, previewSurface, tileAccentColour(accent, previewSurface, fallback), size = 44.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val tileAccents = listOf(null) + TileAccent.entries

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TileAccentPicker(selected: TileAccent?, change: (TileAccent?) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Accent", style = MaterialTheme.typography.titleMedium)
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (accent in tileAccents) {
                val surface = if (selected == accent) colors.secondaryContainer else colors.surface
                val swatch = tileAccentColour(accent, surface)
                FilterChip(
                    selected = selected == accent,
                    onClick = { change(accent) },
                    label = { Text(accent?.label ?: "Theme") },
                    leadingIcon = { Box(Modifier.size(12.dp).background(swatch, CircleShape)) },
                    shape = CircleShape,
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}
