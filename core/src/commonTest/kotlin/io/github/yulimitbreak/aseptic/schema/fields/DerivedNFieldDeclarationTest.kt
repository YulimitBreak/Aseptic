@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.buildStates
import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.locked
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class DerivedNFieldDeclarationTest : BehaviorSpec() {

    init {
        coroutineTestScope = true

        Given("a derived-N field combining multiple Int sources with a sum") {
            val decls = List(4) { MutableValueFieldDeclaration(it + 1) }
            val declaration = DerivedNFieldDeclaration(decls) { it.sum() }

            Then("initial value is computed from all sources") {
                val (_, fieldMap) = buildStates(decls)
                val state = declaration.convert(fieldMap)
                state.value shouldBe 10
            }

            When("the first source updates") {
                Then("derived reflects the updated first source") {
                    val (states, fieldMap) = buildStates(decls)
                    val state = declaration.convert(fieldMap)
                    states[0].locked { update { 10 } }
                    state.value shouldBe 19
                }
            }

            When("the last source updates") {
                Then("derived reflects the updated last source") {
                    val (states, fieldMap) = buildStates(decls)
                    val state = declaration.convert(fieldMap)
                    states[3].locked { update { 40 } }
                    state.value shouldBe 46
                }
            }

            When("all sources update to the same value") {
                Then("derived is recomputed from all updated sources") {
                    val (states, fieldMap) = buildStates(decls)
                    val state = declaration.convert(fieldMap)
                    states.forEach { it.locked { update { 5 } } }
                    state.value shouldBe 20
                }
            }
        }

        Given("a derived-N field joining multiple String sources in declaration order") {
            val decls = List(4) { MutableValueFieldDeclaration(('a' + it).toString()) }
            val declaration = DerivedNFieldDeclaration(decls) { it.joinToString("") }

            Then("initial value concatenates sources in order") {
                val (_, fieldMap) = buildStates(decls)
                val state = declaration.convert(fieldMap)
                state.value shouldBe "abcd"
            }

            When("a source updates") {
                Then("source position is preserved") {
                    val (states, fieldMap) = buildStates(decls)
                    val state = declaration.convert(fieldMap)
                    states[1].locked { update { "X" } }
                    state.value shouldBe "aXcd"
                }
            }
        }
    }
}
