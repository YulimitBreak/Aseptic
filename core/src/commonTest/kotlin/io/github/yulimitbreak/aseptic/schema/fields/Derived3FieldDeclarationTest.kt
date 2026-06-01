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

class Derived3FieldDeclarationTest : BehaviorSpec() {

    init {
        coroutineTestScope = true

        Given("a derived field combining three Int sources with a sum") {
            val decl1 = MutableValueFieldDeclaration(1)
            val decl2 = MutableValueFieldDeclaration(2)
            val decl3 = MutableValueFieldDeclaration(3)
            val declaration = Derived3FieldDeclaration(decl1, decl2, decl3) { a, b, c -> a + b + c }

            Then("initial value is computed from all sources") {
                val scope = CoroutineScope(coroutineContext + Job())
                val state = declaration.convert(
                    flowMapWith(
                        decl1 to MutableStateFlow(1),
                        decl2 to MutableStateFlow(2),
                        decl3 to MutableStateFlow(3)
                    ),
                    scope,
                )
                state.value shouldBe 6
                state.flow.value shouldBe 6
                scope.cancel()
            }

            When("source1 updates") {
                Then("derived reflects latest source1") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val f1 = MutableStateFlow(1)
                    val f2 = MutableStateFlow(2)
                    val f3 = MutableStateFlow(3)
                    val state = declaration.convert(flowMapWith(decl1 to f1, decl2 to f2, decl3 to f3), scope)
                    f1.value = 10
                    testCoroutineScheduler.advanceUntilIdle()
                    state.flow.value shouldBe 15
                    scope.cancel()
                }
            }

            When("source2 updates") {
                Then("derived reflects latest source2") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val f1 = MutableStateFlow(1)
                    val f2 = MutableStateFlow(2)
                    val f3 = MutableStateFlow(3)
                    val state = declaration.convert(flowMapWith(decl1 to f1, decl2 to f2, decl3 to f3), scope)
                    f2.value = 20
                    testCoroutineScheduler.advanceUntilIdle()
                    state.flow.value shouldBe 24
                    scope.cancel()
                }
            }

            When("source3 updates") {
                Then("derived reflects latest source3") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val f1 = MutableStateFlow(1)
                    val f2 = MutableStateFlow(2)
                    val f3 = MutableStateFlow(3)
                    val state = declaration.convert(flowMapWith(decl1 to f1, decl2 to f2, decl3 to f3), scope)
                    f3.value = 30
                    testCoroutineScheduler.advanceUntilIdle()
                    state.flow.value shouldBe 33
                    scope.cancel()
                }
            }

            When("all three sources update sequentially") {
                Then("derived reflects sum of all latest values") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val f1 = MutableStateFlow(1)
                    val f2 = MutableStateFlow(2)
                    val f3 = MutableStateFlow(3)
                    val state = declaration.convert(flowMapWith(decl1 to f1, decl2 to f2, decl3 to f3), scope)
                    f1.value = 5
                    f2.value = 5
                    f3.value = 5
                    testCoroutineScheduler.advanceUntilIdle()
                    state.flow.value shouldBe 15
                    scope.cancel()
                }
            }
        }
    }
}
