@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.fieldMapWith
import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.withTryLock
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class DerivedNFieldDeclarationTest : BehaviorSpec() {

    init {
        Given("a derived-N field combining multiple Int sources with a sum") {
            val decls = List(4) { MutableValueFieldDeclaration(it + 1) }
            val declaration = DerivedNFieldDeclaration(decls) { it.sum() }

            Then("initial value is computed from all sources") {
                val sourceStates = decls.mapIndexed { i, decl ->
                    decl.convert("s$i", StateContainerBuilder.FieldMap())
                }
                val state = declaration.convert("derived", fieldMapWith(decls, sourceStates))
                state.value shouldBe 10
            }

            When("the first source updates") {
                Then("derived reflects the updated first source") {
                    val sourceStates = decls.mapIndexed { i, decl ->
                        decl.convert("s$i", StateContainerBuilder.FieldMap())
                    }
                    val state = declaration.convert("derived", fieldMapWith(decls, sourceStates))
                    sourceStates[0].withTryLock { update { 10 } }
                    state.value shouldBe 19
                }
            }

            When("the last source updates") {
                Then("derived reflects the updated last source") {
                    val sourceStates = decls.mapIndexed { i, decl ->
                        decl.convert("s$i", StateContainerBuilder.FieldMap())
                    }
                    val state = declaration.convert("derived", fieldMapWith(decls, sourceStates))
                    sourceStates[3].withTryLock { update { 40 } }
                    state.value shouldBe 46
                }
            }

            When("all sources update to the same value") {
                Then("derived is recomputed from all updated sources") {
                    val sourceStates = decls.mapIndexed { i, decl ->
                        decl.convert("s$i", StateContainerBuilder.FieldMap())
                    }
                    val state = declaration.convert("derived", fieldMapWith(decls, sourceStates))
                    sourceStates.forEach { it.withTryLock { update { 5 } } }
                    state.value shouldBe 20
                }
            }
        }

        Given("a derived-N field joining multiple String sources in declaration order") {
            val decls = List(4) { MutableValueFieldDeclaration(('a' + it).toString()) }
            val declaration = DerivedNFieldDeclaration(decls) { it.joinToString("") }

            Then("initial value concatenates sources in order") {
                val sourceStates = decls.mapIndexed { i, decl ->
                    decl.convert("s$i", StateContainerBuilder.FieldMap())
                }
                val state = declaration.convert("derived", fieldMapWith(decls, sourceStates))
                state.value shouldBe "abcd"
            }

            When("a source updates") {
                Then("source position is preserved") {
                    val sourceStates = decls.mapIndexed { i, decl ->
                        decl.convert("s$i", StateContainerBuilder.FieldMap())
                    }
                    val state = declaration.convert("derived", fieldMapWith(decls, sourceStates))
                    sourceStates[1].withTryLock { update { "X" } }
                    state.value shouldBe "aXcd"
                }
            }
        }
    }
}
