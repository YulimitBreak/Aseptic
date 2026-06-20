@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.state

import io.github.yulimitbreak.aseptic.schema.fields.MutableValueFieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.ReducedFieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.TrackingFieldDeclaration
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

class StateContainerRealConcurrencyTest : BehaviorSpec() {

    private fun buildContainer(build: StateContainerBuilder.() -> Unit): StateContainer =
        StateContainerBuilder().apply(build).build()

    init {
        Given("a container with a single mutable Int field") {
            When("100 coroutines each increment the field by 1 in parallel") {
                Then("all 100 increments are applied — none are lost to races") {
                    withContext(Dispatchers.Default) {
                        val container = buildContainer {
                            addField("count", false, MutableValueFieldDeclaration(0))
                        }
                        (1..100).map {
                            launch { container.update<(Int) -> Int>("count") { it + 1 } }
                        }.joinAll()
                        container.get<Int>("count") shouldBe 100
                    }
                }
            }
        }

        Given("a container with two reduced fields written atomically") {
            val aDecl = ReducedFieldDeclaration(0) { _, u: Int -> u }
            val bDecl = ReducedFieldDeclaration(0) { _, u: Int -> u }

            fun container() = buildContainer {
                addField("a", false, aDecl)
                addField("b", false, bDecl)
            }

            When("atomic writers and snapshot readers run in parallel") {
                Then("no snapshot ever observes a partial atomic write") {
                    withContext(Dispatchers.Default) {
                        val errors = AtomicInteger(0)
                        val container = container()
                        val writers = (1..10).map { i ->
                            launch {
                                repeat(100) {
                                    container.updateAtomic(setOf("a", "b")) {
                                        mapOf("a" to listOf(i), "b" to listOf(i))
                                    }
                                }
                            }
                        }
                        val readers = (1..10).map {
                            launch {
                                repeat(200) {
                                    container.generateSnapshot { map ->
                                        if (map.get<Int>("a") != map.get<Int>("b")) errors.incrementAndGet()
                                    }
                                }
                            }
                        }
                        (writers + readers).joinAll()
                        withClue({ "generateSnapshot received ${errors.get()} snapshots with partial atomic writes" }) {
                            errors.get() shouldBe 0
                        }
                    }
                }
            }
        }

        // TODO
        xGiven("a container with two ui-visible fields written atomically") {
            val fooDecl = ReducedFieldDeclaration(0) { _, u: Int -> u }
            val barDecl = ReducedFieldDeclaration(0) { _, u: Int -> u }

            fun container() = buildContainer {
                addField("foo", true, fooDecl)
                addField("bar", true, barDecl)
            }

            When("atomic writers and a flow collector run on parallel threads") {
                Then("the ui flow never emits a state where only one field is updated") {
                    withContext(Dispatchers.Default) {
                        val errors = AtomicInteger(0)
                        val scope = CoroutineScope(Dispatchers.Default + Job())
                        val container = container()
                        val uiFlow = container.uiFlow(scope) { map ->
                            map.get<Int>("foo") to map.get<Int>("bar")
                        }
                        val collector = launch {
                            uiFlow.collect { (f, b) -> if (f != b) errors.incrementAndGet() }
                        }
                        (1..10).map { i ->
                            launch {
                                repeat(100) {
                                    container.updateAtomic(setOf("foo", "bar")) {
                                        mapOf("foo" to listOf(i), "bar" to listOf(i))
                                    }
                                }
                            }
                        }.joinAll()
                        delay(100.milliseconds)
                        collector.cancel()
                        scope.cancel()
                        withClue({ "uiFlow emitted ${errors.get()} states with partial atomic writes" }) {
                            errors.get() shouldBe 0
                        }
                    }
                }
            }
        }

        // TODO
        xGiven("a container with mixed field types under concurrent access") {
            val sourceDecl = MutableValueFieldDeclaration(0)
            val trackedDecl = TrackingFieldDeclaration(
                ReducedFieldDeclaration(0) { acc, _: Int -> acc + 1 },
                TrackingFieldDeclaration.Link(sourceDecl) { it },
            )
            val aDecl = ReducedFieldDeclaration(0) { _, u: Int -> u }
            val bDecl = ReducedFieldDeclaration(0) { _, u: Int -> u }
            val uiFooDecl = ReducedFieldDeclaration(0) { _, u: Int -> u }
            val uiBarDecl = ReducedFieldDeclaration(0) { _, u: Int -> u }

            When(
                "direct updates, pre-locked atomics, ui atomics, snapshots, and flow collection all run concurrently"
            ) {
                Then("no partial state is observed and all tracking updates are propagated") {
                    withContext(Dispatchers.Default) {
                        val snapshotErrors = AtomicInteger(0)
                        val flowErrors = AtomicInteger(0)
                        val scope = CoroutineScope(Dispatchers.Default + Job())

                        val container = buildContainer {
                            addField("source", false, sourceDecl)
                            addField("tracked", false, trackedDecl)
                            addField("a", false, aDecl)
                            addField("b", false, bDecl)
                            addField("ui_foo", true, uiFooDecl)
                            addField("ui_bar", true, uiBarDecl)
                        }

                        val uiFlow = container.uiFlow(scope) { map ->
                            map.get<Int>("ui_foo") to map.get<Int>("ui_bar")
                        }
                        val collector = launch {
                            uiFlow.collect { (f, b) -> if (f != b) flowErrors.incrementAndGet() }
                        }

                        // direct writes to source — each triggers one tracking callback
                        val sourceWriters = (1..5).map { i ->
                            launch { repeat(50) { container.update<(Int) -> Int>("source") { i } } }
                        }
                        // pre-locked atomic writes on {a, b} together
                        val atomicWriters = (1..5).map { i ->
                            launch {
                                repeat(50) {
                                    container.updateAtomic(setOf("a", "b")) {
                                        mapOf("a" to listOf(i), "b" to listOf(i))
                                    }
                                }
                            }
                        }
                        // ui atomic writes on {ui_foo, ui_bar} together
                        val uiWriters = (1..5).map { i ->
                            launch {
                                repeat(50) {
                                    container.updateAtomic(setOf("ui_foo", "ui_bar")) {
                                        mapOf("ui_foo" to listOf(i), "ui_bar" to listOf(i))
                                    }
                                }
                            }
                        }
                        // fine-grained snapshot readers on {a, b}
                        val readers = (1..5).map {
                            launch {
                                repeat(100) {
                                    container.generateSnapshot(setOf("a", "b")) { map ->
                                        if (map.get<Int>("a") != map.get<Int>("b")) snapshotErrors.incrementAndGet()
                                    }
                                }
                            }
                        }

                        (sourceWriters + atomicWriters + uiWriters + readers).joinAll()
                        delay(100.milliseconds)
                        collector.cancel()
                        scope.cancel()

                        assertSoftly {
                            withClue({ "uiFlow emitted ${flowErrors.get()} states with partial atomic writes" }) {
                                flowErrors.get() shouldBe 0
                            }
                            withClue(
                                { "generateSnapshot read ${snapshotErrors.get()} snapshots with partial atomic writes" }
                            ) {
                                snapshotErrors.get() shouldBe 0
                            }
                            withClue(
                                {
                                    container.get<Int>("tracked").toString() +
                                        " tracked callbacks were triggered instead of ${5 * 50}"
                                }
                            ) {
                                container.get<Int>("tracked") shouldBe 250
                            }
                        }
                    }
                }
            }
        }
    }
}
