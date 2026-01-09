package gg.fullwin.coro

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * A TimeSource that uses the TestCoroutineScheduler's virtual time.
 */
class TestTimeSource(private val scheduler: TestCoroutineScheduler) : TimeSource {
    override fun markNow(): TimeMark = TestTimeMark(scheduler.currentTime, scheduler)

    private class TestTimeMark(
        private val markedAt: Long,
        private val scheduler: TestCoroutineScheduler
    ) : TimeMark {
        override fun elapsedNow(): Duration =
            (scheduler.currentTime - markedAt).milliseconds

        override fun plus(duration: Duration): TimeMark =
            TestTimeMark(markedAt + duration.inWholeMilliseconds, scheduler)

        override fun minus(duration: Duration): TimeMark =
            TestTimeMark(markedAt - duration.inWholeMilliseconds, scheduler)

        override fun hasPassedNow(): Boolean =
            scheduler.currentTime >= markedAt

        override fun hasNotPassedNow(): Boolean =
            scheduler.currentTime < markedAt
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DebounceTest : FunSpec({

    test("debounce collapses rapid calls") {
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val scope = CoroutineScope(testDispatcher)
            val collected = mutableListOf<Int>()

            val debounced = scope.debounce<Int>(100.milliseconds) { value ->
                collected.add(value)
            }

            debounced(1)
            debounced(2)
            debounced(3)

            advanceTimeBy(150)
            collected shouldContainExactly listOf(3)

            scope.cancel()
        }
    }

    test("debounce allows calls after wait period") {
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val scope = CoroutineScope(testDispatcher)
            val collected = mutableListOf<Int>()

            val debounced = scope.debounce<Int>(50.milliseconds) { value ->
                collected.add(value)
            }

            debounced(1)
            advanceTimeBy(100)

            debounced(2)
            advanceTimeBy(100)

            collected shouldContainExactly listOf(1, 2)

            scope.cancel()
        }
    }

    test("debounce without parameter") {
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val scope = CoroutineScope(testDispatcher)
            var callCount = 0

            val debounced = scope.debounce(50.milliseconds) {
                callCount++
            }

            debounced()
            debounced()
            debounced()

            advanceTimeBy(100)
            callCount shouldBe 1

            scope.cancel()
        }
    }

    test("throttle limits call frequency") {
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val scope = CoroutineScope(testDispatcher)
            val testTimeSource = TestTimeSource(testScheduler)
            val collected = mutableListOf<Int>()

            val throttled = scope.throttle<Int>(100.milliseconds, testTimeSource) { value ->
                collected.add(value)
            }

            throttled(1) // Should execute immediately
            advanceTimeBy(1) // Let coroutine run

            throttled(2) // Should be ignored (within interval)
            advanceTimeBy(20)

            throttled(3) // Should be ignored (within interval)
            advanceTimeBy(100)

            throttled(4) // Should execute (after interval)
            advanceTimeBy(1) // Let coroutine run

            collected shouldContainExactly listOf(1, 4)

            scope.cancel()
        }
    }

    test("throttle without parameter") {
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val scope = CoroutineScope(testDispatcher)
            val testTimeSource = TestTimeSource(testScheduler)
            var callCount = 0

            val throttled = scope.throttle(100.milliseconds, testTimeSource) {
                callCount++
            }

            throttled()
            advanceTimeBy(1)
            throttled()
            advanceTimeBy(20)
            throttled()
            advanceTimeBy(20)

            callCount shouldBe 1

            scope.cancel()
        }
    }

    test("throttleLatest executes last value after interval") {
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val scope = CoroutineScope(testDispatcher)
            val testTimeSource = TestTimeSource(testScheduler)
            val collected = mutableListOf<Int>()

            val throttled = scope.throttleLatest<Int>(100.milliseconds, testTimeSource) { value ->
                collected.add(value)
            }

            throttled(1) // Executes immediately
            advanceTimeBy(1)

            throttled(2) // Queued
            advanceTimeBy(20)

            throttled(3) // Replaces queued value
            advanceTimeBy(150) // Wait for queued execution

            collected shouldContainExactly listOf(1, 3)

            scope.cancel()
        }
    }
})
