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
            results[1].shouldBeInstanceOf<Outcome.Failure>()
            results[2].shouldBeInstanceOf<Outcome.Success<Int>>()
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
            scope.cancel()
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
})
