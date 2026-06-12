@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.dequeue
import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.enqueue
import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.withTryLock
import io.github.yulimitbreak.aseptic.state.FieldState
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import io.github.yulimitbreak.aseptic.state.UpdatableFieldState
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

@Suppress("UNCHECKED_CAST")
private fun <T : Any> FieldState<T?>.asMessageState() =
    this as UpdatableFieldState<T?, MessageFieldDeclaration.Update<T>, Unit>

class MessageFieldTest : BehaviorSpec() {

    init {
        Given("a message field of type String") {
            val declaration = MessageFieldDeclaration<String>()

            Then("initial value is null (queue empty)") {
                val state = declaration.convert("msg", StateContainerBuilder.FieldMap())
                state.value.shouldBeNull()
            }

            When("one message is enqueued") {
                Then("value is that message") {
                    val state = declaration.convert("msg", StateContainerBuilder.FieldMap()).asMessageState()
                    state.withTryLock { enqueue("hello") }
                    state.value shouldBe "hello"
                }
            }

            When("two messages are enqueued") {
                Then("value is the first message (front of queue)") {
                    val state = declaration.convert("msg", StateContainerBuilder.FieldMap()).asMessageState()
                    state.withTryLock { enqueue("first") }
                    state.withTryLock { enqueue("second") }
                    state.value shouldBe "first"
                }
            }

            When("first message is dequeued after two enqueues") {
                Then("value becomes the second message") {
                    val state = declaration.convert("msg", StateContainerBuilder.FieldMap()).asMessageState()
                    state.withTryLock { enqueue("first") }
                    state.withTryLock { enqueue("second") }
                    state.withTryLock { dequeue() }
                    state.value shouldBe "second"
                }
            }

            When("all messages are dequeued") {
                Then("value returns to null") {
                    val state = declaration.convert("msg", StateContainerBuilder.FieldMap()).asMessageState()
                    state.withTryLock { enqueue("only") }
                    state.withTryLock { dequeue() }
                    state.value.shouldBeNull()
                }
            }

            When("dequeue called on empty queue") {
                Then("value remains null (no crash)") {
                    val state = declaration.convert("msg", StateContainerBuilder.FieldMap()).asMessageState()
                    state.withTryLock { dequeue() }
                    state.value.shouldBeNull()
                }
            }
        }

        Given("a message field of type Int") {
            val declaration = MessageFieldDeclaration<Int>()

            Then("initial value is null") {
                val state = declaration.convert("msg", StateContainerBuilder.FieldMap())
                state.value.shouldBeNull()
            }

            When("integer message enqueued then dequeued") {
                Then("value returns to null") {
                    val state = declaration.convert("msg", StateContainerBuilder.FieldMap()).asMessageState()
                    state.withTryLock { enqueue(99) }
                    state.value shouldBe 99
                    state.withTryLock { dequeue() }
                    state.value.shouldBeNull()
                }
            }
        }
    }
}
