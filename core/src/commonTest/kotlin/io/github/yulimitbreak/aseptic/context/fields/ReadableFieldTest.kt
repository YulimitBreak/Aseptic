@file:OptIn(AsepticInternal::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.yulimitbreak.aseptic.context.fields

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.schema.fields.MutableValueFieldDeclaration
import io.github.yulimitbreak.aseptic.state.StateContainer
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReadableFieldTest : BehaviorSpec() {

    private fun container() = StateContainerBuilder().apply {
        addField("count", false, MutableValueFieldDeclaration(10))
    }.build()

    private fun StateContainer.field() = ReadableField<Int>("count", this)

    init {
        coroutineTestScope = true

        Given("a readable field over a container") {
            Then("reading the field returns the current value") {
                container().field().invoke() shouldBe 10
            }
            Then("the field exposes its key") {
                container().field().keys shouldBe setOf("count")
            }
            Then("subscribing to the field yields the current value") {
                container().field().asFlow().first() shouldBe 10
            }

            When("the underlying field is updated") {
                Then("the subscription reflects the updated value") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = container()
                    val emissions = scope.record(container.field().asFlow())
                    container.update<(Int) -> Int>("count") { 25 }
                    testCoroutineScheduler.advanceUntilIdle()
                    emissions.last() shouldBe 25
                    scope.cancel()
                }
            }
        }
    }

    private fun <T> CoroutineScope.record(flow: Flow<T>): List<T> {
        val emissions = mutableListOf<T>()
        launch { flow.collect { emissions.add(it) } }
        return emissions
    }
}
