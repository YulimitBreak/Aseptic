package io.github.yulimitbreak.aseptic.state

import kotlinx.coroutines.flow.StateFlow
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

    // TODO replace with calculating values on access/from UncheckedMap
    abstract val flow: StateFlow<T>

    open val value: T get() = flow.value
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
internal abstract class UpdatableFieldState<out T, in Update, out LinkableUpdate> : FieldState<T>() {

    private var updateCallbacks: MutableList<(LinkableUpdate) -> Unit> = mutableListOf()

    private val mutex = Mutex()

    /**
     * Applies [update] to the field's internal state.
     *
     * Must only be called while [mutex] is held. Throws [IllegalStateException] otherwise.
     */
    fun update(update: Update) {
        check(mutex.isLocked) { "update() must only be called while holding the field mutex" }
        val output = doUpdate(update)
        updateCallbacks.forEach { it.invoke(output) }
    }

    internal abstract fun doUpdate(update: Update): LinkableUpdate

    /**
     * Add update callback - primarily to be used with Linked fields
     */
    internal fun addUpdateCallback(callback: (LinkableUpdate) -> Unit) {
        updateCallbacks.add(callback)
    }

    /** Acquires the field mutex. Called by [StateContainer] before committing writes. */
    internal suspend fun lock() = mutex.lock()

    /**
     * Attempts to acquire the field mutex, returns false on failure
     */
    internal fun tryLock() = mutex.tryLock()

    /** Releases the field mutex. Called by [StateContainer] after committing writes. */
    internal fun unlock() = mutex.unlock()
}
