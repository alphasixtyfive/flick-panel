package app.flickpanel.camera

import kotlinx.coroutines.delay

internal class IntercomConnectionProblem(message: String, val retryable: Boolean = true) : Exception(message)

internal suspend fun connectIntercom(
    allowRetry: Boolean,
    onRetry: (IntercomConnectionProblem) -> Unit,
    connect: suspend () -> Unit,
) {
    try {
        connect()
    } catch (problem: IntercomConnectionProblem) {
        if (!allowRetry || !problem.retryable) throw problem
        onRetry(problem)
        delay(350)
        connect()
    }
}
