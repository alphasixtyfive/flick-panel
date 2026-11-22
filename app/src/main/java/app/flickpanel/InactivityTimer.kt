package app.flickpanel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class InactivityTimer(
    private val scope: CoroutineScope,
    private val elapsedMillis: () -> Long,
    private val onTimeout: () -> Unit,
) {
    private var deadline = 0L
    private var task: Job? = null

    fun restart(timeoutMillis: Long) {
        deadline = elapsedMillis() + timeoutMillis
        if (task?.isActive == true) return
        task = scope.launch {
            while (true) {
                val remaining = deadline - elapsedMillis()
                if (remaining <= 0) break
                delay(remaining)
            }
            task = null
            onTimeout()
        }
    }

    fun cancel() {
        task?.cancel()
        task = null
    }
}
