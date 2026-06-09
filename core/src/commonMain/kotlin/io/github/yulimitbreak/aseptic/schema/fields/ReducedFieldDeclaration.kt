@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import io.github.yulimitbreak.aseptic.state.UpdatableFieldState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Declaration of a field updated by folding incoming update messages into its current value.
 *
 * Unlike [MutableValueFieldDeclaration] where the setter writes a value directly, this field
 * accepts *update messages* of type [U] and derives the new state by applying [update] to
 * the current value. All updates are serialized under a per-field mutex.
 * Useful for append-only or event-driven state (e.g. lists, counters).
 *
 * @param T the type of the field value.
 * @param U the type of the update message.
 * @see io.github.yulimitbreak.aseptic.schema.AsepticSchema.reduced
 */
class ReducedFieldDeclaration<T, U> internal constructor(
    /** The value the field holds before any update message is received. */
    internal val initial: T,
    /** Produces the next field value from the current value and an incoming update message. */
    internal val update: (old: T, update: U) -> T,
) : LinkableFieldDeclaration<T, U, U>() {
    override fun convert(
        fields: StateContainerBuilder.FieldMap,
        coroutineScope: CoroutineScope
    ): UpdatableFieldState<T, U, U> =
        State(initial, update)

    private class State<T, U>(
        initial: T,
        private val reducer: (T, U) -> T,
    ) : UpdatableFieldState<T, U, U>() {

        override val value: T get() = stateFlow.value

        override fun provideFlow(): Flow<T> = stateFlow

        private val stateFlow = MutableStateFlow(initial)

        override fun doUpdate(update: U): U {
            stateFlow.update { reducer(it, update) }
            return update
        }
    }
}
