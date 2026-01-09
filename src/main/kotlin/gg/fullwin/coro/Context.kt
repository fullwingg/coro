package gg.fullwin.coro

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CoroDispatchers {
    @Volatile
    var sync: CoroutineDispatcher = Dispatchers.Default
        private set

    @Volatile
    var async: CoroutineDispatcher = Dispatchers.IO
        private set

    fun configure(
        sync: CoroutineDispatcher = this.sync,
        async: CoroutineDispatcher = this.async
    ) {
        this.sync = sync
        this.async = async
    }
}

suspend fun <T> sync(block: suspend () -> T): T {
    return withContext(CoroDispatchers.sync) { block() }
}

suspend fun <T> async(block: suspend () -> T): T {
    return withContext(CoroDispatchers.async) { block() }
}

suspend fun <T> default(block: suspend () -> T): T {
    return withContext(Dispatchers.Default) { block() }
}

suspend fun <T> io(block: suspend () -> T): T {
    return withContext(Dispatchers.IO) { block() }
}
