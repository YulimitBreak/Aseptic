@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.yulimitbreak.aseptic.state

import io.github.yulimitbreak.aseptic.util.UncheckedMap
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class SnapshotFlowBuilderTest : BehaviorSpec() {

    init {
        coroutineTestScope = true

        Given("a builder with a single source") {
            When("the flow is collected") {
                Then("it emits a snapshot with the source value") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val a = MutableStateFlow(1)
                    val builder = SnapshotFlowBuilder().apply { addSource("a", a) }
                    val emissions = scope.record(builder.build())
                    testCoroutineScheduler.advanceUntilIdle()
                    emissions.last().get<Int>("a") shouldBe 1
                    scope.cancel()
                }
            }

            When("the source updates") {
                Then("a new snapshot is emitted with the updated value") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val a = MutableStateFlow(1)
                    val builder = SnapshotFlowBuilder().apply { addSource("a", a) }
                    val emissions = scope.record(builder.build())
                    a.value = 9
                    testCoroutineScheduler.advanceUntilIdle()
                    emissions.last().get<Int>("a") shouldBe 9
                    scope.cancel()
                }
            }
        }

        Given("a builder with a source and a derived mapper") {
            When("the flow is collected") {
                Then("the snapshot contains both the source and the computed value") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val a = MutableStateFlow(3)
                    val builder = SnapshotFlowBuilder().apply {
                        addSource("a", a)
                        addMapper("doubled") { it.get<Int>("a") * 2 }
                    }
                    val emissions = scope.record(builder.build())
                    testCoroutineScheduler.advanceUntilIdle()
                    emissions.last().let {
                        it.get<Int>("a") shouldBe 3
                        it.get<Int>("doubled") shouldBe 6
                    }
                    scope.cancel()
                }
            }

            When("the source updates") {
                Then("the derived value is recomputed") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val a = MutableStateFlow(3)
                    val builder = SnapshotFlowBuilder().apply {
                        addSource("a", a)
                        addMapper("doubled") { it.get<Int>("a") * 2 }
                    }
                    val emissions = scope.record(builder.build())
                    a.value = 10
                    testCoroutineScheduler.advanceUntilIdle()
                    emissions.last().get<Int>("doubled") shouldBe 20
                    scope.cancel()
                }
            }
        }

        Given("a builder where a mapper depends on an earlier mapper") {
            When("the flow is collected") {
                Then("mappers compute in registration order, reading prior results") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val a = MutableStateFlow(2)
                    val builder = SnapshotFlowBuilder().apply {
                        addSource("a", a)
                        addMapper("doubled") { it.get<Int>("a") * 2 }
                        addMapper("plusOne") { it.get<Int>("doubled") + 1 }
                    }
                    val emissions = scope.record(builder.build())
                    testCoroutineScheduler.advanceUntilIdle()
                    emissions.last().get<Int>("plusOne") shouldBe 5
                    scope.cancel()
                }
            }
        }

        Given("a builder with two sources combined by a mapper") {
            When("either source updates") {
                Then("the snapshot reflects the latest of both") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val a = MutableStateFlow(1)
                    val b = MutableStateFlow(2)
                    val builder = SnapshotFlowBuilder().apply {
                        addSource("a", a)
                        addSource("b", b)
                        addMapper("sum") { it.get<Int>("a") + it.get<Int>("b") }
                    }
                    val emissions = scope.record(builder.build())
                    a.value = 10
                    b.value = 20
                    testCoroutineScheduler.advanceUntilIdle()
                    emissions.last().get<Int>("sum") shouldBe 30
                    scope.cancel()
                }
            }
        }

        Given("a builder with a control gate") {
            When("the gate is closed") {
                Then("source updates do not produce emissions until reopened") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val a = MutableStateFlow(1)
                    val gate = MutableStateFlow(true)
                    val builder = SnapshotFlowBuilder().apply { addSource("a", a) }
                    val emissions = scope.record(builder.build(gate))
                    testCoroutineScheduler.advanceUntilIdle()
                    val countAfterOpen = emissions.size

                    gate.value = false
                    a.value = 2
                    a.value = 3
                    testCoroutineScheduler.advanceUntilIdle()
                    emissions.size shouldBe countAfterOpen

                    gate.value = true
                    testCoroutineScheduler.advanceUntilIdle()
                    emissions.last().get<Int>("a") shouldBe 3
                    scope.cancel()
                }
            }
        }

        Given("a builder with a duplicate mapper name") {
            When("the flow is collected") {
                Then("only the first registered mapper is used") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val builder = SnapshotFlowBuilder().apply {
                        addSource("a", MutableStateFlow(0))
                        addMapper("x") { 1 }
                        addMapper("x") { 2 }
                    }
                    val emissions = scope.record(builder.build())
                    testCoroutineScheduler.advanceUntilIdle()
                    emissions.last().get<Int>("x") shouldBe 1
                    scope.cancel()
                }
            }
        }

        Given("a builder where a mapper name collides with a source name") {
            When("the flow is collected") {
                Then("the source value wins and the mapper is skipped") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val builder = SnapshotFlowBuilder().apply {
                        addSource("a", MutableStateFlow(7))
                        addMapper("a") { 999 }
                    }
                    val emissions = scope.record(builder.build())
                    testCoroutineScheduler.advanceUntilIdle()
                    emissions.last().get<Int>("a") shouldBe 7
                    scope.cancel()
                }
            }
        }

        Given("a builder with only a static mapper and no sources") {
            When("the flow is collected") {
                Then("it still emits a snapshot with the static value") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val builder = SnapshotFlowBuilder().apply {
                        addMapper("s") { 42 }
                    }
                    val emissions = scope.record(builder.build())
                    testCoroutineScheduler.advanceUntilIdle()
                    emissions.last().get<Int>("s") shouldBe 42
                    scope.cancel()
                }
            }
        }
    }

    private fun CoroutineScope.record(flow: Flow<UncheckedMap<String>>): List<UncheckedMap<String>> {
        val emissions = mutableListOf<UncheckedMap<String>>()
        launch { flow.collect { emissions.add(it) } }
        return emissions
    }
}
