@file:Suppress("UNREACHABLE_CODE")

package gg.fullwin.coro

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class OutcomeTest : FunSpec({

    test("safe should return Success for successful operation") {
        val result = safe { 42 }
        result.shouldBeInstanceOf<Outcome.Success<Int>>()
        result.getOrNull() shouldBe 42
    }

    test("safe should return Failure for throwing operation") {
        val result = safe { throw IllegalStateException("boom") }
        result.shouldBeInstanceOf<Outcome.Failure>()
        result.isFailure.shouldBeTrue()
    }

    test("map should transform Success value") {
        val result = safe { 10 }.map { it * 2 }
        result.getOrNull() shouldBe 20
    }

    test("map should propagate Failure") {
        val result = safe<Int> { throw RuntimeException("error") }
            .map { it * 2 }
        result.isFailure.shouldBeTrue()
    }

    test("map should catch exceptions in transform") {
        val result = safe { 10 }.map<Int> { throw RuntimeException("transform error") }
        result.isFailure.shouldBeTrue()
    }

    test("flatMap should chain successful operations") {
        val result = safe { 5 }
            .flatMap { safe { it * 3 } }
        result.getOrNull() shouldBe 15
    }

    test("flatMap should short-circuit on Failure") {
        var called = false
        val result = safe<Int> { throw RuntimeException() }
            .flatMap {
                called = true
                safe { it * 2 }
            }
        called.shouldBeFalse()
        result.isFailure.shouldBeTrue()
    }

    test("recover should transform Failure to Success") {
        val result = safe<Int> { throw RuntimeException() }
            .recover { 0 }
        result.getOrNull() shouldBe 0
    }

    test("recover should not affect Success") {
        val result = safe { 42 }.recover { 0 }
        result.getOrNull() shouldBe 42
    }

    test("onSuccess should execute action for Success") {
        var captured: Int? = null
        safe { 100 }.onSuccess { captured = it }
        captured shouldBe 100
    }

    test("onSuccess should not execute for Failure") {
        var called = false
        safe<Int> { throw RuntimeException() }.onSuccess { called = true }
        called.shouldBeFalse()
    }

    test("onError should execute action for Failure") {
        var captured: Throwable? = null
        safe<Int> { throw IllegalArgumentException("test") }
            .onError { captured = it }
        captured.shouldBeInstanceOf<IllegalArgumentException>()
    }

    test("onError should not execute for Success") {
        var called = false
        safe { 42 }.onError { called = true }
        called.shouldBeFalse()
    }

    test("getOrNull should return value for Success") {
        safe { "hello" }.getOrNull() shouldBe "hello"
    }

    test("getOrNull should return null for Failure") {
        safe<String> { throw RuntimeException() }.getOrNull().shouldBeNull()
    }

    test("getOrElse should return value for Success") {
        safe { 10 }.getOrElse { 0 } shouldBe 10
    }

    test("getOrElse should return default for Failure") {
        safe<Int> { throw RuntimeException() }.getOrElse { 99 } shouldBe 99
    }

    test("getOrThrow should return value for Success") {
        safe { "test" }.getOrThrow() shouldBe "test"
    }

    test("getOrThrow should throw for Failure") {
        val result = safe<String> { throw IllegalStateException("expected") }
        try {
            result.getOrThrow()
            throw AssertionError("Should have thrown")
        } catch (e: IllegalStateException) {
            e.message shouldBe "expected"
        }
    }

    test("orElse should return original for Success") {
        val result = safe { 1 }.orElse { safe { 2 } }
        result.getOrNull() shouldBe 1
    }

    test("orElse should return alternative for Failure") {
        val result = safe<Int> { throw RuntimeException() }
            .orElse { safe { 42 } }
        result.getOrNull() shouldBe 42
    }

    test("toResult should convert Success") {
        val result = Outcome.success(10).toResult()
        result.isSuccess.shouldBeTrue()
        result.getOrNull() shouldBe 10
    }

    test("toResult should convert Failure") {
        val outcome = Outcome.failure(RuntimeException("err"))
        val result = outcome.toResult()
        result.isFailure.shouldBeTrue()
    }

    test("Result.toOutcome should convert success") {
        val outcome = Result.success(42).toOutcome()
        outcome.isSuccess.shouldBeTrue()
        outcome.getOrNull() shouldBe 42
    }

    test("Result.toOutcome should convert failure") {
        val outcome = Result.failure<Int>(RuntimeException()).toOutcome()
        outcome.isFailure.shouldBeTrue()
    }

    test("isSuccess and isFailure flags") {
        val success = Outcome.success(1)
        success.isSuccess.shouldBeTrue()
        success.isFailure.shouldBeFalse()

        val failure = Outcome.failure(RuntimeException())
        failure.isSuccess.shouldBeFalse()
        failure.isFailure.shouldBeTrue()
    }
})
