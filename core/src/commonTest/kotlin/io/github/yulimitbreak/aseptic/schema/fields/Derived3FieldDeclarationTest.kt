@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.buildStates
import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.locked
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class Derived3FieldDeclarationTest : BehaviorSpec() {

    init {
        coroutineTestScope = true

        Given("a derived field combining three Int sources with a sum") {
            val decl1 = MutableValueFieldDeclaration(1)
            val decl2 = MutableValueFieldDeclaration(2)
            val decl3 = MutableValueFieldDeclaration(3)
            val declaration = Derived3FieldDeclaration(decl1, decl2, decl3) { a, b, c -> a + b + c }

            Then("initial value is computed from all sources") {
                val (_, fieldMap) = buildStates(decl1, decl2, decl3)
                val state = declaration.convert(fieldMap)
                state.value shouldBe 6
            }

            When("source1 updates") {
                Then("derived reflects latest source1") {
                    val (states, fieldMap) = buildStates(decl1, decl2, decl3)
                    val state = declaration.convert(fieldMap)
                    states[0].locked { update { 10 } }
                    state.value shouldBe 15
                }
            }

            When("source2 updates") {
                Then("derived reflects latest source2") {
                    val (states, fieldMap) = buildStates(decl1, decl2, decl3)
                    val state = declaration.convert(fieldMap)
                    states[1].locked { update { 20 } }
                    state.value shouldBe 24
                }
            }

            When("source3 updates") {
                Then("derived reflects latest source3") {
                    val (states, fieldMap) = buildStates(decl1, decl2, decl3)
                    val state = declaration.convert(fieldMap)
                    states[2].locked { update { 30 } }
                    state.value shouldBe 33
                }
            }

            When("all three sources update sequentially") {
                Then("derived reflects sum of all latest values") {
                    val (states, fieldMap) = buildStates(decl1, decl2, decl3)
                    val state = declaration.convert(fieldMap)
                    states[0].locked { update { 5 } }
                    states[1].locked { update { 5 } }
                    states[2].locked { update { 5 } }
                    state.value shouldBe 15
                }
            }
        }
    }
}
