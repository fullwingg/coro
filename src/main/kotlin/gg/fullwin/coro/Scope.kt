package gg.fullwin.coro

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * Coroutine scope with lifecycle management. Use [coroScope] to create.
 *
 * ```kotlin
 * val scope = coroScope(onError = { logger.error("Uncaught", it) })
 * scope.go { doWork() }
 * scope.cancel()  // Clean shutdown
 * ```
 */
interface ManagedScope : CoroutineScope {
    fun cancel(cause: String? = null)
    val isActive: Boolean
}

/**
 * Creates a scope with automatic error handling.
 *
 * Uses SupervisorJob internally, so one failing coroutine won't cancel others in the same scope.
 * This is different from regular CoroutineScope where any child failure cancels all siblings.
 *
 * ```kotlin
 * val scope = coroScope(onError = { logger.error("Failed", it) })
 * scope.go { taskA() }  // If this fails...
 * scope.go { taskB() }  // ...this keeps running
 * ```
 */
fun coroScope(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    onError: (Throwable) -> Unit = { it.printStackTrace() }
): ManagedScope {
    val handler = CoroutineExceptionHandler { _, throwable ->
        onError(throwable)
    }
    val job = SupervisorJob()
    return ManagedScopeImpl(job + dispatcher + handler, job)
}

private class ManagedScopeImpl(
    override val coroutineContext: CoroutineContext,
    private val job: Job
) : ManagedScope {
    override fun cancel(cause: String?) {
        job.cancel(cause?.let { CancellationException(it) })
    }

    override val isActive: Boolean
        get() = job.isActive
}

/** Shorter alias for `launch`. Exceptions go to the scope's error handler. */
fun ManagedScope.go(block: suspend CoroutineScope.() -> Unit): Job {
    return launch(block = block)
}

/** Shorter alias for `async`. Returns a Deferred you can await. */
fun <T> ManagedScope.goAsync(block: suspend CoroutineScope.() -> T): Deferred<T> {
    return async(block = block)
}

/**
 * Like [go], but catches exceptions and passes them to [onError] instead of the scope handler.
 *
 * Use this when you want different error handling for a specific operation.
 * CancellationException is re-thrown (not caught) to allow proper cancellation.
 */
fun ManagedScope.goSafe(
    onError: (Throwable) -> Unit = {},
    block: suspend CoroutineScope.() -> Unit
): Job {
    return launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            onError(e)
        }
    }
}

/** Convenience for `load { block() }`. Returns Async<T> with Success or Failure. */
suspend fun <T> ManagedScope.goLoad(block: suspend CoroutineScope.() -> T): Async<T> {
    return load { block() }
}

/**
 * Launches a coroutine on the async dispatcher (fire-and-forget).
 *
 * Use this when you want to start background work without waiting for it.
 * This is the "call anywhere" version - you don't need to be in a suspend context.
 *
 * ```kotlin
 * // In an event handler or regular function:
 * scope.async {
 *     val data = database.query()
 *     processData(data)
 * }
 * ```
 */
fun ManagedScope.async(block: suspend CoroutineScope.() -> Unit): Job {
    return launch(CoroDispatchers.async, block = block)
}

/**
 * Launches a coroutine on the sync dispatcher (fire-and-forget).
 *
 * Use this when you need to run something on the main thread from a regular function.
 * This is the "call anywhere" version - you don't need to be in a suspend context.
 *
 * ```kotlin
 * // In an event handler or regular function:
 * scope.sync {
 *     player.teleport(location)
 *     world.setBlock(pos, block)
 * }
 * ```
 */
fun ManagedScope.sync(block: suspend CoroutineScope.() -> Unit): Job {
    return launch(CoroDispatchers.sync, block = block)
}
