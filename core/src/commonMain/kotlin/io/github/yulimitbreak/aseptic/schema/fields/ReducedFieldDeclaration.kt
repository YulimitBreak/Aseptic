@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.state.FieldKey
import io.github.yulimitbreak.aseptic.state.SnapshotFlowBuilder
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import io.github.yulimitbreak.aseptic.state.UpdatableFieldState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Declaration of a field updated by folding incoming update messages into its current value.
 *
 * Unlike [MutableValueFieldDeclaration] where the setter writes a value directly, this field
 * accepts *update messages* of type [U] and derives the new state by applying [update] to
 * the current value.
 *
 * @param initial initial value of the field
 * @param update produces the next field value from the current one and an incoming update message
 * @param T the type of the field value.
 * @param U the type of the update message.
 * @see io.github.yulimitbreak.aseptic.schema.AsepticSchema.reduced
 */
class ReducedFieldDeclaration<T, U> internal constructor(
    internal val initial: T,
    internal val update: (old: T, update: U) -> T,
) : TrackingCapableFieldDeclaration<T, U, U>() {

    override fun convert(
        key: FieldKey,
        fields: StateContainerBuilder.FieldMap,
    ): UpdatableFieldState<T, U, U> =
        State(key, isLockable = true, initial, update)

    override fun convertForTracking(
        key: FieldKey,
        fields: StateContainerBuilder.FieldMap
    ): UpdatableFieldState<T, U, U> = State(key, isLockable = false, initial, update)

    private class State<T, U>(
        key: FieldKey,
        isLockable: Boolean,
        initial: T,
        private val reducer: (T, U) -> T,
    ) : UpdatableFieldState<T, U, U>(key, isLockable) {

        override val value: T get() = stateFlow.value

        override fun buildSnapshotFlow(snapshotFlowBuilder: SnapshotFlowBuilder) {
            snapshotFlowBuilder.addSource(key, stateFlow)
        }

        private val stateFlow = MutableStateFlow(initial)

        override fun doUpdate(update: U): U {
            stateFlow.update { reducer(it, update) }
            return update
        }
    }
}
