@file:OptIn(AsepticInternal::class)

package io.github.yulimitbreak.aseptic.context.fields

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.schema.fields.MessageFieldDeclaration
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class MessageFieldTest : BehaviorSpec() {

    private fun container() = StateContainerBuilder().apply {
        addField("msg", false, MessageFieldDeclaration<String>())
    }.build()

    init {
        coroutineTestScope = true

        Given("a message field over a container") {
            When("a message is emitted") {
                Then("the emitted message is immediately accessible") {
                    val container = container()
                    MessageField<String>("msg", container).emit("hello")
                    container.get<String?>("msg") shouldBe "hello"
                }
            }
            When("multiple messages are emitted") {
                Then("the first emitted message is accessible first") {
                    val container = container()
                    val field = MessageField<String>("msg", container)
                    field.emit("first")
                    field.emit("second")
                    container.get<String?>("msg") shouldBe "first"
                }
            }
        }
    }
}
