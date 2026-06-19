@file:OptIn(AsepticInternal::class)

package io.github.yulimitbreak.aseptic.context

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.state.FieldKey
import io.github.yulimitbreak.aseptic.util.UncheckedMap
import io.github.yulimitbreak.aseptic.util.UncheckedMapWrapper
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class BaseAtomicScopeTest : BehaviorSpec() {

    private class TestScope(
        source: UncheckedMap<FieldKey>,
        builder: AtomicUpdateBuilder = AtomicUpdateBuilder(),
    ) : BaseAtomicScope(source, builder) {
        val readOnly: Int = readOnlyValue("ro")
        var mutableValue: Int by mutableFieldDelegate("mut")

        val reduced = ReducedFieldAccessor<Int, Int>("red")

        val staticLens = readOnlyLensValue { Lens(it["ro"], it["extra"]) }

        fun <S : BaseAtomicScope> nest(generator: (UncheckedMap<FieldKey>, AtomicUpdateBuilder) -> S) =
            mutableLensProperty(generator)

        data class Lens(val ro: Int, val extra: String)
    }

    private fun source() =
        UncheckedMapWrapper(mapOf<FieldKey, Any?>("ro" to 1, "mut" to 10, "red" to 100, "extra" to "Extra"))

    @Suppress("UNCHECKED_CAST")
    private fun AtomicUpdateBuilder.stagedMutable(key: FieldKey): Int =
        (build().getValue(key).single() as (Int) -> Int).invoke(0)

    init {
        Given("a scope over a source map") {
            Then("a read-only field reflects the source value") {
                TestScope(source()).readOnly shouldBe 1
            }
            Then("a mutable field initially reflects the source value") {
                TestScope(source()).mutableValue shouldBe 10
            }
            Then("the previous value of a reduced field reflects the source") {
                TestScope(source()).reduced.previous shouldBe 100
            }
            Then("a read-only lens field reflects the source values") {
                TestScope(source()).staticLens.run {
                    ro shouldBe 1
                    extra shouldBe "Extra"
                }
            }

            When("the mutable value is written") {
                Then("reading reflects the new value immediately") {
                    val scope = TestScope(source())
                    scope.mutableValue = 20
                    scope.mutableValue shouldBe 20
                }
                Then("the new value is queued for atomic commit") {
                    val builder = AtomicUpdateBuilder()
                    TestScope(source(), builder).mutableValue = 20
                    builder.stagedMutable("mut") shouldBe 20
                }
                Then("the lens containing this field reflects the new value immediately") {
                    val parent = TestScope(source())
                    val lens = parent.nest { source, builder -> TestScope(source, builder) }
                    parent.mutableValue = 30
                    lens.mutableValue shouldBe 30
                }
            }

            When("multiple reduced updates are enqueued") {
                Then("they are recorded in the builder in order") {
                    val builder = AtomicUpdateBuilder()
                    val scope = TestScope(source(), builder)
                    scope.reduced.enqueue(5)
                    scope.reduced.enqueue(7)
                    builder.build().getValue("red") shouldBe listOf(5, 7)
                }
            }

            When("a value is written through a nested lens scope") {
                Then("the write is visible in the parent scope") {
                    val parent = TestScope(source())
                    val lens = parent.nest { source, builder -> TestScope(source, builder) }
                    lens.mutableValue = 30
                    parent.mutableValue shouldBe 30
                }
            }
        }
    }
}
