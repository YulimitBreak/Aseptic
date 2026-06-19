package io.github.yulimitbreak.aseptic.runner

/**
 * How operation dispatcher will handle the situation when there are already operations
 * of the same type running. Only determines how the newly dispatched
 * operation is handled, does not affect anything after the dispatch.
 */
enum class DispatchPolicy {
    /**
     * Run operation concurrently with already running operations, skipping the queue (DEFAULT)
     */
    CONCURRENT,

    /**
     * Cancel all running and queued operations of the same type
     */
    CANCEL,

    /**
     * Only launch this operation once all currently running operations, and operations
     * queued before this one are done or cancelled
     */
    QUEUE,

    /**
     * Immediately cancel this operation if there are any running or queued
     */
    DROP,
}
