@file:OptIn(AsepticInternal::class)

package io.github.yulimitbreak.aseptic.state

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.schema.fields.Derived1FieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.Derived2FieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.Derived3FieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.DerivedNFieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.FieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.withTryLock
import io.github.yulimitbreak.aseptic.schema.fields.MutableValueFieldDeclaration
import io.github.yulimitbreak.aseptic.util.UncheckedMap
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.choose
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.subsequence
import io.kotest.property.checkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class SnapshotFlowBuilderGraphTest : BehaviorSpec() {

    init {
        coroutineTestScope = true

        Given("an arbitrary field graph") {
            When("snapshot flow is built for all derived fields") {
                Then("each snapshot entry matches its pull-based value before and after mutations") {

                    checkAll(graphArb().withSubset()) { (graph, selection) ->
                        val scope = CoroutineScope(coroutineContext + Job())
                        val builder = SnapshotFlowBuilder()
                        selection.forEach { it.buildFlow(builder) }
                        val emissions = scope.record(builder.build())
                        testCoroutineScheduler.advanceUntilIdle()

                        selection.forEach { field ->
                            emissions.last().get<Int>(field.name) shouldBe field.getter()
                        }

                        graph.sources.forEach { it.setter(it.getter() + 7) }
                        testCoroutineScheduler.advanceUntilIdle()

                        selection.forEach { field ->
                            emissions.last().get<Int>(field.name) shouldBe field.getter()
                        }
                        scope.cancel()
                    }
                }
            }

            When("a source outside the dependency closure is updated") {
                Then("no snapshot is emitted, while an in-closure update still emits") {
                    checkAll(graphArb().withSubset()) { (graph, selection) ->

                        val relevant = selection.flatMapTo(mutableSetOf()) {
                            when (it) {
                                is Field.Source -> setOf(it)
                                is Field.Mapping -> it.dependsOn
                            }
                        }
                        val irrelevant = graph.sources - relevant

                        val scope = CoroutineScope(coroutineContext + Job())
                        val builder = SnapshotFlowBuilder()
                        selection.forEach { it.buildFlow(builder) }
                        val emissions = scope.record(builder.build())
                        testCoroutineScheduler.advanceUntilIdle()
                        val baseCount = emissions.size

                        irrelevant.forEach { it.setter(it.getter() + 1) }
                        testCoroutineScheduler.advanceUntilIdle()
                        emissions.size shouldBe baseCount

                        relevant.first().setter(relevant.first().getter() + 1)
                        testCoroutineScheduler.advanceUntilIdle()
                        (emissions.size > baseCount) shouldBe true
                        scope.cancel()
                    }
                }
            }
        }
    }

    private sealed interface Field {

        val name: String
        val getter: () -> Int
        val buildFlow: (SnapshotFlowBuilder) -> Unit

        class Source(
            override val name: String,
            override val getter: () -> Int,
            override val buildFlow: (SnapshotFlowBuilder) -> Unit,
            val setter: (Int) -> Unit,
        ) : Field

        class Mapping(
            override val name: String,
            override val getter: () -> Int,
            override val buildFlow: (SnapshotFlowBuilder) -> Unit,
            val dependsOn: Set<Source>,
        ) : Field
    }

    private class Graph(
        val sources: List<Field.Source>,
        mappers: List<Field.Mapping>,
    ) {
        val full = sources + mappers
    }

    private fun graphArb() = arbitrary {
        val count = Arb.int(1..10).bind()

        val declarations = mutableListOf<FieldDeclaration<Int>>()
        var sourceCount = 0

        repeat(count) { index ->
            val createSource = when {
                sourceCount == 0 -> true
                sourceCount >= count / 2 -> false
                else -> Arb.choose(1 to true, 2 to false).bind()
            }

            if (createSource) {
                declarations += MutableValueFieldDeclaration(index)
                sourceCount += 1
            } else {
                val deps = Arb.subsequence(declarations).filter { it.size in 1..5 }.bind()
                declarations += derivedDeclaration(deps)
            }
        }

        val fieldMap = StateContainerBuilder.FieldMap()
        val sources = mutableListOf<Field.Source>()
        val mappers = mutableListOf<Field.Mapping>()

        declarations.forEachIndexed { index, decl ->
            if (decl is MutableValueFieldDeclaration) {
                val state = decl.convert("F$index[s]", fieldMap)
                sources += Field.Source(
                    name = state.name,
                    getter = { state.value },
                    setter = { value -> state.withTryLock { update { value } } },
                    buildFlow = { builder -> state.buildSnapshotFlow(builder) },
                )
                fieldMap[decl] = state
            } else {
                val state = decl.convert("F$index[d]", fieldMap)
                mappers += Field.Mapping(
                    name = state.name,
                    getter = { state.value },
                    buildFlow = { builder -> state.buildSnapshotFlow(builder) },
                    dependsOn = state.getUpdateSources().mapTo(mutableSetOf()) { source ->
                        sources.find { it.name == source.name }!!
                    },
                )
                fieldMap[decl] = state
            }
        }

        Graph(sources, mappers)
    }

    private fun Arb<Graph>.withSubset(): Arb<Pair<Graph, Set<Field>>> = this.flatMap { graph ->
        Arb.subsequence(graph.full).filter {
            it.size in 1..(graph.full.size / 2).coerceAtLeast(1)
        }
            .map { graph to it.toSet() }
    }

    private fun derivedDeclaration(
        sources: List<FieldDeclaration<Int>>
    ): FieldDeclaration<Int> {
        fun combine(numbers: List<Int>) = numbers.mapIndexed { index, num -> num * (index + 1) }.sum()
        return when (sources.size) {
            0 -> throw IllegalArgumentException("no sources specified")
            1 -> Derived1FieldDeclaration(sources[0]) { combine(listOf(it)) }
            2 -> Derived2FieldDeclaration(sources[0], sources[1]) { s1, s2 -> combine(listOf(s1, s2)) }
            3 -> Derived3FieldDeclaration(sources[0], sources[1], sources[2]) { s1, s2, s3 ->
                combine(listOf(s1, s2, s3))
            }

            else -> DerivedNFieldDeclaration(sources) { combine(it) }
        }
    }

    private fun CoroutineScope.record(flow: Flow<UncheckedMap<String>>): List<UncheckedMap<String>> {
        val emissions = mutableListOf<UncheckedMap<String>>()
        launch { flow.collect { emissions.add(it) } }
        return emissions
    }
}
