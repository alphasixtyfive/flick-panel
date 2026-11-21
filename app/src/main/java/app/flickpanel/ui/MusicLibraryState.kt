package app.flickpanel.ui

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.flickpanel.ha.MediaChoice
import kotlinx.coroutines.CancellationException

internal data class MusicFolder(val items: List<MediaChoice> = emptyList(), val failed: Boolean = false)

@Stable
internal class MusicLibraryState {
    var path by mutableStateOf(emptyList<MediaChoice>())
        private set
    private val contents = mutableStateMapOf<MediaChoice?, MusicFolder>()
    private val positions = mutableMapOf<MediaChoice?, LazyGridState>()

    fun content(folder: MediaChoice?) = contents[folder]

    fun position(folder: MediaChoice?) = positions.getOrPut(folder) { LazyGridState() }

    fun enter(folder: MediaChoice, fromRoot: Boolean) {
        path = if (fromRoot) listOf(folder) else path + folder
    }

    fun up() {
        path = path.dropLast(1)
    }

    suspend fun load(folder: MediaChoice?, fetch: suspend (MediaChoice?) -> List<MediaChoice>) {
        if (contents[folder]?.failed == false) return
        contents.remove(folder)
        try {
            contents[folder] = MusicFolder(fetch(folder))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            contents[folder] = MusicFolder(failed = true)
        }
    }
}
