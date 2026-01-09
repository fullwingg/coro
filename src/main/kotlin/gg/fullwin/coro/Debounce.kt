package gg.fullwin.coro

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

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
