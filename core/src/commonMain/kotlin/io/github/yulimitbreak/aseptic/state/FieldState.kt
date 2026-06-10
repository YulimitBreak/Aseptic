package io.github.yulimitbreak.aseptic.state

import kotlinx.coroutines.sync.Mutex

/**
 * Runtime representation of a single field in a [StateContainer].
 *
 * Each [io.github.yulimitbreak.aseptic.schema.fields.FieldDeclaration] produces one [FieldState]
 * instance via [io.github.yulimitbreak.aseptic.schema.fields.FieldDeclaration.convert] during
 * state construction. The [flow] is the observable source of truth for that field; [value] is
 * a shortcut for the current snapshot.
 *
 * Subclasses fall into two categories:
 * - Read-only fields (derived): implement [FieldState] directly.
 * - Writable fields (mutable, reduced, message, linked): extend [UpdatableFieldState].
 */
internal abstract class FieldState<out T> {

    /**
     * Construct [SnapshotFlowBuilder] from current state
     *
     * Implementation should call [buildSnapshotFlow] on FieldStates that this state depends on **before**
     * adding current state as a mapper
     * Calculation in mappers should use cache, as this can be called on every source field update
     */
    abstract fun buildSnapshotFlow(snapshotFlowBuilder: SnapshotFlowBuilder)

    abstract val value: T
}

/**
 * A [FieldState] that accepts writes serialized under a per-field [Mutex].
 *
 * The mutex is owned by this class and is exposed only via [lock]/[unlock], which are called
 * by [StateContainer] when orchestrating atomic commits. [update] can only be called while the
 * mutex is held.
 *
 * @param T the type of the field value.
 * @param Update the type of the write message accepted by this field.
 * @param LinkableUpdate the value emitted to linked fields after each write.
 */
internal abstract class UpdatableFieldState<out T, in Update, out LinkableUpdate> : FieldState<T>() {

    private var updateCallbacks: MutableList<(LinkableUpdate) -> Unit> = mutableListOf()

    private val mutex = Mutex()

    /**
     * Applies [update] to the field's internal state and notifies all registered callbacks.
     *
     * Must only be called while [mutex] is held. Throws [IllegalStateException] otherwise.
     */
    fun update(update: Update) {
        check(mutex.isLocked) { "update() must only be called while holding the field mutex" }
        val output = doUpdate(update)
        updateCallbacks.forEach { it.invoke(output) }
    }

    /**
     * Applies [update] to internal state and returns the [LinkableUpdate] to broadcast.
     * Called by [update] under the field mutex.
     */
    internal abstract fun doUpdate(update: Update): LinkableUpdate

    /**
     * Registers [callback] to be invoked with the [LinkableUpdate] after every write.
     * Used by [io.github.yulimitbreak.aseptic.schema.fields.LinkedFieldDeclaration] to wire
     * automatic update propagation between fields. Must be called at construction time,
     * before any writes can occur.
     */
    internal fun addUpdateCallback(callback: (LinkableUpdate) -> Unit) {
        updateCallbacks.add(callback)
    }

    /** Acquires the field mutex. Called by [StateContainer] before committing writes. */
    internal suspend fun lock() = mutex.lock()

    /**
     * Attempts to acquire the field mutex without suspending. Returns `false` if already held.
     */
    internal fun tryLock() = mutex.tryLock()

    /** Releases the field mutex. Called by [StateContainer] after committing writes. */
    internal fun unlock() = mutex.unlock()
}
