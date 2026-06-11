@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.state.FieldState
import io.github.yulimitbreak.aseptic.state.SnapshotFlowBuilder
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import io.github.yulimitbreak.aseptic.state.UpdatableFieldState
import io.github.yulimitbreak.aseptic.util.ImmutableQueue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Declaration of a one-way message field for fire-and-forget events sent from state to UI.
 *
 * At runtime backed by a `StateFlow<ImmutableQueue<T>>` starting with an empty queue.
 * Operations enqueue messages under a mutex; the UI dequeues and consumes them.
 * This ensures no message is lost even if the UI is not currently collecting.
 *
 * It is a [FieldDeclaration] rather than a [LinkableFieldDeclaration] by design: message fields
 * cannot participate in the field hierarchy.
 *
 * @param T the type of the message. Must be non-null.
 * @see io.github.yulimitbreak.aseptic.schema.AsepticSchema.message
 */
class MessageFieldDeclaration<T : Any> internal constructor() : FieldDeclaration<T?>() {

    /**
     * The update type for a message field.
     * [Enqueue] adds a message to the back of the queue; [Dequeue] removes the front message.
     */
    sealed interface Update<out T> {
        data class Enqueue<T>(val message: T) : Update<T>
        data object Dequeue : Update<Nothing>
    }

    override fun convert(
        name: String,
        fields: StateContainerBuilder.FieldMap,
    ): FieldState<T?> = State(name)

    private class State<T : Any>(name: String) : UpdatableFieldState<T?, Update<T>, Unit>(name) {
        private val queueFlow = MutableStateFlow<ImmutableQueue<T>>(ImmutableQueue())

        override val value: T? get() = queueFlow.value.next

        override fun buildSnapshotFlow(snapshotFlowBuilder: SnapshotFlowBuilder) {
            snapshotFlowBuilder.addSource(name, queueFlow.map { it.next })
        }

        override fun doUpdate(update: Update<T>) {
            when (update) {
                is Update.Enqueue -> queueFlow.update { it + update.message }
                Update.Dequeue -> queueFlow.update { it.drop() }
            }
        }
    }
}
