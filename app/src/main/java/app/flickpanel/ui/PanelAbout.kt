package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.flickpanel.BuildConfig
import app.flickpanel.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PanelAbout(openFontLicence: () -> Unit) {
    val openLink = rememberOpenWebLink()
    val typography = MaterialTheme.typography
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.padding(start = 16.dp, top = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.app_name), style = typography.headlineLarge)
        Text(
            "Version ${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}",
            style = typography.bodyLarge, color = secondary,
        )
    }
    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(start = 16.dp, top = 24.dp)) {
        Text("Map data", Modifier.semantics { heading() }, style = typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            AboutLink(MapCredits.OPEN_STREET_MAP.label) { openLink(MapCredits.OPEN_STREET_MAP.link) }
            AboutLink("Natural Earth") { openLink(MapCredits.NATURAL_EARTH.link) }
        }
        Text("Typeface", Modifier.padding(top = 12.dp).semantics { heading() }, style = typography.titleMedium)
        AboutLink(PanelPage.FONT_LICENCE.label, openFontLicence)
    }
}

@Composable
private fun AboutLink(label: String, open: () -> Unit) {
    TextButton(
        onClick = open,
        modifier = Modifier.heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, textDecoration = TextDecoration.Underline)
    }
}

@Composable
internal fun FontLicence() {
    val assets = LocalContext.current.assets
    var licence by remember(assets) { mutableStateOf("Loading licence…") }
    LaunchedEffect(assets) {
        licence = try {
            withContext(Dispatchers.IO) {
                assets.open("licenses/google-sans-flex-ofl.txt").bufferedReader().use { it.readText().trimEnd() }
            }
        } catch (_: IOException) {
            "Licence unavailable"
        }
    }
    Text(licence, style = MaterialTheme.typography.bodyMedium)
}
