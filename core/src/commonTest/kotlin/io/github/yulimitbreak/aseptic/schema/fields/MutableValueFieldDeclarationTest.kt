package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.asUpdatable
import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.locked
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class MutableValueFieldDeclarationTest : BehaviorSpec() {

    init {
        Given("a mutable field with initial value 0") {
            val declaration = MutableValueFieldDeclaration(0)

            Then("initial value is 0") {
                val state = declaration.convert(StateContainerBuilder.FlowMap(), this)
                state.value shouldBe 0
                state.flow.value shouldBe 0
            }

            When("updated once to 42") {
                Then("value and flow reflect 42") {
                    val state = declaration.convert(StateContainerBuilder.FlowMap(), this)
                        .asUpdatable<Int, (Int) -> Int>()
                    state.locked { update { 42 } }
                    state.value shouldBe 42
                    state.flow.value shouldBe 42
                }
            }

            When("+1 is applied three times") {
                Then("value is 3") {
                    val state = declaration.convert(StateContainerBuilder.FlowMap(), this)
                        .asUpdatable<Int, (Int) -> Int>()
                    repeat(3) { state.locked { update { it + 1 } } }
                    state.value shouldBe 3
                    state.flow.value shouldBe 3
                }
            }

            When("transforms are chained (+10, then *2)") {
                Then("each transform receives the result of the previous") {
                    val state = declaration.convert(StateContainerBuilder.FlowMap(), this)
                        .asUpdatable<Int, (Int) -> Int>()
                    state.locked { update { it + 10 } }
                    state.locked { update { it * 2 } }
                    // (0 + 10) * 2 = 20
                    state.value shouldBe 20
                    state.flow.value shouldBe 20
                }
            }
        }

        Given("a mutable field with a list as initial value") {
            val declaration = MutableValueFieldDeclaration(listOf("a", "b"))

            Then("initial value is the provided list") {
                val state = declaration.convert(StateContainerBuilder.FlowMap(), this)
                state.value shouldBe listOf("a", "b")
                state.flow.value shouldBe listOf("a", "b")
            }

            When("an item is appended") {
                Then("new list is reflected") {
                    val state = declaration.convert(StateContainerBuilder.FlowMap(), this)
                        .asUpdatable<List<String>, (List<String>) -> List<String>>()
                    state.locked { update { it + "c" } }
                    state.value shouldBe listOf("a", "b", "c")
                    state.flow.value shouldBe listOf("a", "b", "c")
                }
            }
        }
    }
}
