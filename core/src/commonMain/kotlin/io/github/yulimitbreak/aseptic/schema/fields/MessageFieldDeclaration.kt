package io.github.yulimitbreak.aseptic.schema.fields

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
class MessageFieldDeclaration<T> internal constructor() : FieldDeclaration<T>
