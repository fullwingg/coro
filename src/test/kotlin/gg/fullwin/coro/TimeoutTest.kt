package gg.fullwin.coro

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class TimeoutTest : FunSpec({

    test("withTimeout returns Success when operation completes in time") {
        runTest {
            val result = withTimeout(1.seconds) {
                "done"
            }

            result.shouldBeInstanceOf<Outcome.Success<String>>()
            result.getOrNull() shouldBe "done"
        }
    }

    test("withTimeout returns Failure when operation times out") {
        runTest {
            val result = withTimeout(50.milliseconds) {
                delay(500.milliseconds)
                "never"
            }

            result.shouldBeInstanceOf<Outcome.Failure>()
        }
    }

    test("withTimeout catches exceptions in block") {
        runTest {
            val result = withTimeout<String>(1.seconds) {
                throw IllegalStateException("error")
            }

            result.shouldBeInstanceOf<Outcome.Failure>()
            result.error.shouldBeInstanceOf<IllegalStateException>()
        }
    }

    test("withTimeoutOrNull returns value on success") {
        runTest {
            val result = withTimeoutOrNull(1.seconds) {
                42
            }

            result shouldBe 42
        }
    }

    test("withTimeoutOrNull returns null on timeout") {
        runTest {
            val result = withTimeoutOrNull(50.milliseconds) {
                delay(500.milliseconds)
                42
            }

            result.shouldBeNull()
        }
    }

    test("withTimeoutOrElse returns value on success") {
        runTest {
            val result = withTimeoutOrElse(
                duration = 1.seconds,
                default = { 0 }
            ) {
                100
            }

            result shouldBe 100
        }
    }

    test("withTimeoutOrElse returns default on timeout") {
        runTest {
            val result = withTimeoutOrElse(
                duration = 50.milliseconds,
                default = { -1 }
            ) {
                delay(500.milliseconds)
                100
            }

            result shouldBe -1
        }
    }

    test("withTimeoutOrThrow returns value on success") {
        runTest {
            val result = withTimeoutOrThrow(1.seconds) {
                "success"
            }

            result shouldBe "success"
        }
    }

    test("withTimeoutOrThrow throws TimeoutException on timeout") {
        runTest {
            try {
                withTimeoutOrThrow(
                    duration = 50.milliseconds,
                    message = "Custom timeout message"
                ) {
                    delay(500.milliseconds)
                    "never"
                }
                throw AssertionError("Should have thrown")
            } catch (e: TimeoutException) {
                e.message shouldBe "Custom timeout message"
            }
        }
    }
})
