package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.asUpdatable
import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.locked
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class ReducedFieldDeclarationTest : BehaviorSpec() {

    init {
        Given("a counter field (initial 0, reducer adds the update)") {
            val declaration = ReducedFieldDeclaration(0) { old, update: Int -> old + update }

            Then("initial value is 0") {
                val state = declaration.convert(StateContainerBuilder.FlowMap(), this)
                state.value shouldBe 0
                state.flow.value shouldBe 0
            }

            When("updated with 5") {
                Then("value is 5") {
                    val state = declaration.convert(StateContainerBuilder.FlowMap(), this)
                        .asUpdatable<Int, Int>()
                    state.locked { update(5) }
                    state.value shouldBe 5
                    state.flow.value shouldBe 5
                }
            }

            When("updated with 3, then 7") {
                Then("value is 10 (updates fold correctly)") {
                    val state = declaration.convert(StateContainerBuilder.FlowMap(), this)
                        .asUpdatable<Int, Int>()
                    state.locked { update(3) }
                    state.locked { update(7) }
                    state.value shouldBe 10
                    state.flow.value shouldBe 10
                }
            }

            When("updated ten times with 1") {
                Then("value is 10") {
                    val state = declaration.convert(StateContainerBuilder.FlowMap(), this)
                        .asUpdatable<Int, Int>()
                    repeat(10) { state.locked { update(1) } }
                    state.value shouldBe 10
                    state.flow.value shouldBe 10
                }
            }
        }

        Given("a list-append field (initial empty, reducer appends item)") {
            val declaration = ReducedFieldDeclaration(emptyList<String>()) { old, item: String -> old + item }

            Then("initial value is empty list") {
                val state = declaration.convert(StateContainerBuilder.FlowMap(), this)
                state.value shouldBe emptyList<String>()
                state.flow.value shouldBe emptyList<String>()
            }

            When("items appended one by one") {
                Then("list contains them in order") {
                    val state = declaration.convert(StateContainerBuilder.FlowMap(), this)
                        .asUpdatable<List<String>, String>()
                    state.locked { update("a") }
                    state.locked { update("b") }
                    state.locked { update("c") }
                    state.value shouldBe listOf("a", "b", "c")
                    state.flow.value shouldBe listOf("a", "b", "c")
                }
            }
        }

        Given("a reducer that records the old value it received") {
            val seenOldValues = mutableListOf<Int>()
            val declaration = ReducedFieldDeclaration(0) { old, update: Int ->
                seenOldValues += old
                old + update
            }

            When("updated with 1, 2, 3 sequentially") {
                Then("reducer received old values 0, 1, 3 in order") {
                    val state = declaration.convert(StateContainerBuilder.FlowMap(), this)
                        .asUpdatable<Int, Int>()
                    state.locked { update(1) }
                    state.locked { update(2) }
                    state.locked { update(3) }
                    seenOldValues shouldBe listOf(0, 1, 3)
                }
            }
        }
    }
}
