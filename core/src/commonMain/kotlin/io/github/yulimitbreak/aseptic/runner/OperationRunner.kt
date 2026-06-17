package io.github.yulimitbreak.aseptic.runner

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.handle.BaseAsepticHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

@AsepticInternal
internal class OperationRunner<Handle : BaseAsepticHandle>(
    private val coroutineScope: CoroutineScope,
    handle: Handle,
) {

    private val operationScope = CoroutineScope(
        coroutineScope.coroutineContext + SupervisorJob(parent = coroutineScope.coroutineContext[Job])
    )

    var errorHandler: (Throwable) -> Unit = {
        // TODO when logging implemented
    }

    private val groups = mutableMapOf<Any, OperationGroup<Handle>>()

    private val commandChannel = Channel<Command<Handle>>(capacity = Channel.UNLIMITED).also { channel ->
        coroutineScope.launch {
            for (command in channel) {
                when (command) {
                    is Command.Cancel -> command.operation.cancel()
                    is Command.Cleanup -> command.operation.let {
                        val isEmpty = groups[it.key]?.remove(it)
                        if (isEmpty == true && it.key !is StandardOpKey) {
                            groups.remove(it.key)
                        }
                    }
                    is Command.Dispatch -> command.operation.let {
                        groups
                            .getOrPut(it.key) { OperationGroup(handle) }
                            .dispatch(it, command.policy)
                    }
                }
            }
        }
    }

    fun dispatch(
        operation: suspend Handle.() -> Unit,
        key: Any,
        dispatchPolicy: DispatchPolicy,
    ): OperationHandle {
        val instance = OperationInstance(
            key = key,
            operation = operation,
            parentScope = operationScope,
            errorHandler = { errorHandler(it) },
            cleanup = { sendCommand(Command.Cleanup(it)) }
        )

        return OperationHandle {
            sendCommand(Command.Dispatch(instance, dispatchPolicy))
        }
    }

    private fun sendCommand(
        command: Command<Handle>
    ) {
        commandChannel.trySend(command)
    }

    private sealed interface Command<out Handle : BaseAsepticHandle> {

        class Dispatch<Handle : BaseAsepticHandle>(
            val operation: OperationInstance<Handle>,
            val policy: DispatchPolicy
        ) : Command<Handle>

        class Cancel(val operation: OperationInstance<*>) : Command<Nothing>

        class Cleanup<Handle : BaseAsepticHandle>(val operation: OperationInstance<Handle>) : Command<Handle>
    }
}
