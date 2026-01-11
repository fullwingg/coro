package gg.fullwin.coro

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Debounce: waits for quiet period before executing. Cancels previous calls.
 *
 * Use for search-as-you-type where you only want the final value after user stops typing.
 *
 * ```kotlin
 * val search = scope.debounce<String>(300.milliseconds) { query ->
 *     api.search(query)
 * }
 * search("h")     // Canceled by next call
 * search("he")    // Canceled by next call
 * search("hello") // Executes after 300ms of silence
 * ```
 */
fun <T> CoroutineScope.debounce(
    wait: Duration,
    block: suspend (T) -> Unit
): (T) -> Unit {
    var job: Job? = null
    return { value: T ->
        job?.cancel()
        job = launch {
            delay(wait)
            block(value)
        }
    }
}

fun CoroutineScope.debounce(
    wait: Duration,
    block: suspend () -> Unit
): () -> Unit {
    var job: Job? = null
    return {
        job?.cancel()
        job = launch {
            delay(wait)
            block()
        }
    }
}

/**
 * Throttle: executes immediately, then ignores calls for [interval].
 *
 * Use for rate-limiting frequent events like scroll/resize where you want periodic samples.
 *
 * ```kotlin
 * val track = scope.throttle<String>(1.seconds) { button ->
 *     analytics.track(button)
 * }
 * track("buy") // Executes immediately
 * track("buy") // Ignored
 * track("buy") // Ignored
 * // After 1s, next call will execute
 * ```
 */
fun <T> CoroutineScope.throttle(
    interval: Duration,
    timeSource: TimeSource = TimeSource.Monotonic,
    block: suspend (T) -> Unit
): (T) -> Unit {
    val mutex = Mutex()
    var lastExecution: TimeMark? = null

    return { value: T ->
        launch {
            mutex.withLock {
                val last = lastExecution
                val now = timeSource.markNow()

                if (last == null || last.elapsedNow() >= interval) {
                    lastExecution = now
                    block(value)
                }
            }
        }
    }
}

fun CoroutineScope.throttle(
    interval: Duration,
    timeSource: TimeSource = TimeSource.Monotonic,
    block: suspend () -> Unit
): () -> Unit {
    val mutex = Mutex()
    var lastExecution: TimeMark? = null

    return {
        launch {
            mutex.withLock {
                val last = lastExecution
                val now = timeSource.markNow()

                if (last == null || last.elapsedNow() >= interval) {
                    lastExecution = now
                    block()
                }
            }
        }
    }
}

/**
 * ThrottleLatest: like throttle, but queues the latest value to execute after interval.
 *
 * Key difference from throttle: throttle drops intermediate values, this guarantees
 * the most recent value eventually executes. Use for position updates where you want
 * immediate feedback + final state.
 *
 * ```kotlin
 * val update = scope.throttleLatest<Position>(100.milliseconds) { pos ->
 *     sendToServer(pos)
 * }
 * update(pos1) // Executes immediately
 * update(pos2) // Queued
 * update(pos3) // Replaces pos2
 * // After 100ms, pos3 executes
 * ```
 */
fun <T> CoroutineScope.throttleLatest(
    interval: Duration,
    timeSource: TimeSource = TimeSource.Monotonic,
    block: suspend (T) -> Unit
): (T) -> Unit {
    val mutex = Mutex()
    var lastExecution: TimeMark? = null
    var pendingValue: T?
    var pendingJob: Job? = null

    return { value: T ->
        launch {
            mutex.withLock {
                val last = lastExecution
                val now = timeSource.markNow()

                if (last == null || last.elapsedNow() >= interval) {
                    lastExecution = now
                    pendingJob?.cancel()
                    pendingValue = null
                    block(value)
                } else {
                    pendingValue = value
                    if (pendingJob?.isActive != true) {
                        val remaining = interval - last.elapsedNow()
                        pendingJob = launch {
                            delay(remaining)
                            mutex.withLock {
                                @Suppress("UNCHECKED_CAST")
                                val v = pendingValue ?: return@withLock
                                pendingValue = null
                                lastExecution = timeSource.markNow()
                                block(v)
                            }
                        }
                    }
                }
            }
        }
    }
}
