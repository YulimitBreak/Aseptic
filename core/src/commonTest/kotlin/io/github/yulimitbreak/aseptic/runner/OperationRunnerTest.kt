@file:OptIn(AsepticInternal::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.yulimitbreak.aseptic.runner

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.handle.BaseAsepticHandle
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlin.coroutines.cancellation.CancellationException

class OperationRunnerTest : BehaviorSpec() {

    private class TestHandle : BaseAsepticHandle<Nothing, Nothing>(
        StateContainerBuilder().build(),
        snapshotGenerator = { error("unused") },
        atomicScopeGenerator = { error("unused") },
    )

    /**
     * Records lifecycle of named operations and exposes a gate per operation so tests
     * control exactly when each operation finishes.
     */
    private class Tracker {
        val started = mutableListOf<String>()
        val completed = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        private val gates = mutableMapOf<String, CompletableDeferred<Unit>>()

        fun op(name: String): suspend TestHandle.() -> Unit {
            val gate = CompletableDeferred<Unit>()
            gates[name] = gate
            return {
                started += name
                try {
                    gate.await()
                    completed += name
                } catch (e: CancellationException) {
                    cancelled += name
                    throw e
                }
            }
        }

        fun finish(name: String) {
            gates.getValue(name).complete(Unit)
        }
    }

    init {
        coroutineTestScope = true

        Given("an operation dispatched with an arbitrary policy is running") {

            When("a second operation is dispatched with CONCURRENT") {
                Then("both run concurrently and complete") {
                    checkAll(Exhaustive.enum<DispatchPolicy>()) { firstPolicy ->
                        val scope = CoroutineScope(coroutineContext + Job())
                        val runner = OperationRunner(scope, TestHandle())
                        val tracker = Tracker()

                        runner.dispatch(tracker.op("a"), key, firstPolicy)
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.started shouldContainExactly listOf("a")

                        runner.dispatch(tracker.op("b"), key, DispatchPolicy.CONCURRENT)
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.started shouldContainExactlyInAnyOrder listOf("a", "b")

                        tracker.finish("a")
                        tracker.finish("b")
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.completed shouldContainExactlyInAnyOrder listOf("a", "b")
                        scope.cancel()
                    }
                }
            }

            When("a second operation is dispatched with QUEUE") {
                Then("it waits for the running operation to finish") {
                    checkAll(Exhaustive.enum<DispatchPolicy>()) { firstPolicy ->
                        val scope = CoroutineScope(coroutineContext + Job())
                        val runner = OperationRunner(scope, TestHandle())
                        val tracker = Tracker()

                        runner.dispatch(tracker.op("a"), key, firstPolicy)
                        testCoroutineScheduler.advanceUntilIdle()

                        runner.dispatch(tracker.op("b"), key, DispatchPolicy.QUEUE)
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.started shouldContainExactly listOf("a")

                        tracker.finish("a")
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.started shouldContainExactly listOf("a", "b")

                        tracker.finish("b")
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.completed shouldContainExactly listOf("a", "b")
                        scope.cancel()
                    }
                }
            }

            When("a second operation is dispatched with DROP") {
                Then("it is dropped and never runs") {
                    checkAll(Exhaustive.enum<DispatchPolicy>()) { firstPolicy ->
                        val scope = CoroutineScope(coroutineContext + Job())
                        val runner = OperationRunner(scope, TestHandle())
                        val tracker = Tracker()

                        runner.dispatch(tracker.op("a"), key, firstPolicy)
                        testCoroutineScheduler.advanceUntilIdle()

                        runner.dispatch(tracker.op("b"), key, DispatchPolicy.DROP)
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.started shouldContainExactly listOf("a")

                        tracker.finish("a")
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.started shouldContainExactly listOf("a")
                        tracker.completed shouldContainExactly listOf("a")
                        scope.cancel()
                    }
                }
            }

            When("a second operation is dispatched with CANCEL") {
                Then("the running operation is cancelled and the second runs") {
                    checkAll(Exhaustive.enum<DispatchPolicy>()) { firstPolicy ->
                        val scope = CoroutineScope(coroutineContext + Job())
                        val runner = OperationRunner(scope, TestHandle())
                        val tracker = Tracker()

                        runner.dispatch(tracker.op("a"), key, firstPolicy)
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.started shouldContainExactly listOf("a")

                        runner.dispatch(tracker.op("b"), key, DispatchPolicy.CANCEL)
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.cancelled shouldContainExactly listOf("a")
                        tracker.started shouldContainExactly listOf("a", "b")

                        tracker.finish("b")
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.completed shouldContainExactly listOf("b")
                        scope.cancel()
                    }
                }
            }

            When("its OperationHandle.cancel() is called") {
                Then("the operation is cancelled") {
                    checkAll(Exhaustive.enum<DispatchPolicy>()) { firstPolicy ->
                        val scope = CoroutineScope(coroutineContext + Job())
                        val runner = OperationRunner(scope, TestHandle())
                        val tracker = Tracker()

                        val handle = runner.dispatch(tracker.op("a"), key, firstPolicy)
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.started shouldContainExactly listOf("a")

                        handle.cancel()
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.cancelled shouldContainExactly listOf("a")
                        tracker.completed shouldBe emptyList()
                        scope.cancel()
                    }
                }
            }

            When("a second operation under a different key is dispatched with an arbitrary policy") {
                Then("it runs immediately") {
                    checkAll(
                        Exhaustive.enum<DispatchPolicy>(),
                        Exhaustive.enum<DispatchPolicy>()
                    ) { firstPolicy, secondPolicy ->
                        val scope = CoroutineScope(coroutineContext + Job())
                        val runner = OperationRunner(scope, TestHandle())
                        val tracker = Tracker()

                        runner.dispatch(tracker.op("a"), StandardOpKey("x"), firstPolicy)
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.started shouldContainExactly listOf("a")

                        runner.dispatch(tracker.op("b"), StandardOpKey("y"), secondPolicy)
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.started shouldContainExactlyInAnyOrder listOf("a", "b")

                        tracker.finish("a")
                        tracker.finish("b")
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.completed shouldContainExactlyInAnyOrder listOf("a", "b")
                        scope.cancel()
                    }
                }
            }
        }

        Given("two operations dispatched under the same key") {
            When("they are dispatched back-to-back and, separately, with a delay between them") {
                Then("the same set of operations completes either way, for any pair of policies") {
                    checkAll(
                        Exhaustive.enum<DispatchPolicy>(),
                        Exhaustive.enum<DispatchPolicy>()
                    ) { firstPolicy, secondPolicy ->
                        val immediate = run {
                            val scope = CoroutineScope(coroutineContext + Job())
                            val runner = OperationRunner(scope, TestHandle())
                            val tracker = Tracker()
                            runner.dispatch(tracker.op("a"), key, firstPolicy)
                            runner.dispatch(tracker.op("b"), key, secondPolicy)
                            testCoroutineScheduler.advanceUntilIdle()
                            tracker.finish("a")
                            tracker.finish("b")
                            testCoroutineScheduler.advanceUntilIdle()
                            scope.cancel()
                            tracker.completed.toSet()
                        }
                        val delayed = run {
                            val scope = CoroutineScope(coroutineContext + Job())
                            val runner = OperationRunner(scope, TestHandle())
                            val tracker = Tracker()
                            runner.dispatch(tracker.op("a"), key, firstPolicy)
                            testCoroutineScheduler.advanceUntilIdle()
                            runner.dispatch(tracker.op("b"), key, secondPolicy)
                            testCoroutineScheduler.advanceUntilIdle()
                            tracker.finish("a")
                            tracker.finish("b")
                            testCoroutineScheduler.advanceUntilIdle()
                            scope.cancel()
                            tracker.completed.toSet()
                        }
                        immediate shouldBe delayed
                    }
                }
            }
        }

        Given("an operation dispatched with an arbitrary policy is running, with two QUEUE operations behind it") {
            When("each running operation completes") {
                Then("the next queued operation runs, in dispatch order") {
                    checkAll(Exhaustive.enum<DispatchPolicy>()) { firstPolicy ->
                        val scope = CoroutineScope(coroutineContext + Job())
                        val runner = OperationRunner(scope, TestHandle())
                        val tracker = Tracker()

                        runner.dispatch(tracker.op("a"), key, firstPolicy)
                        runner.dispatch(tracker.op("b"), key, DispatchPolicy.QUEUE)
                        runner.dispatch(tracker.op("c"), key, DispatchPolicy.QUEUE)
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.started shouldContainExactly listOf("a")

                        tracker.finish("a")
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.started shouldContainExactly listOf("a", "b")

                        tracker.finish("b")
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.started shouldContainExactly listOf("a", "b", "c")

                        tracker.finish("c")
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.completed shouldContainExactly listOf("a", "b", "c")
                        scope.cancel()
                    }
                }
            }

            When("the first queued operation is cancelled and the running one then completes") {
                Then("the cancelled operation is skipped and the rest run in order") {
                    checkAll(Exhaustive.enum<DispatchPolicy>()) { firstPolicy ->
                        val scope = CoroutineScope(coroutineContext + Job())
                        val runner = OperationRunner(scope, TestHandle())
                        val tracker = Tracker()

                        runner.dispatch(tracker.op("a"), key, firstPolicy)
                        val middle = runner.dispatch(tracker.op("b"), key, DispatchPolicy.QUEUE)
                        runner.dispatch(tracker.op("c"), key, DispatchPolicy.QUEUE)
                        testCoroutineScheduler.advanceUntilIdle()

                        middle.cancel()
                        testCoroutineScheduler.advanceUntilIdle()

                        tracker.finish("a")
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.started shouldContainExactly listOf("a", "c")

                        tracker.finish("c")
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.completed shouldContainExactly listOf("a", "c")
                        scope.cancel()
                    }
                }
            }
        }

        Given("an operation dispatched with an arbitrary policy is running, with one QUEUE operation behind it") {
            When("the queued operation is cancelled before the running one completes") {
                Then("it never runs and the running operation still completes") {
                    checkAll(Exhaustive.enum<DispatchPolicy>()) { firstPolicy ->
                        val scope = CoroutineScope(coroutineContext + Job())
                        val runner = OperationRunner(scope, TestHandle())
                        val tracker = Tracker()

                        runner.dispatch(tracker.op("a"), key, firstPolicy)
                        val queued = runner.dispatch(tracker.op("b"), key, DispatchPolicy.QUEUE)
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.started shouldContainExactly listOf("a")

                        queued.cancel()
                        testCoroutineScheduler.advanceUntilIdle()

                        tracker.finish("a")
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.started shouldContainExactly listOf("a")
                        tracker.completed shouldContainExactly listOf("a")
                        scope.cancel()
                    }
                }
            }

            When("the running operation is cancelled via its handle") {
                Then("it is cancelled and the queued operation runs") {
                    checkAll(Exhaustive.enum<DispatchPolicy>()) { firstPolicy ->
                        val scope = CoroutineScope(coroutineContext + Job())
                        val runner = OperationRunner(scope, TestHandle())
                        val tracker = Tracker()

                        val running = runner.dispatch(tracker.op("a"), key, firstPolicy)
                        runner.dispatch(tracker.op("b"), key, DispatchPolicy.QUEUE)
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.started shouldContainExactly listOf("a")

                        running.cancel()
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.cancelled shouldContainExactly listOf("a")
                        tracker.started shouldContainExactly listOf("a", "b")

                        tracker.finish("b")
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.completed shouldContainExactly listOf("b")
                        scope.cancel()
                    }
                }
            }

            When("the running operation throws a non-cancellation exception") {
                Then("the error handler receives the exception and the queue proceeds") {
                    checkAll(Exhaustive.enum<DispatchPolicy>()) { firstPolicy ->
                        val scope = CoroutineScope(coroutineContext + Job())
                        val runner = OperationRunner(scope, TestHandle())
                        val tracker = Tracker()
                        val errors = mutableListOf<Throwable>()
                        runner.errorHandler = { errors += it }

                        runner.dispatch({ throw IllegalStateException("boom") }, key, firstPolicy)
                        runner.dispatch(tracker.op("next"), key, DispatchPolicy.QUEUE)
                        testCoroutineScheduler.advanceUntilIdle()

                        errors.map { it.message } shouldContainExactly listOf("boom")
                        tracker.started shouldContainExactly listOf("next")

                        tracker.finish("next")
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.completed shouldContainExactly listOf("next")
                        scope.cancel()
                    }
                }
            }

            When("a third operation is dispatched with CONCURRENT") {
                Then("it runs immediately alongside the running one, leaving the queue intact") {
                    checkAll(Exhaustive.enum<DispatchPolicy>()) { firstPolicy ->
                        val scope = CoroutineScope(coroutineContext + Job())
                        val runner = OperationRunner(scope, TestHandle())
                        val tracker = Tracker()

                        runner.dispatch(tracker.op("a"), key, firstPolicy)
                        runner.dispatch(tracker.op("b"), key, DispatchPolicy.QUEUE)
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.started shouldContainExactly listOf("a")

                        runner.dispatch(tracker.op("c"), key, DispatchPolicy.CONCURRENT)
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.started shouldContainExactlyInAnyOrder listOf("a", "c")

                        tracker.finish("a")
                        tracker.finish("b")
                        tracker.finish("c")
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.completed shouldContainExactlyInAnyOrder listOf("a", "b", "c")
                        scope.cancel()
                    }
                }
            }

            When("a third operation is dispatched with QUEUE") {
                Then("it is appended behind the existing queued operation") {
                    checkAll(Exhaustive.enum<DispatchPolicy>()) { firstPolicy ->
                        val scope = CoroutineScope(coroutineContext + Job())
                        val runner = OperationRunner(scope, TestHandle())
                        val tracker = Tracker()

                        runner.dispatch(tracker.op("a"), key, firstPolicy)
                        runner.dispatch(tracker.op("b"), key, DispatchPolicy.QUEUE)
                        testCoroutineScheduler.advanceUntilIdle()

                        runner.dispatch(tracker.op("c"), key, DispatchPolicy.QUEUE)
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.started shouldContainExactly listOf("a")

                        tracker.finish("a")
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.started shouldContainExactly listOf("a", "b")

                        tracker.finish("b")
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.started shouldContainExactly listOf("a", "b", "c")

                        tracker.finish("c")
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.completed shouldContainExactly listOf("a", "b", "c")
                        scope.cancel()
                    }
                }
            }

            When("a third operation is dispatched with DROP") {
                Then("it is dropped while the running and queued operations are untouched") {
                    checkAll(Exhaustive.enum<DispatchPolicy>()) { firstPolicy ->
                        val scope = CoroutineScope(coroutineContext + Job())
                        val runner = OperationRunner(scope, TestHandle())
                        val tracker = Tracker()

                        runner.dispatch(tracker.op("a"), key, firstPolicy)
                        runner.dispatch(tracker.op("b"), key, DispatchPolicy.QUEUE)
                        testCoroutineScheduler.advanceUntilIdle()

                        runner.dispatch(tracker.op("c"), key, DispatchPolicy.DROP)
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.started shouldContainExactly listOf("a")

                        tracker.finish("a")
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.started shouldContainExactly listOf("a", "b")

                        tracker.finish("b")
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.completed shouldContainExactly listOf("a", "b")
                        scope.cancel()
                    }
                }
            }

            When("a third operation is dispatched with CANCEL") {
                Then("both the running and queued operations are cancelled and the third runs") {
                    checkAll(Exhaustive.enum<DispatchPolicy>()) { firstPolicy ->
                        val scope = CoroutineScope(coroutineContext + Job())
                        val runner = OperationRunner(scope, TestHandle())
                        val tracker = Tracker()

                        runner.dispatch(tracker.op("a"), key, firstPolicy)
                        runner.dispatch(tracker.op("b"), key, DispatchPolicy.QUEUE)
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.started shouldContainExactly listOf("a")

                        runner.dispatch(tracker.op("c"), key, DispatchPolicy.CANCEL)
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.cancelled shouldContainExactly listOf("a")
                        tracker.started shouldContainExactlyInAnyOrder listOf("a", "c")

                        tracker.finish("c")
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.completed shouldContainExactly listOf("c")
                        scope.cancel()
                    }
                }
            }
        }

        Given("two operations running concurrently") {
            When("one is cancelled via its handle") {
                Then("only that operation is cancelled and the other runs to completion") {
                    checkAll(Exhaustive.enum<DispatchPolicy>()) { firstPolicy ->
                        val scope = CoroutineScope(coroutineContext + Job())
                        val runner = OperationRunner(scope, TestHandle())
                        val tracker = Tracker()

                        val first = runner.dispatch(tracker.op("a"), key, firstPolicy)
                        runner.dispatch(tracker.op("b"), key, DispatchPolicy.CONCURRENT)
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.started shouldContainExactlyInAnyOrder listOf("a", "b")

                        first.cancel()
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.cancelled shouldContainExactly listOf("a")

                        tracker.finish("b")
                        testCoroutineScheduler.advanceUntilIdle()
                        tracker.completed shouldContainExactly listOf("b")
                        scope.cancel()
                    }
                }
            }
        }
    }

    private companion object {
        val key = StandardOpKey("op")
    }
}
