package gg.fullwin.coro

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

class RetryTest : FunSpec({

    test("retry returns Success on first try") {
        runTest {
            var attempts = 0
            val result = retry(times = 3, delay = 10.milliseconds) {
                attempts++
                "success"
            }

            result.shouldBeInstanceOf<Outcome.Success<String>>()
            result.getOrNull() shouldBe "success"
            attempts shouldBe 1
        }
    }

    test("retry retries on failure and eventually succeeds") {
        runTest {
            var attempts = 0
            val result = retry(times = 3, delay = 10.milliseconds) {
                attempts++
                if (attempts < 3) throw RuntimeException("fail")
                "success"
            }

            result.shouldBeInstanceOf<Outcome.Success<String>>()
            result.getOrNull() shouldBe "success"
            attempts shouldBe 3
        }
    }

    test("retry returns Failure after all attempts exhausted") {
        runTest {
            var attempts = 0
            val result = retry<String>(times = 3, delay = 10.milliseconds) {
                attempts++
                throw RuntimeException("always fails")
            }

            result.shouldBeInstanceOf<Outcome.Failure>()
            attempts shouldBe 3
        }
    }

    test("retry with predicate stops early for non-matching errors") {
        runTest {
            var attempts = 0
            val result = retry<String>(
                times = 5,
                delay = 10.milliseconds,
                predicate = { it is IllegalStateException }
            ) {
                attempts++
                if (attempts == 1) throw IllegalStateException("retry this")
                throw IllegalArgumentException("don't retry this")
            }

            result.shouldBeInstanceOf<Outcome.Failure>()
            result.error.shouldBeInstanceOf<IllegalArgumentException>()
            attempts shouldBe 2
        }
    }

    test("retry with exponential backoff increases delay") {
        // Use real time for this test since we're measuring actual delays
        var attempts = 0
        val elapsed = measureTime {
            retry<String>(
                times = 3,
                delay = 50.milliseconds,
                exponential = true
            ) {
                attempts++
                throw RuntimeException()
            }
        }

        attempts shouldBe 3
        // First retry: 50ms, second retry: 100ms = 150ms minimum
        elapsed.inWholeMilliseconds shouldBeGreaterThanOrEqual 140
    }

    test("retryOrThrow returns value on success") {
        runTest {
            val result = retryOrThrow(times = 2, delay = 10.milliseconds) {
                42
            }
            result shouldBe 42
        }
    }

    test("retryOrThrow throws on failure") {
        runTest {
            try {
                retryOrThrow<Int>(times = 2, delay = 10.milliseconds) {
                    throw IllegalStateException("expected")
                }
                throw AssertionError("Should have thrown")
            } catch (e: IllegalStateException) {
                e.message shouldBe "expected"
            }
        }
    }

    test("retry DSL builder") {
        runTest {
            var attempts = 0
            val builder: RetryBuilder<String>.() -> Unit = {
                times = 3
                delay = 10.milliseconds
                exponential = false
                retryIf { it is RuntimeException }
                attempt {
                    attempts++
                    if (attempts < 2) throw RuntimeException()
                    "done"
                }
            }
            val result = retry(builder)

            result.shouldBeInstanceOf<Outcome.Success<String>>()
            result.getOrNull() shouldBe "done"
            attempts shouldBe 2
        }
    }
})
