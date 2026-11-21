package app.flickpanel.ha

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeParseException

private const val MAX_GRAPH_POINTS = 160

internal fun decodeHistory(rows: JSONArray?): List<GraphPoint> {
    val points = rows.objects().mapNotNull { row ->
        val value = (row.text("s") ?: row.text("state"))?.toDoubleOrNull()?.takeIf(Double::isFinite)
            ?: return@mapNotNull null
        val timestamp = row.historyTimestamp() ?: return@mapNotNull null
        GraphPoint(timestamp, value)
    }.toMutableList()
    points.sortBy(GraphPoint::time)
    if (points.size <= MAX_GRAPH_POINTS) return points
    // Keep both endpoints and distribute the remaining points across the period.
    return List(MAX_GRAPH_POINTS) { index ->
        points[(index.toLong() * points.lastIndex / (MAX_GRAPH_POINTS - 1)).toInt()]
    }
}

private fun JSONObject.historyTimestamp(): Long? {
    val seconds = optDouble("lu", optDouble("lc", Double.NaN))
    if (seconds.isFinite()) return (seconds * 1_000).toLong()
    val timestamp = text("last_updated") ?: text("last_changed") ?: return null
    return try {
        Instant.parse(timestamp).toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    } catch (_: ArithmeticException) {
        null
    }
}
