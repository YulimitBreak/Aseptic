package io.github.yulimitbreak.aseptic.state

import kotlinx.coroutines.flow.Flow
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
 * - Read-only fields (derived, delta-derived): implement [FieldState] directly.
 * - Writable fields (mutable, reduced, message): extend [UpdatableFieldState].
 */
internal abstract class FieldState<out T> {

    abstract fun produceFlow(): Flow<T>
    abstract val value: T

    abstract fun addUpdateCallback(callback: (T) -> Unit)
}

/**
 * A [FieldState] that accepts writes serialized under a per-field [Mutex].
 *
 * The mutex is owned by this class and is exposed only via [lock]/[unlock], which are called
 * by [StateContainer] when orchestrating atomic commits. [update] can only be called from under lock
 *
 * @param T the type of the field value.
 * @param Update the type of the update message accepted by this field.
 */
internal abstract class UpdatableFieldState<out T, in Update> : FieldState<T>() {

    private val mutex = Mutex()

    /**
     * Applies [update] to the field's internal state.
     *
     * Must only be called while [mutex] is held. Throws [IllegalStateException] otherwise.
     */
    fun update(update: Update) {
        check(mutex.isLocked) { "update() must only be called while holding the field mutex" }
        doUpdate(update)
    }

    protected abstract fun doUpdate(update: Update)

    /** Acquires the field mutex. Called by [StateContainer] before committing writes. */
    suspend fun lock() = mutex.lock()

    /** Releases the field mutex. Called by [StateContainer] after committing writes. */
    fun unlock() = mutex.unlock()
}
