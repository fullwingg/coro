package gg.fullwin.coro

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest

class AsyncTest : FunSpec({

    test("load should return Success for successful operation") {
        runTest {
            var blockCalled = false
            val result = load {
                blockCalled = true
                42
            }
            blockCalled.shouldBeTrue()
            result.shouldBeInstanceOf<Async.Success<Int>>()
            result.getOrNull() shouldBe 42
            result.isSuccess.shouldBeTrue()
        }
    }

    test("load should return Failure for throwing operation") {
        runTest {
            val result = load<Int> { throw IllegalStateException("boom") }
            result.shouldBeInstanceOf<Async.Failure>()
            result.isFailure.shouldBeTrue()
        }
    }

    test("load executes suspend block") {
        runTest {
            var counter = 0
            val result = load {
                counter++
                kotlinx.coroutines.delay(1)
                counter++
                "done"
            }
            counter shouldBe 2
            result.getOrNull() shouldBe "done"
        }
    }

    test("Uninitialized state") {
        val state: Async<Int> = Async.Uninitialized
        state.isUninitialized.shouldBeTrue()
        state.isLoading.shouldBeFalse()
        state.isSuccess.shouldBeFalse()
        state.isFailure.shouldBeFalse()
        // Test isComplete with both isSuccess and isFailure false
        state.isComplete.shouldBeFalse()
        state.getOrNull().shouldBeNull()
    }

    test("Loading state") {
        val state: Async<Int> = Async.Loading
        state.isUninitialized.shouldBeFalse()
        state.isLoading.shouldBeTrue()
        state.isSuccess.shouldBeFalse()
        state.isFailure.shouldBeFalse()
        // Test isComplete with both isSuccess and isFailure false
        state.isComplete.shouldBeFalse()
        state.getOrNull().shouldBeNull()
    }

    test("Success state") {
        val state: Async<Int> = Async.Success(42)
        state.isUninitialized.shouldBeFalse()
        state.isLoading.shouldBeFalse()
        // Test isSuccess branch first (short-circuit in isComplete)
        state.isSuccess.shouldBeTrue()
        state.isFailure.shouldBeFalse()
        // Test isComplete when isSuccess is true
        state.isComplete.shouldBeTrue()
        state.getOrNull() shouldBe 42
    }

    test("Failure state") {
        val error = RuntimeException("test error")
        val state: Async<Int> = Async.Failure(error)
        state.isUninitialized.shouldBeFalse()
        state.isLoading.shouldBeFalse()
        // Test with isSuccess false but isFailure true
        state.isSuccess.shouldBeFalse()
        state.isFailure.shouldBeTrue()
        // Test isComplete when isFailure is true
        state.isComplete.shouldBeTrue()
        state.getOrNull().shouldBeNull()
        state.errorOrNull() shouldBe error
    }

    test("errorOrNull returns null for non-Failure states") {
        (Async.Uninitialized as Async<Int>).errorOrNull().shouldBeNull()
        (Async.Loading as Async<Int>).errorOrNull().shouldBeNull()
        Async.Success(42).errorOrNull().shouldBeNull()
    }

    test("getOrElse should return value for Success") {
        val state = Async.Success(10)
        state.getOrElse { 0 } shouldBe 10
    }

    test("getOrElse should return default for non-Success states") {
        (Async.Loading as Async<Int>).getOrElse { 99 } shouldBe 99
        (Async.Uninitialized as Async<Int>).getOrElse { 99 } shouldBe 99
        (Async.Failure(RuntimeException()) as Async<Int>).getOrElse { 99 } shouldBe 99
    }

    test("getOrNull returns null for non-Success states") {
        val loading: Async<Int> = Async.Loading
        loading.getOrNull().shouldBeNull()

        val uninitialized: Async<Int> = Async.Uninitialized
        uninitialized.getOrNull().shouldBeNull()

        val failure: Async<Int> = Async.Failure(RuntimeException())
        failure.getOrNull().shouldBeNull()
    }

    test("map transforms Success value") {
        val result = Async.Success(10).map { it * 2 }
        result.shouldBeInstanceOf<Async.Success<Int>>()
        result.getOrNull() shouldBe 20
    }

    test("map returns Uninitialized for Uninitialized") {
        val state: Async<Int> = Async.Uninitialized
        val result = state.map { it * 2 }
        result.shouldBeInstanceOf<Async.Uninitialized>()
        result.isUninitialized.shouldBeTrue()
    }

    test("map returns Loading for Loading") {
        val state: Async<Int> = Async.Loading
        val result = state.map { it * 2 }
        result.shouldBeInstanceOf<Async.Loading>()
        result.isLoading.shouldBeTrue()
    }

    test("map preserves Failure") {
        val error = RuntimeException("test error")
        val state: Async<Int> = Async.Failure(error)
        val result = state.map { it * 2 }
        result.shouldBeInstanceOf<Async.Failure>()
        result.isFailure.shouldBeTrue()
        result.errorOrNull() shouldBe error
    }

    test("onSuccess callback") {
        var captured: Int? = null
        Async.Success(100).onSuccess { captured = it }
        captured shouldBe 100

        captured = null
        Async.Loading.onSuccess { captured = 1 }
        captured.shouldBeNull()
    }

    test("onFailure callback") {
        var captured: Throwable? = null
        val error = RuntimeException("test")
        Async.Failure(error).onFailure { captured = it }
        captured shouldBe error

        captured = null
        Async.Success(1).onFailure { captured = it }
        captured.shouldBeNull()
    }

    test("onLoading callback") {
        var called = false
        Async.Loading.onLoading { called = true }
        called.shouldBeTrue()

        called = false
        Async.Success(1).onLoading { called = true }
        called.shouldBeFalse()
    }

    test("fold should handle all states") {
        val uninitialized: Async<Int> = Async.Uninitialized
        uninitialized.fold(
            onUninitialized = { "uninit" },
            onLoading = { "loading" },
            onSuccess = { "success: $it" },
            onFailure = { "failure" }
        ) shouldBe "uninit"

        val loading: Async<Int> = Async.Loading
        loading.fold(
            onUninitialized = { "uninit" },
            onLoading = { "loading" },
            onSuccess = { "success: $it" },
            onFailure = { "failure" }
        ) shouldBe "loading"

        val success = Async.Success(42)
        success.fold(
            onUninitialized = { "uninit" },
            onLoading = { "loading" },
            onSuccess = { "success: $it" },
            onFailure = { "failure" }
        ) shouldBe "success: 42"

        val failure: Async<Int> = Async.Failure(RuntimeException())
        failure.fold(
            onUninitialized = { "uninit" },
            onLoading = { "loading" },
            onSuccess = { "success: $it" },
            onFailure = { "failure" }
        ) shouldBe "failure"
    }

    test("toAsync converts Outcome.Success") {
        val outcome = Outcome.success(42)
        val async = outcome.toAsync()
        async.shouldBeInstanceOf<Async.Success<Int>>()
        async.getOrNull() shouldBe 42
    }

    test("toAsync converts Outcome.Failure") {
        val error = RuntimeException("test")
        val outcome = Outcome.failure(error)
        val async = outcome.toAsync()
        async.shouldBeInstanceOf<Async.Failure>()
        async.errorOrNull() shouldBe error
    }

    test("toOutcome converts Async.Success") {
        val async = Async.Success(10)
        val outcome = async.toOutcome()
        outcome.shouldBeInstanceOf<Outcome.Success<Int>>()
        outcome.getOrNull() shouldBe 10
    }

    test("toOutcome converts Async.Failure") {
        val error = RuntimeException()
        val async = Async.Failure(error)
        val outcome = async.toOutcome()
        outcome.shouldBeInstanceOf<Outcome.Failure>()
    }

    test("toOutcome returns null for Loading") {
        val async: Async<Int> = Async.Loading
        async.toOutcome().shouldBeNull()
    }

    test("toOutcome returns null for Uninitialized") {
        val async: Async<Int> = Async.Uninitialized
        async.toOutcome().shouldBeNull()
    }

    test("companion factory methods") {
        Async.success(1).shouldBeInstanceOf<Async.Success<Int>>()
        Async.failure(RuntimeException()).shouldBeInstanceOf<Async.Failure>()
        Async.loading().shouldBeInstanceOf<Async.Loading>()
        Async.uninitialized().shouldBeInstanceOf<Async.Uninitialized>()
    }
})
