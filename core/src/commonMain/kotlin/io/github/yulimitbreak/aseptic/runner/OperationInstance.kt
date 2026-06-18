package io.github.yulimitbreak.aseptic.runner

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.context.BaseAsepticContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * A holder for the [operation], it manages the operation execution, cancellation and cleanup.
 * Holds a [Job] for the operation
 *
 * Not thread-safe, access to it is only done through serialized command handling in [OperationRunner]
 */
@AsepticInternal
internal class OperationInstance<Context : BaseAsepticContext<*, *>>(
    val key: Any,
    private val operation: suspend Context.() -> Unit,
    parentScope: CoroutineScope,
    private val errorHandler: (Throwable) -> Unit,
    private val cleanup: (OperationInstance<Context>) -> Unit
) {

    @Volatile
    private var job: Job? = null

    // Using an intermediary scope allows to cancel the operation
    // regardless of whether or not the operation was started
    private val instanceParentJob = Job(parentScope.coroutineContext[Job]).apply {
        invokeOnCompletion { cause ->
            // If job == null but cause is cancellation, it means that scope was cancelled before
            // the operation could start, so this scope is doing the cleanup instead of the operation code
            if (cause is CancellationException && job == null) cleanup(this@OperationInstance)
        }
    }
    private val instanceScope = CoroutineScope(parentScope.coroutineContext + instanceParentJob)
    val isCancelled get() = instanceParentJob.isCancelled || job?.isCancelled == true

    fun cancel() {
        instanceScope.cancel()
    }

    @Suppress("TooGenericExceptionCaught")
    fun run(context: Context) {
        job = instanceScope.launch(start = CoroutineStart.LAZY) {
            try {
                context.operation()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                errorHandler(e)
            }
        }.apply {
            invokeOnCompletion { cleanup(this@OperationInstance) }
        }
        job?.start()
    }
}
