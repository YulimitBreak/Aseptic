@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.flowMapWith
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow

class DerivedDeltaFieldDeclarationTest : BehaviorSpec() {

    init {
        coroutineTestScope = true

        Given("a delta field tracking Int source changes") {
            val sourceDecl = MutableValueFieldDeclaration(10)

            Then("initial flow value equals initial param — mapper not called") {
                val scope = CoroutineScope(coroutineContext + Job())
                var mapperCallCount = 0
                val sourceFlow = MutableStateFlow(10)
                val declaration = DerivedDeltaFieldDeclaration(sourceDecl, initial = 0) { old, new, prev ->
                    mapperCallCount++
                    prev + (new - old)
                }
                val state = declaration.convert(flowMapWith(sourceDecl, sourceFlow), scope)
                testCoroutineScheduler.advanceUntilIdle()
                state.value shouldBe 0
                state.flow.value shouldBe 0
                mapperCallCount shouldBe 0
                scope.cancel()
            }

            When("source updates once") {
                Then("mapper receives correct arguments") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    data class Call(val oldSrc: Int, val newSrc: Int, val oldResult: Int)
                    val calls = mutableListOf<Call>()
                    val sourceFlow = MutableStateFlow(10)
                    val declaration = DerivedDeltaFieldDeclaration(sourceDecl, initial = 0) { old, new, prev ->
                        calls += Call(old, new, prev)
                        prev + (new - old)
                    }
                    val state = declaration.convert(flowMapWith(sourceDecl, sourceFlow), scope)
                    testCoroutineScheduler.advanceUntilIdle() // allow collection to start before emitting changes
                    sourceFlow.value = 15
                    testCoroutineScheduler.advanceUntilIdle()
                    state.flow.value shouldBe 5
                    calls.size shouldBe 1
                    calls[0].oldSrc shouldBe 10
                    calls[0].newSrc shouldBe 15
                    calls[0].oldResult shouldBe 0
                    scope.cancel()
                }
            }

            When("source updates twice") {
                Then("mapper state threads across calls") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    data class Call(val oldSrc: Int, val newSrc: Int, val oldResult: Int)
                    val calls = mutableListOf<Call>()
                    val sourceFlow = MutableStateFlow(10)
                    val declaration = DerivedDeltaFieldDeclaration(sourceDecl, initial = 0) { old, new, prev ->
                        calls += Call(old, new, prev)
                        prev + (new - old)
                    }
                    val state = declaration.convert(flowMapWith(sourceDecl, sourceFlow), scope)
                    testCoroutineScheduler.advanceUntilIdle() // allow collection to start before emitting changes
                    sourceFlow.value = 15
                    testCoroutineScheduler.advanceUntilIdle()
                    sourceFlow.value = 12
                    testCoroutineScheduler.advanceUntilIdle()
                    state.flow.value shouldBe 2
                    calls.size shouldBe 2
                    calls[1].oldSrc shouldBe 15 // previous newSrc threaded through
                    calls[1].newSrc shouldBe 12
                    calls[1].oldResult shouldBe 5 // previous result threaded through
                    scope.cancel()
                }
            }

            When("source changes multiple times") {
                Then("deltas accumulate correctly") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val sourceFlow = MutableStateFlow(10)
                    val declaration = DerivedDeltaFieldDeclaration(sourceDecl, initial = 0) { old, new, prev ->
                        prev + (new - old)
                    }
                    val state = declaration.convert(flowMapWith(sourceDecl, sourceFlow), scope)
                    testCoroutineScheduler.advanceUntilIdle() // allow collection to start before emitting changes
                    // 10→20→5→30  net delta: +20
                    sourceFlow.value = 20
                    testCoroutineScheduler.advanceUntilIdle()
                    sourceFlow.value = 5
                    testCoroutineScheduler.advanceUntilIdle()
                    sourceFlow.value = 30
                    testCoroutineScheduler.advanceUntilIdle()
                    state.flow.value shouldBe 20
                    scope.cancel()
                }
            }
        }

        Given("a delta field accumulating String source transitions") {
            val sourceDecl = MutableValueFieldDeclaration("idle")

            Then("initial result is the provided initial string") {
                val scope = CoroutineScope(coroutineContext + Job())
                val sourceFlow = MutableStateFlow("idle")
                val declaration = DerivedDeltaFieldDeclaration(sourceDecl, initial = "") { old, new, prev ->
                    "$prev[$old→$new]"
                }
                val state = declaration.convert(flowMapWith(sourceDecl, sourceFlow), scope)
                testCoroutineScheduler.advanceUntilIdle()
                state.value shouldBe ""
                scope.cancel()
            }

            When("source transitions through multiple states") {
                Then("result records each transition in order") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val sourceFlow = MutableStateFlow("idle")
                    val declaration = DerivedDeltaFieldDeclaration(sourceDecl, initial = "") { old, new, prev ->
                        "$prev[$old→$new]"
                    }
                    val state = declaration.convert(flowMapWith(sourceDecl, sourceFlow), scope)
                    testCoroutineScheduler.advanceUntilIdle() // allow collection to start before emitting changes
                    sourceFlow.value = "loading"
                    testCoroutineScheduler.advanceUntilIdle()
                    sourceFlow.value = "done"
                    testCoroutineScheduler.advanceUntilIdle()
                    state.flow.value shouldBe "[idle→loading][loading→done]"
                    scope.cancel()
                }
            }
        }
    }
}
