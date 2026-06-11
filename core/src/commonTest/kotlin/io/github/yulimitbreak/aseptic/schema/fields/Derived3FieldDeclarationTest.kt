@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.fieldMapWith
import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.withTryLock
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class Derived3FieldDeclarationTest : BehaviorSpec() {

    init {
        Given("a derived field combining three Int sources with a sum") {
            val decl1 = MutableValueFieldDeclaration(1)
            val decl2 = MutableValueFieldDeclaration(2)
            val decl3 = MutableValueFieldDeclaration(3)
            val declaration = Derived3FieldDeclaration(decl1, decl2, decl3) { a, b, c -> a + b + c }

            Then("initial value is computed from all sources") {
                val s1 = decl1.convert("s1", StateContainerBuilder.FieldMap())
                val s2 = decl2.convert("s2", StateContainerBuilder.FieldMap())
                val s3 = decl3.convert("s3", StateContainerBuilder.FieldMap())
                val state = declaration.convert(
                    "derived",
                    fieldMapWith(decl1 to s1, decl2 to s2, decl3 to s3),
                )
                state.value shouldBe 6
            }

            When("source1 updates") {
                Then("derived reflects latest source1") {
                    val s1 = decl1.convert("s1", StateContainerBuilder.FieldMap())
                    val s2 = decl2.convert("s2", StateContainerBuilder.FieldMap())
                    val s3 = decl3.convert("s3", StateContainerBuilder.FieldMap())
                    val state = declaration.convert(
                        "derived",
                        fieldMapWith(decl1 to s1, decl2 to s2, decl3 to s3),

                    )
                    s1.withTryLock { update { 10 } }
                    state.value shouldBe 15
                }
            }

            When("source2 updates") {
                Then("derived reflects latest source2") {
                    val s1 = decl1.convert("s1", StateContainerBuilder.FieldMap())
                    val s2 = decl2.convert("s2", StateContainerBuilder.FieldMap())
                    val s3 = decl3.convert("s3", StateContainerBuilder.FieldMap())
                    val state = declaration.convert(
                        "derived",
                        fieldMapWith(decl1 to s1, decl2 to s2, decl3 to s3),

                    )
                    s2.withTryLock { update { 20 } }
                    state.value shouldBe 24
                }
            }

            When("source3 updates") {
                Then("derived reflects latest source3") {
                    val s1 = decl1.convert("s1", StateContainerBuilder.FieldMap())
                    val s2 = decl2.convert("s2", StateContainerBuilder.FieldMap())
                    val s3 = decl3.convert("s3", StateContainerBuilder.FieldMap())
                    val state = declaration.convert(
                        "derived",
                        fieldMapWith(decl1 to s1, decl2 to s2, decl3 to s3),

                    )
                    s3.withTryLock { update { 30 } }
                    state.value shouldBe 33
                }
            }

            When("all three sources update sequentially") {
                Then("derived reflects sum of all latest values") {
                    val s1 = decl1.convert("s1", StateContainerBuilder.FieldMap())
                    val s2 = decl2.convert("s2", StateContainerBuilder.FieldMap())
                    val s3 = decl3.convert("s3", StateContainerBuilder.FieldMap())
                    val state = declaration.convert(
                        "derived",
                        fieldMapWith(decl1 to s1, decl2 to s2, decl3 to s3),

                    )
                    s1.withTryLock { update { 5 } }
                    s2.withTryLock { update { 5 } }
                    s3.withTryLock { update { 5 } }
                    state.value shouldBe 15
                }
            }
        }
    }
}
