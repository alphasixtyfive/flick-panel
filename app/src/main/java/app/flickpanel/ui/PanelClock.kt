package app.flickpanel.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.flickpanel.PanelViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Composable
private fun clockFormatter(): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    val use24Hour = DateFormat.is24HourFormat(LocalContext.current)
    return remember(locale, use24Hour) {
        val pattern = DateFormat.getBestDateTimePattern(locale, if (use24Hour) "Hm" else "hma")
        DateTimeFormatter.ofPattern(pattern, locale)
    }
}

@Composable
private fun dateFormatter(): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale)
    }
}

@Composable
private fun clockTime(): ZonedDateTime {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                now = ZonedDateTime.now()
                delay(60_025 - System.currentTimeMillis() % 60_000)
            }
        }
    }
    return now
}

@Composable
internal fun AmbientClock(message: String?, messageEnabled: Boolean, albumId: String?, reduceMotion: Boolean, model: PanelViewModel) {
    val now = clockTime()
    val time = now.format(clockFormatter())
    val date = now.format(dateFormatter())
    val shadow = if (albumId == null) null else Shadow(Color(0xb0000000), Offset(0f, 2f), blurRadius = 12f)
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xff10151b))
        .clickable(interactionSource = null, indication = null, onClickLabel = "Open home controls", onClick = model::wake)) {
        val short = maxHeight < 480.dp
        val density = LocalDensity.current
        val fontScale = density.fontScale.coerceAtLeast(1f)
        IdlePhotos(albumId, model::browsePhotos, model::loadPhoto, reduceMotion, Modifier.matchParentSize())
        val availableClock = minOf((maxWidth - 24.dp).value * .18f, maxHeight.value * .35f)
        val size = minOf(160f, availableClock / fontScale).sp
        Column(Modifier.align(Alignment.Center).padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(time, fontSize = size, lineHeight = size, maxLines = 1,
                fontWeight = FontWeight.Medium, color = Color(0xfff1f6fb), style = MaterialTheme.typography.displayMedium.copy(shadow = shadow))
            Text(date, style = MaterialTheme.typography.titleLarge.copy(shadow = shadow),
                color = Color(0xffedf3fa), modifier = Modifier.padding(top = if (short) 4.dp else 12.dp),
                textAlign = TextAlign.Center, maxLines = if (short) 1 else 2, overflow = TextOverflow.Ellipsis)
            if (messageEnabled) {
                val caption = if (short) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium
                Box(
                    Modifier.widthIn(max = 600.dp).padding(top = if (short) 10.dp else 20.dp)
                        .heightIn(min = with(density) { caption.lineHeight.toDp() }),
                    contentAlignment = Alignment.Center,
                ) {
                    if (message != null) {
                        Text(message, color = Color(0xffeef4fa), textAlign = TextAlign.Center,
                            style = caption.copy(shadow = shadow), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
internal fun HeaderClock(wide: Boolean, showIdleScreen: (() -> Unit)?) {
    val clock = Modifier.heightIn(min = 48.dp)
    val modifier = if (showIdleScreen == null) clock else clock.clickable(
        interactionSource = null, indication = null,
        onClickLabel = "Show idle screen", role = Role.Button, onClick = showIdleScreen,
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        val style = if (wide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleLarge
        Text(clockTime().format(clockFormatter()), style = style.copy(fontFeatureSettings = "'tnum'"), maxLines = 1)
    }
}
