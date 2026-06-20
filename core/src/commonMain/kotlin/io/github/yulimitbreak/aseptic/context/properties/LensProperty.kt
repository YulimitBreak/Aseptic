@file:OptIn(AsepticInternal::class)

package io.github.yulimitbreak.aseptic.context.properties

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.context.BaseAtomicScope
import io.github.yulimitbreak.aseptic.context.FieldLockProperty
import io.github.yulimitbreak.aseptic.state.FieldKey
import io.github.yulimitbreak.aseptic.state.StateContainer
import io.github.yulimitbreak.aseptic.util.UncheckedMap

/**
 * An accessor to a declared [lens][io.github.yulimitbreak.aseptic.schema.AsepticSchema.lens] - it
 * is a convenience way to retrieve and observe partial snapshots
 */
open class LensProperty<Lens> @AsepticInternal constructor(
    override val keys: Set<FieldKey>,
    protected val container: StateContainer,
    private val snapshotGenerator: (UncheckedMap<FieldKey>) -> Lens,
) : FieldLockProperty {

    /**
     * Returns an internally consistent instance of [Lens] — creates a partial snapshot
     * of the lens' fields
     */
    suspend operator fun invoke(): Lens = container.generateSnapshot(keys, snapshotGenerator)

    /**
     * Returns a flow emitting [Lens] - observes the partial snapshot of the lens' fields
     */
    fun asFlow() = container.snapshotFlow(keys, snapshotGenerator)
}

/**
 * An accessor to a declared [lens][io.github.yulimitbreak.aseptic.schema.AsepticSchema.lens] that has
 * [mutable value][io.github.yulimitbreak.aseptic.schema.AsepticSchema.mutable] fields - it
 * is a convenience way to retrieve and observe partial snapshots, and update them atomically
 */
open class MutableLensProperty<Lens, LensScope : BaseAtomicScope> @AsepticInternal constructor(
    keys: Set<FieldKey>,
    container: StateContainer,
    snapshotGenerator: (UncheckedMap<FieldKey>) -> Lens,
    private val scopeGenerator: (UncheckedMap<FieldKey>) -> LensScope,
) : LensProperty<Lens>(keys, container, snapshotGenerator) {

    /**
     * Takes a snapshot of the current state to generate a mutable AtomicScope for the [Lens].
     * After [update] completes, all changes to the mutable properties are written
     * atomically to the actual state. Prevents this lens' fields from being updated by other sources until
     * completion
     *
     * @see io.github.yulimitbreak.aseptic.context.BaseAsepticContext.atomic
     */
    suspend fun updateAtomic(update: LensScope.() -> Unit) {
        container.updateAtomic(keys) { source ->
            scopeGenerator(source).apply(update).updateBuilder.build()
        }
    }
}
