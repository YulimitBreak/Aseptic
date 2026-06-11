@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.fieldMapWith
import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.withTryLock
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class Derived2FieldDeclarationTest : BehaviorSpec() {

    init {
        Given("a derived field combining two Int sources with a sum") {
            val decl1 = MutableValueFieldDeclaration(3)
            val decl2 = MutableValueFieldDeclaration(7)
            val declaration = Derived2FieldDeclaration(decl1, decl2) { a, b -> a + b }

            Then("initial value is computed from both sources") {
                val s1 = decl1.convert("s1", StateContainerBuilder.FieldMap())
                val s2 = decl2.convert("s2", StateContainerBuilder.FieldMap())
                val state = declaration.convert("derived", fieldMapWith(decl1 to s1, decl2 to s2))
                state.value shouldBe 10
            }

            When("source1 updates") {
                Then("derived reflects latest source1") {
                    val s1 = decl1.convert("s1", StateContainerBuilder.FieldMap())
                    val s2 = decl2.convert("s2", StateContainerBuilder.FieldMap())
                    val state = declaration.convert("derived", fieldMapWith(decl1 to s1, decl2 to s2))
                    s1.withTryLock { update { 10 } }
                    state.value shouldBe 17
                }
            }

            When("source2 updates") {
                Then("derived reflects latest source2") {
                    val s1 = decl1.convert("s1", StateContainerBuilder.FieldMap())
                    val s2 = decl2.convert("s2", StateContainerBuilder.FieldMap())
                    val state = declaration.convert("derived", fieldMapWith(decl1 to s1, decl2 to s2))
                    s2.withTryLock { update { 100 } }
                    state.value shouldBe 103
                }
            }

            When("both sources update") {
                Then("derived reflects sum of latest values") {
                    val s1 = decl1.convert("s1", StateContainerBuilder.FieldMap())
                    val s2 = decl2.convert("s2", StateContainerBuilder.FieldMap())
                    val state = declaration.convert("derived", fieldMapWith(decl1 to s1, decl2 to s2))
                    s1.withTryLock { update { 20 } }
                    s2.withTryLock { update { 30 } }
                    state.value shouldBe 50
                }
            }
        }

        Given("a derived field that concatenates two String sources") {
            val decl1 = MutableValueFieldDeclaration("foo")
            val decl2 = MutableValueFieldDeclaration("bar")
            val declaration = Derived2FieldDeclaration(decl1, decl2) { a, b -> "$a$b" }

            Then("initial value is computed from both sources") {
                val s1 = decl1.convert("s1", StateContainerBuilder.FieldMap())
                val s2 = decl2.convert("s2", StateContainerBuilder.FieldMap())
                val state = declaration.convert("derived", fieldMapWith(decl1 to s1, decl2 to s2))
                state.value shouldBe "foobar"
            }
        }
    }
}
