@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.yulimitbreak.aseptic.state

import io.github.yulimitbreak.aseptic.schema.fields.Derived1FieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.Derived2FieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.MutableValueFieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.ReducedFieldDeclaration
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
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

            Then("get() returns initial value") {
                val scope = CoroutineScope(coroutineContext + Job())
                val container = buildContainer {
                    addField("count", false, countDecl)
                }
                container.get<Int>("count") shouldBe 10
                scope.cancel()
            }

            When("field is updated via update()") {
                Then("get() reflects new value") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = buildContainer {
                        addField("count", false, countDecl)
                    }
                    container.update<(Int) -> Int>("count") { it + 5 }
                    container.get<Int>("count") shouldBe 15
                    scope.cancel()
                }

                Then("successive updates are applied in order") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = buildContainer {
                        addField("count", false, countDecl)
                    }
                    container.update<(Int) -> Int>("count") { it + 5 }
                    container.update<(Int) -> Int>("count") { it * 2 }
                    container.get<Int>("count") shouldBe 30
                    scope.cancel()
                }
            }
        }

        Given("a container with a static field") {
            Then("get() returns the static value") {
                val scope = CoroutineScope(coroutineContext + Job())
                val container = buildContainer {
                    addStaticField("step", false, 7)
                }
                container.get<Int>("step") shouldBe 7
                scope.cancel()
            }
        }

        Given("a container with a mutable source and a derived field") {
            val sourceDecl = MutableValueFieldDeclaration(4)
            val squaredDecl = Derived1FieldDeclaration(sourceDecl) { it * it }

            Then("derived field reflects initial source value") {
                val scope = CoroutineScope(coroutineContext + Job())
                val container = buildContainer {
                    addField("source", false, sourceDecl)
                    addField("squared", false, squaredDecl)
                }
                container.get<Int>("squared") shouldBe 16
                scope.cancel()
            }

            When("source field is updated") {
                Then("derived field updates accordingly") {
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

            When("generateSnapshot specifies the derived field in lockRequest") {
                Then("snapshot reflects the correct derived value") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = buildContainer {
                        addField("source", false, sourceDecl)
                        addField("derived", false, squaredDecl)
                    }
                    container.update<(Int) -> Int>("source") { 4 }
                    val snapshot = container.generateSnapshot(setOf("derived")) { map ->
                        map.get<Int>("derived")
                    }
                    snapshot shouldBe 16
                    scope.cancel()
                }
            }
        }

        // Use ReducedFieldDeclaration for updateAtomic tests: update type is the value itself,
        // so map values are plain Ints rather than lambdas.
        Given("a container with two reduced Int fields (initial 0)") {
            val aDecl = ReducedFieldDeclaration(0) { _, update: Int -> update }
            val bDecl = ReducedFieldDeclaration(0) { _, update: Int -> update }

            When("updateAtomic with empty lockRequest writes both fields") {
                Then("both fields reflect new values") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = buildContainer {
                        addField("a", false, aDecl)
                        addField("b", false, bDecl)
                    }
                    container.updateAtomic(emptySet()) { mapOf("a" to 10, "b" to 20) }
                    assertSoftly {
                        container.get<Int>("a") shouldBe 10
                        container.get<Int>("b") shouldBe 20
                    }
                    scope.cancel()
                }
            }

            When("updateAtomic with empty lockRequest returns empty map") {
                Then("fields are unchanged") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = buildContainer {
                        addField("a", false, aDecl)
                        addField("b", false, bDecl)
                    }
                    container.updateAtomic(emptySet()) { emptyMap() }
                    assertSoftly {
                        container.get<Int>("a") shouldBe 0
                        container.get<Int>("b") shouldBe 0
                    }
                    scope.cancel()
                }
            }

            When("updateAtomic with pre-locked fields matching writes") {
                Then("both fields reflect new values") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = buildContainer {
                        addField("a", false, aDecl)
                        addField("b", false, bDecl)
                    }
                    container.updateAtomic(setOf("a", "b")) { mapOf("a" to 100, "b" to 200) }
                    assertSoftly {
                        container.get<Int>("a") shouldBe 100
                        container.get<Int>("b") shouldBe 200
                    }
                    scope.cancel()
                }
            }

            When("updateAtomic pre-locked writes a field not in lockRequest") {
                Then("check fails with IllegalStateException") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = buildContainer {
                        addField("a", false, aDecl)
                        addField("b", false, bDecl)
                    }
                    shouldThrow<IllegalStateException> {
                        container.updateAtomic(setOf("a")) { mapOf("a" to 10, "b" to 20) }
                    }
                    scope.cancel()
                }
            }

            When("two concurrent pre-locked atomics on the same field") {
                Then("both complete and one value wins") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = buildContainer {
                        addField("a", false, aDecl)
                        addField("b", false, bDecl)
                    }
                    launch { container.updateAtomic(setOf("a", "b")) { mapOf("a" to 1, "b" to 1) } }
                    launch { container.updateAtomic(setOf("a", "b")) { mapOf("a" to 2, "b" to 2) } }
                    testCoroutineScheduler.advanceUntilIdle()
                    val a = container.get<Int>("a")
                    val b = container.get<Int>("b")
                    // Whichever wins, a and b must be consistent (same commit)
                    assertSoftly {
                        (a == 1 || a == 2) shouldBe true
                        b shouldBe a
                    }
                    scope.cancel()
                }
            }
        }

        Given("a container with two reduced fields for snapshot tests") {
            val xDecl = ReducedFieldDeclaration(5) { _, update: Int -> update }
            val yDecl = ReducedFieldDeclaration(10) { _, update: Int -> update }

            Then("full snapshot returns all current field values") {
                val scope = CoroutineScope(coroutineContext + Job())
                val container = buildContainer {
                    addField("x", false, xDecl)
                    addField("y", false, yDecl)
                }
                val snapshot = container.generateSnapshot(emptySet()) { map ->
                    map.get<Int>("x") to map.get<Int>("y")
                }
                assertSoftly {
                    snapshot.first shouldBe 5
                    snapshot.second shouldBe 10
                }
                scope.cancel()
            }

            Then("fine-grained snapshot returns specified field value") {
                val scope = CoroutineScope(coroutineContext + Job())
                val container = buildContainer {
                    addField("x", false, xDecl)
                    addField("y", false, yDecl)
                }
                val snapshot = container.generateSnapshot(setOf("x")) { map -> map.get<Int>("x") }
                snapshot shouldBe 5
                scope.cancel()
            }

            When("fields are updated then snapshot taken") {
                Then("snapshot reflects updated values") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = buildContainer {
                        addField("x", false, xDecl)
                        addField("y", false, yDecl)
                    }
                    container.updateAtomic(emptySet()) { mapOf("x" to 99, "y" to 77) }
                    val snapshot = container.generateSnapshot(emptySet()) { map ->
                        map.get<Int>("x") to map.get<Int>("y")
                    }
                    assertSoftly {
                        snapshot.first shouldBe 99
                        snapshot.second shouldBe 77
                    }
                    scope.cancel()
                }
            }
        }

        Given("a container with a single mutable Int field for asFlow tests") {
            val countDecl = MutableValueFieldDeclaration(7)

            Then("asFlow() emits initial value") {
                val scope = CoroutineScope(coroutineContext + Job())
                val container = buildContainer {
                    addField("count", false, countDecl)
                }
                container.asFlow<Int>("count").first() shouldBe 7
                scope.cancel()
            }

            When("field is updated") {
                Then("asFlow() emits updated value") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = buildContainer {
                        addField("count", false, countDecl)
                    }
                    container.update<(Int) -> Int>("count") { it + 3 }
                    container.asFlow<Int>("count").first() shouldBe 10
                    scope.cancel()
                }
            }
        }

        Given("a container with two reduced fields for concurrent update tests") {
            val countDecl = ReducedFieldDeclaration(0) { _, update: Int -> update }

            When("two concurrent update() calls on the same field") {
                Then("both complete without deadlock") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = buildContainer {
                        addField("count", false, countDecl)
                    }
                    launch { container.update("count", 1) }
                    launch { container.update("count", 2) }
                    testCoroutineScheduler.advanceUntilIdle()
                    val result = container.get<Int>("count")
                    (result == 1 || result == 2) shouldBe true
                    scope.cancel()
                }
            }
        }

        Given("a container with two reduced fields for updateAtomic subset-write test") {
            val aDecl = ReducedFieldDeclaration(0) { _, update: Int -> update }
            val bDecl = ReducedFieldDeclaration(0) { _, update: Int -> update }

            When("updateAtomic pre-locked writes only a subset of lockRequest") {
                Then("written field updates, unlocked field unchanged") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = buildContainer {
                        addField("a", false, aDecl)
                        addField("b", false, bDecl)
                    }
                    container.updateAtomic(setOf("a", "b")) { mapOf("a" to 55) }
                    assertSoftly {
                        container.get<Int>("a") shouldBe 55
                        container.get<Int>("b") shouldBe 0
                    }
                    scope.cancel()
                }
            }
        }

        Given("a container with ui and non-ui fields") {
            val visibleDecl = ReducedFieldDeclaration(1) { _, update: Int -> update }
            val hiddenDecl = ReducedFieldDeclaration(100) { _, update: Int -> update }

            Then("uiFlow initial value maps only ui fields") {
                val scope = CoroutineScope(coroutineContext + Job())
                val container = buildContainer {
                    addField("visible", true, visibleDecl)
                    addField("hidden", false, hiddenDecl)
                }
                val uiFlow = container.uiFlow(scope) { map -> map.get<Int>("visible") }
                uiFlow.value shouldBe 1
                scope.cancel()
            }

            When("ui field is updated") {
                Then("uiFlow emits the updated value") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = buildContainer {
                        addField("visible", true, visibleDecl)
                        addField("hidden", false, hiddenDecl)
                    }
                    val uiFlow = container.uiFlow(scope) { map -> map.get<Int>("visible") }
                    container.update("visible", 42)
                    testCoroutineScheduler.advanceUntilIdle()
                    uiFlow.value shouldBe 42
                    scope.cancel()
                }
            }

            When("non-ui field is updated") {
                Then("uiFlow value does not change") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = buildContainer {
                        addField("visible", true, visibleDecl)
                        addField("hidden", false, hiddenDecl)
                    }
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

            Then("uiFlow initial value combines both fields") {
                val scope = CoroutineScope(coroutineContext + Job())
                val container = buildContainer {
                    addField("foo", true, fooDecl)
                    addField("bar", true, barDecl)
                }
                val uiFlow = container.uiFlow(scope) { map ->
                    map.get<Int>("foo") to map.get<Int>("bar")
                }
                assertSoftly {
                    uiFlow.value.first shouldBe 1
                    uiFlow.value.second shouldBe 2
                }
                scope.cancel()
            }

            When("either ui field is updated") {
                Then("uiFlow emits updated combined value") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = buildContainer {
                        addField("foo", true, fooDecl)
                        addField("bar", true, barDecl)
                    }
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

        Given("a container with two mutables and a derived field combining both") {
            val aDecl = ReducedFieldDeclaration(0) { _, u: Int -> u }
            val bDecl = ReducedFieldDeclaration(0) { _, u: Int -> u }
            val derivedDecl = Derived2FieldDeclaration(aDecl, bDecl) { a, b -> a + b }

            When("generateSnapshot specifies the derived field in lockRequest") {
                Then("snapshot reflects the consistent combined value") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = buildContainer {
                        addField("a", false, aDecl)
                        addField("b", false, bDecl)
                        addField("derived", false, derivedDecl)
                    }
                    container.updateAtomic(emptySet()) { mapOf("a" to 3, "b" to 7) }
                    val snapshot = container.generateSnapshot(setOf("derived")) { map ->
                        map.get<Int>("derived")
                    }
                    snapshot shouldBe 10
                    scope.cancel()
                }
            }
        }

        Given("a container with a ui-visible static field") {
            Then("uiFlow initial value includes static field value") {
                val scope = CoroutineScope(coroutineContext + Job())
                val container = buildContainer {
                    addStaticField("label", true, "hello")
                }
                val uiFlow = container.uiFlow(scope) { map -> map.get<String>("label") }
                uiFlow.value shouldBe "hello"
                scope.cancel()
            }
        }
    }
}
