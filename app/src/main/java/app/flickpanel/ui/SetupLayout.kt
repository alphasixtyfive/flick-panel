package app.flickpanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.flickpanel.config.PanelTheme

internal val setupPadding = 24.dp

internal enum class SetupSection(val label: String, val icon: ImageVector) {
    FAVOURITES("Favourites", Icons.Rounded.FavoriteBorder),
    ROOMS("Rooms", Icons.Rounded.MeetingRoom),
    CAMERAS("Cameras", Icons.Rounded.Videocam),
    PANEL("Panel", Icons.Rounded.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SetupLayout(
    theme: PanelTheme,
    highContrast: Boolean,
    reduceMotion: Boolean,
    destination: Any,
    section: SetupSection,
    openSection: (SetupSection) -> Unit,
    detailTitle: String?,
    canSave: Boolean,
    dirty: Boolean,
    busy: Boolean,
    back: () -> Unit,
    close: () -> Unit,
    save: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = back, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        PanelColors(theme, highContrast) {
            BoxWithConstraints(
                Modifier.fillMaxWidth().imePadding(),
                contentAlignment = Alignment.Center,
            ) {
                val compact = maxWidth < mediumWindowWidthDp.dp
                val expanded = maxWidth >= tabletWidthDp.dp
                val short = maxHeight < 480.dp
                val fontScale = LocalDensity.current.fontScale
                val shortSave = maxWidth < 380.dp || compact && fontScale >= 1.3f
                val narrowToolbar = compact || short
                val margin = if (compact || short) 0.dp else 14.dp
                val detail = detailTitle != null
                val colors = MaterialTheme.colorScheme
                Surface(
                    Modifier.padding(horizontal = margin).widthIn(max = 1000.dp)
                        .fillMaxWidth().fillMaxHeight(if (compact || short) 1f else .92f),
                    color = colors.surface,
                    shape = if (margin == 0.dp) RectangleShape else MaterialTheme.shapes.extraLarge,
                ) {
                    Row {
                        if (expanded) SetupNavigation(section, openSection, close, busy)
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            Row(Modifier.fillMaxWidth().heightIn(min = if (narrowToolbar) 56.dp else 72.dp)
                                .padding(horizontal = if (compact) 12.dp else setupPadding),
                                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (detail || !expanded) {
                                    IconButton(onClick = back, enabled = !busy) {
                                        Icon(if (detail) Icons.AutoMirrored.Rounded.ArrowBack else Icons.Rounded.Close,
                                            if (detail) "Back" else "Cancel editing")
                                    }
                                }
                                Text(detailTitle ?: if (expanded) section.label else "Setup", Modifier.weight(1f).semantics { heading() },
                                    style = if (narrowToolbar) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                                    maxLines = if (short) 1 else 2, overflow = TextOverflow.Ellipsis)
                                if (canSave) {
                                    val label = if (dirty) "Save changes" else "Done"
                                    if (shortSave) {
                                        FilledIconButton(onClick = save, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                                            Icon(Icons.Rounded.Check, label)
                                        }
                                    } else {
                                        Button(onClick = save, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
                                    }
                                }
                            }
                            if (!expanded && !detail) {
                                SetupTabs(section, busy, compact || fontScale >= 1.3f, openSection)
                            }
                            Box(Modifier.weight(1f).fillMaxWidth().panelEntrance(destination, reduceMotion)) {
                                content()
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SetupTabs(section: SetupSection, busy: Boolean, scrollable: Boolean, open: (SetupSection) -> Unit) {
    val color = MaterialTheme.colorScheme.surface
    val tabs: @Composable () -> Unit = {
        for (destination in SetupSection.entries) {
            Tab(
                selected = destination == section,
                onClick = { open(destination) },
                enabled = !busy,
                text = { Text(destination.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
    if (scrollable) {
        SecondaryScrollableTabRow(selectedTabIndex = section.ordinal, containerColor = color, edgePadding = 8.dp, tabs = tabs)
    } else {
        SecondaryTabRow(selectedTabIndex = section.ordinal, containerColor = color, tabs = tabs)
    }
}

@Composable
private fun SetupNavigation(section: SetupSection, open: (SetupSection) -> Unit, close: () -> Unit, busy: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.width(212.dp).fillMaxHeight().background(colors.surfaceContainerLow)
        .verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = close, enabled = !busy) { Icon(Icons.Rounded.Close, "Cancel editing") }
            Text("Setup", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        }
        for (destination in SetupSection.entries) {
            NavigationDrawerItem(
                label = { Text(destination.label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                selected = destination == section,
                onClick = { if (!busy) open(destination) },
                icon = { Icon(destination.icon, null) },
                modifier = Modifier.heightIn(min = 52.dp),
                shape = RoundedCornerShape(24.dp),
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = colors.primaryContainer,
                    selectedTextColor = colors.onPrimaryContainer,
                    selectedIconColor = colors.onPrimaryContainer,
                    unselectedContainerColor = Color.Transparent,
                ),
            )
        }
    }
}
