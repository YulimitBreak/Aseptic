@file:OptIn(AsepticInternal::class)

package io.github.yulimitbreak.aseptic.handle.fields

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.state.AtomicUpdate
import io.github.yulimitbreak.aseptic.state.FieldKey
import io.github.yulimitbreak.aseptic.state.StateContainer
import io.github.yulimitbreak.aseptic.util.UncheckedMap

open class LensProperty<Lens> @AsepticInternal constructor(
    internal val keys: Set<FieldKey>,
    protected val container: StateContainer,
    private val snapshotGenerator: (UncheckedMap<FieldKey>) -> Lens
) {

    suspend operator fun invoke(): Lens = container.generateSnapshot(keys, snapshotGenerator)

    fun asFlow() = container.snapshotFlow(keys, snapshotGenerator)

    protected suspend fun updateAtomic(update: (Lens) -> AtomicUpdate) {
        container.updateAtomic(keys) { update(snapshotGenerator(it)) }
    }
}
