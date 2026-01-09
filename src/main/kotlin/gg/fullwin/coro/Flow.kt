package gg.fullwin.coro

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Duration

fun <T> Flow<T>.mapSafe(transform: suspend (T) -> T): Flow<Outcome<T>> = map { value ->
    safe { transform(value) }
}

fun <T, R> Flow<T>.mapToOutcome(transform: suspend (T) -> R): Flow<Outcome<R>> = map { value ->
    safe { transform(value) }
}


class SafeCollector<T>(
    private val flow: Flow<T>,
    private val collector: suspend (T) -> Unit
) {
    private var errorHandler: (suspend (Throwable) -> Unit)? = null
    private var completionHandler: (suspend () -> Unit)? = null

    fun onError(handler: suspend (Throwable) -> Unit): SafeCollector<T> {
        this.errorHandler = handler
        return this
    }

    fun onComplete(handler: suspend () -> Unit): SafeCollector<T> {
        this.completionHandler = handler
        return this
    }

    suspend fun collect() {
        try {
            flow.collect { value ->
                try {
                    collector(value)
                } catch (e: Throwable) {
                    errorHandler?.invoke(e)
                }
            }
            completionHandler?.invoke()
        } catch (e: Throwable) {
            errorHandler?.invoke(e)
        }
    }

    fun launchIn(scope: CoroutineScope): Job {
        return scope.launchIn(this)
    }
}

fun <T> Flow<T>.collectSafe(collector: suspend (T) -> Unit): SafeCollector<T> {
    return SafeCollector(this, collector)
}

private fun <T> CoroutineScope.launchIn(collector: SafeCollector<T>): Job {
    return launch {
        collector.collect()
    }
}

fun <T> Flow<T>.onErrorReturn(value: T): Flow<T> = catch { emit(value) }

fun <T> Flow<T>.onErrorResume(fallback: Flow<T>): Flow<T> = catch { emitAll(fallback) }

fun <T> Flow<T>.retryWithDelay(
    times: Int = 3,
    delay: Duration
): Flow<T> = retryWhen { _, attempt ->
    if (attempt < times) {
        kotlinx.coroutines.delay(delay)
        true
    } else {
        false
    }
}

fun <T> Flow<T>.retryExponential(
    times: Int = 3,
    initialDelay: Duration,
    maxDelay: Duration = initialDelay * 16
): Flow<T> = retryWhen { _, attempt ->
    if (attempt < times) {
        val delayMs = (initialDelay.inWholeMilliseconds * (1L shl attempt.toInt()))
            .coerceAtMost(maxDelay.inWholeMilliseconds)
        kotlinx.coroutines.delay(delayMs)
        true
    } else {
        false
    }
}

fun <T> Flow<T>.throttle(
    duration: Duration,
    currentTime: () -> Long = { System.currentTimeMillis() }
): Flow<T> = flow {
    var lastEmission: Long? = null
    collect { value ->
        val now = currentTime()
        val last = lastEmission
        if (last == null || now - last >= duration.inWholeMilliseconds) {
            lastEmission = now
            emit(value)
        }
    }
}

@OptIn(FlowPreview::class)
fun <T> Flow<T>.debounce(duration: Duration): Flow<T> = debounce(duration.inWholeMilliseconds)

fun <T> Flow<Outcome<T>>.filterSuccessful(): Flow<T> = mapNotNull { it.getOrNull() }

fun <T> Flow<Outcome<T>>.filterFailed(): Flow<Throwable> = mapNotNull {
    (it as? Outcome.Failure)?.error
}
