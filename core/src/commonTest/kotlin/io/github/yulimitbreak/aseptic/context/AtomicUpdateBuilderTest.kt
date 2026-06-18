@file:OptIn(AsepticInternal::class)

package io.github.yulimitbreak.aseptic.context

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class AtomicUpdateBuilderTest : BehaviorSpec() {

    init {
        Given("an empty builder") {
            Then("an unset key yields the default value") {
                AtomicUpdateBuilder().getMutable("x", 42) shouldBe 42
            }
            Then("the resulting update is empty") {
                AtomicUpdateBuilder().build() shouldBe emptyMap()
            }
        }

        Given("a builder") {
            When("a mutable value is staged") {
                Then("reading a staged key yields the staged value, ignoring the default") {
                    val builder = AtomicUpdateBuilder()
                    builder.setMutable("x", 7)
                    builder.getMutable("x", 42) shouldBe 7
                }
                Then("the resulting update contains a mapper that yields the staged value") {
                    val builder = AtomicUpdateBuilder()
                    builder.setMutable("x", 9)
                    val mapper = builder.build().getValue("x").single()
                    @Suppress("UNCHECKED_CAST")
                    (mapper as (Int) -> Int).invoke(0) shouldBe 9
                }
                And("staged again with a different value") {
                    Then("the later value overwrites the earlier one") {
                        val builder = AtomicUpdateBuilder()
                        builder.setMutable("x", 7)
                        builder.setMutable("x", 9)
                        builder.getMutable("x", 0) shouldBe 9
                    }
                }
            }

            When("reduced updates are enqueued") {
                Then("the resulting update preserves enqueued updates in order") {
                    val builder = AtomicUpdateBuilder()
                    builder.enqueueUpdate("r", 1)
                    builder.enqueueUpdate("r", 2)
                    builder.enqueueUpdate("r", 3)
                    builder.build().getValue("r") shouldBe listOf(1, 2, 3)
                }
            }

            When("both a mutable value and reduced updates are staged") {
                Then("the resulting update contains both keys") {
                    val builder = AtomicUpdateBuilder()
                    builder.setMutable("m", 5)
                    builder.enqueueUpdate("r", 1)
                    builder.build().keys shouldBe setOf("m", "r")
                }
            }
        }
    }
}
