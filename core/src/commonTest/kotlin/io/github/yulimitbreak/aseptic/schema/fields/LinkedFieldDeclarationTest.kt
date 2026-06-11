@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.fieldMapWith
import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.withTryLock
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class LinkedFieldDeclarationTest : BehaviorSpec() {

    init {
        Given("a reduced accumulator linked to a reduced source with identity mapper") {
            val sourceDecl = ReducedFieldDeclaration(0) { _, u: Int -> u }
            val wrappedDecl = ReducedFieldDeclaration(0) { acc, u: Int -> acc + u }
            val linkedDecl = LinkedFieldDeclaration(wrappedDecl, LinkedFieldDeclaration.Link(sourceDecl) { it })

            Then("initial value matches the wrapped field initial") {
                val sourceState = sourceDecl.convert("source", StateContainerBuilder.FieldMap())
                val linkedState = linkedDecl.convert("linked", fieldMapWith(sourceDecl to sourceState))
                linkedState.value shouldBe 0
            }

            When("source is updated once") {
                Then("linked field reflects the update") {
                    val sourceState = sourceDecl.convert("source", StateContainerBuilder.FieldMap())
                    val linkedState = linkedDecl.convert("linked", fieldMapWith(sourceDecl to sourceState))
                    sourceState.withTryLock { update(10) }
                    linkedState.value shouldBe 10
                }
            }

            When("source is updated multiple times") {
                Then("linked field accumulates all updates") {
                    val sourceState = sourceDecl.convert("source", StateContainerBuilder.FieldMap())
                    val linkedState = linkedDecl.convert("linked", fieldMapWith(sourceDecl to sourceState))
                    sourceState.withTryLock { update(3) }
                    sourceState.withTryLock { update(7) }
                    linkedState.value shouldBe 10
                }
            }

            When("source is updated") {
                Then("source value is not affected by the linked field") {
                    val sourceState = sourceDecl.convert("source", StateContainerBuilder.FieldMap())
                    linkedDecl.convert("linked", fieldMapWith(sourceDecl to sourceState))
                    sourceState.withTryLock { update(42) }
                    sourceState.value shouldBe 42
                }
            }
        }

        Given("a reduced field linked to a reduced source with a doubling mapper") {
            val sourceDecl = ReducedFieldDeclaration(0) { _, u: Int -> u }
            val wrappedDecl = ReducedFieldDeclaration(0) { acc, u: Int -> acc + u }
            val linkedDecl = LinkedFieldDeclaration(wrappedDecl, LinkedFieldDeclaration.Link(sourceDecl) { u: Int -> u * 2 })

            When("source is updated") {
                Then("mapper is applied before forwarding to linked field") {
                    val sourceState = sourceDecl.convert("source", StateContainerBuilder.FieldMap())
                    val linkedState = linkedDecl.convert("linked", fieldMapWith(sourceDecl to sourceState))
                    sourceState.withTryLock { update(5) }
                    linkedState.value shouldBe 10
                }
            }

            When("source is updated multiple times") {
                Then("mapper is applied to each update independently") {
                    val sourceState = sourceDecl.convert("source", StateContainerBuilder.FieldMap())
                    val linkedState = linkedDecl.convert("linked", fieldMapWith(sourceDecl to sourceState))
                    sourceState.withTryLock { update(3) }
                    sourceState.withTryLock { update(4) }
                    linkedState.value shouldBe 14
                }
            }
        }

        Given("a reduced List field linked to a mutable Int source") {
            val sourceDecl = MutableValueFieldDeclaration(0)
            val wrappedDecl = ReducedFieldDeclaration(emptyList<Int>()) { acc, u: Int -> acc + u }
            val linkedDecl = LinkedFieldDeclaration(wrappedDecl, LinkedFieldDeclaration.Link(sourceDecl) { it })

            When("mutable source is updated") {
                Then("new value is forwarded to linked field") {
                    val sourceState = sourceDecl.convert("source", StateContainerBuilder.FieldMap())
                    val linkedState = linkedDecl.convert("linked", fieldMapWith(sourceDecl to sourceState))
                    sourceState.withTryLock { update { 5 } }
                    linkedState.value shouldBe listOf(5)
                }
            }

            When("mutable source is updated multiple times") {
                Then("all new values are accumulated in linked field") {
                    val sourceState = sourceDecl.convert("source", StateContainerBuilder.FieldMap())
                    val linkedState = linkedDecl.convert("linked", fieldMapWith(sourceDecl to sourceState))
                    sourceState.withTryLock { update { 3 } }
                    sourceState.withTryLock { update { 7 } }
                    linkedState.value shouldBe listOf(3, 7)
                }
            }
        }
        Given("a three-field chain: mutable source → reduced middle → reduced accumulator tail") {
            val aDecl = MutableValueFieldDeclaration(0)
            val bDecl = ReducedFieldDeclaration(0) { _, u: Int -> u }
            val cDecl = ReducedFieldDeclaration(emptyList<Int>()) { acc, u: Int -> acc + u }
            val bLinked = LinkedFieldDeclaration(bDecl, LinkedFieldDeclaration.Link(aDecl) { it })
            val cLinked = LinkedFieldDeclaration(cDecl, LinkedFieldDeclaration.Link(bLinked) { it })

            When("source is updated once") {
                Then("update propagates through the full chain") {
                    val aState = aDecl.convert("a", StateContainerBuilder.FieldMap())
                    val bState = bLinked.convert("b", fieldMapWith(aDecl to aState))
                    val cState = cLinked.convert("c", fieldMapWith(aDecl to aState, bLinked to bState))
                    aState.withTryLock { update { 5 } }
                    bState.value shouldBe 5
                    cState.value shouldBe listOf(5)
                }
            }

            When("source is updated multiple times") {
                Then("each update propagates through the full chain") {
                    val aState = aDecl.convert("a", StateContainerBuilder.FieldMap())
                    val bState = bLinked.convert("b", fieldMapWith(aDecl to aState))
                    val cState = cLinked.convert("c", fieldMapWith(aDecl to aState, bLinked to bState))
                    aState.withTryLock { update { 3 } }
                    aState.withTryLock { update { 7 } }
                    bState.value shouldBe 7
                    cState.value shouldBe listOf(3, 7)
                }
            }
        }
    }
}
