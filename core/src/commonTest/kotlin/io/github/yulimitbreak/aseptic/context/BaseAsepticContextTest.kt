@file:OptIn(AsepticInternal::class)

package io.github.yulimitbreak.aseptic.context

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.context.fields.UpdatableField
import io.github.yulimitbreak.aseptic.schema.fields.MutableValueFieldDeclaration
import io.github.yulimitbreak.aseptic.state.FieldKey
import io.github.yulimitbreak.aseptic.state.StateContainer
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import io.github.yulimitbreak.aseptic.util.UncheckedMap
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class BaseAsepticContextTest : BehaviorSpec() {

    private data class Snap(val first: String, val last: String)

    private class CtxScope(source: UncheckedMap<FieldKey>) : BaseAtomicScope(source) {
        var first: String by mutableFieldDelegate("first")
        var last: String by mutableFieldDelegate("last")
    }

    private class Ctx(container: StateContainer) : BaseAsepticContext<Snap, CtxScope>(
        container,
        snapshotGenerator = { Snap(it["first"], it["last"]) },
        atomicScopeGenerator = { CtxScope(it) },
    )

    private fun container() = StateContainerBuilder().apply {
        addField("first", false, MutableValueFieldDeclaration("A"))
        addField("last", false, MutableValueFieldDeclaration("B"))
    }.build()

    private fun StateContainer.lock(key: FieldKey) =
        UpdatableField<String, (String) -> String>(key, this)

    init {
        coroutineTestScope = true

        Given("a context over two mutable fields") {
            Then("the current values are readable as a consistent snapshot") {
                Ctx(container()).snapshot() shouldBe Snap("A", "B")
            }

            When("a deferred atomic writes both fields") {
                Then("both writes are applied") {
                    val ctx = Ctx(container())
                    ctx.atomic {
                        first = "X"
                        last = "Y"
                    }
                    ctx.snapshot() shouldBe Snap("X", "Y")
                }
            }

            When("a pre-locked atomic writes only the locked fields") {
                Then("both writes are applied") {
                    val container = container()
                    val ctx = Ctx(container)
                    ctx.atomic(container.lock("first"), container.lock("last")) {
                        first = "P"
                        last = "Q"
                    }
                    ctx.snapshot() shouldBe Snap("P", "Q")
                }
            }

            When("a pre-locked atomic writes a field outside the lock set") {
                Then("it fails with IllegalStateException") {
                    val container = container()
                    val ctx = Ctx(container)
                    shouldThrow<IllegalStateException> {
                        ctx.atomic(container.lock("first")) {
                            last = "Z"
                        }
                    }
                }
            }

            When("a fine-grained snapshot is taken over a lock") {
                Then("it returns the snapshot of the current state") {
                    val container = container()
                    Ctx(container).snapshot(container.lock("first")) shouldBe Snap("A", "B")
                }
            }
        }
    }
}
