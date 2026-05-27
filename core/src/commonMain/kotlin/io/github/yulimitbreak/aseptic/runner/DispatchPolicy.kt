package io.github.yulimitbreak.aseptic.runner

/**
 * How operation dispatcher will handle the situation when there are already operations
 * of the same type running
 */
enum class DispatchPolicy {
    /**
     * Run operation concurrently with already running operations (DEFAULT)
     */
    CONCURRENT,

    /**
     * Cancel all running and queued operations of the same type
     */
    CANCEL,

    /**
     * Only launch this operation once all currently running operations, and operations
     * queued before this one are done or canceled
     */
    QUEUE,

    /**
     * Immediately cancel this operation if there are any running or queued
     */
    DROP,
}
