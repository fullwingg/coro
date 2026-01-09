package gg.fullwin.coro

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

interface ManagedScope : CoroutineScope {
    fun cancel(cause: String? = null)
    val isActive: Boolean
}

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

fun ManagedScope.go(block: suspend CoroutineScope.() -> Unit): Job {
    return launch(block = block)
}

fun <T> ManagedScope.goAsync(block: suspend CoroutineScope.() -> T): Deferred<T> {
    return async(block = block)
}

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

suspend fun <T> ManagedScope.goLoad(block: suspend CoroutineScope.() -> T): Async<T> {
    return load { block() }
}
