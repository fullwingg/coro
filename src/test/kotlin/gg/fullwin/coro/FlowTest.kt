package gg.fullwin.coro

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

class FlowTest : FunSpec({

    test("mapSafe wraps successful transforms in Outcome.Success") {
        runTest {
            val results = flowOf(1, 2, 3)
                .mapSafe { it * 2 }
                .toList()

            results.size shouldBe 3
            results.forEach { it.shouldBeInstanceOf<Outcome.Success<Int>>() }
            results.mapNotNull { it.getOrNull() } shouldContainExactly listOf(2, 4, 6)
        }
    }

    test("mapSafe wraps failures in Outcome.Failure") {
        runTest {
            val results = flowOf(1, 2, 3)
                .mapSafe { value ->
                    if (value == 2) throw RuntimeException("fail")
                    value
                }
                .toList()

            results.size shouldBe 3
            results[0].shouldBeInstanceOf<Outcome.Success<Int>>()
            results[0].getOrNull() shouldBe 1
            results[1].shouldBeInstanceOf<Outcome.Failure>()
            results[2].shouldBeInstanceOf<Outcome.Success<Int>>()
            results[2].getOrNull() shouldBe 3
        }
    }

    test("mapToOutcome transforms with different return type") {
        runTest {
            val results = flowOf(1, 2, 3)
                .mapToOutcome { "value: $it" }
                .toList()

            results.mapNotNull { it.getOrNull() } shouldContainExactly listOf(
                "value: 1",
                "value: 2",
                "value: 3"
            )
        }
    }

    test("mapToOutcome wraps failures in Outcome.Failure") {
        runTest {
            val results = flowOf(1, 2, 3)
                .mapToOutcome<Int, String> { value ->
                    if (value == 2) throw RuntimeException("transform failed")
                    "value: $value"
                }
                .toList()

            results.size shouldBe 3
            results[0].shouldBeInstanceOf<Outcome.Success<String>>()
            results[0].getOrNull() shouldBe "value: 1"
            results[1].shouldBeInstanceOf<Outcome.Failure>()
            results[2].shouldBeInstanceOf<Outcome.Success<String>>()
            results[2].getOrNull() shouldBe "value: 3"
        }
    }

    test("collectSafe handles errors in collector") {
        runTest {
            val collected = mutableListOf<Int>()
            var capturedError: Throwable? = null

            flowOf(1, 2, 3)
                .collectSafe { value ->
                    if (value == 2) throw RuntimeException("error on 2")
                    collected.add(value)
                }
                .onError { capturedError = it }
                .collect()

            collected shouldContainExactly listOf(1, 3)
            capturedError.shouldBeInstanceOf<RuntimeException>()
        }
    }

    test("collectSafe onComplete is called") {
        runTest {
            var completed = false

            flowOf(1, 2, 3)
                .collectSafe { }
                .onComplete { completed = true }
                .collect()

            completed shouldBe true
        }
    }

    test("collectSafe launchIn returns job") {
        runTest {
            val scope = coroScope()
            val collected = mutableListOf<Int>()

            val job = flowOf(1, 2, 3)
                .collectSafe { collected.add(it) }
                .launchIn(scope)

            job.join()
            collected shouldContainExactly listOf(1, 2, 3)
            job.isCompleted shouldBe true
            scope.cancel()
        }
    }

    test("collectSafe launchIn with error handler") {
        runTest {
            val scope = coroScope()
            val collected = mutableListOf<Int>()
            var errorCaught = false

            val job = flow {
                emit(1)
                emit(2)
                throw RuntimeException("error in flow")
            }
                .collectSafe { collected.add(it) }
                .onError { errorCaught = true }
                .launchIn(scope)

            job.join()
            collected shouldContainExactly listOf(1, 2)
            errorCaught shouldBe true
            scope.cancel()
        }
    }


    test("collectSafe handles flow errors") {
        runTest {
            val collected = mutableListOf<Int>()
            var capturedError: Throwable? = null

            flow {
                emit(1)
                throw RuntimeException("flow error")
            }
                .collectSafe { collected.add(it) }
                .onError { capturedError = it }
                .collect()

            collected shouldContainExactly listOf(1)
            capturedError.shouldBeInstanceOf<RuntimeException>()
        }
    }

    test("collectSafe onComplete called even with errors") {
        runTest {
            var completed = false
            var errorCaught = false

            flow {
                emit(1)
                throw RuntimeException()
            }
                .collectSafe { }
                .onError { errorCaught = true }
                .onComplete { completed = true }
                .collect()

            errorCaught shouldBe true
            completed shouldBe true
        }
    }

    test("collectSafe without handlers") {
        runTest {
            val collected = mutableListOf<Int>()

            flowOf(1, 2, 3)
                .collectSafe { collected.add(it) }
                .collect()

            collected shouldContainExactly listOf(1, 2, 3)
        }
    }

    test("collectSafe with only onError handler") {
        runTest {
            val collected = mutableListOf<Int>()
            var errorCaught = false

            flowOf(1, 2, 3)
                .collectSafe {
                    if (it == 2) throw RuntimeException()
                    collected.add(it)
                }
                .onError { errorCaught = true }
                .collect()

            collected shouldContainExactly listOf(1, 3)
            errorCaught shouldBe true
        }
    }

    test("collectSafe with only onComplete handler") {
        runTest {
            val collected = mutableListOf<Int>()
            var completed = false

            flowOf(1, 2, 3)
                .collectSafe { collected.add(it) }
                .onComplete { completed = true }
                .collect()

            collected shouldContainExactly listOf(1, 2, 3)
            completed shouldBe true
        }
    }

    test("collectSafe without handlers when error occurs in collector") {
        runTest {
            val collected = mutableListOf<Int>()

            flowOf(1, 2, 3)
                .collectSafe {
                    if (it == 2) throw RuntimeException("error")
                    collected.add(it)
                }
                .collect()

            // Error is swallowed since no handler
            collected shouldContainExactly listOf(1, 3)
        }
    }

    test("collectSafe without handlers when error occurs in flow") {
        runTest {
            val collected = mutableListOf<Int>()

            flow {
                emit(1)
                throw RuntimeException("flow error")
            }
                .collectSafe { collected.add(it) }
                .collect()

            // Error is swallowed since no handler
            collected shouldContainExactly listOf(1)
        }
    }

    test("collectSafe onError catches flow errors but onComplete still runs") {
        runTest {
            var errorCaught = false
            var completed = false

            flow {
                emit(1)
                emit(2)
                throw RuntimeException("flow failure")
            }
                .collectSafe { }
                .onError { errorCaught = true }
                .onComplete { completed = true }
                .collect()

            errorCaught shouldBe true
            completed shouldBe true
        }
    }

    test("collectSafe without onComplete when error occurs") {
        runTest {
            var errorCaught = false

            flow {
                emit(1)
                throw RuntimeException()
            }
                .collectSafe { }
                .onError { errorCaught = true }
                .collect()

            errorCaught shouldBe true
        }
    }

    test("collectSafe without onComplete when successful") {
        runTest {
            val collected = mutableListOf<Int>()

            flowOf(1, 2, 3)
                .collectSafe { collected.add(it) }
                .onError { throw AssertionError("Should not error") }
                .collect()

            collected shouldContainExactly listOf(1, 2, 3)
        }
    }

    test("collectSafe with onComplete but no onError when successful") {
        runTest {
            val collected = mutableListOf<Int>()
            var completed = false

            flowOf(1, 2, 3)
                .collectSafe { collected.add(it) }
                .onComplete { completed = true }
                .collect()

            collected shouldContainExactly listOf(1, 2, 3)
            completed shouldBe true
        }
    }

    test("onErrorReturn emits fallback value") {
        runTest {
            val results = flow {
                emit(1)
                throw RuntimeException()
            }
                .onErrorReturn(99)
                .toList()

            results shouldContainExactly listOf(1, 99)
        }
    }

    test("onErrorReturn does not emit fallback when no error") {
        runTest {
            val results = flowOf(1, 2, 3)
                .onErrorReturn(99)
                .toList()

            results shouldContainExactly listOf(1, 2, 3)
        }
    }

    test("onErrorResume switches to fallback flow") {
        runTest {
            val results = flow {
                emit(1)
                throw RuntimeException()
            }
                .onErrorResume(flowOf(10, 20))
                .toList()

            results shouldContainExactly listOf(1, 10, 20)
        }
    }

    test("onErrorResume does not switch when no error") {
        runTest {
            val results = flowOf(1, 2, 3)
                .onErrorResume(flowOf(10, 20))
                .toList()

            results shouldContainExactly listOf(1, 2, 3)
        }
    }

    test("retryWithDelay retries on failure") {
        runTest {
            var attempts = 0
            val results = flow {
                attempts++
                if (attempts < 3) throw RuntimeException()
                emit(42)
            }
                .retryWithDelay(times = 3, delay = 10.milliseconds)
                .toList()

            attempts shouldBe 3
            results shouldContainExactly listOf(42)
        }
    }

    test("retryWithDelay executes with specific times parameter") {
        runTest {
            var attempts = 0
            val results = flow {
                attempts++
                if (attempts < 2) throw RuntimeException()
                emit("success")
            }
                .retryWithDelay(times = 5, delay = 1.milliseconds)
                .toList()

            attempts shouldBe 2
            results shouldContainExactly listOf("success")
        }
    }

    test("retryWithDelay uses default times parameter") {
        runTest {
            var attempts = 0
            val results = flow {
                attempts++
                if (attempts < 3) throw RuntimeException()
                emit(100)
            }
                .retryWithDelay(delay = 10.milliseconds)
                .toList()

            attempts shouldBe 3
            results shouldContainExactly listOf(100)
        }
    }

    test("retryWithDelay fails after exhausting retries") {
        runTest {
            var attempts = 0
            try {
                flow<Int> {
                    attempts++
                    throw RuntimeException("always fails")
                }
                    .retryWithDelay(times = 2, delay = 10.milliseconds)
                    .toList()
                throw AssertionError("Should have thrown")
            } catch (e: RuntimeException) {
                e.message shouldBe "always fails"
                attempts shouldBe 3 // Initial attempt + 2 retries
            }
        }
    }

    test("retryWithDelay succeeds without retry") {
        runTest {
            var attempts = 0
            val results = flow {
                attempts++
                emit(42)
            }
                .retryWithDelay(times = 3, delay = 10.milliseconds)
                .toList()

            attempts shouldBe 1
            results shouldContainExactly listOf(42)
        }
    }

    test("retryExponential uses exponential backoff") {
        // Use real time for this test
        var attempts = 0
        val elapsed = measureTime {
            flow {
                attempts++
                if (attempts < 3) throw RuntimeException()
                emit("success")
            }
                .retryExponential(
                    times = 3,
                    initialDelay = 20.milliseconds,
                    maxDelay = 100.milliseconds
                )
                .toList()
        }

        attempts shouldBe 3
        // First retry: 20ms, second retry: 40ms = 60ms minimum
        elapsed.inWholeMilliseconds shouldBeGreaterThanOrEqual 50
    }

    test("retryExponential executes with specific times parameter") {
        runTest {
            var attempts = 0
            val results = flow {
                attempts++
                if (attempts < 2) throw RuntimeException()
                emit(99)
            }
                .retryExponential(times = 4, initialDelay = 1.milliseconds)
                .toList()

            attempts shouldBe 2
            results shouldContainExactly listOf(99)
        }
    }

    test("retryExponential uses default times parameter") {
        runTest {
            var attempts = 0
            val results = flow {
                attempts++
                if (attempts < 3) throw RuntimeException()
                emit("done")
            }
                .retryExponential(initialDelay = 10.milliseconds)
                .toList()

            attempts shouldBe 3
            results shouldContainExactly listOf("done")
        }
    }

    test("retryExponential fails after exhausting retries") {
        runTest {
            var attempts = 0
            try {
                flow<String> {
                    attempts++
                    throw RuntimeException("persistent failure")
                }
                    .retryExponential(times = 2, initialDelay = 10.milliseconds)
                    .toList()
                throw AssertionError("Should have thrown")
            } catch (e: RuntimeException) {
                e.message shouldBe "persistent failure"
                attempts shouldBe 3 // Initial attempt + 2 retries
            }
        }
    }

    test("retryExponential succeeds without retry") {
        runTest {
            var attempts = 0
            val results = flow {
                attempts++
                emit("success")
            }
                .retryExponential(times = 3, initialDelay = 10.milliseconds)
                .toList()

            attempts shouldBe 1
            results shouldContainExactly listOf("success")
        }
    }

    test("throttle filters rapid emissions") {
        runTest {
            var virtualTime = 0L
            val results = flow {
                emit(1)
                virtualTime += 10
                delay(10.milliseconds)
                emit(2) // Should be filtered
                virtualTime += 10
                delay(10.milliseconds)
                emit(3) // Should be filtered
                virtualTime += 100
                delay(100.milliseconds)
                emit(4) // Should pass
            }
                .throttle(50.milliseconds) { virtualTime }
                .toList()

            results shouldContainExactly listOf(1, 4)
        }
    }

    test("throttle emits when within time window") {
        runTest {
            var virtualTime = 0L
            val results = flow {
                emit(1)
                virtualTime += 60
                delay(10.milliseconds)
                emit(2) // Should pass - enough time elapsed
                virtualTime += 20
                delay(10.milliseconds)
                emit(3) // Should be filtered
                virtualTime += 50
                delay(10.milliseconds)
                emit(4) // Should pass
            }
                .throttle(50.milliseconds) { virtualTime }
                .toList()

            results shouldContainExactly listOf(1, 2, 4)
        }
    }

    test("throttle with default time source") {
        // Use real time to test default currentTime parameter
        val results = flow {
            emit(1)
            delay(60.milliseconds) // Wait longer than throttle duration
            emit(2) // Should pass
            delay(10.milliseconds) // Wait shorter than throttle duration
            emit(3) // Should be filtered
        }
            .throttle(50.milliseconds) // No currentTime parameter - uses default
            .toList()

        results shouldContainExactly listOf(1, 2)
    }

    test("debounce emits last value after silence") {
        runTest {
            val results = flow {
                emit(1)
                delay(10.milliseconds)
                emit(2)
                delay(10.milliseconds)
                emit(3) // Last in rapid sequence
                delay(100.milliseconds) // Silence period
                emit(4)
                delay(10.milliseconds)
                emit(5) // Last value
            }
                .debounce(50.milliseconds)
                .toList()

            results shouldContainExactly listOf(3, 5)
        }
    }

    test("filterSuccessful extracts values from Success outcomes") {
        runTest {
            val results = flowOf(
                Outcome.success(1),
                Outcome.failure(RuntimeException()),
                Outcome.success(2),
                Outcome.failure(RuntimeException()),
                Outcome.success(3)
            )
                .filterSuccessful()
                .toList()

            results shouldContainExactly listOf(1, 2, 3)
        }
    }

    test("filterFailed extracts errors from Failure outcomes") {
        runTest {
            val error1 = RuntimeException("error1")
            val error2 = RuntimeException("error2")

            val results = flowOf(
                Outcome.success(1),
                Outcome.failure(error1),
                Outcome.success(2),
                Outcome.failure(error2)
            )
                .filterFailed()
                .toList()

            results shouldContainExactly listOf(error1, error2)
        }
    }

    test("filterFailed returns empty when all successful") {
        runTest {
            val results = flowOf(
                Outcome.success(1),
                Outcome.success(2),
                Outcome.success(3)
            )
                .filterFailed()
                .toList()

            results shouldContainExactly emptyList()
        }
    }

    test("filterSuccessful returns empty when all failed") {
        runTest {
            val results = flowOf<Outcome<Int>>(
                Outcome.failure(RuntimeException()),
                Outcome.failure(RuntimeException()),
                Outcome.failure(RuntimeException())
            )
                .filterSuccessful()
                .toList()

            results shouldContainExactly emptyList()
        }
    }
})
