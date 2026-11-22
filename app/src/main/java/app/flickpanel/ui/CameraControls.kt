package app.flickpanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fence
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flickpanel.PanelUi
import app.flickpanel.config.CameraAction
import app.flickpanel.config.CameraActionIcon
import app.flickpanel.config.CameraActionKind
import app.flickpanel.config.defaultCameraActionLabel
import app.flickpanel.ha.Link
import app.flickpanel.ha.cameraActionKind
import app.flickpanel.ha.cameraActionReady

private val cameraHeaderControlSize = 48.dp
private val cameraHeaderShade = Brush.verticalGradient(
    0f to Color(0xb4000000), .54f to Color(0x8c000000), 1f to Color.Transparent,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CameraControls(
    label: String,
    status: CameraStatus,
    startingCall: Boolean,
    micOn: Boolean,
    notice: String?,
    testResult: CameraTestResult?,
    close: () -> Unit,
    state: PanelUi,
    actions: List<CameraAction>,
    runAction: (String) -> Unit,
    talkControl: (@Composable (compact: Boolean) -> Unit)?,
    speakerControl: (@Composable (Modifier) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
        val inset = if (maxHeight < 480.dp || maxWidth < 600.dp) 12.dp else 20.dp
        val headerInset = inset + 4.dp
        val compact = maxWidth < 480.dp || maxHeight < 360.dp
        val narrow = maxWidth < 420.dp
        val short = maxHeight < 300.dp
        val headerTop = inset + if (short) 0.dp else if (compact) 12.dp else 20.dp
        val dockWidth = maxWidth - inset * 2
        val actionCount = actions.size + if (talkControl == null) 0 else 1
        val useActionMenu = short && actions.size > 1 &&
            (maxWidth < 360.dp || dockWidth < 100.dp * actionCount - 4.dp)
        Box(Modifier.fillMaxSize()) {
            if (talkControl != null || actions.isNotEmpty()) {
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xc20c1014))))
                        .padding(start = inset, end = inset, top = 22.dp, bottom = inset),
                    contentAlignment = Alignment.Center,
                ) {
                    if (useActionMenu) {
                        val showFirst = dockWidth >= if (talkControl == null) 148.dp else 248.dp
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (showFirst) {
                                val first = actions.first()
                                CameraActionButton(first, state, compact = true) { runAction(first.entity) }
                            }
                            talkControl?.invoke(true)
                            CameraActionsMenu(if (showFirst) actions.drop(1) else actions, state, runAction)
                        }
                    } else {
                        val beforeTalk = if (talkControl != null) actions.size / 2 else actions.size
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            actions.forEachIndexed { index, action ->
                                if (index == beforeTalk) talkControl?.invoke(compact)
                                CameraActionButton(action, state, compact) { runAction(action.entity) }
                            }
                            if (beforeTalk == actions.size) talkControl?.invoke(compact)
                        }
                    }
                }
            }
            CameraHeader(
                label, status, startingCall, micOn, notice, testResult, close, speakerControl,
                narrow = narrow,
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .background(cameraHeaderShade)
                    .padding(start = headerInset, top = headerTop, end = headerInset, bottom = 36.dp),
            )
        }
    }
}

@Composable
internal fun IntercomTalkButton(talking: Boolean, live: Boolean, retry: Boolean, compact: Boolean, tap: () -> Unit) {
    val (label, icon) = when {
        talking -> "End call" to Icons.Rounded.CallEnd
        retry -> "Retry intercom" to Icons.Rounded.Refresh
        else -> "Talk" to Icons.Rounded.Mic
    }
    val colors = MaterialTheme.colorScheme
    ViewerAction(
        label, icon, tap, compact,
        enabled = live || talking || retry,
        primary = true,
        background = if (talking) colors.errorContainer else colors.primaryContainer,
        foreground = if (talking) colors.onErrorContainer else colors.onPrimaryContainer,
    )
}

@Composable
private fun CameraActionButton(action: CameraAction, state: PanelUi, compact: Boolean, run: () -> Unit) {
    val control = cameraActionPresentation(action, state)
    val colors = MaterialTheme.colorScheme
    ViewerAction(
        control.label, cameraActionSymbol(action.icon, action.entity), run, compact,
        enabled = control.enabled,
        status = control.status,
        pending = control.pending,
        background = if (control.selected) colors.secondaryContainer else viewerSurface,
        foreground = if (control.selected) colors.onSecondaryContainer else Color.White,
    )
}

@Composable
private fun CameraActionsMenu(actions: List<CameraAction>, state: PanelUi, runAction: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ViewerIconButton(Icons.Rounded.MoreHoriz, "More camera actions", { expanded = true }, Modifier.size(48.dp))
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            for (action in actions) {
                val control = cameraActionPresentation(action, state)
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(control.label)
                            control.status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    },
                    onClick = {
                        expanded = false
                        runAction(action.entity)
                    },
                    enabled = control.enabled,
                )
            }
        }
    }
}

private data class CameraActionPresentation(
    val label: String, val status: String?, val enabled: Boolean, val pending: Boolean, val selected: Boolean,
)

private fun cameraActionPresentation(action: CameraAction, state: PanelUi): CameraActionPresentation {
    val entity = state.entities[action.entity]
    val pending = state.pending[action.entity]
    val kind = entity?.cameraActionKind
    val available = state.link == Link.ONLINE && entity?.available == true && kind != null
    val enabled = available && pending == null && entity?.cameraActionReady == true
    val selected = kind == CameraActionKind.TOGGLE && entity?.state == "on"
    val name = entity?.name ?: action.entity.substringAfter('.').replace('_', ' ')
    val label = action.label ?: defaultCameraActionLabel(action.entity, name)
    val status = when {
        pending != null -> pending
        !available -> "Unavailable"
        kind == CameraActionKind.OPEN_GATE -> entity?.coverStatus()
        kind == CameraActionKind.TOGGLE -> if (selected) "On" else "Off"
        else -> null
    }
    return CameraActionPresentation(label, status, enabled, pending != null, selected)
}

@Composable
private fun ViewerAction(
    label: String,
    icon: ImageVector?,
    tap: () -> Unit,
    compact: Boolean,
    enabled: Boolean,
    primary: Boolean = false,
    status: String? = null,
    pending: Boolean = false,
    background: Color,
    foreground: Color,
) {
    val primarySize = if (compact) 64.dp else 72.dp
    val diameter = if (primary) primarySize else primarySize - 8.dp
    val width = if (compact) 96.dp else 116.dp
    val captionStyle = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge
    val caption = icon != null || pending
    val container = if (enabled) background else viewerSurface
    val content = if (enabled) foreground else Color(0xffb5bfc9)
    Surface(
        onClick = tap,
        enabled = enabled,
        modifier = Modifier.width(width).semantics {
            role = Role.Button
            if (status != null) stateDescription = status
        },
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        contentColor = Color.White,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(primarySize), contentAlignment = Alignment.Center) {
                val button = if (caption) Modifier.size(diameter) else Modifier.size(width - 8.dp, diameter)
                Box(button.background(container, CircleShape), contentAlignment = Alignment.Center) {
                    when {
                        pending -> CircularProgressIndicator(Modifier.size(20.dp), color = content, strokeWidth = 2.dp)
                        icon != null -> Icon(icon, null, Modifier.size(if (primary) 32.dp else 28.dp), tint = content)
                        else -> Text(
                            label, Modifier.padding(horizontal = 8.dp),
                            color = content, style = captionStyle,
                            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (caption) {
                Text(label, Modifier.padding(top = 4.dp, bottom = 3.dp),
                    color = if (enabled) Color.White else Color(0xffb5bfc9),
                    style = captionStyle, textAlign = TextAlign.Center,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

internal fun cameraActionSymbol(icon: CameraActionIcon, entity: String): ImageVector? = when (icon) {
    CameraActionIcon.AUTOMATIC -> when (entity.substringBefore('.')) {
        "cover" -> Icons.Rounded.Fence
        "light" -> Icons.Rounded.Lightbulb
        "switch", "input_boolean" -> Icons.Rounded.PowerSettingsNew
        else -> null
    }
    CameraActionIcon.TEXT_ONLY -> null
    CameraActionIcon.LIGHT -> Icons.Rounded.Lightbulb
    CameraActionIcon.GATE -> Icons.Rounded.Fence
    CameraActionIcon.POWER -> Icons.Rounded.PowerSettingsNew
    CameraActionIcon.SOUND -> Icons.AutoMirrored.Rounded.VolumeUp
    CameraActionIcon.BELL -> Icons.Rounded.Notifications
    CameraActionIcon.PLAY -> Icons.Rounded.PlayArrow
}

@Composable
private fun CameraHeader(
    label: String,
    status: CameraStatus,
    startingCall: Boolean,
    micOn: Boolean,
    notice: String?,
    testResult: CameraTestResult?,
    close: () -> Unit,
    speakerControl: (@Composable (Modifier) -> Unit)?,
    narrow: Boolean,
    modifier: Modifier,
) {
    val statusLabel = testResult?.label ?: when {
        startingCall -> if (narrow) "Connecting…" else "Connecting call…"
        micOn -> "Live · Mic on"
        status == CameraStatus.UNAVAILABLE -> "Unavailable"
        else -> status.label
    }
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                label, Modifier.weight(1f).semantics { heading() },
                color = Color.White,
                style = if (narrow) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                maxLines = if (narrow) 2 else 1, overflow = TextOverflow.Ellipsis,
            )
            speakerControl?.invoke(Modifier.size(cameraHeaderControlSize))
            CameraHeaderStatus(
                statusLabel,
                liveDot = status == CameraStatus.LIVE && (testResult == null || testResult == CameraTestResult.TALK_READY),
                compact = narrow,
                describeLiveVideo = status == CameraStatus.LIVE && testResult != null,
            )
            ViewerIconButton(
                Icons.Rounded.Close, "Close camera", close,
                Modifier.padding(start = if (narrow) 8.dp else 12.dp).size(cameraHeaderControlSize),
            )
        }
        if (notice != null) {
            Surface(
                Modifier.widthIn(max = 240.dp).semantics { liveRegion = LiveRegionMode.Polite },
                color = viewerSurface, contentColor = Color(0xffe1e7ed), shape = panelCardShape,
            ) {
                Text(notice, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun CameraHeaderStatus(
    label: String, liveDot: Boolean, compact: Boolean, describeLiveVideo: Boolean,
) {
    Row(
        Modifier.widthIn(max = if (compact) 126.dp else 170.dp).semantics {
            liveRegion = LiveRegionMode.Polite
            if (describeLiveVideo) stateDescription = "Live video"
        },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (liveDot) Box(Modifier.size(7.dp).background(Color(0xff7fe6a8), CircleShape))
        Text(label, color = Color.White,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            maxLines = if (compact) 2 else 1, overflow = TextOverflow.Ellipsis)
    }
}
