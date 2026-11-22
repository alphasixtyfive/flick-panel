package app.flickpanel

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CameraPreviewFilesTest {
    @get:Rule val files = TemporaryFolder()

    @Test
    fun aPreviewSurvivesARestartAndStaysWithItsAccount() = runBlocking {
        val directory = files.newFolder()
        val preview = byteArrayOf(1, 2, 3)
        CameraPreviewFiles(directory, "account-a", 1024).write("camera.entry", preview)

        val restored = CameraPreviewFiles(directory, "account-a", 1024)
        assertArrayEquals(preview, restored.read("camera.entry"))
        assertNull(CameraPreviewFiles(directory, "account-b", 1024).read("camera.entry"))
        assertNull(restored.read("camera.garage"))
        assertTrue(directory.listFiles().orEmpty().all { it.extension == "preview" })
    }

    @Test
    fun persistenceKeepsThePreviousFrameUntilTheWriteIntervalElapses() = runBlocking {
        val directory = files.newFolder()
        val store = CameraPreviewFiles(directory, "account", 1024)
        val preview = byteArrayOf(1, 2, 3)
        val newer = byteArrayOf(4, 5)
        store.write("camera.entry", preview)
        store.write("camera.entry", newer)
        assertArrayEquals(preview, store.read("camera.entry"))

        assertTrue(directory.listFiles().orEmpty().single().setLastModified(System.currentTimeMillis() - 121_000))
        store.write("camera.entry", newer)
        assertArrayEquals(newer, store.read("camera.entry"))
    }

    @Test
    fun expiryAndByteBudgetKeepOnlyRecentPreviews() = runBlocking {
        val directory = files.newFolder()
        val store = CameraPreviewFiles(directory, "account", 8)
        store.write("camera.expired", ByteArray(3))
        assertTrue(directory.listFiles().orEmpty().single().setLastModified(System.currentTimeMillis() - 4 * 86_400_000L))
        assertNull(store.read("camera.expired"))
        store.write("camera.older", ByteArray(5))
        assertTrue(directory.listFiles().orEmpty().single().setLastModified(System.currentTimeMillis() - 5_000))
        store.write("camera.newer", ByteArray(5))
        assertNull(store.read("camera.older"))
        assertArrayEquals(ByteArray(5), store.read("camera.newer"))
        assertTrue(directory.listFiles().orEmpty().single().length() <= 8)
    }
}
