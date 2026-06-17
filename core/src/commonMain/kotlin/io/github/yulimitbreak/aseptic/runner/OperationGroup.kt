@file:OptIn(AsepticInternal::class)

package io.github.yulimitbreak.aseptic.runner

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.handle.BaseAsepticHandle

internal class OperationGroup<Handle : BaseAsepticHandle>(
    private val handle: Handle,
) {

    private val running = mutableSetOf<OperationInstance<Handle>>()

    private val queued = ArrayDeque<OperationInstance<Handle>>()

    fun dispatch(operation: OperationInstance<Handle>, dispatchPolicy: DispatchPolicy) {
        fun runNormally() {
            running.add(operation)
            operation.run(handle)
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

    fun remove(operation: OperationInstance<Handle>): Boolean {
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
            next.run(handle)
        }
        return false
    }
}
