@file:OptIn(AsepticInternal::class)

package io.github.yulimitbreak.aseptic.context.fields

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.schema.fields.MutableValueFieldDeclaration
import io.github.yulimitbreak.aseptic.schema.fields.ReducedFieldDeclaration
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class UpdatableFieldTest : BehaviorSpec() {

    private fun mutableField() = StateContainerBuilder().apply {
        addField("count", false, MutableValueFieldDeclaration(0))
    }.build().let { UpdatableField<Int, (Int) -> Int>("count", it) }

    private fun reducedField() = StateContainerBuilder().apply {
        addField("acc", false, ReducedFieldDeclaration(0) { old, update: Int -> old + update })
    }.build().let { UpdatableField<Int, Int>("acc", it) }

    init {
        coroutineTestScope = true

        Given("an updatable mutable field") {
            When("a transform is applied to the field") {
                Then("the value reflects the transform") {
                    val field = mutableField()
                    field.update { it + 5 }
                    field.invoke() shouldBe 5
                }
            }
            When("the value is overwritten directly") {
                Then("the value is replaced, ignoring the previous one") {
                    val field = mutableField()
                    field.update { it + 5 }
                    field.set(42)
                    field.invoke() shouldBe 42
                }
            }
        }

        Given("an updatable reduced field") {
            When("multiple updates are applied") {
                Then("they fold into the value in order") {
                    val field = reducedField()
                    field.update(5)
                    field.update(3)
                    field.invoke() shouldBe 8
                }
            }
        }
    }
}
