package app.flickpanel.ha

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeParseException

private const val MAX_GRAPH_POINTS = 160

internal data class GraphHistory(val start: Long, val end: Long, val points: List<GraphPoint>)

internal fun decodeHistory(rows: JSONArray?): List<GraphPoint> {
    val points = rows.objects().mapNotNull { row ->
        val value = (row.text("s") ?: row.text("state"))?.toDoubleOrNull()?.takeIf(Double::isFinite)
            ?: return@mapNotNull null
        val timestamp = row.historyTimestamp() ?: return@mapNotNull null
        GraphPoint(timestamp, value)
    }.toMutableList()
    points.sortBy(GraphPoint::time)
    if (points.size <= MAX_GRAPH_POINTS) return points
    val buckets = (MAX_GRAPH_POINTS - 2) / 2
    val interior = points.size - 2
    return buildList(MAX_GRAPH_POINTS) {
        add(points.first())
        repeat(buckets) { bucket ->
            val start = 1 + (bucket.toLong() * interior / buckets).toInt()
            val end = 1 + ((bucket + 1L) * interior / buckets).toInt()
            var low = start
            var high = start
            for (index in start + 1 until end) {
                if (points[index].value < points[low].value) low = index
                if (points[index].value >= points[high].value) high = index
            }
            add(points[minOf(low, high)])
            if (low != high) add(points[maxOf(low, high)])
        }
        add(points.last())
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
