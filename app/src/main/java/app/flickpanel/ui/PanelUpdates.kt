package app.flickpanel.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.flickpanel.BuildConfig
import app.flickpanel.updates.PanelRelease
import app.flickpanel.updates.PanelUpdateDownload
import app.flickpanel.updates.UpdateProblem
import app.flickpanel.updates.launchPanelInstaller
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

private sealed interface UpdateView {
    data object Checking : UpdateView
    data object Current : UpdateView
    data object Failed : UpdateView
    data object Downloading : UpdateView
    data object Preparing : UpdateView
    data class Available(val release: PanelRelease) : UpdateView
    data class Ready(val release: PanelRelease, val apk: File) : UpdateView
}

@Composable
internal fun PanelUpdates(hasChanges: Boolean) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val downloads = remember(context) { PanelUpdateDownload(context.cacheDir) }
    val scope = rememberCoroutineScope()
    val progress = remember { MutableStateFlow(0) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var view by remember { mutableStateOf<UpdateView>(UpdateView.Checking) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun check() {
        view = UpdateView.Checking
        error = null
        try {
            val release = downloads.check(BuildConfig.VERSION_NAME, Build.SUPPORTED_ABIS.toList())
            val apk = release?.let { downloads.cached(it) }
            view = when {
                release == null -> UpdateView.Current
                apk != null -> UpdateView.Ready(release, apk)
                else -> UpdateView.Available(release)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.updateMessage("Could not check for updates. Try again.")
            view = UpdateView.Failed
        }
    }
    LaunchedEffect(downloads) { check() }

    val current = view
    val status = when (current) {
        UpdateView.Checking -> "Checking for updates…"
        UpdateView.Current -> "Flick Panel is up to date."
        UpdateView.Failed -> null
        UpdateView.Downloading -> {
            val percent by progress.collectAsStateWithLifecycle()
            "Downloading… $percent%"
        }
        UpdateView.Preparing -> "Preparing Android’s installer…"
        is UpdateView.Available -> {
            val size = Formatter.formatShortFileSize(context, current.release.size)
            "Version ${current.release.version} is available · $size"
        }
        is UpdateView.Ready -> "Version ${current.release.version} is ready to install."
    }
    Column(
        Modifier.padding(start = setupBackButtonSize),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Installed version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
        if (status != null) Text(status, style = MaterialTheme.typography.bodyMedium)
        ErrorText(error)
        when (current) {
            UpdateView.Current, UpdateView.Failed -> {
                UpdateAction("Check again") { scope.launch { check() } }
            }
            is UpdateView.Available -> {
                UpdateAction("Download update") {
                    if (view == current && downloadJob?.isActive != true) {
                        view = UpdateView.Downloading
                        error = null
                        progress.value = 0
                        downloadJob = scope.launch {
                            try {
                                val apk = downloads.download(current.release) { progress.value = it }
                                view = UpdateView.Ready(current.release, apk)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                error = failure.updateMessage("Could not download the update. Try again.")
                                view = current
                            }
                        }
                    }
                }
            }
            UpdateView.Downloading -> {
                UpdateAction("Cancel download") {
                    if (view == UpdateView.Downloading) {
                        view = UpdateView.Checking
                        val job = downloadJob
                        job?.cancel()
                        scope.launch {
                            job?.join()
                            check()
                        }
                    }
                }
            }
            is UpdateView.Ready -> {
                if (hasChanges) {
                    Text("Save your changes before installing the update.", style = MaterialTheme.typography.bodyMedium)
                    return@Column
                }
                UpdateAction("Install update") {
                    if (view == current) {
                        if (!context.packageManager.canRequestPackageInstalls()) {
                            error = "Allow Flick Panel to install updates in Android Settings, then open Updates again."
                            try {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.fromParts("package", context.packageName, null)))
                            } catch (_: ActivityNotFoundException) {
                                error = "Open Install unknown apps in Android Settings and allow Flick Panel."
                            } catch (_: SecurityException) {
                                error = "Android does not allow this device to install app updates."
                            }
                        } else {
                            view = UpdateView.Preparing
                            error = null
                            scope.launch {
                                try {
                                    launchPanelInstaller(context, current.apk, current.release) { confirmation ->
                                        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                            throw UpdateProblem("Return to Updates to open Android’s installer.")
                                        }
                                        context.startActivity(confirmation)
                                    }
                                    view = current
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (failure: Exception) {
                                    error = failure.updateMessage("Could not open the installer. Try again.")
                                    view = if (downloads.cached(current.release) != null) current
                                        else UpdateView.Available(current.release)
                                }
                            }
                        }
                    }
                }
            }
            UpdateView.Checking, UpdateView.Preparing -> Unit
        }
    }
}

@Composable
private fun UpdateAction(label: String, action: () -> Unit) {
    TextButton(
        onClick = action,
        modifier = Modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(end = 16.dp),
    ) { Text(label) }
}

private fun Exception.updateMessage(fallback: String): String = (this as? UpdateProblem)?.message ?: fallback
