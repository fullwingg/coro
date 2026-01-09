package gg.fullwin.coro

sealed class Async<out T> {
    data object Uninitialized : Async<Nothing>()
    data object Loading : Async<Nothing>()
    data class Success<T>(val value: T) : Async<T>()
    data class Failure(val error: Throwable) : Async<Nothing>()

    val isUninitialized: Boolean get() = this is Uninitialized
    val isLoading: Boolean get() = this is Loading
    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure
    val isComplete: Boolean get() = isSuccess || isFailure

    fun getOrNull(): T? = when (this) {
        is Success -> value
        else -> null
    }

    inline fun getOrElse(default: () -> @UnsafeVariance T): T = when (this) {
        is Success -> value
        else -> default()
    }

    fun errorOrNull(): Throwable? = when (this) {
        is Failure -> error
        else -> null
    }

    inline fun <R> map(transform: (T) -> R): Async<R> = when (this) {
        is Uninitialized -> Uninitialized
        is Loading -> Loading
        is Success -> Success(transform(value))
        is Failure -> Failure(error)
    }

    inline fun onSuccess(action: (T) -> Unit): Async<T> {
        if (this is Success) action(value)
        return this
    }

    inline fun onFailure(action: (Throwable) -> Unit): Async<T> {
        if (this is Failure) action(error)
        return this
    }

    inline fun onLoading(action: () -> Unit): Async<T> {
        if (this is Loading) action()
        return this
    }

    inline fun <R> fold(
        onUninitialized: () -> R,
        onLoading: () -> R,
        onSuccess: (T) -> R,
        onFailure: (Throwable) -> R
    ): R = when (this) {
        is Uninitialized -> onUninitialized()
        is Loading -> onLoading()
        is Success -> onSuccess(value)
        is Failure -> onFailure(error)
    }

    companion object {
        fun <T> success(value: T): Async<T> = Success(value)
        fun failure(error: Throwable): Async<Nothing> = Failure(error)
        fun loading(): Async<Nothing> = Loading
        fun uninitialized(): Async<Nothing> = Uninitialized
    }
}

suspend fun <T> load(block: suspend () -> T): Async<T> {
    return try {
        Async.Success(block())
    } catch (e: Throwable) {
        Async.Failure(e)
    }
}

fun <T> Outcome<T>.toAsync(): Async<T> = when (this) {
    is Outcome.Success -> Async.Success(value)
    is Outcome.Failure -> Async.Failure(error)
}

fun <T> Async<T>.toOutcome(): Outcome<T>? = when (this) {
    is Async.Success -> Outcome.Success(value)
    is Async.Failure -> Outcome.Failure(error)
    else -> null
}
