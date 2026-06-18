package io.github.yulimitbreak.aseptic.state

import kotlinx.coroutines.flow.StateFlow

/**
 * An access point to the message queue for a specific field
 */
interface Messages<T : Any> {

    /**
     * A StateFlow of messages - emits oldest emitted message until it is consumed, or null, if there's
     * no unconsumed messages.
     *
     * Messages do not get consumed automatically, [consume] should be called explicitly after the message
     * was handled.
     */
    val flow: StateFlow<T?>

    /**
     * Consumes the currently active message, removing it from the queue and allowing the flow to emit the next one.
     */
    fun consume()
}
