package gg.fullwin.coro

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Railway-oriented error handling - represents operations that can succeed or fail.
 *
 * Unlike try/catch, failures become values you can transform and chain.
 * All transformation methods automatically catch exceptions.
 *
 * ```kotlin
 * safe { api.fetchUser(id) }
 *     .map { it.name.uppercase() }  // if this throws, becomes Failure
 *     .recover { "Anonymous" }      // handle errors in the chain
 * ```
 */
sealed class Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>()
    data class Failure(val error: Throwable) : Outcome<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    /**
     * Transforms success values. Exceptions in [transform] automatically become Failure.
     * This is the key difference from standard map - no need to wrap in try/catch.
     */
    inline fun <R> map(transform: (T) -> R): Outcome<R> = when (this) {
        is Success -> safe { transform(value) }
        is Failure -> this
    }

    /**
     * Like [map], but for transformations that themselves return Outcome.
     * Use this to chain operations that can fail: `outcome.flatMap { validateUser(it) }`
     */
    inline fun <R> flatMap(transform: (T) -> Outcome<R>): Outcome<R> = when (this) {
        is Success -> try {
            transform(value)
        } catch (e: Throwable) {
            Failure(e)
        }
        is Failure -> this
    }

    /**
     * Converts failures back to success by providing a fallback value.
     * The recovery function can also throw, which will be wrapped in Failure.
     */
    inline fun recover(transform: (Throwable) -> @UnsafeVariance T): Outcome<T> = when (this) {
        is Success -> this
        is Failure -> safe { transform(error) }
    }

    inline fun onSuccess(action: (T) -> Unit): Outcome<T> {
        if (this is Success) action(value)
        return this
    }

    inline fun onError(action: (Throwable) -> Unit): Outcome<T> {
        if (this is Failure) action(error)
        return this
    }

    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }

    inline fun getOrElse(default: () -> @UnsafeVariance T): T = when (this) {
        is Success -> value
        is Failure -> default()
    }

    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw error
    }

    companion object {
        fun <T> success(value: T): Outcome<T> = Success(value)
        fun failure(error: Throwable): Outcome<Nothing> = Failure(error)
    }
}

/**
 * Entry point for railway-oriented error handling. Wraps any code that might throw.
 *
 * ```kotlin
 * safe { api.fetchUser(id) }      // Returns Outcome instead of throwing
 *     .map { it.name }
 *     .getOrElse { "Unknown" }
 * ```
 */
@OptIn(ExperimentalContracts::class)
inline fun <T> safe(block: () -> T): Outcome<T> {
    contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
    return try {
        Outcome.Success(block())
    } catch (e: Throwable) {
        Outcome.Failure(e)
    }
}

/** Suspending version of [safe]. */
@OptIn(ExperimentalContracts::class)
suspend inline fun <T> safeSuspend(block: suspend () -> T): Outcome<T> {
    contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
    return try {
        Outcome.Success(block())
    } catch (e: Throwable) {
        Outcome.Failure(e)
    }
}

/**
 * Tries a fallback operation if this one failed. Useful for primary/backup service patterns.
 *
 * ```kotlin
 * safe { primaryApi.fetch() }
 *     .orElse { safe { backupApi.fetch() } }
 * ```
 */
inline fun <T> Outcome<T>.orElse(alternative: () -> Outcome<T>): Outcome<T> = when (this) {
    is Outcome.Success -> this
    is Outcome.Failure -> alternative()
}

fun <T> Outcome<T>.toResult(): Result<T> = when (this) {
    is Outcome.Success -> Result.success(value)
    is Outcome.Failure -> Result.failure(error)
}

fun <T> Result<T>.toOutcome(): Outcome<T> = fold(
    onSuccess = { Outcome.Success(it) },
    onFailure = { Outcome.Failure(it) }
)
