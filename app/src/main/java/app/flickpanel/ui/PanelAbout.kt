package app.flickpanel.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.flickpanel.BuildConfig
import app.flickpanel.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

@Composable
internal fun PanelAbout(openFontLicence: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            Modifier.size(80.dp),
            color = colorResource(R.color.flick_brand_background),
            shape = MaterialTheme.shapes.large,
        ) {
            Image(painterResource(R.drawable.flick_foreground), contentDescription = null, modifier = Modifier.size(80.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text(
                "Version ${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    SettingsListGroup("Credits") {
        CreditRow("OpenStreetMap contributors", "Local map data · Open Database Licence 1.0")
        CreditRow("Natural Earth", "World map data · Public domain")
        SettingLink(PanelPage.FONT_LICENCE.label, "SIL Open Font License 1.1", openFontLicence)
    }
}

@Composable
private fun CreditRow(name: String, detail: String) {
    ListItem(
        headlineContent = { Text(name) },
        supportingContent = { Text(detail) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
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
