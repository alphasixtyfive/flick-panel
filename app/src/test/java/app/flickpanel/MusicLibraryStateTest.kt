package app.flickpanel

import app.flickpanel.ha.MediaChoice
import app.flickpanel.ui.MusicLibraryState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MusicLibraryStateTest {
    @Test fun keepsTheRootAndRecentlyVisitedFolders() {
        runBlocking {
            val library = MusicLibraryState()
            val folders = List(25) { MediaChoice("folder-$it", "library", "Folder $it", expandable = true, playable = false) }
            val first = folders.first()
            val oldest = folders[1]

            library.load(null) { folders }
            for (folder in folders.take(24)) library.load(folder) { emptyList() }
            library.load(first) { throw AssertionError("A cached folder should not be fetched again") }
            library.load(folders.last()) { emptyList() }

            assertEquals(folders, library.content(null)?.items)
            assertNotNull(library.content(first))
            assertNotNull(library.content(folders.last()))
            assertNull(library.content(oldest))

            library.load(oldest) { listOf(first) }
            assertEquals(listOf(first), library.content(oldest)?.items)
        }
    }
}
