package io.github.yulimitbreak.aseptic.context

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.state.FieldKey
import io.github.yulimitbreak.aseptic.state.StateContainer
import io.github.yulimitbreak.aseptic.util.UncheckedMap

/**
 * Base class for generated XxxxContext classes, that provides access to a [StateContainer]
 * and implementation of atomic/snapshot behavior
 */
@AsepticInternal
abstract class BaseAsepticContext<Snapshot, AtomicScope : BaseAtomicScope> protected constructor(
    protected val container: StateContainer,
    private val snapshotGenerator: (UncheckedMap<FieldKey>) -> Snapshot,
    private val atomicScopeGenerator: (UncheckedMap<FieldKey>) -> AtomicScope,
) {

    /**
     * Generate a snapshot of the current state - it is guaranteed to be internally consistent,
     * with no partial atomic writes, but it awaits for all ongoing writes to complete first
     *
     * ```kotlin
     * @Operation
     * suspend fun ProfileContext.save(saveProfile: suspend (String, String) -> Unit) {
     *      // all fields captured at one consistent moment
     *      val current = snapshot()
     *      saveProfile(current.firstName, current.lastName)
     * }
     * ```
     */
    suspend fun snapshot(): Snapshot = container.generateSnapshot(snapshotGenerator)

    /**
     * Generate a snapshot of the current state, ensuring that the specified fields are internally consistent
     * with each other, with no partial atomic writes - but no such guarantees are given for fields
     * outside the locking set.
     *
     * All field types except for [MessageField][io.github.yulimitbreak.aseptic.context.fields.MessageField]
     * can be used as a locking set, as well as
     * [LensProperty][io.github.yulimitbreak.aseptic.context.properties.LensProperty]
     *
     * ```kotlin
     * @Operation
     * suspend fun ProfileContext.logFullName(log: (String) -> Unit) {
     *      // firstName and lastName are mutually consistent; other fields are read opportunistically
     *      val current = snapshot(firstName, lastName)
     *      log("${current.firstName} ${current.lastName}")
     * }
     * ```
     */
    suspend fun snapshot(firstLock: FieldLockProperty, vararg otherLocks: FieldLockProperty) =
        container.generateSnapshot(
            (otherLocks.asIterable() + firstLock).flatMapTo(mutableSetOf()) { it.keys },
            snapshotGenerator
        )

    /**
     * Takes snapshot of the current state in order to generate mutable AtomicScope. After the completion
     * of [update] all changes to the mutable properties will be written atomically to actual state.
     *
     * Updates to [mutable][io.github.yulimitbreak.aseptic.schema.AsepticSchema.mutable] value fields
     * overwrite the current state even if the fields were updated since snapshot was taken. To ensure that
     * relevant fields are unchanged for the duration of the [update], use locking set version of atomic.
     *
     * Dependencies between fields are not preserved in the scope, update to a mutable value field
     * does not update the values of the derived fields.
     *
     * Values returned by members of atomic scope are taken at the moment of execution and do not update
     * for the duration of [atomic] unless updated manually. But because of that they might not reflect
     * the actual state of the fields after the start
     *
     * ```kotlin
     * @Operation
     * suspend fun ProfileContext.reset() {
     *      // both writes land together - no observer ever sees only one of them applied
     *      atomic {
     *          firstName = ""
     *          lastName = ""
     *      }
     * }
     * ```
     */
    suspend fun atomic(update: AtomicScope.() -> Unit) {
        val map = container.frozenSnapshotMap()
        container.updateAtomic {
            atomicScopeGenerator(map).apply(update).updateBuilder.build()
        }
    }

    /**
     * Takes snapshot of the current state in order to generate mutable AtomicScope. After the completion
     * of [update] all changes to the mutable properties will be written atomically to actual state.
     *
     * Prevents fields specified in locking set from being updated by other sources, ensuring that
     * the state of these fields is not changed for the duration of [update]. No such guarantees
     * apply to fields outside the set.
     *
     * **Updated fields must be a subset of the locking set** - violation would throw [IllegalStateException]
     *
     * All field types except for [MessageField][io.github.yulimitbreak.aseptic.context.fields.MessageField]
     * can be used as a locking set, as well as
     * [LensProperty][io.github.yulimitbreak.aseptic.context.properties.LensProperty]
     *
     * Values returned by members of atomic scope for the fields in the locking set are taken at the moment
     * of execution and do not change for the duration of [atomic] unless updated manually. But because of that
     * they might not reflect the actual state of the fields after the start. Values of fields outside
     * the locking set aren't guaranteed to be frozen and might return different values at different points in time.
     *
     * ```kotlin
     * @Operation
     * suspend fun ProfileContext.appendDot() {
     *      // firstName is held stable for the whole read-modify-write - no other writer can
     *      // change it between the read and the commit, so no update is lost
     *      atomic(firstName) {
     *          firstName = firstName + "."
     *      }
     * }
     * ```
     */
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
