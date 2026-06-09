@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import io.github.yulimitbreak.aseptic.state.UpdatableFieldState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Declaration of a mutable field whose value is set directly by operations.
 *
 * At runtime backed by a `MutableStateFlow` initialised with [initial].
 * The generated state handle exposes a typed setter. All writes are serialized under a mutex.
 *
 * @param T the type of the field value.
 * @see io.github.yulimitbreak.aseptic.schema.AsepticSchema.mutable
 */
class MutableValueFieldDeclaration<T> internal constructor(
    /** The value the field holds before any update is applied. */
    internal val initial: T,
) : LinkableFieldDeclaration<T, (T) -> T, T>() {
    override fun convert(
        fields: StateContainerBuilder.FieldMap,
        coroutineScope: CoroutineScope
    ): UpdatableFieldState<T, (T) -> T, T> = State(initial)

    private class State<T>(
        initial: T
    ) : UpdatableFieldState<T, (T) -> T, T>() {

        override fun doUpdate(update: (T) -> T): T {
            stateFlow.update(update)
            return stateFlow.value
        }

        override val value: T get() = stateFlow.value

        override fun provideFlow(): Flow<T> = stateFlow

        private val stateFlow = MutableStateFlow(initial)
    }
}
