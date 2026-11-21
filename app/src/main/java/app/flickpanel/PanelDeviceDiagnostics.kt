package app.flickpanel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import app.flickpanel.ha.PanelSensor
import app.flickpanel.ha.PanelSensorReading
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

internal fun batterySaverReadings(context: Context): Flow<PanelSensorReading?> {
    val power = context.getSystemService(PowerManager::class.java) ?: return flowOf(null)
    return callbackFlow {
        fun report() = trySend(PanelSensorReading(PanelSensor.BATTERY_SAVER, power.isPowerSaveMode))

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                report()
            }
        }
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        report()
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()
}

internal fun wifiSignalReadings(context: Context): Flow<PanelSensorReading?> {
    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)) return flowOf(null)
    val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return flowOf(null)
    return flow {
        while (true) {
            val rssi = wifiRssi(context, connectivity)?.takeIf { it in -126..0 }
            emit(PanelSensorReading(PanelSensor.WIFI_SIGNAL, rssi ?: "unknown"))
            delay(60_000)
        }
    }.distinctUntilChanged()
}

@Suppress("DEPRECATION")
private fun wifiRssi(context: Context, connectivity: ConnectivityManager): Int? {
    val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return null
    if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (capabilities.transportInfo as? WifiInfo)?.rssi
    } else {
        context.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo?.rssi
    }
}
