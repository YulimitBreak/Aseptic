@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.yulimitbreak.aseptic.state

import io.github.yulimitbreak.aseptic.schema.fields.Derived1FieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.Derived2FieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.MutableValueFieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.ReducedFieldDeclaration
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class StateContainerTest : BehaviorSpec() {

    private fun buildContainer(
        build: StateContainerBuilder.() -> Unit,
    ): StateContainer = StateContainerBuilder().apply(build).build()

    init {
        coroutineTestScope = true

        Given("a container with a single mutable Int field") {
            val countDecl = MutableValueFieldDeclaration(10)

            Then("get() returns the initial value") {
                val scope = CoroutineScope(coroutineContext + Job())
                val container = buildContainer { addField("count", false, countDecl) }
                container.get<Int>("count") shouldBe 10
                scope.cancel()
            }

            When("the field is updated") {
                Then("get() reflects the new value") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = buildContainer { addField("count", false, countDecl) }
                    container.update<(Int) -> Int>("count") { it + 5 }
                    container.get<Int>("count") shouldBe 15
                    scope.cancel()
                }

                Then("successive updates are applied in order") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = buildContainer { addField("count", false, countDecl) }
                    container.update<(Int) -> Int>("count") { it + 5 }
                    container.update<(Int) -> Int>("count") { it * 2 }
                    container.get<Int>("count") shouldBe 30
                    scope.cancel()
                }
            }

            When("multiple concurrent updates each transform the field based on its current value") {
                Then("all transforms are applied — none are lost to races") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = buildContainer { addField("count", false, countDecl) }
                    repeat(5) { launch { container.update<(Int) -> Int>("count") { it + 1 } } }
                    testCoroutineScheduler.advanceUntilIdle()
                    container.get<Int>("count") shouldBe 15
                    scope.cancel()
                }
            }

            When("the field's flow is collected") {
                Then("it emits the initial value") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = buildContainer { addField("count", false, countDecl) }
                    container.asFlow<Int>("count").first() shouldBe 10
                    scope.cancel()
                }
            }

            When("the field is updated and its flow is collected") {
                Then("the flow emits the updated value") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = buildContainer { addField("count", false, countDecl) }
                    container.update<(Int) -> Int>("count") { it + 3 }
                    container.asFlow<Int>("count").first() shouldBe 13
                    scope.cancel()
                }
            }
        }

        Given("a container with a static field") {
            Then("get() returns the static value") {
                val scope = CoroutineScope(coroutineContext + Job())
                val container = buildContainer { addStaticField("step", false, 7) }
                container.get<Int>("step") shouldBe 7
                scope.cancel()
            }
        }

        Given("a container with a mutable source and a derived field") {
            val sourceDecl = MutableValueFieldDeclaration(4)
            val squaredDecl = Derived1FieldDeclaration(sourceDecl) { it * it }

            Then("the derived field reflects the initial source value") {
                val scope = CoroutineScope(coroutineContext + Job())
                val container = buildContainer {
                    addField("source", false, sourceDecl)
                    addField("squared", false, squaredDecl)
                }
                container.get<Int>("squared") shouldBe 16
                scope.cancel()
            }

            When("the source field is updated") {
                Then("the derived field updates accordingly") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = buildContainer {
                        addField("source", false, sourceDecl)
                        addField("squared", false, squaredDecl)
                    }
                    container.update<(Int) -> Int>("source") { 5 }
                    testCoroutineScheduler.advanceUntilIdle()
                    container.get<Int>("squared") shouldBe 25
                    scope.cancel()
                }
            }

            When("a fine-grained snapshot specifies the derived field in the lock request") {
                Then("the snapshot reflects the correct derived value") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = buildContainer {
                        addField("source", false, sourceDecl)
                        addField("squared", false, squaredDecl)
                    }
                    val snapshot = container.generateSnapshot(setOf("squared")) { map ->
                        map.get<Int>("squared")
                    }
                    snapshot shouldBe 16
                    scope.cancel()
                }
            }
        }

        // ReducedFieldDeclaration is used so update values are plain Ints (wrapped in listOf())
        // rather than transform lambdas, which keeps the atomic-write maps readable.
        Given("a container with two reduced Int fields") {
            val aDecl = ReducedFieldDeclaration(5) { _, update: Int -> update }
            val bDecl = ReducedFieldDeclaration(10) { _, update: Int -> update }

            fun container() = buildContainer {
                addField("a", false, aDecl)
                addField("b", false, bDecl)
            }

            When("updateAtomic deferred-commits both fields") {
                Then("both fields reflect the new values") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = container()
                    container.updateAtomic { mapOf("a" to listOf(100), "b" to listOf(200)) }
                    assertSoftly {
                        container.get<Int>("a") shouldBe 100
                        container.get<Int>("b") shouldBe 200
                    }
                    scope.cancel()
                }
            }

            When("updateAtomic deferred-commits an empty map") {
                Then("fields are unchanged") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = container()
                    container.updateAtomic { emptyMap() }
                    assertSoftly {
                        container.get<Int>("a") shouldBe 5
                        container.get<Int>("b") shouldBe 10
                    }
                    scope.cancel()
                }
            }

            When("updateAtomic pre-locks the fields it writes") {
                Then("both fields reflect the new values") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = container()
                    container.updateAtomic(setOf("a", "b")) { mapOf("a" to listOf(100), "b" to listOf(200)) }
                    assertSoftly {
                        container.get<Int>("a") shouldBe 100
                        container.get<Int>("b") shouldBe 200
                    }
                    scope.cancel()
                }
            }

            When("updateAtomic pre-locks fewer fields than it writes") {
                Then("it fails with IllegalStateException") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = container()
                    shouldThrow<IllegalStateException> {
                        container.updateAtomic(setOf("a")) { mapOf("a" to listOf(10), "b" to listOf(20)) }
                    }
                    scope.cancel()
                }
            }

            When("updateAtomic pre-locks both fields but writes only one") {
                Then("the written field updates and the other stays unchanged") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = container()
                    container.updateAtomic(setOf("a", "b")) { mapOf("a" to listOf(55)) }
                    assertSoftly {
                        container.get<Int>("a") shouldBe 55
                        container.get<Int>("b") shouldBe 10
                    }
                    scope.cancel()
                }
            }

            When("two pre-locked atomics run concurrently on the same fields") {
                Then("both complete and commit consistently") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = container()
                    launch { container.updateAtomic(setOf("a", "b")) { mapOf("a" to listOf(1), "b" to listOf(1)) } }
                    launch { container.updateAtomic(setOf("a", "b")) { mapOf("a" to listOf(2), "b" to listOf(2)) } }
                    testCoroutineScheduler.advanceUntilIdle()
                    val a = container.get<Int>("a")
                    val b = container.get<Int>("b")
                    assertSoftly {
                        withClue("field has value $a instead of one of committed values") {
                            (a == 1 || a == 2) shouldBe true
                        }
                        withClue("two fields don't have consistent values") {
                            b shouldBe a
                        }
                    }
                    scope.cancel()
                }
            }

            When("two update() calls run concurrently on the same field") {
                Then("both complete without deadlock and one value wins") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = container()
                    launch { container.update("a", 1) }
                    launch { container.update("a", 2) }
                    testCoroutineScheduler.advanceUntilIdle()
                    val result = container.get<Int>("a")
                    (result == 1 || result == 2) shouldBe true
                    scope.cancel()
                }
            }

            When("two deferred atomics run concurrently on the same fields") {
                Then("both complete and the final state is consistent across fields") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = container()
                    launch { container.updateAtomic { mapOf("a" to listOf(1), "b" to listOf(1)) } }
                    launch { container.updateAtomic { mapOf("a" to listOf(2), "b" to listOf(2)) } }
                    testCoroutineScheduler.advanceUntilIdle()
                    container.get<Int>("a") shouldBe container.get<Int>("b")
                    scope.cancel()
                }
            }

            When("a direct update() and a deferred atomic run concurrently on the same field") {
                Then("both complete without deadlock and the field holds one of the two written values") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = container()
                    launch { container.update("a", 99) }
                    launch { container.updateAtomic { mapOf("a" to listOf(77)) } }
                    testCoroutineScheduler.advanceUntilIdle()
                    val result = container.get<Int>("a")
                    (result == 99 || result == 77) shouldBe true
                    scope.cancel()
                }
            }

            When("a full snapshot is taken after two concurrent deferred atomics") {
                Then("the snapshot sees a consistent state across both fields") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = container()
                    launch { container.updateAtomic { mapOf("a" to listOf(1), "b" to listOf(1)) } }
                    launch { container.updateAtomic { mapOf("a" to listOf(2), "b" to listOf(2)) } }
                    testCoroutineScheduler.advanceUntilIdle()
                    val snapshot = container.generateSnapshot { map -> map.get<Int>("a") to map.get<Int>("b") }
                    snapshot.first shouldBe snapshot.second
                    scope.cancel()
                }
            }

            When("a pre-locked atomic and a fine-grained snapshot compete for the same field locks") {
                Then("both complete without deadlock and the snapshot sees a consistent state") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = container()
                    var snapshot = 0 to 0
                    launch { container.updateAtomic(setOf("a", "b")) { mapOf("a" to listOf(100), "b" to listOf(200)) } }
                    launch {
                        snapshot = container.generateSnapshot(setOf("a", "b")) { map ->
                            map.get<Int>("a") to map.get<Int>("b")
                        }
                    }
                    testCoroutineScheduler.advanceUntilIdle()
                    val (a, b) = snapshot
                    ((a == 5 && b == 10) || (a == 100 && b == 200)) shouldBe true
                    scope.cancel()
                }
            }

            When("a full snapshot is taken") {
                Then("it returns all current field values") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = container()
                    val snapshot = container.generateSnapshot { map ->
                        map.get<Int>("a") to map.get<Int>("b")
                    }
                    assertSoftly {
                        snapshot.first shouldBe 5
                        snapshot.second shouldBe 10
                    }
                    scope.cancel()
                }
            }

            When("a fine-grained snapshot is taken for a single field") {
                Then("it returns that field's value") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = container()
                    val snapshot = container.generateSnapshot(setOf("a")) { map -> map.get<Int>("a") }
                    snapshot shouldBe 5
                    scope.cancel()
                }
            }

            When("fields are updated then a full snapshot is taken") {
                Then("the snapshot reflects the updated values") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = container()
                    container.updateAtomic { mapOf("a" to listOf(99), "b" to listOf(77)) }
                    val snapshot = container.generateSnapshot { map ->
                        map.get<Int>("a") to map.get<Int>("b")
                    }
                    assertSoftly {
                        snapshot.first shouldBe 99
                        snapshot.second shouldBe 77
                    }
                    scope.cancel()
                }
            }

            When("frozenSnapshotMap is called and fields are later updated") {
                Then("the frozen map is unaffected by the later updates") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = container()
                    val frozen = container.frozenSnapshotMap()
                    container.updateAtomic { mapOf("a" to listOf(99), "b" to listOf(88)) }
                    assertSoftly {
                        frozen.get<Int>("a") shouldBe 5
                        frozen.get<Int>("b") shouldBe 10
                    }
                    scope.cancel()
                }
            }

            When("a full snapshot and a fine-grained snapshot run concurrently") {
                Then("both complete without deadlock and return correct values") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = container()
                    var fullSnap = 0 to 0
                    var fineSnap = 0
                    launch { fullSnap = container.generateSnapshot { map -> map.get<Int>("a") to map.get<Int>("b") } }
                    launch { fineSnap = container.generateSnapshot(setOf("a")) { map -> map.get<Int>("a") } }
                    testCoroutineScheduler.advanceUntilIdle()
                    assertSoftly {
                        fullSnap shouldBe (5 to 10)
                        fineSnap shouldBe 5
                    }
                    scope.cancel()
                }
            }
        }

        Given("a container with two mutables and a derived field combining both") {
            val aDecl = ReducedFieldDeclaration(0) { _, u: Int -> u }
            val bDecl = ReducedFieldDeclaration(0) { _, u: Int -> u }
            val derivedDecl = Derived2FieldDeclaration(aDecl, bDecl) { a, b -> a + b }

            When("a fine-grained snapshot specifies the derived field in the lock request") {
                Then("the snapshot reflects the consistent combined value") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = buildContainer {
                        addField("a", false, aDecl)
                        addField("b", false, bDecl)
                        addField("derived", false, derivedDecl)
                    }
                    container.updateAtomic { mapOf("a" to listOf(3), "b" to listOf(7)) }
                    val snapshot = container.generateSnapshot(setOf("derived")) { map ->
                        map.get<Int>("derived")
                    }
                    snapshot shouldBe 10
                    scope.cancel()
                }
            }
        }

        Given("a container with ui and non-ui fields") {
            val visibleDecl = ReducedFieldDeclaration(1) { _, update: Int -> update }
            val hiddenDecl = ReducedFieldDeclaration(100) { _, update: Int -> update }

            fun container() = buildContainer {
                addField("visible", true, visibleDecl)
                addField("hidden", false, hiddenDecl)
            }

            Then("the ui flow's initial value maps only ui fields") {
                val scope = CoroutineScope(coroutineContext + Job())
                val uiFlow = container().uiFlow(scope) { map -> map.get<Int>("visible") }
                uiFlow.value shouldBe 1
                scope.cancel()
            }

            When("a ui field is updated") {
                Then("the ui flow emits the updated value") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = container()
                    val uiFlow = container.uiFlow(scope) { map -> map.get<Int>("visible") }
                    container.update("visible", 42)
                    testCoroutineScheduler.advanceUntilIdle()
                    uiFlow.value shouldBe 42
                    scope.cancel()
                }
            }

            When("a non-ui field is updated") {
                Then("the ui flow value does not change") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = container()
                    val uiFlow = container.uiFlow(scope) { map -> map.get<Int>("visible") }
                    val before = uiFlow.value
                    container.update("hidden", 999)
                    testCoroutineScheduler.advanceUntilIdle()
                    uiFlow.value shouldBe before
                    scope.cancel()
                }
            }
        }

        Given("a container with two ui fields") {
            val fooDecl = ReducedFieldDeclaration(1) { _, update: Int -> update }
            val barDecl = ReducedFieldDeclaration(2) { _, update: Int -> update }

            fun container() = buildContainer {
                addField("foo", true, fooDecl)
                addField("bar", true, barDecl)
            }

            Then("the ui flow's initial value combines both fields") {
                val scope = CoroutineScope(coroutineContext + Job())
                val uiFlow = container().uiFlow(scope) { map ->
                    map.get<Int>("foo") to map.get<Int>("bar")
                }
                assertSoftly {
                    uiFlow.value.first shouldBe 1
                    uiFlow.value.second shouldBe 2
                }
                scope.cancel()
            }

            When("either ui field is updated") {
                Then("the ui flow emits the updated combined value") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = container()
                    val uiFlow = container.uiFlow(scope) { map ->
                        map.get<Int>("foo") to map.get<Int>("bar")
                    }
                    container.update("foo", 10)
                    testCoroutineScheduler.advanceUntilIdle()
                    assertSoftly {
                        uiFlow.value.first shouldBe 10
                        uiFlow.value.second shouldBe 2
                    }
                    container.update("bar", 20)
                    testCoroutineScheduler.advanceUntilIdle()
                    assertSoftly {
                        uiFlow.value.first shouldBe 10
                        uiFlow.value.second shouldBe 20
                    }
                    scope.cancel()
                }
            }
        }

        Given("a container with a ui-visible static field") {
            Then("the ui flow's initial value includes the static field value") {
                val scope = CoroutineScope(coroutineContext + Job())
                val container = buildContainer { addStaticField("label", true, "hello") }
                val uiFlow = container.uiFlow(scope) { map -> map.get<String>("label") }
                uiFlow.value shouldBe "hello"
                scope.cancel()
            }
        }

        Given("a container with a reduced field using an accumulating reducer") {
            val accumDecl = ReducedFieldDeclaration(0) { prev, delta: Int -> prev + delta }

            fun container() = buildContainer { addField("total", false, accumDecl) }

            When("multiple updates are applied sequentially") {
                Then("each update receives the accumulated result of all previous updates") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = container()
                    container.update("total", 5)
                    container.update("total", 3)
                    container.update("total", 2)
                    container.get<Int>("total") shouldBe 10
                    scope.cancel()
                }
            }

            When("multiple concurrent updates each add to the accumulated total") {
                Then("all deltas are applied — none are lost to races") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = container()
                    repeat(4) { launch { container.update("total", 10) } }
                    testCoroutineScheduler.advanceUntilIdle()
                    container.get<Int>("total") shouldBe 40
                    scope.cancel()
                }
            }
        }

        Given("a container with two ui-visible reduced fields updated atomically") {
            val fooDecl = ReducedFieldDeclaration(0) { _, u: Int -> u }
            val barDecl = ReducedFieldDeclaration(0) { _, u: Int -> u }

            fun container() = buildContainer {
                addField("foo", true, fooDecl)
                addField("bar", true, barDecl)
            }

            When("multiple atomics concurrently write both fields to matching values") {
                Then("the ui flow never emits a state where only one field is updated") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = container()
                    val uiFlow = container.uiFlow(scope) { map -> map.get<Int>("foo") to map.get<Int>("bar") }
                    val emissions = mutableListOf<Pair<Int, Int>>()
                    val collectJob = launch { uiFlow.collect { emissions.add(it) } }
                    testCoroutineScheduler.advanceUntilIdle()
                    repeat(5) { i ->
                        launch {
                            container.updateAtomic(
                                setOf("foo", "bar")
                            ) { mapOf("foo" to listOf(i + 1), "bar" to listOf(i + 1)) }
                        }
                    }
                    testCoroutineScheduler.advanceUntilIdle()
                    collectJob.cancel()
                    val badEmissions = emissions.filter { (f, b) -> f != b }
                    badEmissions.isEmpty() shouldBe true
                    scope.cancel()
                }
            }
        }
    }
}
