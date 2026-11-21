package app.flickpanel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import app.flickpanel.ha.HaClient
import app.flickpanel.ha.PanelRemoteState
import app.flickpanel.ha.PanelSensor
import app.flickpanel.ha.PanelSensorReading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import kotlin.math.roundToInt

private val sensorRetryDelays = listOf(0L, 1_000L, 2_000L)

internal suspend fun publishPanelTelemetry(
    context: Context, client: HaClient, webhookId: String, ui: StateFlow<PanelUi>,
) {
    client.panelRemoteState.collectLatest { status ->
        if (status != PanelRemoteState.READY) return@collectLatest
        val display = ui.map(PanelUi::displayReport).distinctUntilChanged().map(DisplayReport::reading)
        val published = mutableMapOf<PanelSensor, PanelSensorReading>()
        combine(
            batteryReadings(context), display, ambientLightReadings(context),
            batterySaverReadings(context), wifiSignalReadings(context),
        ) { battery, screen, light, batterySaver, wifi ->
            battery + screen + listOfNotNull(light, batterySaver, wifi)
        }
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

@OptIn(FlowPreview::class)
private fun ambientLightReadings(context: Context): Flow<PanelSensorReading?> {
    val manager = context.getSystemService(SensorManager::class.java) ?: return flowOf(null)
    val sensor = manager.getDefaultSensor(Sensor.TYPE_LIGHT) ?: return flowOf(null)
    return callbackFlow<PanelSensorReading?> {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val lux = event.values.firstOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: return
                trySend(PanelSensorReading(PanelSensor.ILLUMINANCE, lux.roundToInt()))
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        if (manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
            awaitClose { manager.unregisterListener(listener) }
        } else {
            close()
        }
    }.conflate().sample(10_000).distinctUntilChanged().onStart { emit(null) }
}

private fun Intent.batterySensors(): List<PanelSensorReading> = buildList {
    val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = getIntExtra(BatteryManager.EXTRA_SCALE, 0)
    if (scale > 0 && level in 0..scale) {
        add(PanelSensorReading(PanelSensor.BATTERY, (level * 100f / scale).roundToInt(), batteryDetails()))
    }
    val status = getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
    if (status != BatteryManager.BATTERY_STATUS_UNKNOWN) {
        add(PanelSensorReading(PanelSensor.CHARGING, status == BatteryManager.BATTERY_STATUS_CHARGING))
    }
    val plugged = getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
    if (plugged >= 0) {
        val powerSource = when (plugged) {
            0 -> "battery"
            BatteryManager.BATTERY_PLUGGED_AC -> "ac"
            BatteryManager.BATTERY_PLUGGED_USB -> "usb"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            BatteryManager.BATTERY_PLUGGED_DOCK -> "dock"
            else -> "external"
        }
        add(PanelSensorReading(PanelSensor.PLUGGED, plugged != 0, mapOf("power_source" to powerSource)))
    }
    val temperature = getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
    if (temperature in -400..1000) add(PanelSensorReading(PanelSensor.TEMPERATURE, temperature / 10.0))
}

private fun Intent.batteryDetails(): Map<String, Any> = buildMap {
    val health = when (getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
        BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "failure"
        BatteryManager.BATTERY_HEALTH_COLD -> "cold"
        else -> null
    }
    if (health != null) put("health", health)
    val millivolts = getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
    if (millivolts > 0) put("voltage_v", (millivolts / 100f).roundToInt() / 10.0)
}
