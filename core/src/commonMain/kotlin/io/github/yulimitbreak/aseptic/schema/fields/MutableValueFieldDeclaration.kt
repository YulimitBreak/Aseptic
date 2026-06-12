@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.state.FieldKey
import io.github.yulimitbreak.aseptic.state.SnapshotFlowBuilder
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import io.github.yulimitbreak.aseptic.state.UpdatableFieldState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Declaration of a mutable field whose value is set directly by operations.
 *
 * @param initial initial value of the field
 * @param T the type of the field value.
 * @see io.github.yulimitbreak.aseptic.schema.AsepticSchema.mutable
 */
class MutableValueFieldDeclaration<T> internal constructor(
    internal val initial: T,
) : UpdatableFieldDeclaration<T, (T) -> T, T>() {
    override fun convert(
        key: FieldKey,
        fields: StateContainerBuilder.FieldMap,
    ): UpdatableFieldState<T, (T) -> T, T> = State(key, isLockable = true, initial)

    override fun convertForTracking(
        key: FieldKey,
        fields: StateContainerBuilder.FieldMap
    ): UpdatableFieldState<T, (T) -> T, T> = State(key, isLockable = false, initial)

    private class State<T>(
        key: FieldKey,
        isLockable: Boolean,
        initial: T
    ) : UpdatableFieldState<T, (T) -> T, T>(key, isLockable) {

        override fun doUpdate(update: (T) -> T): T {
            stateFlow.update(update)
            return stateFlow.value
        }

        override val value: T get() = stateFlow.value

        override fun buildSnapshotFlow(snapshotFlowBuilder: SnapshotFlowBuilder) {
            snapshotFlowBuilder.addSource(key, stateFlow)
        }

        private val stateFlow = MutableStateFlow(initial)
    }
}
