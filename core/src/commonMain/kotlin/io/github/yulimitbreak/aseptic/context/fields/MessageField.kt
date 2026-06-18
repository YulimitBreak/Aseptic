@file:OptIn(AsepticInternal::class)

package io.github.yulimitbreak.aseptic.context.fields

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.schema.fields.MessageFieldDeclaration
import io.github.yulimitbreak.aseptic.state.FieldKey
import io.github.yulimitbreak.aseptic.state.StateContainer

/**
 * An accessor to [message][io.github.yulimitbreak.aseptic.schema.AsepticSchema.message] fields
 */
class MessageField<T : Any> @AsepticInternal constructor(
    internal val key: FieldKey,
    private val container: StateContainer,
) {

    /**
     * Add the [message] to the message queue
     */
    suspend fun emit(message: T) {
        container.update(key, MessageFieldDeclaration.Update.Enqueue(message))
    }
}
