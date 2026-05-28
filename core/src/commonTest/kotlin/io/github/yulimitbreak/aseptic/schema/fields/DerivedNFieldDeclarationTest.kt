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

        Given("a derived-N field summing four Int sources (initial 1, 2, 3, 4)") {
            val decls = List(4) { MutableValueFieldDeclaration(it + 1) }
            val declaration = DerivedNFieldDeclaration(decls) { it.sum() }

            Then("initial value is 1 + 2 + 3 + 4 = 10") {
                val scope = CoroutineScope(coroutineContext + Job())
                val flows = List(4) { MutableStateFlow(it + 1) }
                val state = declaration.convert(flowMapWith(decls, flows), scope)
                state.value shouldBe 10
                state.flow.value shouldBe 10
                scope.cancel()
            }

            When("first source updates to 10") {
                Then("derived is 10 + 2 + 3 + 4 = 19") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val flows = List(4) { MutableStateFlow(it + 1) }
                    val state = declaration.convert(flowMapWith(decls, flows), scope)
                    flows[0].value = 10
                    testCoroutineScheduler.advanceUntilIdle()
                    state.flow.value shouldBe 19
                    scope.cancel()
                }
            }

            When("last source updates to 40") {
                Then("derived is 1 + 2 + 3 + 40 = 46") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val flows = List(4) { MutableStateFlow(it + 1) }
                    val state = declaration.convert(flowMapWith(decls, flows), scope)
                    flows[3].value = 40
                    testCoroutineScheduler.advanceUntilIdle()
                    state.flow.value shouldBe 46
                    scope.cancel()
                }
            }

            When("all sources set to 5") {
                Then("derived is 20") {
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

        Given("a derived-N field joining four String sources in declaration order") {
            val decls = List(4) { MutableValueFieldDeclaration(('a' + it).toString()) }
            val declaration = DerivedNFieldDeclaration(decls) { it.joinToString("") }

            Then("initial value concatenates sources in order") {
                val scope = CoroutineScope(coroutineContext + Job())
                val flows = List(4) { MutableStateFlow(('a' + it).toString()) }
                val state = declaration.convert(flowMapWith(decls, flows), scope)
                state.value shouldBe "abcd"
                scope.cancel()
            }

            When("second source changes to X") {
                Then("derived is aXcd") {
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
