package gg.fullwin.coro

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout as ktxWithTimeout
import kotlin.time.Duration

/** Wraps kotlinx.coroutines.withTimeout to return Outcome instead of throwing. */
suspend fun <T> withTimeout(duration: Duration, block: suspend () -> T): Outcome<T> {
    return try {
        Outcome.Success(ktxWithTimeout(duration) { block() })
    } catch (e: TimeoutCancellationException) {
        Outcome.Failure(e)
    } catch (e: Throwable) {
        Outcome.Failure(e)
    }
}

/** Alias for kotlinx.coroutines.withTimeoutOrNull. */
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

/**
 * Like kotlinx.coroutines.withTimeout, but throws a custom TimeoutException instead of
 * TimeoutCancellationException. Useful when you want a clearer exception type.
 */
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
