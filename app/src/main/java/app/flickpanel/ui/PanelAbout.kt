package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import app.flickpanel.BuildConfig
import app.flickpanel.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

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
    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(top = 20.dp)) {
        Text("Credits", Modifier.padding(start = 16.dp, bottom = 8.dp).semantics { heading() },
            style = typography.titleMedium, color = secondary)
        AboutLink(MapCredits.OPEN_STREET_MAP.label, external = true) { openLink(MapCredits.OPEN_STREET_MAP.link) }
        AboutLink("Natural Earth", external = true) { openLink(MapCredits.NATURAL_EARTH.link) }
        AboutLink(PanelPage.FONT_LICENCE.label, open = openFontLicence)
    }
}

@Composable
private fun AboutLink(label: String, external: Boolean = false, open: () -> Unit) {
    TextButton(
        onClick = open,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Icon(
            if (external) Icons.AutoMirrored.Rounded.OpenInNew else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            if (external) "Opens in browser" else null,
            Modifier.padding(start = 12.dp).size(20.dp),
        )
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
