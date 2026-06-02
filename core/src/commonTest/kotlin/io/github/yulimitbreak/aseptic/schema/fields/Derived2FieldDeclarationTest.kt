@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.buildStates
import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.locked
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class Derived2FieldDeclarationTest : BehaviorSpec() {

    init {
        coroutineTestScope = true

        Given("a derived field combining two Int sources with a sum") {
            val decl1 = MutableValueFieldDeclaration(3)
            val decl2 = MutableValueFieldDeclaration(7)
            val declaration = Derived2FieldDeclaration(decl1, decl2) { a, b -> a + b }

            Then("initial value is computed from both sources") {
                val (_, fieldMap) = buildStates(decl1, decl2)
                val state = declaration.convert(fieldMap)
                state.value shouldBe 10
            }

            When("source1 updates") {
                Then("derived reflects latest source1") {
                    val (states, fieldMap) = buildStates(decl1, decl2)
                    val state = declaration.convert(fieldMap)
                    states[0].locked { update { 10 } }
                    state.value shouldBe 17
                }
            }

            When("source2 updates") {
                Then("derived reflects latest source2") {
                    val (states, fieldMap) = buildStates(decl1, decl2)
                    val state = declaration.convert(fieldMap)
                    states[1].locked { update { 100 } }
                    state.value shouldBe 103
                }
            }

            When("both sources update") {
                Then("derived reflects sum of latest values") {
                    val (states, fieldMap) = buildStates(decl1, decl2)
                    val state = declaration.convert(fieldMap)
                    states[0].locked { update { 20 } }
                    states[1].locked { update { 30 } }
                    state.value shouldBe 50
                }
            }
        }

        Given("a derived field that concatenates two String sources") {
            val decl1 = MutableValueFieldDeclaration("foo")
            val decl2 = MutableValueFieldDeclaration("bar")
            val declaration = Derived2FieldDeclaration(decl1, decl2) { a, b -> "$a$b" }

            Then("initial value is computed from both sources") {
                val (_, fieldMap) = buildStates(decl1, decl2)
                val state = declaration.convert(fieldMap)
                state.value shouldBe "foobar"
            }
        }
    }
}
