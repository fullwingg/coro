package gg.fullwin.coro

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pluggable dispatchers for [sync] and [async] context switching.
 *
 * In Minecraft/game engines, set [sync] to the main thread dispatcher to safely
 * interact with the game world. In other apps, use for separating UI from background work.
 *
 * ```kotlin
 * CoroDispatchers.configure(
 *     sync = MinecraftDispatcher,  // Main thread for world modifications
 *     async = Dispatchers.IO        // Background for database/network
 * )
 * ```
 */
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

/**
 * Switches to the [CoroDispatchers.sync] dispatcher.
 *
 * Use for operations that must run on the main thread.
 *
 * ```kotlin
 * async { database.fetchPlayer(uuid) }  // Background thread
 * sync { player.teleport(location) }     // Back to main thread
 * ```
 */
suspend fun <T> sync(block: suspend () -> T): T {
    return withContext(CoroDispatchers.sync) { block() }
}

/**
 * Switches to the [CoroDispatchers.async] dispatcher.
 *
 * Use for I/O and background work that shouldn't block the main thread.
 */
suspend fun <T> async(block: suspend () -> T): T {
    return withContext(CoroDispatchers.async) { block() }
}

/**
 * Switches to [Dispatchers.Default] for CPU-intensive work.
 *
 * Use this for heavy calculations, not I/O. For I/O, use [async].
 */
suspend fun <T> default(block: suspend () -> T): T {
    return withContext(Dispatchers.Default) { block() }
}
