package app.flickpanel.config

import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

internal enum class PanelBackupAction { BACK_UP, RESTORE }

internal object PanelBackup {
    private const val format = "flick-panel"
    private const val maxBytes = 512 * 1024

    fun write(config: PanelConfig, output: OutputStream) {
        val bytes = JSONObject().put("format", format).put("version", 1)
            .put("layout", JSONObject(PanelConfigCodec.encode(config)))
            .toString(2).toByteArray(Charsets.UTF_8)
        if (bytes.size > maxBytes) throw PanelStoreProblem("This layout is too large to back up")
        output.write(bytes)
    }

    fun read(input: InputStream): PanelConfig {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val length = input.read(buffer)
            if (length == -1) break
            if (bytes.size() + length > maxBytes) throw PanelStoreProblem("This backup file is too large")
            bytes.write(buffer, 0, length)
        }
        try {
            val backup = JSONObject(bytes.toString(Charsets.UTF_8.name()))
            if (backup.optString("format") != format) throw PanelStoreProblem("Choose a Flick Panel backup file")
            if (backup.opt("version") != 1) throw PanelStoreProblem("This backup needs a newer Flick Panel")
            val layout = backup.getJSONObject("layout")
            val tiles = layout.getJSONArray("tiles")
            val config = PanelConfigCodec.decode(layout.toString())
            if (tiles.length() != config.favourites.size) throw PanelStoreProblem("This backup has invalid tiles")
            return config
        } catch (_: JSONException) {
            throw PanelStoreProblem("This backup file couldn't be read")
        } catch (_: IllegalArgumentException) {
            throw PanelStoreProblem("This backup has an unsupported layout")
        }
    }
}
