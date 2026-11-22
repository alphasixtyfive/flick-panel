package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
        val project = "https://github.com/alphasixtyfive/flick-panel"
        SettingLink("User guide", null, { openLink("$project/blob/main/docs/manual.md") }, external = true)
        SettingLink("Flick Panel on GitHub", null, { openLink(project) }, external = true)
        Text("Credits", Modifier.padding(start = 16.dp, top = 12.dp).semantics { heading() }, style = typography.titleMedium)
        SettingLink(MapCredits.OPEN_STREET_MAP.label, "Map data", { openLink(MapCredits.OPEN_STREET_MAP.link) }, external = true)
        SettingLink("Natural Earth", "Map data", { openLink(MapCredits.NATURAL_EARTH.link) }, external = true)
        SettingLink(PanelPage.FONT_LICENCE.label, null, openFontLicence)
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
