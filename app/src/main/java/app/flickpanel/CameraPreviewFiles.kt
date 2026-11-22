package app.flickpanel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

internal class CameraPreviewFiles(
    private val directory: File,
    private val account: String,
    private val byteLimit: Long,
) {
    suspend fun read(entity: String): ByteArray? = onDisk {
        val file = path(entity)
        if (!file.isRecent(System.currentTimeMillis())) return@onDisk null
        file.inputStream().use { input ->
            val size = input.channel.size()
            if (size !in 1..maxPreviewBytes.toLong()) return@onDisk null
            ByteArray(size.toInt()).also { DataInputStream(input).readFully(it) }
        }
    }

    suspend fun write(entity: String, bytes: ByteArray) {
        if (bytes.size !in 1..maxPreviewBytes) return
        onDisk {
            Files.createDirectories(directory.toPath())
            val temporary = Files.createTempFile(directory.toPath(), "preview-", ".part")
            try {
                Files.write(temporary, bytes)
                Files.move(temporary, path(entity).toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                Files.deleteIfExists(temporary)
            }
            prune()
        }
    }

    private fun path(entity: String) = File(directory, "${previewKey(account, entity)}.preview")

    private fun prune() {
        val now = System.currentTimeMillis()
        var bytes = 0L
        var entries = 0
        directory.listFiles().orEmpty().sortedByDescending(File::lastModified).forEach { file ->
            val size = file.length()
            when (file.extension) {
                "part" -> file.delete()
                "preview" -> if (!file.isRecent(now) || entries >= maxPreviewFiles || bytes + size > byteLimit) {
                    file.delete()
                } else {
                    bytes += size
                    entries++
                }
            }
        }
    }

    private fun File.isRecent(now: Long) =
        now - lastModified() in 0..maxPreviewAgeMillis && length() in 1..maxPreviewBytes.toLong()

    private suspend fun <T> onDisk(work: () -> T): T? = withContext(Dispatchers.IO) {
        previewFileLock.withLock {
            try {
                work()
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            }
        }
    }
}

private const val maxPreviewAgeMillis = 72 * 60 * 60 * 1_000L
private const val maxPreviewBytes = 512 * 1024
private const val maxPreviewFiles = 32
private val previewFileLock = Mutex()

internal fun previewKey(first: String, second: String): String =
    MessageDigest.getInstance("SHA-256").digest("$first\u0000$second".toByteArray())
        .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
