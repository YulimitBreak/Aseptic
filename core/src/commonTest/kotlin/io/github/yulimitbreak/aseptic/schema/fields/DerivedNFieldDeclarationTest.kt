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

class DerivedNFieldDeclarationTest : BehaviorSpec() {

    init {
        coroutineTestScope = true

        Given("a derived-N field combining multiple Int sources with a sum") {
            val decls = List(4) { MutableValueFieldDeclaration(it + 1) }
            val declaration = DerivedNFieldDeclaration(decls) { it.sum() }

            Then("initial value is computed from all sources") {
                val scope = CoroutineScope(coroutineContext + Job())
                val flows = List(4) { MutableStateFlow(it + 1) }
                val state = declaration.convert(flowMapWith(decls, flows), scope)
                state.value shouldBe 10
                state.flow.value shouldBe 10
                scope.cancel()
            }

            When("the first source updates") {
                Then("derived reflects the updated first source") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val flows = List(4) { MutableStateFlow(it + 1) }
                    val state = declaration.convert(flowMapWith(decls, flows), scope)
                    flows[0].value = 10
                    testCoroutineScheduler.advanceUntilIdle()
                    state.flow.value shouldBe 19
                    scope.cancel()
                }
            }

            When("the last source updates") {
                Then("derived reflects the updated last source") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val flows = List(4) { MutableStateFlow(it + 1) }
                    val state = declaration.convert(flowMapWith(decls, flows), scope)
                    flows[3].value = 40
                    testCoroutineScheduler.advanceUntilIdle()
                    state.flow.value shouldBe 46
                    scope.cancel()
                }
            }

            When("all sources update to the same value") {
                Then("derived is recomputed from all updated sources") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val flows = List(4) { MutableStateFlow(it + 1) }
                    val state = declaration.convert(flowMapWith(decls, flows), scope)
                    flows.forEach { it.value = 5 }
                    testCoroutineScheduler.advanceUntilIdle()
                    state.flow.value shouldBe 20
                    scope.cancel()
                }
            }
        }

        Given("a derived-N field joining multiple String sources in declaration order") {
            val decls = List(4) { MutableValueFieldDeclaration(('a' + it).toString()) }
            val declaration = DerivedNFieldDeclaration(decls) { it.joinToString("") }

            Then("initial value concatenates sources in order") {
                val scope = CoroutineScope(coroutineContext + Job())
                val flows = List(4) { MutableStateFlow(('a' + it).toString()) }
                val state = declaration.convert(flowMapWith(decls, flows), scope)
                state.value shouldBe "abcd"
                scope.cancel()
            }

            When("a source updates") {
                Then("source position is preserved") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val flows = List(4) { MutableStateFlow(('a' + it).toString()) }
                    val state = declaration.convert(flowMapWith(decls, flows), scope)
                    flows[1].value = "X"
                    testCoroutineScheduler.advanceUntilIdle()
                    state.flow.value shouldBe "aXcd"
                    scope.cancel()
                }
            }
        }
    }
}
