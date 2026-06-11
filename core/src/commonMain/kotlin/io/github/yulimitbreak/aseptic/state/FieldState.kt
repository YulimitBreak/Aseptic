package io.github.yulimitbreak.aseptic.state

import kotlinx.coroutines.sync.Mutex

/**
 * Runtime representation of a single field in a [StateContainer].
 *
 * Each [io.github.yulimitbreak.aseptic.schema.fields.FieldDeclaration] produces one [FieldState]
 * instance via [io.github.yulimitbreak.aseptic.schema.fields.FieldDeclaration.convert] during
 * state construction.
 *
 * Subclasses fall into two categories:
 * - Read-only fields (derived): implement [FieldState] directly.
 * - Writable fields (mutable, reduced, message, tracking): extend [UpdatableFieldState].
 */
internal abstract class FieldState<out T>(
    /** Name of this field in the owning schema. */
    val name: String,
) {

    /**
     * Registers this field into the given [snapshotFlowBuilder].
     *
     * Implementations must call [buildSnapshotFlow] on every source field they depend on **before**
     * adding themselves as a mapper, so dependencies are registered (and computed) first.
     * Mapper bodies should read from the cache, since they may run on every source update.
     */
    abstract fun buildSnapshotFlow(snapshotFlowBuilder: SnapshotFlowBuilder)

    abstract val value: T

    /**
     * Fields that this field is immediately dependent on - i.e. their update will cause this field to update.
     * Non-transitive
     */
    internal open val dependencies = emptySet<FieldState<*>>()

    /**
     * Returns the set of source fields (that have a source flow and a mutex and can be updated)
     * that this field transitively depends on
     */
    internal fun getUpdateSources(): Set<UpdatableFieldState<*, *, *>> {
        val result = dependencies.flatMapTo(mutableSetOf()) { it.getUpdateSources() }
        if (this is UpdatableFieldState<*, *, *>) {
            result.add(this)
        }
        return result
    }
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
 * @param TrackedUpdate the value emitted to linked fields after each write.
 */
internal abstract class UpdatableFieldState<out T, in Update, out TrackedUpdate>(
    name: String,
    val isLockable: Boolean = true,
) : FieldState<T>(name) {

    private val updateCallbacks: MutableList<(TrackedUpdate) -> Unit> = mutableListOf()

    private val mutex = if (isLockable) Mutex() else null

    /**
     * Applies [update] to the field's internal state and notifies all registered callbacks.
     *
     * Must only be called while [mutex] is held. Throws [IllegalStateException] otherwise.
     */
    fun update(update: Update) {
        check(mutex?.isLocked ?: true) { "update() must only be called while holding the field mutex" }
        val output = doUpdate(update)
        updateCallbacks.forEach { it.invoke(output) }
    }

    /**
     * Applies [update] to internal state and returns the [TrackedUpdate] to broadcast.
     *
     * Called by [update] under the field mutex.
     */
    internal abstract fun doUpdate(update: Update): TrackedUpdate

    /**
     * Registers [callback] to be invoked with the [TrackedUpdate] after every write.
     * Used by [io.github.yulimitbreak.aseptic.schema.fields.TrackingFieldDeclaration] to wire
     * automatic update propagation between fields. Must be called at construction time,
     * before any writes can occur.
     */
    internal open fun addUpdateCallback(callback: (TrackedUpdate) -> Unit) {
        updateCallbacks.add(callback)
    }

    /**
     * Acquires the field mutex. Called by [StateContainer] before committing writes.
     * If not lockable, does nothing
     */
    internal suspend fun lock() {
        mutex?.lock()
    }

    /**
     * Attempts to acquire the field mutex without suspending. Returns `false` if already held.'
     * Returns `true` if not lockable
     */
    internal fun tryLock(): Boolean {
        return mutex?.tryLock() ?: true
    }

    /**
     * Releases the field mutex. Called by [StateContainer] after committing writes.
     * If not lockable, does nothing
     */
    internal fun unlock() {
        mutex?.unlock()
    }
}
