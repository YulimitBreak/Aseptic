package io.github.yulimitbreak.aseptic.handle

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.handle.fields.FieldLockProperty
import io.github.yulimitbreak.aseptic.state.FieldKey
import io.github.yulimitbreak.aseptic.state.StateContainer
import io.github.yulimitbreak.aseptic.util.UncheckedMap

@AsepticInternal
abstract class BaseAsepticHandle<Snapshot, AtomicScope : BaseAtomicScope> protected constructor(
    private val container: StateContainer,
    private val snapshotGenerator: (UncheckedMap<FieldKey>) -> Snapshot,
    private val atomicScopeGenerator: (UncheckedMap<FieldKey>) -> AtomicScope,
) {

    suspend fun snapshot(): Snapshot = container.generateSnapshot(snapshotGenerator)

    suspend fun snapshot(firstLock: FieldLockProperty, vararg otherLocks: FieldLockProperty) =
        container.generateSnapshot(
            (otherLocks.asIterable() + firstLock).flatMapTo(mutableSetOf()) { it.keys },
            snapshotGenerator
        )

    suspend fun atomic(update: AtomicScope.() -> Unit) {
        container.updateAtomic { source ->
            atomicScopeGenerator(source).apply(update).updateBuilder.build()
        }
    }

    suspend fun atomic(
        firstLock: FieldLockProperty,
        vararg otherLocks: FieldLockProperty,
        update: AtomicScope.() -> Unit
    ) {
        container.updateAtomic(
            lockRequest = (otherLocks.asIterable() + firstLock).flatMapTo(mutableSetOf()) { it.keys }
        ) { source ->
            atomicScopeGenerator(source).apply(update).updateBuilder.build()
        }
    }
}
