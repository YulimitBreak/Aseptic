package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.state.FieldState
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import io.github.yulimitbreak.aseptic.state.UpdatableFieldState
import io.github.yulimitbreak.aseptic.util.ImmutableQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Declaration of a one-way message field for fire-and-forget events sent from state to UI.
 *
 * At runtime backed by a `StateFlow<ImmutableQueue<T>>` starting with an empty queue.
 * Operations enqueue messages under a mutex; the UI dequeues and consumes them.
 * This ensures no message is lost even if the UI is not currently collecting.
 *
 * @param T the type of the message.
 * @see io.github.yulimitbreak.aseptic.schema.AsepticSchema.message
 */
class MessageFieldDeclaration<T> internal constructor() : FieldDeclaration<T?>() {
    override fun convert(
        flows: StateContainerBuilder.FlowMap,
        coroutineScope: CoroutineScope,
    ): FieldState<T?> = State(coroutineScope)

    private class State<T>(coroutineScope: CoroutineScope) : UpdatableFieldState<T?, T>() {
        private val queueFlow = MutableStateFlow<ImmutableQueue<T>>(ImmutableQueue())

        override val flow: StateFlow<T?> = queueFlow
            .map { it.firstOrNull() }
            .stateIn(coroutineScope, SharingStarted.Eagerly, null)

        override fun doUpdate(update: T) {
            queueFlow.update { it + update }
        }
    }
}
