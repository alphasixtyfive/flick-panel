package app.flickpanel.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.flickpanel.ha.Entity
import app.flickpanel.ha.PersonLocation
import app.flickpanel.ha.personStatus

@Composable
internal fun PersonTile(
    label: String, live: Entity?, online: Boolean, modifier: Modifier, avatar: ImageBitmap? = null, open: () -> Unit,
) {
    val person = live.takeIf { online }
    val location = person?.let { PersonLocation.from(it) }
    val colors = MaterialTheme.colorScheme

    Surface(
        onClick = open,
        enabled = location != null,
        modifier = modifier.semantics { if (location != null) onClick(label = "Open map", action = null) },
        shape = panelCardShape,
        color = colors.surfaceContainer,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(tileContentPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PersonAvatar(label, avatar)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TileTitle(label)
                val status = person?.personStatus()
                if (status != null) TileValue(status, maxLines = 2)
                else TileStatus("Unavailable")
            }
        }
    }
}

@Composable
private fun PersonAvatar(name: String, avatar: ImageBitmap?) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.size(56.dp).clearAndSetSemantics {}, shape = CircleShape,
        color = colors.secondaryContainer, contentColor = colors.onSecondaryContainer) {
        if (avatar != null) {
            Image(avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            val initials = remember(name) { avatarInitials(name) }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (initials.isEmpty()) {
                    Icon(Icons.Rounded.Person, null, Modifier.size(compactTileIconSize))
                } else {
                    val type = MaterialTheme.typography.titleMedium
                    val style = with(LocalDensity.current) { type.copy(fontSize = 22.dp.toSp(), lineHeight = 28.dp.toSp()) }
                    Text(initials, style = style, maxLines = 1)
                }
            }
        }
    }
}

private val nameSpacing = Regex("[\\p{Z}\\s]+")
private val nameInitial = Regex("\\p{L}\\p{M}*|\\p{N}")

internal fun avatarInitials(name: String): String {
    val parts = name.splitToSequence(nameSpacing).mapNotNull { nameInitial.find(it)?.value }.toList()
    return when (parts.size) {
        0 -> ""
        1 -> parts.first().uppercase()
        else -> (parts.first() + parts.last()).uppercase()
    }
}
