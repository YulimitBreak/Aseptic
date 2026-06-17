package io.github.yulimitbreak.aseptic.runner

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.handle.BaseAsepticHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

@AsepticInternal
internal class OperationInstance<Handle : BaseAsepticHandle>(
    val key: Any,
    private val operation: suspend Handle.() -> Unit,
    parentScope: CoroutineScope,
    private val errorHandler: (Throwable) -> Unit,
    private val cleanup: (OperationInstance<Handle>) -> Unit
) {

    @Volatile
    private var job: Job? = null

    private val instanceParentJob = Job(parentScope.coroutineContext[Job]).apply {
        invokeOnCompletion { cause ->
            if (cause is CancellationException && job == null) cleanup(this@OperationInstance)
        }
    }
    private val instanceScope = CoroutineScope(parentScope.coroutineContext + instanceParentJob)

    val isCancelled get() = instanceParentJob.isCancelled

    fun cancel() {
        instanceScope.cancel()
    }

    @Suppress("TooGenericExceptionCaught")
    fun run(handle: Handle) {
        job = instanceScope.launch(start = CoroutineStart.LAZY) {
            try {
                handle.operation()
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
