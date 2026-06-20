package io.github.yulimitbreak.aseptic.runner

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.context.BaseAsepticContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Manages running operations according to their keys and dispatch policies.
 *
 * All interactions are serialized using a [Channel]
 */
@AsepticInternal
internal class OperationRunner<Context : BaseAsepticContext<*, *>>(
    private val coroutineScope: CoroutineScope,
    context: Context,
) {

    private val operationScope = CoroutineScope(
        coroutineScope.coroutineContext + SupervisorJob(parent = coroutineScope.coroutineContext[Job])
    )

    @Volatile
    var errorHandler: (Throwable) -> Unit = { throw it }

    private val groups = mutableMapOf<OperationKey, OperationGroup<Context>>()

    private val commandChannel = Channel<Command<Context>>(capacity = Channel.UNLIMITED).also { channel ->
        coroutineScope.launch {
            for (command in channel) {
                when (command) {
                    is Command.Cancel -> command.operation.cancel()
                    is Command.Cleanup -> command.operation.let {
                        val isEmpty = groups[it.key]?.remove(it)
                        if (isEmpty == true && it.key !is StandardOperationKey) {
                            groups.remove(it.key)
                        }
                    }
                    is Command.Dispatch -> command.operation.let {
                        groups
                            .getOrPut(it.key) { OperationGroup(context) }
                            .dispatch(it, command.policy)
                    }
                }
            }
        }
    }

    /**
     * Dispatch a new [operation] with a specified [key] using a specified [dispatchPolicy]
     */
    fun dispatch(
        operation: suspend Context.() -> Unit,
        dispatchPolicy: DispatchPolicy,
        key: OperationKey,
    ): OperationHandle {
        val instance = OperationInstance(
            key = key,
            operation = operation,
            parentScope = operationScope,
            errorHandler = { errorHandler(it) },
            cleanup = { sendCommand(Command.Cleanup(it)) }
        )

        sendCommand(Command.Dispatch(instance, dispatchPolicy))

        return OperationHandle {
            sendCommand(Command.Cancel(instance))
        }
    }

    private fun sendCommand(
        command: Command<Context>,
    ) {
        commandChannel.trySend(command)
    }

    private sealed interface Command<out Context : BaseAsepticContext<*, *>> {

        class Dispatch<Context : BaseAsepticContext<*, *>>(
            val operation: OperationInstance<Context>,
            val policy: DispatchPolicy,
        ) : Command<Context>

        class Cancel(val operation: OperationInstance<*>) : Command<Nothing>

        class Cleanup<Context : BaseAsepticContext<*, *>>(val operation: OperationInstance<Context>) : Command<Context>
    }
}
