package io.github.yulimitbreak.aseptic.state

import io.github.yulimitbreak.aseptic.AsepticInternal
import io.github.yulimitbreak.aseptic.schema.fields.FieldDeclaration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Wires all field declarations in a schema into a [StateContainer].
 *
 * Generated `XxxState` constructors instantiate this builder, builder methods for every
 * schema member in **declaration order**, then call [build] to produce the live [StateContainer].
 *
 * Declaration order matters: [UpdatableFieldState] entries are appended to [lockingOrder]
 * in the order they are added, and [StateContainer] must always acquire field mutexes in that
 * order. This prevents deadlocks when multiple field mutexes are held simultaneously.
 *
 * The [FlowMap] is threaded through every [FieldDeclaration.convert] call so that derived
 * fields can look up the [StateFlow] of their source fields. Because declarations are added in
 * order, a source field's flow is always registered before any field that depends on it.
 */
@AsepticInternal
class StateContainerBuilder(private val coroutineScope: CoroutineScope) {

    private val fields = mutableMapOf<String, FieldState<*>>()

    private val lockingOrder = mutableListOf<String>()

    private val flowMap = FlowMap()

    private val uiFields = mutableSetOf<String>()

    /**
     * Registers a non-field schema member as a static (never-changing) field.
     *
     * Used for schema constructor parameters and plain `val` members that are referenced by
     * derived fields.
     */
    fun <T> addStaticField(name: String, value: T) {
        fields[name] = StaticFieldState(value)
    }

    /**
     * Converts [field] into a live [FieldState] and registers it.
     *
     * Calls [FieldDeclaration.convert] with the current [FlowMap] (so derived fields can
     * resolve source flows) and the shared [coroutineScope] (for `stateIn` sharing). If the
     * resulting state is [UpdatableFieldState], the field name is appended to [lockingOrder].
     * Setting [uiVisible] as true adds the dependency of UI mapper on this field
     * Finally, the new flow is added to [FlowMap] so subsequent fields can use it as a source.
     */
    fun <T> addField(name: String, uiVisible: Boolean, field: FieldDeclaration<T>) {
        val state = field.convert(flowMap, coroutineScope)
        fields[name] = state
        if (state is UpdatableFieldState<*, *>) {
            lockingOrder += name
        }
        flowMap[field] = state.flow
        if (uiVisible) uiFields += name
    }

    fun build(): StateContainer {
        TODO()
    }

    private class StaticFieldState<T>(override val value: T) : FieldState<T>() {
        override val flow by lazy { MutableStateFlow(value) }
    }

    /**
     * Typed index from [FieldDeclaration] instances to their live [StateFlow]s. Populated
     * incrementally as fields are added via [addField] / [addStaticField].
     */
    @Suppress("UNCHECKED_CAST")
    internal class FlowMap {

        private val flows = mutableMapOf<FieldDeclaration<*>, StateFlow<*>>()

        operator fun <T> get(declaration: FieldDeclaration<T>): StateFlow<T> =
            flows[declaration] as StateFlow<T>

        operator fun <T> set(declaration: FieldDeclaration<T>, flow: StateFlow<T>) {
            flows[declaration] = flow
        }
    }
}
