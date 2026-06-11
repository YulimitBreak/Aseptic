@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.fieldMapWith
import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.withTryLock
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class Derived1FieldDeclarationTest : BehaviorSpec() {

    init {
        Given("a derived field mapping an Int source with a doubling function") {
            val sourceDecl = MutableValueFieldDeclaration(3)

            Then("mapper is applied to initial value") {
                val sourceState = sourceDecl.convert("source", StateContainerBuilder.FieldMap())
                val state = Derived1FieldDeclaration(sourceDecl) { it * 2 }
                    .convert("derived", fieldMapWith(sourceDecl to sourceState))
                state.value shouldBe 6
            }

            When("source updates") {
                Then("derived reflects the new source value") {
                    val sourceState = sourceDecl.convert("source", StateContainerBuilder.FieldMap())
                    val state = Derived1FieldDeclaration(sourceDecl) { it * 2 }
                        .convert("derived", fieldMapWith(sourceDecl to sourceState))
                    sourceState.withTryLock { update { 10 } }
                    state.value shouldBe 20
                }
            }

            When("source updates multiple times") {
                Then("derived always reflects latest mapped value") {
                    val sourceState = sourceDecl.convert("source", StateContainerBuilder.FieldMap())
                    val state = Derived1FieldDeclaration(sourceDecl) { it * 2 }
                        .convert("derived", fieldMapWith(sourceDecl to sourceState))
                    for (i in listOf(1, 5, 100, 0)) {
                        sourceState.withTryLock { update { i } }
                        state.value shouldBe i * 2
                    }
                }
            }
        }

        Given("a derived field mapping a String source to its length") {
            val sourceDecl = MutableValueFieldDeclaration("hello")

            Then("mapper is applied to initial value") {
                val sourceState = sourceDecl.convert("source", StateContainerBuilder.FieldMap())
                val state = Derived1FieldDeclaration(sourceDecl) { it.length }
                    .convert("derived", fieldMapWith(sourceDecl to sourceState))
                state.value shouldBe 5
            }

            When("source changes to empty string") {
                Then("derived value is 0") {
                    val sourceState = sourceDecl.convert("source", StateContainerBuilder.FieldMap())
                    val state = Derived1FieldDeclaration(sourceDecl) { it.length }
                        .convert("derived", fieldMapWith(sourceDecl to sourceState))
                    sourceState.withTryLock { update { "" } }
                    state.value shouldBe 0
                }
            }
        }
    }
}
