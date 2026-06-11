@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.fieldMapWith
import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.withTryLock
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class LinkedFieldTest : BehaviorSpec() {

    init {
        Given("a reduced accumulator tracking a reduced source with identity mapper") {
            val sourceDecl = ReducedFieldDeclaration(0) { _, u: Int -> u }
            val wrappedDecl = ReducedFieldDeclaration(0) { acc, u: Int -> acc + u }
            val trackingDecl = TrackingFieldDeclaration(wrappedDecl, TrackingFieldDeclaration.Link(sourceDecl) { it })

            Then("initial value matches the wrapped field initial") {
                val sourceState = sourceDecl.convert("source", StateContainerBuilder.FieldMap())
                val trackingState = trackingDecl.convert("tracking", fieldMapWith(sourceDecl to sourceState))
                trackingState.value shouldBe 0
            }

            When("source is updated once") {
                Then("tracking field reflects the update") {
                    val sourceState = sourceDecl.convert("source", StateContainerBuilder.FieldMap())
                    val trackingState = trackingDecl.convert("tracking", fieldMapWith(sourceDecl to sourceState))
                    sourceState.withTryLock { update(10) }
                    trackingState.value shouldBe 10
                }
            }

            When("source is updated multiple times") {
                Then("tracking field accumulates all updates") {
                    val sourceState = sourceDecl.convert("source", StateContainerBuilder.FieldMap())
                    val trackingState = trackingDecl.convert("tracking", fieldMapWith(sourceDecl to sourceState))
                    sourceState.withTryLock { update(3) }
                    sourceState.withTryLock { update(7) }
                    trackingState.value shouldBe 10
                }
            }

            When("source is updated") {
                Then("source value is not affected by the tracking field") {
                    val sourceState = sourceDecl.convert("source", StateContainerBuilder.FieldMap())
                    trackingDecl.convert("tracking", fieldMapWith(sourceDecl to sourceState))
                    sourceState.withTryLock { update(42) }
                    sourceState.value shouldBe 42
                }
            }
        }

        Given("a reduced field tracking a reduced source with a doubling mapper") {
            val sourceDecl = ReducedFieldDeclaration(0) { _, u: Int -> u }
            val wrappedDecl = ReducedFieldDeclaration(0) { acc, u: Int -> acc + u }
            val trackingDecl = TrackingFieldDeclaration(
                wrappedDecl,
                TrackingFieldDeclaration.Link(sourceDecl) { u: Int -> u * 2 }
            )

            When("source is updated") {
                Then("mapper is applied before forwarding to tracking field") {
                    val sourceState = sourceDecl.convert("source", StateContainerBuilder.FieldMap())
                    val trackingState = trackingDecl.convert("tracking", fieldMapWith(sourceDecl to sourceState))
                    sourceState.withTryLock { update(5) }
                    trackingState.value shouldBe 10
                }
            }

            When("source is updated multiple times") {
                Then("mapper is applied to each update independently") {
                    val sourceState = sourceDecl.convert("source", StateContainerBuilder.FieldMap())
                    val trackingState = trackingDecl.convert("tracking", fieldMapWith(sourceDecl to sourceState))
                    sourceState.withTryLock { update(3) }
                    sourceState.withTryLock { update(4) }
                    trackingState.value shouldBe 14
                }
            }
        }

        Given("a reduced List field tracking a mutable Int source") {
            val sourceDecl = MutableValueFieldDeclaration(0)
            val wrappedDecl = ReducedFieldDeclaration(emptyList<Int>()) { acc, u: Int -> acc + u }
            val trackingDecl = TrackingFieldDeclaration(wrappedDecl, TrackingFieldDeclaration.Link(sourceDecl) { it })

            When("mutable source is updated") {
                Then("new value is forwarded to tracking field") {
                    val sourceState = sourceDecl.convert("source", StateContainerBuilder.FieldMap())
                    val trackingState = trackingDecl.convert("tracking", fieldMapWith(sourceDecl to sourceState))
                    sourceState.withTryLock { update { 5 } }
                    trackingState.value shouldBe listOf(5)
                }
            }

            When("mutable source is updated multiple times") {
                Then("all new values are accumulated in tracking field") {
                    val sourceState = sourceDecl.convert("source", StateContainerBuilder.FieldMap())
                    val trackingState = trackingDecl.convert("tracking", fieldMapWith(sourceDecl to sourceState))
                    sourceState.withTryLock { update { 3 } }
                    sourceState.withTryLock { update { 7 } }
                    trackingState.value shouldBe listOf(3, 7)
                }
            }
        }

        Given("a three-field chain: mutable source → reduced middle → reduced accumulator tail") {
            val aDecl = MutableValueFieldDeclaration(0)
            val bDecl = ReducedFieldDeclaration(0) { _, u: Int -> u }
            val cDecl = ReducedFieldDeclaration(emptyList<Int>()) { acc, u: Int -> acc + u }
            val bTracking = TrackingFieldDeclaration(bDecl, TrackingFieldDeclaration.Link(aDecl) { it })
            val cTracking = TrackingFieldDeclaration(cDecl, TrackingFieldDeclaration.Link(bTracking) { it })

            When("source is updated once") {
                Then("update propagates through the full chain") {
                    val aState = aDecl.convert("a", StateContainerBuilder.FieldMap())
                    val bState = bTracking.convert("b", fieldMapWith(aDecl to aState))
                    val cState = cTracking.convert("c", fieldMapWith(aDecl to aState, bTracking to bState))
                    aState.withTryLock { update { 5 } }
                    bState.value shouldBe 5
                    cState.value shouldBe listOf(5)
                }
            }

            When("source is updated multiple times") {
                Then("each update propagates through the full chain") {
                    val aState = aDecl.convert("a", StateContainerBuilder.FieldMap())
                    val bState = bTracking.convert("b", fieldMapWith(aDecl to aState))
                    val cState = cTracking.convert("c", fieldMapWith(aDecl to aState, bTracking to bState))
                    aState.withTryLock { update { 3 } }
                    aState.withTryLock { update { 7 } }
                    bState.value shouldBe 7
                    cState.value shouldBe listOf(3, 7)
                }
            }
        }
    }
}
