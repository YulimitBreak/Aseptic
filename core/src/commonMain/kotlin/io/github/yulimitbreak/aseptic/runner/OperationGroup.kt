@file:OptIn(AsepticInternal::class)

package io.github.yulimitbreak.aseptic.runner

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.context.BaseAsepticContext

/**
 * A container for all operations (running and queued) with the same key
 */
internal class OperationGroup<Context : BaseAsepticContext<*, *>>(
    private val context: Context,
) {

    private val running = mutableSetOf<OperationInstance<Context>>()

    private val queued = ArrayDeque<OperationInstance<Context>>()

    /**
     * Dispatch operation with the specified [DispatchPolicy]
     *
     * @see DispatchPolicy
     */
    fun dispatch(operation: OperationInstance<Context>, dispatchPolicy: DispatchPolicy) {
        fun runNormally() {
            running.add(operation)
            operation.run(context)
        }

        when (dispatchPolicy) {
            DispatchPolicy.CONCURRENT -> runNormally()
            DispatchPolicy.CANCEL -> {
                running.forEach { it.cancel() }
                queued.forEach { it.cancel() }
                runNormally()
            }
            DispatchPolicy.QUEUE -> {
                if (running.isNotEmpty()) {
                    queued.add(operation)
                } else {
                    runNormally()
                }
            }
            DispatchPolicy.DROP -> {
                if (running.isEmpty() && queued.isEmpty()) {
                    runNormally()
                } else {
                    operation.cancel()
                }
            }
        }
    }

    /**
     * Removes the operation from running/queue, and runs next one from
     * queue if there's no more running
     *
     * Returns `true` if both running and queue are empty, and
     * the group can be safely removed
     */
    fun remove(operation: OperationInstance<Context>): Boolean {
        running.remove(operation)
        queued.remove(operation)
        if (running.isEmpty()) {
            val next = queued.removeFirstOrNull() ?: return true
            if (next.isCancelled) {
                // To avoid running cancelled but not removed operation, we keep the running empty
                // and wait for that operation cleanup to remove it first and then try to trigger the queue
                // drain again
                return false
            }
            running.add(next)
            next.run(context)
        }
        return false
    }
}
