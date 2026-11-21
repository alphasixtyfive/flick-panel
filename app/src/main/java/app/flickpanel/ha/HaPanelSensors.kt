package app.flickpanel.ha

import org.json.JSONObject

internal enum class PanelSensor(
    val id: String,
    val label: String,
    val icon: String,
    val type: String = "sensor",
    val deviceClass: String? = null,
    val unit: String? = null,
) {
    BATTERY("battery_level", "Battery level", "mdi:battery", deviceClass = "battery", unit = "%"),
    CHARGING("battery_charging", "Battery charging", "mdi:battery-charging", "binary_sensor", "battery_charging"),
    PLUGGED("charger_connected", "Charger connected", "mdi:power-plug", "binary_sensor", "plug"),
    TEMPERATURE("battery_temperature", "Battery temperature", "mdi:thermometer", deviceClass = "temperature", unit = "°C"),
    ILLUMINANCE("illuminance", "Ambient light", "mdi:brightness-6", deviceClass = "illuminance", unit = "lx"),
    DISPLAY("display_mode", "Display mode", "mdi:tablet-dashboard"),
}

internal data class PanelSensorReading(
    val sensor: PanelSensor,
    val value: Any,
    val attributes: Map<String, Any> = emptyMap(),
) {
    fun update(): JSONObject = JSONObject()
        .put("type", sensor.type)
        .put("unique_id", sensor.id)
        .put("state", value)
        .put("icon", sensor.icon)
        .put("attributes", JSONObject(attributes))

    fun registration(): JSONObject = update()
        .put("name", sensor.label)
        .put("entity_category", "diagnostic")
        .put("device_class", sensor.deviceClass)
        .put("unit_of_measurement", sensor.unit)
        .put("state_class", if (sensor.unit == null) null else "measurement")
}
