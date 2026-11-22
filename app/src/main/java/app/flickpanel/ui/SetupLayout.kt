package app.flickpanel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.flickpanel.config.PanelTheme

internal val setupPadding = 24.dp
internal val setupBackButtonSize = 48.dp

internal enum class SetupSection(val label: String, val icon: ImageVector) {
    FAVOURITES("Favourites", Icons.Rounded.FavoriteBorder),
    ROOMS("Rooms", Icons.Rounded.MeetingRoom),
    CAMERAS("Cameras", Icons.Rounded.Videocam),
    PANEL("Panel", Icons.Rounded.Settings),
}

@Composable
internal fun SetupLayout(
    theme: PanelTheme,
    highContrast: Boolean,
    reduceMotion: Boolean,
    destination: Any,
    section: SetupSection,
    openSection: (SetupSection) -> Unit,
    detailTitle: String?,
    detailWidth: Dp,
    toolbarAction: (@Composable () -> Unit)?,
    showSave: Boolean,
    busy: Boolean,
    back: () -> Unit,
    close: () -> Unit,
    save: () -> Unit,
    feedback: SnackbarHostState,
    content: @Composable () -> Unit,
) {
    BackHandler(onBack = back)
    PanelColors(theme, highContrast) {
        Surface(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(top = appBarTopPadding)) {
                val fontScale = LocalDensity.current.fontScale
                val twoPanes = maxWidth >= tabletWidthDp.dp && maxWidth >= 640.dp * fontScale
                val shortScreen = !twoPanes && maxHeight < 360.dp && maxWidth >= 440.dp
                val categoryWidth = (224.dp * fontScale.coerceAtLeast(1f)).coerceAtMost(maxWidth / 3)
                Row(Modifier.fillMaxSize()) {
                    if (twoPanes) {
                        SetupCategoryPane(section, openSection, busy, Modifier.width(categoryWidth))
                    } else if (shortScreen) {
                        SetupNavigationRail(section, busy, openSection)
                    }
                    Column(Modifier.weight(1f).fillMaxHeight().padding(start = if (twoPanes) 12.dp else 0.dp)) {
                        SetupToolbar(
                            title = detailTitle ?: section.label,
                            busy = busy,
                            back = back.takeIf { detailTitle != null },
                            close = close,
                            action = toolbarAction,
                        )
                        Column(
                            Modifier.weight(1f).align(if (twoPanes) Alignment.Start else Alignment.CenterHorizontally)
                                .widthIn(max = detailWidth).fillMaxWidth(),
                        ) {
                            Box(Modifier.weight(1f).fillMaxWidth().panelEntrance(destination, reduceMotion)) {
                                content()
                                SnackbarHost(feedback, Modifier.align(Alignment.BottomCenter).padding(horizontal = setupPadding))
                            }
                            if (showSave) {
                                PanelActionFooter(setupPadding) {
                                    Button(onClick = save, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                                        Text("Save changes")
                                    }
                                }
                            }
                            if (!twoPanes && !shortScreen && detailTitle == null) SetupNavigationBar(section, busy, openSection)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupNavigationRail(section: SetupSection, busy: Boolean, open: (SetupSection) -> Unit) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
            for (destination in SetupSection.entries) {
                NavigationRailItem(
                    selected = destination == section,
                    onClick = { open(destination) },
                    enabled = !busy,
                    icon = { Icon(destination.icon, destination.label) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupToolbar(
    title: String,
    busy: Boolean,
    back: (() -> Unit)?,
    close: () -> Unit,
    action: (@Composable () -> Unit)?,
) {
    TopAppBar(
        title = {
            Text(
                title, Modifier.semantics { heading() },
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        },
        actions = {
            action?.invoke()
            ViewerCloseButton("Close settings", close, enabled = !busy)
        },
        modifier = Modifier.padding(start = setupPadding - 16.dp, end = 12.dp),
        navigationIcon = {
            if (back != null) {
                IconButton(onClick = back, modifier = Modifier.padding(end = 8.dp).size(setupBackButtonSize), enabled = !busy) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                }
            }
        },
        windowInsets = WindowInsets(0, 0, 0, 0),
    )
}

@Composable
private fun SetupCategoryPane(
    section: SetupSection, open: (SetupSection) -> Unit, busy: Boolean, modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxHeight().background(colors.surfaceContainerLow).padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", Modifier.padding(start = 12.dp).semantics { heading() },
                style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).selectableGroup().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (destination in SetupSection.entries) {
                val selected = destination == section
                Surface(
                    selected = selected,
                    onClick = { open(destination) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = if (selected) colors.secondaryContainer else Color.Transparent,
                    contentColor = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(destination.icon, null)
                        Text(destination.label, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupNavigationBar(section: SetupSection, busy: Boolean, open: (SetupSection) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelMedium
    val widths = rememberNavigationLabelWidths(SetupSection.entries.map(SetupSection::label), labelStyle, padding = 4.dp)
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        for ((index, destination) in SetupSection.entries.withIndex()) {
            val selected = destination == section
            Column(
                Modifier.weight(widths[index].value).heightIn(min = 80.dp).clip(MaterialTheme.shapes.large)
                    .selectable(selected, enabled = !busy, role = Role.Tab, onClick = { open(destination) })
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                Box(
                    Modifier.size(56.dp, 32.dp)
                        .background(if (selected) colors.secondaryContainer else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(destination.icon, null, tint = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant)
                }
                Text(destination.label, style = labelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (selected) colors.onSurface else colors.onSurfaceVariant)
            }
        }
    }
}
