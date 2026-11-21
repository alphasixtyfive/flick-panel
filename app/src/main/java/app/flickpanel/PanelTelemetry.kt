package app.flickpanel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import app.flickpanel.ha.HaClient
import app.flickpanel.ha.PanelRemoteState
import app.flickpanel.ha.PanelSensor
import app.flickpanel.ha.PanelSensorReading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

private val sensorRetryDelays = listOf(0L, 1_000L, 2_000L)

internal suspend fun publishPanelTelemetry(
    context: Context, client: HaClient, webhookId: String, ui: StateFlow<PanelUi>,
) {
    client.panelRemoteState.collectLatest { status ->
        if (status != PanelRemoteState.READY) return@collectLatest
        val display = ui.map(PanelUi::displayReport).distinctUntilChanged().map(DisplayReport::reading)
        val published = mutableMapOf<PanelSensor, PanelSensorReading>()
        combine(batteryReadings(context), display) { battery, screen -> battery + screen }
            .conflate()
            .collect { readings ->
                val changed = readings.filter { published[it.sensor] != it }
                if (changed.isEmpty()) return@collect
                for (pause in sensorRetryDelays) {
                    delay(pause)
                    try {
                        client.publishPanelSensors(webhookId, changed)
                        changed.associateByTo(published, PanelSensorReading::sensor)
                        break
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Keep unacknowledged readings eligible for retry.
                    }
                }
            }
    }
}

private data class DisplayReport(val mode: String, val page: Page, val brightness: Int?, val wallDisplay: Boolean) {
    fun reading() = PanelSensorReading(PanelSensor.DISPLAY, mode, mapOf(
        "page" to page.name.lowercase(),
        "brightness_override" to (brightness ?: "auto"),
        "wall_display" to wallDisplay,
    ))
}

private fun PanelUi.displayReport(): DisplayReport {
    val mode = when {
        screenBlank -> "blank"
        clock -> "clock"
        else -> when (viewer) {
            is PanelViewer.Camera -> "camera"
            is PanelViewer.Music -> "music"
            is PanelViewer.Agenda -> "agenda"
            is PanelViewer.Content -> "content"
            null -> "dashboard"
        }
    }
    return DisplayReport(mode, page, brightnessPercent, keepsDisplayAwake)
}

private fun batteryReadings(context: Context) = callbackFlow {
    val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) trySend(intent.batterySensors())
        }
    }
    val sticky = ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    trySend(sticky?.batterySensors().orEmpty())
    awaitClose { context.unregisterReceiver(receiver) }
}.distinctUntilChanged()

private fun Intent.batterySensors(): List<PanelSensorReading> = buildList {
    val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = getIntExtra(BatteryManager.EXTRA_SCALE, 0)
    if (scale > 0 && level in 0..scale) {
        add(PanelSensorReading(PanelSensor.BATTERY, (level * 100f / scale).roundToInt()))
    }
    val status = getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
    if (status != BatteryManager.BATTERY_STATUS_UNKNOWN) {
        add(PanelSensorReading(PanelSensor.CHARGING, status == BatteryManager.BATTERY_STATUS_CHARGING))
    }
    val plugged = getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
    if (plugged >= 0) add(PanelSensorReading(PanelSensor.PLUGGED, plugged != 0))
    val temperature = getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
    if (temperature in -400..1000) add(PanelSensorReading(PanelSensor.TEMPERATURE, temperature / 10.0))
}
