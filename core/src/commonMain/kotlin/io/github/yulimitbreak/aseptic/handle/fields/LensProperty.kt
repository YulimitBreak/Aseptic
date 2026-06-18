@file:OptIn(AsepticInternal::class)

package io.github.yulimitbreak.aseptic.handle.fields

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.handle.BaseAtomicScope
import io.github.yulimitbreak.aseptic.state.FieldKey
import io.github.yulimitbreak.aseptic.state.StateContainer
import io.github.yulimitbreak.aseptic.util.UncheckedMap

open class LensProperty<Lens> @AsepticInternal constructor(
    override val keys: Set<FieldKey>,
    protected val container: StateContainer,
    private val snapshotGenerator: (UncheckedMap<FieldKey>) -> Lens
) : FieldLockProperty {

    suspend operator fun invoke(): Lens = container.generateSnapshot(keys, snapshotGenerator)

    fun asFlow() = container.snapshotFlow(keys, snapshotGenerator)
}

open class MutableLensProperty<Lens, LensScope : BaseAtomicScope> @AsepticInternal constructor(
    keys: Set<FieldKey>,
    container: StateContainer,
    snapshotGenerator: (UncheckedMap<FieldKey>) -> Lens,
    private val scopeGenerator: (UncheckedMap<FieldKey>) -> LensScope
) : LensProperty<Lens>(keys, container, snapshotGenerator) {

    protected suspend fun updateAtomic(update: LensScope.() -> Unit) {
        container.updateAtomic(keys) { source ->
            scopeGenerator(source).apply(update).updateBuilder.build()
        }
    }
}
