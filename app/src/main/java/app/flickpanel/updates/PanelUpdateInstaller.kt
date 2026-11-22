package app.flickpanel.updates

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.security.MessageDigest
import java.util.UUID

internal suspend fun launchPanelInstaller(context: Context, apk: File, release: PanelRelease, launch: (Intent) -> Unit) {
    val manager = context.packageManager
    val installer = manager.packageInstaller
    var stagedId: Int? = null
    var handedOff = false
    try {
        withContext(Dispatchers.IO) {
            val candidate = try {
                verifyApk(manager, context.packageName, apk, release)
            } catch (badPackage: UpdateProblem) {
                apk.delete()
                throw badPackage
            }
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(candidate.packageName)
                setSize(release.size)
                if (Build.VERSION.SDK_INT >= 31) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
            }
            val id = installer.createSession(params)
            stagedId = id
            installer.openSession(id).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("base.apk", 0, release.size).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        session.fsync(output)
                    }
                }
            }
        }
        val confirmation = installerConfirmation(context, installer, checkNotNull(stagedId))
        currentCoroutineContext().ensureActive()
        launch(confirmation)
        handedOff = true
    } finally {
        val id = stagedId
        if (id != null && !handedOff) {
            withContext(NonCancellable + Dispatchers.IO) {
                // Android may already have closed the session after rejecting the APK.
                runCatching { installer.abandonSession(id) }
            }
        }
    }
}

private suspend fun verifyApk(manager: PackageManager, packageName: String, file: File, release: PanelRelease): PackageInfo {
    if (file.length() != release.size) throw UpdateProblem("The update is incomplete. Download it again.")
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    if (digest.hexDigest() != release.sha256) throw UpdateProblem("The update could not be verified. Download it again.")
    val installed = manager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    val candidate = manager.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
        ?: throw UpdateProblem("Android could not read the update package.")
    if (candidate.packageName != packageName || candidate.versionName != release.version ||
        candidate.longVersionCode <= installed.longVersionCode
    ) throw UpdateProblem("This APK is not a newer Flick Panel release.")
    if ((candidate.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE) > Build.VERSION.SDK_INT) {
        throw UpdateProblem("This update requires a newer version of Android.")
    }
    val current = installed.signingInfo
    val incoming = candidate.signingInfo
    val currentSigners = current?.apkContentsSigners.orEmpty().toSet()
    val incomingSigners = incoming?.apkContentsSigners.orEmpty().toSet()
    val history = incoming?.signingCertificateHistory.orEmpty().toSet()
    val matches = currentSigners.isNotEmpty() && currentSigners == incomingSigners
    val rotated = currentSigners.size == 1 && incomingSigners.size == 1 && history.containsAll(currentSigners + incomingSigners)
    if (!matches && !rotated) throw UpdateProblem("This update uses a different signing key. Use the original update source for this installation.")
    return candidate
}

private suspend fun installerConfirmation(context: Context, installer: PackageInstaller, sessionId: Int): Intent {
    val action = "${context.packageName}.UPDATE.${UUID.randomUUID()}"
    val response = CompletableDeferred<Intent>()
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == action && intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1) == sessionId) {
                response.complete(intent)
            }
        }
    }
    val flags = PendingIntent.FLAG_ONE_SHOT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
    val callback = PendingIntent.getBroadcast(context, sessionId, Intent(action).setPackage(context.packageName), flags)
    ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
    try {
        withContext(Dispatchers.IO) {
            installer.openSession(sessionId).use { it.commit(callback.intentSender) }
        }
        val result = withTimeoutOrNull(20_000) { response.await() }
            ?: throw UpdateProblem("Android did not open the installer. Try again.")
        if (result.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            == PackageInstaller.STATUS_PENDING_USER_ACTION
        ) {
            val intent = if (Build.VERSION.SDK_INT >= 33) result.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            else @Suppress("DEPRECATION") result.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            if (intent != null) return intent
        }
        throw UpdateProblem("Android could not prepare this update for installation.")
    } finally {
        callback.cancel()
        context.unregisterReceiver(receiver)
    }
}
