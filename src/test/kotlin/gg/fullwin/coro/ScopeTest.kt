package gg.fullwin.coro

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicReference

class ScopeTest : FunSpec({

    test("coroScope creates an active scope") {
        val scope = coroScope()
        scope.isActive.shouldBeTrue()
        scope.cancel()
        scope.isActive.shouldBeFalse()
    }

    test("coroScope with custom dispatcher") {
        val scope = coroScope(dispatcher = Dispatchers.IO)
        scope.isActive.shouldBeTrue()
        scope.cancel()
    }

    test("coroScope error handler is called on uncaught exception") {
        runTest {
            val capturedError = AtomicReference<Throwable?>(null)
            val scope = coroScope(
                dispatcher = Dispatchers.Default,
                onError = { capturedError.set(it) }
            )

            scope.go {
                throw IllegalStateException("test error")
            }

            // Wait for error to propagate (real time since using Default dispatcher)
            Thread.sleep(200)
            scope.cancel()

            capturedError.get().shouldBeInstanceOf<IllegalStateException>()
        }
    }

    test("go launches coroutine") {
        runTest {
            val scope = coroScope()
            var executed = false

            val job = scope.go {
                executed = true
            }

            job.join()
            executed.shouldBeTrue()
            scope.cancel()
        }
    }

    test("goAsync returns deferred value") {
        runTest {
            val scope = coroScope()

            val deferred = scope.goAsync {
                42
            }

            deferred.await() shouldBe 42
            scope.cancel()
        }
    }

    test("goSafe catches exceptions") {
        runTest {
            val scope = coroScope()
            var capturedError: Throwable? = null

            val job = scope.goSafe(
                onError = { capturedError = it }
            ) {
                throw RuntimeException("caught")
            }

            job.join()
            capturedError.shouldBeInstanceOf<RuntimeException>()
            scope.cancel()
        }
    }

    test("goSafe does not catch CancellationException") {
        runTest {
            val scope = coroScope()
            var errorCaught = false

            val job = scope.goSafe(
                onError = { errorCaught = true }
            ) {
                delay(10000)
            }

            delay(50)
            job.cancel()
            job.join()

            errorCaught.shouldBeFalse()
            scope.cancel()
        }
    }

    test("goLoad returns Async state") {
        runTest {
            val scope = coroScope()

            val result = scope.goLoad { 100 }
            result.shouldBeInstanceOf<Async.Success<Int>>()
            result.getOrNull() shouldBe 100

            val failResult = scope.goLoad<Int> { throw RuntimeException() }
            failResult.shouldBeInstanceOf<Async.Failure>()

            scope.cancel()
        }
    }

    test("cancel with reason") {
        val scope = coroScope()
        scope.isActive.shouldBeTrue()
        scope.cancel("shutting down")
        scope.isActive.shouldBeFalse()
    }

    test("scope cancellation cancels child jobs") {
        runTest {
            val scope = coroScope()
            var completed = false

            scope.go {
                delay(10000)
                completed = true
            }

            delay(50)
            scope.cancel()

            completed.shouldBeFalse()
        }
    }
})
