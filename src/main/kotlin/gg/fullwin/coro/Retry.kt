package gg.fullwin.coro

import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

suspend fun <T> retry(
    times: Int = 3,
    delay: Duration = 1.seconds,
    exponential: Boolean = false,
    predicate: (Throwable) -> Boolean = { true },
    block: suspend () -> T
): Outcome<T> {
    var lastError: Throwable? = null
    var currentDelay = delay

    repeat(times) { attempt ->
        try {
            return Outcome.Success(block())
        } catch (e: Throwable) {
            if (!predicate(e)) {
                return Outcome.Failure(e)
            }
            lastError = e
            if (attempt < times - 1) {
                delay(currentDelay)
                if (exponential) {
                    currentDelay *= 2
                }
            }
        }
    }

    return Outcome.Failure(lastError ?: IllegalStateException("Retry failed with no error"))
}

suspend fun <T> retryOrThrow(
    times: Int = 3,
    delay: Duration = 1.seconds,
    exponential: Boolean = false,
    predicate: (Throwable) -> Boolean = { true },
    block: suspend () -> T
): T = retry(times, delay, exponential, predicate, block).getOrThrow()

class RetryBuilder<T> {
    var times: Int = 3
    var delay: Duration = 1.seconds
    var exponential: Boolean = false
    var predicate: (Throwable) -> Boolean = { true }
    private var block: (suspend () -> T)? = null

    fun attempt(block: suspend () -> T) {
        this.block = block
    }

    fun retryIf(predicate: (Throwable) -> Boolean) {
        this.predicate = predicate
    }

    suspend fun execute(): Outcome<T> {
        val b = block ?: throw IllegalStateException("No block provided")
        return retry(times, delay, exponential, predicate, b)
    }
}

suspend fun <T> retry(builder: RetryBuilder<T>.() -> Unit): Outcome<T> {
    return RetryBuilder<T>().apply(builder).execute()
}
