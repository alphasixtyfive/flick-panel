package app.flickpanel.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
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
    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
        Row(
            Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(
                painterResource(R.drawable.flick_foreground),
                contentDescription = null,
                modifier = Modifier.size(64.dp).background(
                    colorResource(R.color.flick_brand_background), RoundedCornerShape(16.dp),
                ),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.app_name), style = typography.headlineMedium)
                Text("Version ${BuildConfig.VERSION_NAME}", style = typography.bodyLarge, color = secondary)
            }
        }
        val developer = "https://github.com/alphasixtyfive"
        val project = "$developer/flick-panel"
        SettingLink("Developed by", "@alphasixtyfive", { openLink(developer) }, external = true)
        SettingLink("User guide", null, { openLink("$project/blob/main/docs/manual.md") }, external = true)
        SettingLink("Source code", "GitHub", { openLink(project) }, external = true)
        Text(
            "Credits",
            Modifier.padding(start = 16.dp, top = 16.dp).semantics { heading() },
            style = typography.titleMedium,
        )
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
