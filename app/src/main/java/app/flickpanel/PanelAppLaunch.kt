package app.flickpanel

import android.content.ActivityNotFoundException
import android.content.Context

internal fun Context.openPanelApp(packageName: String) {
    val intent = packageManager.getLaunchIntentForPackage(packageName) ?: throw ActivityNotFoundException()
    startActivity(intent)
}
