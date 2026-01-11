# Coro

Kotlin coroutine utilities designed for Minecraft plugins, Discord bots, and game servers.

```kotlin
repositories {
    maven("https://maven.fullwin.gg/releases")
}

dependencies {
    implementation("gg.fullwin:coro:1.0.1")
}
```

## Quick Guide

| Feature | Use When |
|---------|----------|
| **Outcome** | You want to chain operations that might fail without try/catch |
| **Async** | You need loading states for UI/commands (Uninitialized → Loading → Success/Failure) |
| **sync/async** | You need to switch between main thread and background (Minecraft world ops) |
| **Scope** | You want automatic error handling and easy cleanup for coroutines |
| **retry** | Calling unreliable APIs (network, external services) |
| **debounce** | Handling rapid user input (search-as-you-type) |
| **throttle** | Rate-limiting frequent events (position updates, scroll) |

## Outcome - Railway Error Handling

**Problem:** Try/catch blocks break the flow. Nested error handling gets messy.

**Solution:** Errors become values you can transform and chain.

```kotlin
// The key insight: map/flatMap automatically catch exceptions
val username = safe { api.fetchUser(id) }
    .map { it.name.uppercase() }      // if this throws, becomes Failure
    .recover { "Anonymous" }           // only runs on Failure
    .getOrElse { "Guest" }

// Primary/backup pattern
val data = safe { primaryApi.fetch() }
    .orElse { safe { backupApi.fetch() } }  // tries backup if primary fails
```

**Outcome vs Async:** Use Outcome when you only care about Success/Failure. Use Async when you need Loading states for UI feedback.

## Async - Loading State Container

**Problem:** Need to show loading spinners, handle uninitialized state, and distinguish between "not loaded yet" and "loaded but failed".

**Solution:** Four states that match your UI needs.

```kotlin
val userState = load { database.fetchUser(uuid) }

// Pattern match all states
userState.fold(
    onUninitialized = { sender.msg("Use /load to fetch data") },
    onLoading = { sender.msg("Loading...") },
    onSuccess = { sender.msg("Player: ${it.name}") },
    onFailure = { sender.msg("Error: ${it.message}") }
)
```

## Context Switching - Main Thread Safety

**Problem:** Minecraft/game servers require certain operations on the main thread. Blocking the main thread freezes the server.

**Solution:** Easy switching between sync (main thread) and async (background).

```kotlin
// One-time setup for your plugin
CoroDispatchers.configure(
    sync = MinecraftDispatcher,  // your main thread dispatcher
    async = Dispatchers.IO
)

// Two ways to use sync/async:

// 1. Fire-and-forget launchers (call from anywhere, even non-suspend functions)
@EventHandler
fun onJoin(event: PlayerJoinEvent) {
    scope.async {  // Launches on background thread
        val data = database.fetchPlayer(event.player.uuid)
        scope.sync {  // Switches back to main thread
            applyData(event.player, data)
        }
    }
}

// 2. Context switchers (inside suspend functions)
suspend fun teleportPlayer() {
    val location = async { database.getLastLocation(uuid) }  // suspend & switch
    sync { player.teleport(location) }  // suspend & switch back
}
```

**Why not use `withContext` directly?** You could, but `sync/async` gives you:
- Centralized dispatcher config (one place to change for your whole plugin)
- Shorter, clearer code
- Easier to grep for main thread operations

**Quick Reference - Context Switching:**
```kotlin
// Launchers (call from anywhere):
scope.async { }   // Launch on background (I/O)
scope.sync { }    // Launch on main thread
scope.go { }      // Launch on Default (CPU work)

// Context switchers (inside suspend functions):
async { }         // Switch to background (I/O)
sync { }          // Switch to main thread
default { }       // Switch to Default (CPU work)
```

## Scope - SupervisorJob Built-in

**Problem:** By default, one coroutine failure cancels all siblings. You want isolated failures.

**Solution:** `coroScope` uses SupervisorJob internally.

```kotlin
val scope = coroScope(onError = { logger.error("Failed", it) })

scope.go { taskA() }  // If this crashes...
scope.go { taskB() }  // ...this keeps running
scope.go { taskC() }  // ...and so does this

// Clean shutdown
onDisable { scope.cancel() }
```

## Retry - Smart Backoff

**When:** Calling external APIs, databases, or any service that might be temporarily unavailable.

```kotlin
// Exponential backoff for network calls
retry(times = 5, delay = 100.milliseconds, exponential = true) {
    mojangApi.fetchProfile(uuid)
}

// Only retry specific errors (don't retry validation errors!)
retry(predicate = { it is IOException || it is SocketTimeoutException }) {
    externalApi.fetch()
}
```

## Timeout

```kotlin
// Returns Outcome (composable with other Outcome operations)
withTimeout(5.seconds) { longQuery() }
    .recover { cachedData }

// Quick timeout with fallback
val data = withTimeoutOrElse(2.seconds, default = { emptyList() }) {
    fetchRecentPlayers()
}
```

## Debounce & Throttle - Rate Limiting

**Understanding the difference:**

| Function | Behavior | Use Case |
|----------|----------|----------|
| **debounce** | Waits for silence, cancels previous calls | Search-as-you-type (only search after user stops typing) |
| **throttle** | Executes first call, drops rest | Rate-limiting analytics events |
| **throttleLatest** | Executes first, queues latest | Position sync (immediate feedback + final state) |

```kotlin
val scope = coroScope()

// Debounce: search after user stops typing
val search = scope.debounce<String>(300.milliseconds) { query ->
    api.search(query)
}
search("h")     // Canceled
search("he")    // Canceled
search("hello") // Executes after 300ms of silence

// ThrottleLatest: position updates (want both immediate + final)
val syncPos = scope.throttleLatest<Location>(100.milliseconds) { loc ->
    database.updatePosition(player, loc)
}
// Rapid movement: saves immediately, then saves final position after movement stops
```

## Flow Extensions

**Key insight:** `collectSafe` catches exceptions in the collector itself (not just upstream), so one failing item doesn't cancel the whole flow.

```kotlin
// Process events where each might fail
eventBus.events
    .collectSafe { event ->
        handleEvent(event)  // if this throws, logs error and continues
    }
    .onError { logger.warn("Event failed", it) }
    .launchIn(scope)

// Transform with error handling per-item
playerJoinFlow
    .mapSafe { fetchPlayerData(it.uuid) }  // some fetches might fail
    .filterSuccessful()                     // skip failures, continue
    .collect { data -> applyData(data) }

// Debounce for search
chatInput
    .debounce(300.milliseconds)
    .collect { query -> performSearch(query) }
```

## Common Patterns

**Plugin initialization:**
```kotlin
class MyPlugin : JavaPlugin() {
    private val scope = coroScope(onError = { logger.error("Coroutine failed", it) })

    override fun onEnable() {
        CoroDispatchers.configure(
            sync = MinecraftDispatcher(this),
            async = Dispatchers.IO
        )
    }

    override fun onDisable() {
        scope.cancel("Plugin disabling")
    }
}
```

**Command with loading feedback:**
```kotlin
command("whois") { args ->
    val state = load { mojangApi.fetchProfile(args[0]) }

    state.fold(
        onUninitialized = { },  // shouldn't happen
        onLoading = { sender.msg("Looking up player...") },
        onSuccess = { sender.msg("Name: ${it.name}, UUID: ${it.uuid}") },
        onFailure = { sender.msg("Player not found") }
    )
}
```

**Background task with main thread updates:**
```kotlin
// From a regular function (fire-and-forget launcher):
fun refreshLeaderboard() {
    scope.async {
        val results = database.queryTopPlayers(10)
        scope.sync {
            scoreboard.update(results)  // back to main thread
        }
    }
}

// From a suspend function (context switchers):
suspend fun teleportPlayer() {
    val location = async { database.getLastLocation(uuid) }  // I/O work
    sync { player.teleport(location) }  // back to main thread
}

// CPU-intensive work:
suspend fun generateTerrain(region: Region) {
    val blocks = default {
        complexTerrainAlgorithm(region)  // Heavy calculation
    }
    sync { region.setBlocks(blocks) }  // Apply to world on main thread
}

// In an event handler:
@EventHandler
fun onPlayerMove(event: PlayerMoveEvent) {
    scope.async {
        val region = database.getRegion(event.to)
        scope.sync {
            if (!region.canEnter(event.player)) {
                event.isCancelled = true
            }
        }
    }
}
```
