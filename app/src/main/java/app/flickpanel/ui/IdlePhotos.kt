package app.flickpanel.ui

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.flickpanel.ha.PhotoAlbum
import app.flickpanel.ha.PhotoFolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val photoIntervalMillis = 25_000L
private const val albumRetryMillis = 60_000L
private val clockShade = Brush.radialGradient(
    0f to Color(0x48000000),
    0.45f to Color(0x36000000),
    1f to Color.Transparent,
)

@Composable
internal fun IdlePhotos(
    albumId: String?,
    browse: suspend (String?) -> PhotoAlbum,
    load: suspend (String) -> Bitmap?,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    if (albumId == null) return
    var photo by remember(albumId) { mutableStateOf<Bitmap?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(albumId, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                val photos = photoRequest { browse(albumId).photos }.orEmpty().shuffled()
                if (photos.isEmpty()) {
                    delay(albumRetryMillis)
                    continue
                }
                var hasPhoto = false
                for (id in photos) {
                    val bitmap = photoRequest { load(id) }
                    if (bitmap == null) {
                        delay(2_000)
                        continue
                    }
                    photo = bitmap
                    hasPhoto = true
                    if (photos.size == 1) return@repeatOnLifecycle
                    delay(photoIntervalMillis)
                }
                if (!hasPhoto) delay(albumRetryMillis)
            }
        }
    }
    Box(modifier) {
        Crossfade(photo, Modifier.fillMaxSize(), animationSpec = tween(if (reduceMotion) 0 else 400),
            label = "Idle photo") { bitmap ->
            if (bitmap != null) {
                Image(
                    bitmap = remember(bitmap) { bitmap.asImageBitmap() },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        if (photo != null) Box(Modifier.matchParentSize().background(clockShade))
    }
}

@Composable
internal fun PhotoAlbumPicker(
    selectedId: String?,
    browse: suspend (String?) -> PhotoAlbum,
    onSelect: (PhotoFolder) -> Unit,
    onDismiss: () -> Unit,
) {
    var path by remember { mutableStateOf(emptyList<PhotoFolder>()) }
    val current = path.lastOrNull()
    var album by remember(current) { mutableStateOf<PhotoAlbum?>(null) }
    var failed by remember(current) { mutableStateOf(false) }
    var retry by remember(current) { mutableIntStateOf(0) }
    LaunchedEffect(current, retry) {
        failed = false
        album = photoRequest { browse(current?.id) }
        failed = album == null
    }

    val choice = current?.takeIf { album?.photos?.isNotEmpty() == true }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose photo folder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (current != null) {
                        FilledTonalIconButton(onClick = { path = path.dropLast(1) }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Parent folder")
                        }
                    }
                    Text(
                        current?.title ?: "Home Assistant media",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val displayed = album
                when {
                    failed -> {
                        Text("Couldn't load this folder.")
                        TextButton(onClick = { retry++ }) { Text("Try again") }
                    }
                    displayed == null -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    else -> {
                        val count = displayed.photos.size
                        Text(when (count) {
                            0 -> "No photos in this folder"
                            1 -> "1 photo"
                            else -> "$count photos"
                        })
                        if (displayed.folders.isNotEmpty()) {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 288.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                items(displayed.folders, key = PhotoFolder::id) { folder ->
                                    Surface(
                                        onClick = { path = path + folder },
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shape = MaterialTheme.shapes.medium,
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Icon(Icons.Rounded.Folder, null, Modifier.size(22.dp))
                                            Text(folder.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { choice?.let(onSelect) }, enabled = choice != null) {
                Text(if (choice?.id == selectedId && selectedId != null) "Keep this folder" else "Use this folder")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private suspend fun <T> photoRequest(request: suspend () -> T): T? = try {
    request()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}
