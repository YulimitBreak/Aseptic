@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.flowMapWith
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow

class Derived1FieldDeclarationTest : BehaviorSpec() {

    init {
        coroutineTestScope = true

        Given("a derived field that doubles an Int source (initial 3)") {
            val sourceDecl = MutableValueFieldDeclaration(3)

            Then("initial value is mapper(source initial) = 6") {
                val scope = CoroutineScope(coroutineContext + Job())
                val sourceFlow = MutableStateFlow(3)
                val state = Derived1FieldDeclaration(sourceDecl) { it * 2 }
                    .convert(flowMapWith(sourceDecl, sourceFlow), scope)
                state.value shouldBe 6
                state.flow.value shouldBe 6
                scope.cancel()
            }

            When("source updates to 10") {
                Then("derived value becomes 20") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val sourceFlow = MutableStateFlow(3)
                    val state = Derived1FieldDeclaration(sourceDecl) { it * 2 }
                        .convert(flowMapWith(sourceDecl, sourceFlow), scope)
                    sourceFlow.value = 10
                    testCoroutineScheduler.advanceUntilIdle()
                    state.flow.value shouldBe 20
                    scope.cancel()
                }
            }

            When("source updates multiple times") {
                Then("derived always reflects latest mapped value") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val sourceFlow = MutableStateFlow(3)
                    val state = Derived1FieldDeclaration(sourceDecl) { it * 2 }
                        .convert(flowMapWith(sourceDecl, sourceFlow), scope)
                    for (i in listOf(1, 5, 100, 0)) {
                        sourceFlow.value = i
                        testCoroutineScheduler.advanceUntilIdle()
                        state.flow.value shouldBe i * 2
                    }
                    scope.cancel()
                }
            }
        }

        Given("a derived field that maps String to its length") {
            val sourceDecl = MutableValueFieldDeclaration("hello")

            Then("initial value is 5") {
                val scope = CoroutineScope(coroutineContext + Job())
                val sourceFlow = MutableStateFlow("hello")
                val state = Derived1FieldDeclaration(sourceDecl) { it.length }
                    .convert(flowMapWith(sourceDecl, sourceFlow), scope)
                state.value shouldBe 5
                scope.cancel()
            }

            When("source changes to empty string") {
                Then("derived value is 0") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val sourceFlow = MutableStateFlow("hello")
                    val state = Derived1FieldDeclaration(sourceDecl) { it.length }
                        .convert(flowMapWith(sourceDecl, sourceFlow), scope)
                    sourceFlow.value = ""
                    testCoroutineScheduler.advanceUntilIdle()
                    state.flow.value shouldBe 0
                    scope.cancel()
                }
            }
        }
    }
}
