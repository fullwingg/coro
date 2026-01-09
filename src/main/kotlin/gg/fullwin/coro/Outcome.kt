package gg.fullwin.coro

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

sealed class Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>()
    data class Failure(val error: Throwable) : Outcome<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    inline fun <R> map(transform: (T) -> R): Outcome<R> = when (this) {
        is Success -> safe { transform(value) }
        is Failure -> this
    }

    inline fun <R> flatMap(transform: (T) -> Outcome<R>): Outcome<R> = when (this) {
        is Success -> try {
            transform(value)
        } catch (e: Throwable) {
            Failure(e)
        }
        is Failure -> this
    }

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

@OptIn(ExperimentalContracts::class)
inline fun <T> safe(block: () -> T): Outcome<T> {
    contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
    return try {
        Outcome.Success(block())
    } catch (e: Throwable) {
        Outcome.Failure(e)
    }
}

@OptIn(ExperimentalContracts::class)
suspend inline fun <T> safeSuspend(block: suspend () -> T): Outcome<T> {
    contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
    return try {
        Outcome.Success(block())
    } catch (e: Throwable) {
        Outcome.Failure(e)
    }
}

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
