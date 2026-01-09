package gg.fullwin.coro

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout as ktxWithTimeout
import kotlin.time.Duration

suspend fun <T> withTimeout(duration: Duration, block: suspend () -> T): Outcome<T> {
    return try {
        Outcome.Success(ktxWithTimeout(duration) { block() })
    } catch (e: TimeoutCancellationException) {
        Outcome.Failure(e)
    } catch (e: Throwable) {
        Outcome.Failure(e)
    }
}

suspend fun <T> withTimeoutOrNull(duration: Duration, block: suspend () -> T): T? {
    return kotlinx.coroutines.withTimeoutOrNull(duration) { block() }
}

suspend fun <T> withTimeoutOrElse(
    duration: Duration,
    default: () -> T,
    block: suspend () -> T
): T {
    return withTimeout(duration, block).getOrElse(default)
}

class TimeoutException(message: String, cause: Throwable? = null) : Exception(message, cause)

suspend fun <T> withTimeoutOrThrow(
    duration: Duration,
    message: String = "Operation timed out after $duration",
    block: suspend () -> T
): T {
    return try {
        ktxWithTimeout(duration) { block() }
    } catch (e: TimeoutCancellationException) {
        throw TimeoutException(message, e)
    }
}
