package io.github.yulimitbreak.aseptic.runner

/**
 * A handle to the dispatched operation
 */
fun interface OperationHandle {
    /**
     * Cancel a running or queued operation - the cancellation is not guaranteed to happen immediately,
     * but will happen before any dispatches and cancels requested after
     */
    fun cancel()
}
