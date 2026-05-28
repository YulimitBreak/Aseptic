@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.schema.fields.FieldTestUtils.asUpdatable
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
                val state = declaration.convert(StateContainerBuilder.FlowMap(), scope)
                state.flow.value.shouldBeNull()
                state.value.shouldBeNull()
                scope.cancel()
            }

            When("one message is enqueued") {
                Then("flow value is that message") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val state = declaration.convert(StateContainerBuilder.FlowMap(), scope)
                        .asUpdatable<String?, String>()
                    state.locked { update("hello") }
                    testCoroutineScheduler.advanceUntilIdle()
                    state.flow.value shouldBe "hello"
                    scope.cancel()
                }
            }

            When("two messages are enqueued") {
                Then("flow value is the first message (front of queue)") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val state = declaration.convert(StateContainerBuilder.FlowMap(), scope)
                        .asUpdatable<String?, String>()
                    state.locked { update("first") }
                    state.locked { update("second") }
                    testCoroutineScheduler.advanceUntilIdle()
                    state.flow.value shouldBe "first"
                    scope.cancel()
                }
            }

            When("three messages are enqueued") {
                Then("flow value is still the first message") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val state = declaration.convert(StateContainerBuilder.FlowMap(), scope)
                        .asUpdatable<String?, String>()
                    state.locked { update("a") }
                    state.locked { update("b") }
                    state.locked { update("c") }
                    testCoroutineScheduler.advanceUntilIdle()
                    state.flow.value shouldBe "a"
                    scope.cancel()
                }
            }
        }

        Given("a message field of type Int") {
            val declaration = MessageFieldDeclaration<Int>()

            Then("initial value is null") {
                val scope = CoroutineScope(coroutineContext + Job())
                val state = declaration.convert(StateContainerBuilder.FlowMap(), scope)
                state.value.shouldBeNull()
                scope.cancel()
            }

            When("integer message enqueued") {
                Then("flow value is that integer") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val state = declaration.convert(StateContainerBuilder.FlowMap(), scope)
                        .asUpdatable<Int?, Int>()
                    state.locked { update(99) }
                    testCoroutineScheduler.advanceUntilIdle()
                    state.flow.value shouldBe 99
                    scope.cancel()
                }
            }
        }
    }
}
