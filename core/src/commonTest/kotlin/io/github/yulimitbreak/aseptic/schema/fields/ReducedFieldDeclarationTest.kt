@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.asUpdatable
import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.locked
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class ReducedFieldDeclarationTest : BehaviorSpec() {

    init {
        Given("a reduced Int field with an additive reducer") {
            val declaration = ReducedFieldDeclaration(0) { old, update: Int -> old + update }

            Then("initial value is preserved") {
                val state = declaration.convert(StateContainerBuilder.FlowMap(), this)
                state.value shouldBe 0
                state.flow.value shouldBe 0
            }

            When("updated once") {
                Then("reducer is applied") {
                    val state = declaration.convert(StateContainerBuilder.FlowMap(), this)
                        .asUpdatable<Int, Int>()
                    state.locked { update(5) }
                    state.value shouldBe 5
                    state.flow.value shouldBe 5
                }
            }

            When("updated multiple times sequentially") {
                Then("all updates are folded") {
                    val state = declaration.convert(StateContainerBuilder.FlowMap(), this)
                        .asUpdatable<Int, Int>()
                    state.locked { update(3) }
                    state.locked { update(7) }
                    state.value shouldBe 10
                    state.flow.value shouldBe 10
                }
            }

            When("updated repeatedly with the same value") {
                Then("all updates are accumulated") {
                    val state = declaration.convert(StateContainerBuilder.FlowMap(), this)
                        .asUpdatable<Int, Int>()
                    repeat(10) { state.locked { update(1) } }
                    state.value shouldBe 10
                    state.flow.value shouldBe 10
                }
            }
        }

        Given("a reduced List field with an append reducer") {
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

        Given("a reduced Int field that records each old value seen by the reducer") {
            val seenOldValues = mutableListOf<Int>()
            val declaration = ReducedFieldDeclaration(0) { old, update: Int ->
                seenOldValues += old
                old + update
            }

            When("updated sequentially") {
                Then("reducer receives the previous accumulated value on each call") {
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
