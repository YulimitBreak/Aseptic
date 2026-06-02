@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.asUpdatable
import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.dequeue
import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.enqueue
import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.locked
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel

class MessageFieldDeclarationTest : BehaviorSpec() {

    init {
        coroutineTestScope = true

        Given("a message field of type String") {
            val declaration = MessageFieldDeclaration<String>()

            Then("initial flow value is null (queue empty)") {
                val scope = CoroutineScope(coroutineContext + Job())
                val state = declaration.convert(StateContainerBuilder.FieldMap())
                state.value.shouldBeNull()
                state.value.shouldBeNull()
                scope.cancel()
            }

            When("one message is enqueued") {
                Then("flow value is that message") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val state = declaration.convert(StateContainerBuilder.FieldMap())
                        .asUpdatable<String?, MessageFieldDeclaration.Update<String>>()
                    state.locked { enqueue("hello") }
                    testCoroutineScheduler.advanceUntilIdle()
                    state.value shouldBe "hello"
                    scope.cancel()
                }
            }

            When("two messages are enqueued") {
                Then("flow value is the first message (front of queue)") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val state = declaration.convert(StateContainerBuilder.FieldMap())
                        .asUpdatable<String?, MessageFieldDeclaration.Update<String>>()
                    state.locked { enqueue("first") }
                    state.locked { enqueue("second") }
                    testCoroutineScheduler.advanceUntilIdle()
                    state.value shouldBe "first"
                    scope.cancel()
                }
            }

            When("first message is dequeued after two enqueues") {
                Then("flow value becomes the second message") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val state = declaration.convert(StateContainerBuilder.FieldMap())
                        .asUpdatable<String?, MessageFieldDeclaration.Update<String>>()
                    state.locked { enqueue("first") }
                    state.locked { enqueue("second") }
                    testCoroutineScheduler.advanceUntilIdle()
                    state.locked { dequeue() }
                    testCoroutineScheduler.advanceUntilIdle()
                    state.value shouldBe "second"
                    scope.cancel()
                }
            }

            When("all messages are dequeued") {
                Then("flow value returns to null") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val state = declaration.convert(StateContainerBuilder.FieldMap())
                        .asUpdatable<String?, MessageFieldDeclaration.Update<String>>()
                    state.locked { enqueue("only") }
                    testCoroutineScheduler.advanceUntilIdle()
                    state.locked { dequeue() }
                    testCoroutineScheduler.advanceUntilIdle()
                    state.value.shouldBeNull()
                    scope.cancel()
                }
            }

            When("dequeue called on empty queue") {
                Then("flow value remains null (no crash)") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val state = declaration.convert(StateContainerBuilder.FieldMap())
                        .asUpdatable<String?, MessageFieldDeclaration.Update<String>>()
                    state.locked { dequeue() }
                    testCoroutineScheduler.advanceUntilIdle()
                    state.value.shouldBeNull()
                    scope.cancel()
                }
            }
        }

        Given("a message field of type Int") {
            val declaration = MessageFieldDeclaration<Int>()

            Then("initial value is null") {
                val scope = CoroutineScope(coroutineContext + Job())
                val state = declaration.convert(StateContainerBuilder.FieldMap())
                state.value.shouldBeNull()
                scope.cancel()
            }

            When("integer message enqueued then dequeued") {
                Then("flow value returns to null") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val state = declaration.convert(StateContainerBuilder.FieldMap())
                        .asUpdatable<Int?, MessageFieldDeclaration.Update<Int>>()
                    state.locked { enqueue(99) }
                    testCoroutineScheduler.advanceUntilIdle()
                    state.value shouldBe 99
                    state.locked { dequeue() }
                    testCoroutineScheduler.advanceUntilIdle()
                    state.value.shouldBeNull()
                    scope.cancel()
                }
            }
        }
    }
}
