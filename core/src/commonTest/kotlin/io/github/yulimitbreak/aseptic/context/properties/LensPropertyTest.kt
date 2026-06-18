@file:OptIn(AsepticInternal::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.yulimitbreak.aseptic.context.properties

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.context.BaseAtomicScope
import io.github.yulimitbreak.aseptic.schema.fields.MutableValueFieldDeclaration
import io.github.yulimitbreak.aseptic.state.FieldKey
import io.github.yulimitbreak.aseptic.state.StateContainer
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import io.github.yulimitbreak.aseptic.util.UncheckedMap
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LensPropertyTest : BehaviorSpec() {

    private data class FullName(val first: String, val last: String)

    private class FullNameScope(source: UncheckedMap<FieldKey>) : BaseAtomicScope(source) {
        var first: String by mutableFieldDelegate("first")
        var last: String by mutableFieldDelegate("last")
    }

    private val keys = setOf("first", "last")

    private fun container() = StateContainerBuilder().apply {
        addField("first", false, MutableValueFieldDeclaration("A"))
        addField("last", false, MutableValueFieldDeclaration("B"))
    }.build()

    private fun StateContainer.lens() =
        LensProperty(keys, this) { FullName(it["first"], it["last"]) }

    private fun StateContainer.mutableLens() =
        MutableLensProperty(
            keys = keys,
            container = this,
            snapshotGenerator = { FullName(it["first"], it["last"]) },
            scopeGenerator = { FullNameScope(it) },
        )

    init {
        coroutineTestScope = true

        Given("a lens property over two fields") {
            Then("reading the lens returns a consistent snapshot") {
                container().lens().invoke() shouldBe FullName("A", "B")
            }
            Then("the lens exposes both source field keys") {
                container().lens().keys shouldBe setOf("first", "last")
            }
            Then("subscribing to the lens yields the current snapshot") {
                container().lens().asFlow().first() shouldBe FullName("A", "B")
            }

            When("a source field is updated") {
                Then("the subscription reflects the updated snapshot") {
                    val scope = CoroutineScope(coroutineContext + Job())
                    val container = container()
                    val emissions = scope.record(container.lens().asFlow())
                    container.update<(String) -> String>("first") { "X" }
                    testCoroutineScheduler.advanceUntilIdle()
                    emissions.last() shouldBe FullName("X", "B")
                    scope.cancel()
                }
            }
        }

        Given("a mutable lens property") {
            When("both sources are written atomically") {
                Then("both fields are updated") {
                    val container = container()
                    container.mutableLens().updateAtomic {
                        first = "X"
                        last = "Y"
                    }
                    container.mutableLens().invoke() shouldBe FullName("X", "Y")
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
