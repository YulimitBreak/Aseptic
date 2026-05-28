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

class Derived2FieldDeclarationTest : BehaviorSpec() {

    init {
        coroutineTestScope = true

        Given("a derived field that sums two Int sources (initial 3 and 7)") {
            val decl1 = MutableValueFieldDeclaration(3)
            val decl2 = MutableValueFieldDeclaration(7)
            val declaration = Derived2FieldDeclaration(decl1, decl2) { a, b -> a + b }

            Then("initial value is 3 + 7 = 10") {
                val scope = CoroutineScope(coroutineContext + Job())
                val state = declaration.convert(
                    flowMapWith(decl1 to MutableStateFlow(3), decl2 to MutableStateFlow(7)),
                    scope
                )
                state.value shouldBe 10
                state.flow.value shouldBe 10
                scope.cancel()
            }

            When("source1 updates to 10") {
                Then("derived value is 10 + 7 = 17 (uses latest source2)") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val flow1 = MutableStateFlow(3)
                    val flow2 = MutableStateFlow(7)
                    val state = declaration.convert(flowMapWith(decl1 to flow1, decl2 to flow2), scope)
                    flow1.value = 10
                    testCoroutineScheduler.advanceUntilIdle()
                    state.flow.value shouldBe 17
                    scope.cancel()
                }
            }

            When("source2 updates to 100") {
                Then("derived value is 3 + 100 = 103 (uses latest source1)") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val flow1 = MutableStateFlow(3)
                    val flow2 = MutableStateFlow(7)
                    val state = declaration.convert(flowMapWith(decl1 to flow1, decl2 to flow2), scope)
                    flow2.value = 100
                    testCoroutineScheduler.advanceUntilIdle()
                    state.flow.value shouldBe 103
                    scope.cancel()
                }
            }

            When("both sources update") {
                Then("derived reflects sum of latest values") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val flow1 = MutableStateFlow(3)
                    val flow2 = MutableStateFlow(7)
                    val state = declaration.convert(flowMapWith(decl1 to flow1, decl2 to flow2), scope)
                    flow1.value = 20
                    flow2.value = 30
                    testCoroutineScheduler.advanceUntilIdle()
                    state.flow.value shouldBe 50
                    scope.cancel()
                }
            }
        }

        Given("a derived field that concatenates two String sources") {
            val decl1 = MutableValueFieldDeclaration("foo")
            val decl2 = MutableValueFieldDeclaration("bar")
            val declaration = Derived2FieldDeclaration(decl1, decl2) { a, b -> "$a$b" }

            Then("initial value is foobar") {
                val scope = CoroutineScope(coroutineContext + Job())
                val state = declaration.convert(
                    flowMapWith(decl1 to MutableStateFlow("foo"), decl2 to MutableStateFlow("bar")),
                    scope
                )
                state.value shouldBe "foobar"
                scope.cancel()
            }
        }
    }
}
