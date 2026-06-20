package io.github.yulimitbreak.aseptic.state

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.context.BaseAsepticContext
import io.github.yulimitbreak.aseptic.runner.DispatchPolicy
import io.github.yulimitbreak.aseptic.runner.OperationHandle
import io.github.yulimitbreak.aseptic.runner.OperationKey
import io.github.yulimitbreak.aseptic.runner.OperationRunner
import io.github.yulimitbreak.aseptic.schema.fields.MessageFieldDeclaration
import io.github.yulimitbreak.aseptic.util.UncheckedMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * An entry point to the Aseptic system.
 *
 * A generated state class has methods generated for all `@Operation` annotated functions,
 * handles for observing and consuming messages from
 * [message][io.github.yulimitbreak.aseptic.schema.AsepticSchema.message] fields, and [ui] state flow.
 *
 * The generated class constructor copies constructor parameters from the schema in order to create a
 * schema instance and use it to generate the state container
 *
 */
@AsepticInternal
abstract class BaseAsepticState<Context : BaseAsepticContext<*, *>, Ui>(
    private val scope: CoroutineScope,
    stateContainerGenerator: StateContainerBuilder.() -> StateContainer,
    contextGenerator: (StateContainer) -> Context,
    uiMapper: (UncheckedMap<FieldKey>) -> Ui,
) {

    private val stateContainer = stateContainerGenerator(StateContainerBuilder())
    private val runner = OperationRunner(scope, contextGenerator(stateContainer))

    /**
     * Returns StateFlow emitting current UI state
     */
    val ui = stateContainer.uiFlow(scope, uiMapper)

    /**
     * Sets a fallback error handler for uncaught exceptions happening in operations. It is not intended to be
     * a normal execution flow, operations should handle all errors themselves
     */
    fun onOperationError(onError: (Throwable) -> Unit) {
        runner.errorHandler = onError
    }

    /**
     * Dispatch a new operation
     */
    protected fun dispatchOperation(
        operation: suspend Context.() -> Unit,
        dispatchPolicy: DispatchPolicy,
        key: OperationKey,
    ): OperationHandle =
        runner.dispatch(operation, dispatchPolicy, key)

    /**
     * Provides access to message flow for a specified key and consumption method.
     *
     * Unchecked, only used from generated code
     */
    protected fun <Message : Any> messageFlow(key: FieldKey) = object : Messages<Message> {
        override val flow: StateFlow<Message?> = stateContainer.asFlow<Message?>(key)
            .stateIn(scope, started = SharingStarted.Eagerly, initialValue = stateContainer[key])

        override fun consume() {
            scope.launch { stateContainer.update(key, MessageFieldDeclaration.Update.Dequeue) }
        }
    }
}
