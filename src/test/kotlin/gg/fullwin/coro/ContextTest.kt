package gg.fullwin.coro

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest

class ContextTest : FunSpec({

    test("sync switches to sync dispatcher") {
        runTest {
            CoroDispatchers.configure(
                sync = Dispatchers.Default,
                async = Dispatchers.IO
            )

            val result = sync { "executed" }
            result shouldBe "executed"
        }
    }

    test("async switches to async dispatcher") {
        runTest {
            CoroDispatchers.configure(
                sync = Dispatchers.Default,
                async = Dispatchers.IO
            )

            val result = async { "executed" }
            result shouldBe "executed"
        }
    }

    test("default uses Default dispatcher") {
        runTest {
            val result = default { 42 }
            result shouldBe 42
        }
    }

    test("CoroDispatchers.configure updates dispatchers") {
        val customSync = Dispatchers.Unconfined
        val customAsync = Dispatchers.Default

        CoroDispatchers.configure(
            sync = customSync,
            async = customAsync
        )

        CoroDispatchers.sync shouldBe customSync
        CoroDispatchers.async shouldBe customAsync
    }

    test("CoroDispatchers.configure with partial update") {
        val originalAsync = CoroDispatchers.async
        val newSync = Dispatchers.Default

        CoroDispatchers.configure(sync = newSync)

        CoroDispatchers.sync shouldBe newSync
        CoroDispatchers.async shouldBe originalAsync
    }

    test("CoroDispatchers.configure updates only async") {
        val originalSync = CoroDispatchers.sync
        val newAsync = Dispatchers.Default

        CoroDispatchers.configure(async = newAsync)

        CoroDispatchers.sync shouldBe originalSync
        CoroDispatchers.async shouldBe newAsync
    }
})
