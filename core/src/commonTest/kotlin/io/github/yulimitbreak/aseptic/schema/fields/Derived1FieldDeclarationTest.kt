@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.buildState
import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.locked
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class Derived1FieldDeclarationTest : BehaviorSpec() {

    init {
        coroutineTestScope = true

        Given("a derived field mapping an Int source with a doubling function") {
            val sourceDecl = MutableValueFieldDeclaration(3)

            Then("mapper is applied to initial value") {
                val (_, fieldMap) = sourceDecl.buildState()
                val state = Derived1FieldDeclaration(sourceDecl) { it * 2 }.convert(fieldMap)
                state.value shouldBe 6
            }

            When("source updates") {
                Then("derived reflects the new source value") {
                    val (sourceState, fieldMap) = sourceDecl.buildState()
                    val state = Derived1FieldDeclaration(sourceDecl) { it * 2 }.convert(fieldMap)
                    sourceState.locked { update { 10 } }
                    state.value shouldBe 20
                }
            }

            When("source updates multiple times") {
                Then("derived always reflects latest mapped value") {
                    val (sourceState, fieldMap) = sourceDecl.buildState()
                    val state = Derived1FieldDeclaration(sourceDecl) { it * 2 }.convert(fieldMap)
                    for (i in listOf(1, 5, 100, 0)) {
                        sourceState.locked { update { i } }
                        state.value shouldBe i * 2
                    }
                }
            }
        }

        Given("a derived field mapping a String source to its length") {
            val sourceDecl = MutableValueFieldDeclaration("hello")

            Then("mapper is applied to initial value") {
                val (_, fieldMap) = sourceDecl.buildState()
                val state = Derived1FieldDeclaration(sourceDecl) { it.length }.convert(fieldMap)
                state.value shouldBe 5
            }

            When("source changes to empty string") {
                Then("derived value is 0") {
                    val (sourceState, fieldMap) = sourceDecl.buildState()
                    val state = Derived1FieldDeclaration(sourceDecl) { it.length }.convert(fieldMap)
                    sourceState.locked { update { "" } }
                    state.value shouldBe 0
                }
            }
        }
    }
}
