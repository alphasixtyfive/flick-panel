package app.flickpanel.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.flickpanel.config.TileKind
import app.flickpanel.config.TileSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class LauncherApp(val packageName: String, val label: String)

internal fun openHomeAppSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Home app settings are unavailable", Toast.LENGTH_SHORT).show()
    }
}

@Composable
internal fun AppPicker(added: Set<String>, choose: (TileSpec) -> Unit) {
    val context = LocalContext.current
    var apps by remember(context.packageName) { mutableStateOf<List<LauncherApp>?>(null) }
    LaunchedEffect(context.packageName) {
        apps = withContext(Dispatchers.IO) {
            val manager = context.packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            manager.queryIntentActivities(launcher, 0).mapNotNull {
                val pkg = it.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) null else LauncherApp(pkg, it.loadLabel(manager).toString())
            }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
        }
    }
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(horizontal = setupPadding, vertical = 9.dp)) {
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search apps") })
        val available = apps
        if (available == null) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(15.dp))
            return@Column
        }
        val results = remember(available, added, query) {
            available.filter { it.packageName !in added && it.label.contains(query.trim(), ignoreCase = true) }
        }
        if (results.isEmpty()) Text("No matching apps", Modifier.padding(vertical = 14.dp))
        LazyColumn(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(results, key = { it.packageName }) { app ->
                Surface(onClick = { choose(TileSpec("app:${app.packageName}", TileKind.APP, app.label.take(72))) },
                    shape = panelCardShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text(app.label, Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
internal fun AppTile(tile: TileSpec, modifier: Modifier) {
    val context = LocalContext.current
    val pkg = tile.appPackage ?: return
    val label = tile.label ?: pkg.substringAfterLast('.')
    var icon by remember(pkg) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(pkg) {
        icon = withContext(Dispatchers.IO) {
            try { context.packageManager.getApplicationIcon(pkg).toBitmap(72, 72) }
            catch (_: PackageManager.NameNotFoundException) { null }
        }
    }
    val image = remember(icon) { icon?.asImageBitmap() }
    Surface(onClick = {
        try {
            val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: throw ActivityNotFoundException()
            context.startActivity(launch)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "$label is unavailable", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, "$label couldn't open", Toast.LENGTH_SHORT).show()
        }
    }, modifier = modifier, shape = panelCardShape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(tileContentPadding), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (image != null) Image(image, null, Modifier.size(29.dp))
            else Icon(Icons.Rounded.Apps, null, Modifier.size(29.dp), tint = MaterialTheme.colorScheme.primary)
            TileTitle(label, Modifier.weight(1f))
            Icon(Icons.Rounded.ArrowOutward, "Open app", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
