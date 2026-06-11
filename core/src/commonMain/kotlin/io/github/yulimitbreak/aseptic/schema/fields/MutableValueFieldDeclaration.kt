@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.state.SnapshotFlowBuilder
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import io.github.yulimitbreak.aseptic.state.UpdatableFieldState
import kotlinx.coroutines.CoroutineScope
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

        override fun buildSnapshotFlow(snapshotFlowBuilder: SnapshotFlowBuilder) {
            snapshotFlowBuilder.addSource(this, stateFlow)
        }

        private val stateFlow = MutableStateFlow(initial)
    }
}
