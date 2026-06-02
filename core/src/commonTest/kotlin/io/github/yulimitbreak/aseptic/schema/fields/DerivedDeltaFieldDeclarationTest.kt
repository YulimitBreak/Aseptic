@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.buildState
import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.locked
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class DerivedDeltaFieldDeclarationTest : BehaviorSpec() {

    init {
        coroutineTestScope = true

        Given("a delta field tracking Int source changes") {
            val sourceDecl = MutableValueFieldDeclaration(10)

            Then("initial value equals initial param — mapper not called") {
                var mapperCallCount = 0
                val (_, fieldMap) = sourceDecl.buildState()
                val declaration = DerivedDeltaFieldDeclaration(sourceDecl, initial = 0) { old, new, prev ->
                    mapperCallCount++
                    prev + (new - old)
                }
                val state = declaration.convert(fieldMap)
                state.value shouldBe 0
                mapperCallCount shouldBe 0
            }

            When("source updates once") {
                Then("mapper receives correct arguments") {
                    data class Call(val oldSrc: Int, val newSrc: Int, val oldResult: Int)
                    val calls = mutableListOf<Call>()
                    val (sourceState, fieldMap) = sourceDecl.buildState()
                    val declaration = DerivedDeltaFieldDeclaration(sourceDecl, initial = 0) { old, new, prev ->
                        calls += Call(old, new, prev)
                        prev + (new - old)
                    }
                    val state = declaration.convert(fieldMap)
                    sourceState.locked { update { 15 } }
                    state.value shouldBe 5
                    calls.size shouldBe 1
                    calls[0].oldSrc shouldBe 10
                    calls[0].newSrc shouldBe 15
                    calls[0].oldResult shouldBe 0
                }
            }

            When("source updates twice") {
                Then("mapper state threads across calls") {
                    data class Call(val oldSrc: Int, val newSrc: Int, val oldResult: Int)
                    val calls = mutableListOf<Call>()
                    val (sourceState, fieldMap) = sourceDecl.buildState()
                    val declaration = DerivedDeltaFieldDeclaration(sourceDecl, initial = 0) { old, new, prev ->
                        calls += Call(old, new, prev)
                        prev + (new - old)
                    }
                    val state = declaration.convert(fieldMap)
                    sourceState.locked { update { 15 } }
                    sourceState.locked { update { 12 } }
                    state.value shouldBe 2
                    calls.size shouldBe 2
                    calls[1].oldSrc shouldBe 15
                    calls[1].newSrc shouldBe 12
                    calls[1].oldResult shouldBe 5
                }
            }

            When("source changes multiple times") {
                Then("deltas accumulate correctly") {
                    val (sourceState, fieldMap) = sourceDecl.buildState()
                    val declaration = DerivedDeltaFieldDeclaration(sourceDecl, initial = 0) { old, new, prev ->
                        prev + (new - old)
                    }
                    val state = declaration.convert(fieldMap)
                    // 10→20→5→30  net delta: +20
                    sourceState.locked { update { 20 } }
                    sourceState.locked { update { 5 } }
                    sourceState.locked { update { 30 } }
                    state.value shouldBe 20
                }
            }
        }

        Given("a delta field accumulating String source transitions") {
            val sourceDecl = MutableValueFieldDeclaration("idle")

            Then("initial result is the provided initial string") {
                val (_, fieldMap) = sourceDecl.buildState()
                val declaration = DerivedDeltaFieldDeclaration(sourceDecl, initial = "") { old, new, prev ->
                    "$prev[$old→$new]"
                }
                val state = declaration.convert(fieldMap)
                state.value shouldBe ""
            }

            When("source transitions through multiple states") {
                Then("result records each transition in order") {
                    val (sourceState, fieldMap) = sourceDecl.buildState()
                    val declaration = DerivedDeltaFieldDeclaration(sourceDecl, initial = "") { old, new, prev ->
                        "$prev[$old→$new]"
                    }
                    val state = declaration.convert(fieldMap)
                    sourceState.locked { update { "loading" } }
                    sourceState.locked { update { "done" } }
                    state.value shouldBe "[idle→loading][loading→done]"
                }
            }
        }
    }
}
