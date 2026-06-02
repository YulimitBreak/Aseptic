@file:OptIn(io.github.yulimitbreak.aseptic.AsepticInternal::class)

package io.github.yulimitbreak.aseptic.schema.fields

import io.github.yulimitbreak.aseptic.state.FieldState
import io.github.yulimitbreak.aseptic.state.StateContainerBuilder
import io.github.yulimitbreak.aseptic.state.UpdatableFieldState
import io.github.yulimitbreak.aseptic.util.ImmutableQueue
import kotlinx.coroutines.flow.Flow
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
        fields: StateContainerBuilder.FieldMap,
    ): FieldState<T?> = State()

    private class State<T : Any> : UpdatableFieldState<T?, Update<T>>() {
        private val queueFlow = MutableStateFlow<ImmutableQueue<T>>(ImmutableQueue())

        override fun produceFlow(): Flow<T?> = queueFlow.map { it.next }

        override val value: T? get() = queueFlow.value.next

        override fun addUpdateCallback(callback: (T?) -> Unit) {
            throw IllegalStateException("Message fields should not be depended on")
        }

        override fun doUpdate(update: Update<T>) {
            when (update) {
                is Update.Enqueue -> queueFlow.update { it + update.message }
                Update.Dequeue -> queueFlow.update { it.drop() }
            }
        }
    }
}
