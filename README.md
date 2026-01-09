# Coro

Kotlin coroutine utilities for cleaner async code.

```kotlin
repositories {
    maven("https://maven.fullwin.gg/releases")
}

dependencies {
    implementation("gg.fullwin:coro:1.0.0")
}
```

## Outcome

Railway-oriented error handling without try/catch.

```kotlin
// Wrap risky operations
val result = safe { api.fetchUser(id) }

// Chain transformations
val name = safe { api.fetchUser(id) }
    .map { it.name.uppercase() }
    .flatMap { safe { validate(it) } }
    .recover { "Anonymous" }
    .onSuccess { log("Got: $it") }
    .onError { log("Failed: ${it.message}") }

// Extract values
result.getOrNull()           // T?
result.getOrElse { default } // T
result.getOrThrow()          // T or throws

// Suspending version
val data = safeSuspend { fetchFromApi() }

// Fallback to alternative
val user = safe { primaryApi.fetch() }
    .orElse { safe { backupApi.fetch() } }
```

## Async

State container for async operations (useful for UI state).

```kotlin
sealed class Async<out T> {
    object Uninitialized
    object Loading
    data class Success<T>(val value: T)
    data class Failure(val error: Throwable)
}

// Load data
val state: Async<User> = load { repository.getUser(id) }

// Handle states
state.fold(
    onUninitialized = { showEmpty() },
    onLoading = { showSpinner() },
    onSuccess = { showUser(it) },
    onFailure = { showError(it) }
)

// Or use callbacks
state
    .onLoading { showSpinner() }
    .onSuccess { showUser(it) }
    .onFailure { showError(it) }

// Transform and extract
state.map { it.name }
state.getOrNull()
state.getOrElse { defaultUser }
```

## Scope

Managed coroutine scope with error handling.

```kotlin
// Create a scope
val scope = coroScope(
    dispatcher = Dispatchers.IO,
    onError = { e -> logger.error("Uncaught", e) }
)

// Launch coroutines
scope.go { doWork() }                          // Job
scope.goAsync { computeValue() }               // Deferred<T>
scope.goSafe(onError = { log(it) }) { risky() } // Job, catches exceptions
scope.goLoad { fetchData() }                   // Async<T>

// Cleanup
scope.cancel()
scope.cancel("Shutting down")
```

## Retry

Retry failed operations with backoff.

```kotlin
// Basic retry
val result = retry(times = 3, delay = 1.seconds) {
    unreliableApi.fetch()
}

// Exponential backoff
val result = retry(
    times = 5,
    delay = 100.milliseconds,
    exponential = true
) {
    api.fetch()
}

// Retry only specific exceptions
val result = retry(
    times = 3,
    delay = 1.seconds,
    predicate = { it is IOException }
) {
    api.fetch()
}

// Throw on failure instead of returning Outcome
val value = retryOrThrow(times = 3, delay = 1.seconds) {
    api.fetch()
}

// DSL builder
val result = retry<User> {
    times = 3
    delay = 500.milliseconds
    exponential = true
    retryIf { it is NetworkException }
    attempt { api.fetchUser() }
}
```

## Timeout

Timeout operations with various return types.

```kotlin
// Returns Outcome
val result = withTimeout(5.seconds) { slowOperation() }

// Returns nullable
val value = withTimeoutOrNull(5.seconds) { slowOperation() }

// Returns default on timeout
val value = withTimeoutOrElse(5.seconds, default = { fallback }) {
    slowOperation()
}

// Throws TimeoutException
val value = withTimeoutOrThrow(5.seconds, message = "Too slow") {
    slowOperation()
}
```

## Debounce & Throttle

Rate-limit function calls.

```kotlin
val scope = coroScope()

// Debounce: collapses rapid calls, executes after quiet period
val saveSearch = scope.debounce<String>(300.milliseconds) { query ->
    api.search(query)
}
saveSearch("h")
saveSearch("he")
saveSearch("hello") // Only this one executes (after 300ms)

// Throttle: executes immediately, ignores calls within interval
val trackClick = scope.throttle<String>(1.seconds) { button ->
    analytics.track(button)
}
trackClick("buy") // Executes
trackClick("buy") // Ignored (within 1s)
trackClick("buy") // Ignored

// ThrottleLatest: like throttle, but queues the last call
val updatePosition = scope.throttleLatest<Position>(100.milliseconds) { pos ->
    sendToServer(pos)
}
updatePosition(pos1) // Executes immediately
updatePosition(pos2) // Queued
updatePosition(pos3) // Replaces pos2 in queue
// After 100ms, pos3 executes
```

## Flow Extensions

Safe collection and error handling for flows.

```kotlin
// Safe mapping (wraps in Outcome)
flowOf(1, 2, 3)
    .mapSafe { riskyTransform(it) }  // Flow<Outcome<T>>
    .filterSuccessful()               // Flow<T> (only successes)
    .collect { println(it) }

// Safe collection with error handling
eventFlow()
    .collectSafe { event ->
        process(event) // exceptions caught per-item
    }
    .onError { e -> log("Processing failed", e) }
    .onComplete { log("Done") }
    .launchIn(scope)

// Error recovery
flow { emit(fetchData()) }
    .onErrorReturn(defaultValue)     // emit fallback on error
    .onErrorResume(backupFlow)       // switch to fallback flow

// Retry
flow { emit(api.fetch()) }
    .retryWithDelay(times = 3, delay = 1.seconds)
    .retryExponential(times = 3, initialDelay = 100.milliseconds)
    .collect { }

// Throttle emissions
sensorFlow()
    .throttle(100.milliseconds)  // max 10 emissions per second
    .collect { }
```
